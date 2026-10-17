/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.zui.server.input;

import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.TaskStackListener;
import android.app.WindowConfiguration;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemProperties;
import android.util.Log;
import android.view.SurfaceControlHdrLayerInfoListener;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Content adaptive backlight control (CABC) of the panel, through the Lenovo display HAL,
 * ported from the stock ZUI display service (services.jar ZuiDisplayService:
 * judgeCabcMode, requestCabcMode, writeCabcData, onOrResetCabcValue, the Dolby Vision
 * receiver and the HDR check):
 *
 * - 50 ms after a change of the tasks (MSG_JUDGE_CABC_MODE), the visible tasks pick the mode:
 *   UI (1) by default, moving image (3) for the stock list of video apps, games and office
 *   apps, off (0) for the stock gallery list; a visible task that is not full screen keeps
 *   UI. The mode is written when it differs from the last one (the first request always
 *   writes it).
 * - Off while HDR or Dolby Vision video is on the screen: the mode of the apps is only
 *   remembered meanwhile. HDR: written at once, back at once. Dolby Vision (the
 *   com.dolby.vision_play broadcast): off 1500 ms after the start, back at once at the stop.
 *   Stock read sys.zui.hdr_state, set by its own SurfaceFlinger; this uses the SurfaceFlinger
 *   HDR layer listener instead.
 * - All of it only with persist.debug.cabc_debug (true by default), as in stock.
 *
 * Not in stock: the panel turns CABC to UI mode every time it powers on (its on commands),
 * so the effective mode is written again when the screen comes on.
 */
final class CabcController {
    private static final String TAG = "LenovoCabc";

    /** Stock ZuiDisplayService.SYSPROP_CABC_DEBUG. */
    private static final String SYSPROP_CABC_DEBUG = "persist.debug.cabc_debug";

    private static final int MODE_OFF = 0;
    private static final int MODE_UI = 1;
    private static final int MODE_MOVIE = 3;

    /** Stock MSG_CABC_DELAY. */
    private static final long JUDGE_DELAY_MS = 50;
    /** Stock MyBroadcastReceiver: NetworkConstants.ETHER_MTU. */
    private static final long DOLBY_VISION_START_DELAY_MS = 1500;

    /** Stock ZuiDisplayService.DOLBY_VISION_ACTION: "start" / "stop" in the "action" extra. */
    private static final String ACTION_DOLBY_VISION = "com.dolby.vision_play";

    /** Stock ZuiDisplayService mMovModePackageList. */
    private static final Set<String> MOVIE_APPS = new HashSet<>(Arrays.asList(
            "com.ss.android.ugc.aweme", "com.tencent.qqlive", "com.smile.gifmaker",
            "com.youku.phone", "com.qiyi.video", "com.kuaishou.nebula", "com.qiyi.video.pad",
            "com.ss.android.ugc.aweme.lite", "com.hunantv.imgo.activity",
            "com.ss.android.article.video", "com.le123.ysdq", "com.lemon.lv", "com.duowan.kiwi",
            "com.kwai.videoeditor", "com.babycloud.hanju", "air.tv.douyu.android",
            "com.cmcc.cmvideo", "com.xunlei.downloadprovider", "cn.wps.moffice_eng",
            "com.tencent.wemeet.app", "com.microsoft.office.officehub", "com.jideos.jnotes",
            "com.newskyer.draw", "com.adsk.sketchbook", "com.wacom.bamboopapertab",
            "com.microsoft.office.word", "com.mubu.app", "com.youdao.note",
            "com.microsoft.office.excel", "com.microsoft.office.powerpoint",
            "net.xmind.doughnut", "com.fiistudio.fiinote", "com.tencent.tmgp.pubgmhd",
            "com.tencent.tmgp.sgame", "com.kiloo.subwaysurf",
            "com.minitech.miniworld.TMobile.Lenovo", "com.tencent.jkchess",
            "com.happyelements.AndroidAnimal", "com.netease.mc.lenovo", "com.tencent.tmgp.cod",
            "com.tencent.mf.uam", "com.tencent.ig", "com.popcap.pvz2cthdlx"));

    /** Stock ZuiDisplayService mOffModePackageList. */
    private static final Set<String> OFF_APPS = new HashSet<>(Arrays.asList(
            "com.zui.gallery", "com.fstar.Pattern"));

    private final Context mContext;
    private final Handler mHandler;

    /** Stock mCurrentCabcMode: the mode of the visible apps. */
    private int mCurrentMode = -1;
    private boolean mHdr;
    private boolean mDolbyVision;
    private boolean mCabcDebug;

    private final Runnable mRequestMode = Safe.run("cabc request", this::requestCabcMode);

    CabcController(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
    }

    void start() {
        if (!LenovoHal.available(LenovoHal.DISPLAY)) {
            Log.w(TAG, "no Lenovo display HAL, leaving CABC to the panel");
            return;
        }
        mCabcDebug = SystemProperties.getBoolean(SYSPROP_CABC_DEBUG, true);
        if (!mCabcDebug) {
            Log.w(TAG, SYSPROP_CABC_DEBUG + " is off, leaving CABC to the panel");
            return;
        }

        try {
            ActivityTaskManager.getService().registerTaskStackListener(new TaskStackListener() {
                @Override
                public void onTaskStackChanged() {
                    // binder thread: stock judgeCabcMode()
                    mHandler.removeCallbacks(mRequestMode);
                    mHandler.postDelayed(mRequestMode, JUDGE_DELAY_MS);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "registerTaskStackListener", e);
        }

        IBinder display = internalDisplayToken();
        if (display != null) {
            new SurfaceControlHdrLayerInfoListener() {
                @Override
                public void onHdrInfoChanged(IBinder displayToken, int numberOfHdrLayers,
                        int maxW, int maxH, int flags, float maxDesiredHdrSdrRatio) {
                    final boolean hdr = numberOfHdrLayers > 0;
                    Safe.post(mHandler, "cabc hdr", () -> onHdr(hdr));
                }
            }.register(display);
        }

        mContext.registerReceiver(Safe.receiver("cabc dolby vision", (c, i) -> {
            String action = i.getStringExtra("action");
            if (!mDolbyVision && "start".equals(action)) {
                mDolbyVision = true;
                onOrResetCabcValue(MODE_OFF, DOLBY_VISION_START_DELAY_MS);
            } else if (mDolbyVision && "stop".equals(action)) {
                mDolbyVision = false;
                onOrResetCabcValue(mCurrentMode, 0);
            }
        }), new IntentFilter(ACTION_DOLBY_VISION), null, mHandler, Context.RECEIVER_EXPORTED);

        mContext.registerReceiver(Safe.receiver("cabc screen on", (c, i) -> {
            if (mCurrentMode >= 0) {
                writeCabcData(mHdr || mDolbyVision ? MODE_OFF : mCurrentMode);
            }
        }), new IntentFilter(Intent.ACTION_SCREEN_ON), null, mHandler);

        mHandler.postDelayed(mRequestMode, JUDGE_DELAY_MS);
    }

    /** The token of the built-in display, from DisplayControl in services.jar. */
    private static IBinder internalDisplayToken() {
        try {
            Class<?> control = Class.forName("com.android.server.display.DisplayControl");
            long[] ids = (long[]) control.getMethod("getPhysicalDisplayIds").invoke(null);
            if (ids == null || ids.length == 0) return null;
            return (IBinder) control.getMethod("getPhysicalDisplayToken", long.class)
                    .invoke(null, ids[0]);
        } catch (Exception e) {
            Log.e(TAG, "display token", e);
            return null;
        }
    }

    /** Stock HDR check (sys.zui.hdr_state 1 / 0). */
    private void onHdr(boolean hdr) {
        if (!mHdr && hdr) {
            mHdr = true;
            onOrResetCabcValue(MODE_OFF, 0);
        } else if (mHdr && !hdr) {
            mHdr = false;
            onOrResetCabcValue(mCurrentMode, 0);
        }
    }

    /**
     * Stock requestCabcMode(): the visible tasks pick the mode; any visible task that is not
     * full screen (split screen, freeform) keeps UI mode.
     */
    private void requestCabcMode() {
        int mode = MODE_UI;
        try {
            List<ActivityManager.RunningTaskInfo> tasks =
                    mContext.getSystemService(ActivityManager.class).getRunningTasks(10);
            int found = MODE_UI;
            boolean fullScreen = true;
            for (ActivityManager.RunningTaskInfo task : tasks) {
                if (!task.isVisible || task.topActivity == null) continue;
                if (task.getWindowingMode() != WindowConfiguration.WINDOWING_MODE_FULLSCREEN) {
                    fullScreen = false;
                    break;
                }
                String pkg = task.topActivity.getPackageName();
                if (OFF_APPS.contains(pkg)) {
                    found = MODE_OFF;
                } else if (MOVIE_APPS.contains(pkg)) {
                    found = MODE_MOVIE;
                }
            }
            mode = fullScreen ? found : MODE_UI;
        } catch (Exception e) {
            Log.w(TAG, "running tasks", e);
            return;
        }
        if (mCurrentMode != mode) {
            mCurrentMode = mode;
            if (mDolbyVision || mHdr) {
                Log.i(TAG, "cabc stays off while dolby vision " + mDolbyVision + " or hdr "
                        + mHdr);
                return;
            }
            writeCabcData(mode);
        }
    }

    /** Stock onOrResetCabcValue(): MSG_WRITE_CABC_VALUE after the delay. */
    private void onOrResetCabcValue(int mode, long delayMs) {
        if (mCabcDebug) {
            mHandler.postDelayed(Safe.run("cabc write", () -> writeCabcData(mode)), delayMs);
        }
    }

    /** Stock writeCabcData() (the SLR check is of a disabled feature). */
    private void writeCabcData(int mode) {
        if (LenovoHal.setCabcMode(mode)) {
            Log.i(TAG, "cabc mode " + mode + " (apps " + mCurrentMode + ", hdr " + mHdr
                    + ", dolby vision " + mDolbyVision + ")");
        }
    }
}
