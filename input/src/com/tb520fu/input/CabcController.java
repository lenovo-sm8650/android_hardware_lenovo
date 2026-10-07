/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.input;

import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.TaskStackListener;
import android.app.WindowConfiguration;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.SurfaceControlHdrLayerInfoListener;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Content adaptive backlight control (CABC) of the panel, through the Lenovo
 * display HAL, the way the stock ZUI display service (ZuiDisplayService) does
 * it:
 *
 * - UI mode for most apps, the moving image mode for the stock list of video
 *   apps, games and office apps, and off for the stock gallery list.
 * - Off while HDR or Dolby Vision video is on the screen: CABC dims the
 *   backlight behind the tone mapping, which assumes the panel brightness.
 *   Stock read sys.zui.hdr_state, set by its own SurfaceFlinger; this uses the
 *   SurfaceFlinger HDR layer listener instead.
 *
 * The panel turns CABC to UI mode every time it powers on (its on commands),
 * so the mode is set again when the screen comes on.
 */
final class CabcController {
    private static final String TAG = "TB520FUCabc";

    private static final int MODE_OFF = 0;
    private static final int MODE_UI = 1;
    private static final int MODE_MOVIE = 3;

    /** Stock ZuiDisplayService: "start" / "stop" in the "action" extra. */
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

    /** Mode for the visible apps, before HDR. */
    private int mAppMode = MODE_UI;
    private boolean mHdr;
    private boolean mDolbyVision;
    private int mMode = -1;

    private final Runnable mCheckApps = Safe.run("cabc apps", this::checkApps);

    CabcController(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
    }

    void start() {
        if (!LenovoHal.available(LenovoHal.DISPLAY)) {
            Log.w(TAG, "no Lenovo display HAL, leaving CABC to the panel");
            return;
        }

        try {
            ActivityTaskManager.getService().registerTaskStackListener(new TaskStackListener() {
                @Override
                public void onTaskStackChanged() {
                    // binder thread: debounce onto our handler (stock waits 50 ms)
                    mHandler.removeCallbacks(mCheckApps);
                    mHandler.postDelayed(mCheckApps, 50);
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
                    Safe.post(mHandler, "cabc hdr", () -> {
                        if (hdr == mHdr) return;
                        mHdr = hdr;
                        update(false);
                    });
                }
            }.register(display);
        }

        mContext.registerReceiver(Safe.receiver("cabc dolby vision", (c, i) -> {
            String action = i.getStringExtra("action");
            if ("start".equals(action)) {
                mDolbyVision = true;
            } else if ("stop".equals(action)) {
                mDolbyVision = false;
            } else {
                return;
            }
            update(false);
        }), new IntentFilter(ACTION_DOLBY_VISION), null, mHandler, Context.RECEIVER_EXPORTED);

        mContext.registerReceiver(Safe.receiver("cabc screen on", (c, i) -> update(true)),
                new IntentFilter(Intent.ACTION_SCREEN_ON), null, mHandler);

        checkApps();
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

    /**
     * Stock requestCabcMode(): the visible tasks pick the mode; any visible task
     * that is not full screen (split screen, freeform) keeps UI mode.
     */
    private void checkApps() {
        int mode = MODE_UI;
        try {
            List<ActivityManager.RunningTaskInfo> tasks =
                    mContext.getSystemService(ActivityManager.class).getRunningTasks(10);
            for (ActivityManager.RunningTaskInfo task : tasks) {
                if (!task.isVisible || task.topActivity == null) continue;
                if (task.getWindowingMode() != WindowConfiguration.WINDOWING_MODE_FULLSCREEN) {
                    mode = MODE_UI;
                    break;
                }
                String pkg = task.topActivity.getPackageName();
                if (OFF_APPS.contains(pkg)) {
                    mode = MODE_OFF;
                } else if (MOVIE_APPS.contains(pkg)) {
                    mode = MODE_MOVIE;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "running tasks", e);
            return;
        }
        if (mode == mAppMode) return;
        mAppMode = mode;
        update(false);
    }

    private void update(boolean force) {
        int mode = mHdr || mDolbyVision ? MODE_OFF : mAppMode;
        if (mode == mMode && !force) return;
        if (LenovoHal.setCabcMode(mode)) {
            if (mode != mMode) {
                Log.i(TAG, "cabc mode " + mode + " (app " + mAppMode + ", hdr " + mHdr
                        + ", dolby vision " + mDolbyVision + ")");
            }
            mMode = mode;
        }
    }
}
