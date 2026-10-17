/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.zui.server.input;

import android.app.ActivityTaskManager;
import android.app.TaskStackListener;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.hardware.input.InputManager;
import android.media.AudioManager;
import android.os.Handler;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Pen remote control (Settings > Pen > Remote control), ported from the stock
 * BluetoothPenInputPolicy.processStylusPenRemoteControl / processTopPkgChanged: while the switch is
 * on, the pen buttons drive the foreground app (camera shutter, slide show, page turn, media keys,
 * scrolling in the short video apps) instead of reaching PenService.
 */
final class PenRemoteControl {
    private static final String TAG = "LenovoPenRemote";

    /** Remote control switch of the pen settings (Settings.Secure). */
    private static final String SETTING_REMOTE_CONTROL = "pen_set_remote_control_on";
    /** Kind of the foreground app as remote control sees it, read by PenService. */
    private static final String SETTING_REMOTE_STATE = "pen_remote_control_state";
    private static final String ACTION_REMOTE_CHANGED =
            "lenovo.intent.action.INPUT_DEVICE_PEN_REMOUNT_CONTROL_CHANGED";
    /** Stock STYLUS_PEN_REMOTE_CONTROL_CLICK_NOTIFY_TIMES. */
    private static final int FAIL_NOTIFY_TIMES = 2;

    // Stock WhiteListManager.getRemoteControlPackageType()
    private static final int TYPE_CAMERA = 1;
    private static final int TYPE_OFFICE = 2;
    private static final int TYPE_VIDEO = 3;
    private static final int TYPE_MUSIC = 4;
    private static final int TYPE_READING = 5;
    // Stock framework-res config_stylus_pen_remote_control_*_packages (TB520FU ROW).
    private static final List<String> CAMERA_PACKAGES =
            Arrays.asList("com.lenovotab.camera", "com.zui.camera");
    private static final List<String> MUSIC_PACKAGES = Arrays.asList(
            "com.google.android.apps.youtube.music", "com.tblenovo.soundrecorder",
            "com.tencent.qqmusic", "com.tencent.qqmusicpad", "com.netease.cloudmusic",
            "cn.kuwo.player", "cmccwm.mobilemusic", "com.kugou.android");
    private static final List<String> OFFICE_PACKAGES = Arrays.asList(
            "com.microsoft.office.officehub", "com.microsoft.office.officehubrow",
            "com.microsoft.office.powerpoint", "com.google.android.apps.docs.editors.slides",
            "cn.wps.moffice_eng", "com.zuxos.wpslauncher", "com.mobisystems.office",
            "com.infraware.office.link");
    private static final List<String> READING_PACKAGES = Arrays.asList(
            "com.dragon.read", "com.kmxs.reader", "com.esbook.reader", "com.qq.reader",
            "com.shuqi.controller", "com.tencent.weread", "com.chaozh.iReaderFree",
            "com.qidian.QDReader", "com.jd.app.reader", "com.jjwxc.reader");
    private static final List<String> VIDEO_PACKAGES = Arrays.asList(
            "com.google.android.youtube", "com.tencent.qqlive", "com.qiyi.video",
            "com.qiyi.video.pad", "com.youku.phone", "tv.danmaku.bili",
            "com.ss.android.ugc.aweme", "com.smile.gifmaker");
    /** Stock sendInjectEvents(): swipe time and length of the short video scroll. */
    private static final int SWIPE_DURATION_MS = 100;
    private static final float SWIPE_DISTANCE = 800f;

    private final Context mContext;
    private final Handler mHandler;
    private final BluetoothPenConnectPolicy mPen;
    private final BluetoothPenInputPolicy mKeys;

    private volatile boolean mEnabled;
    private volatile int mType;
    private String mFocusPkg;
    private final Rect mFocusBounds = new Rect();
    private int mFocusDisplay;
    private boolean mShortVideo;
    private boolean mPptStarted;
    private int mFailClicks;
    private final Runnable mCheckFocus = Safe.run("pen remote focus", this::checkFocus);

    PenRemoteControl(Context context, Handler handler, BluetoothPenConnectPolicy pen,
            BluetoothPenInputPolicy keys) {
        mContext = context;
        mHandler = handler;
        mPen = pen;
        mKeys = keys;
    }

    void start() {
        ContentResolver cr = mContext.getContentResolver();
        cr.registerContentObserver(Settings.Secure.getUriFor(SETTING_REMOTE_CONTROL), false,
                Safe.observer(mHandler, "pen remote control", uri -> readEnabled()),
                UserHandle.USER_ALL);
        readEnabled();
        try {
            ActivityTaskManager.getService().registerTaskStackListener(new TaskStackListener() {
                @Override
                public void onTaskStackChanged() {
                    // binder thread: debounce onto our handler
                    mHandler.removeCallbacks(mCheckFocus);
                    mHandler.postDelayed(mCheckFocus, 100);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "registerTaskStackListener", e);
        }
        checkFocus();
    }

    boolean isEnabled() {
        return mEnabled;
    }

    /** Stock getClickStatusType(): click type of a pen button key, -1 for none. */
    static int clickType(int code) {
        switch (code) {
            case BluetoothPenInputPolicy.KEY_ONE_CLICK: return 0;
            case BluetoothPenInputPolicy.KEY_TWO_CLICK: return 1;
            case BluetoothPenInputPolicy.KEY_THREE_CLICK: return 2;
            case BluetoothPenInputPolicy.KEY_LONG_CLICK: return 3;
            case BluetoothPenInputPolicy.KEY_PRESS_CLICK: return 4;
            case BluetoothPenInputPolicy.KEY_D_SLIDE_DOWN: return 6;
            case BluetoothPenInputPolicy.KEY_D_SLIDE_UP: return 5;
            default: return -1;
        }
    }

    /**
     * Stock processStylusPenKeyEvent() with the remote control on. Returns whether the key was
     * used; two unused keys in a row tell PenService (PEN_REMOUNT_CONTROL_CHANGED).
     */
    boolean onKey(int type, boolean wake) {
        if (type == 4) {
            post(() -> mKeys.sendClick(4, wake));
            return true;
        }
        boolean used = process(type, wake);
        if (used) {
            mFailClicks = 0;
        } else if (mFailClicks == FAIL_NOTIFY_TIMES - 1) {
            Intent i = new Intent(ACTION_REMOTE_CHANGED);
            i.addFlags(Intent.FLAG_RECEIVER_FOREGROUND | Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
            mContext.sendBroadcastAsUser(i, UserHandle.ALL);
            mFailClicks = 0;
        } else {
            mFailClicks++;
        }
        return used;
    }

    /** Stock setStylusPenRemountControl(). */
    private void readEnabled() {
        mEnabled = "1".equals(Settings.Secure.getStringForUser(mContext.getContentResolver(),
                SETTING_REMOTE_CONTROL, UserHandle.USER_CURRENT));
        mFailClicks = 0;
        Log.d(TAG, "pen remote control " + mEnabled);
    }

    /** Stock processTopPkgChanged(): which kind of app the remote control drives. */
    private void checkFocus() {
        String pkg = null;
        try {
            ActivityTaskManager.RootTaskInfo info =
                    ActivityTaskManager.getService().getFocusedRootTaskInfo();
            if (info != null && info.topActivity != null) {
                pkg = info.topActivity.getPackageName();
                mFocusBounds.set(info.bounds);
                mFocusDisplay = info.displayId;
            }
        } catch (Exception e) {
            Log.w(TAG, "focused task", e);
        }
        if (Objects.equals(pkg, mFocusPkg)) return;
        mFocusPkg = pkg;
        mType = packageType(pkg);
        Settings.Global.putInt(mContext.getContentResolver(), SETTING_REMOTE_STATE, mType);
        mShortVideo = "com.smile.gifmaker".equals(pkg) || "com.kwai.video".equals(pkg)
                || "com.ss.android.ugc.trill".equals(pkg)
                || "com.zhiliaoapp.musically".equals(pkg);
        Log.d(TAG, "focus " + pkg + " type " + mType);
    }

    private static int packageType(String pkg) {
        if (TextUtils.isEmpty(pkg)) return -1;
        if (READING_PACKAGES.contains(pkg)) return TYPE_READING;
        if (CAMERA_PACKAGES.contains(pkg)) return TYPE_CAMERA;
        if (OFFICE_PACKAGES.contains(pkg)) return TYPE_OFFICE;
        if (VIDEO_PACKAGES.contains(pkg)) return TYPE_VIDEO;
        return MUSIC_PACKAGES.contains(pkg) ? TYPE_MUSIC : 0;
    }

    /** Stock processStylusPenRemoteControl(). */
    private boolean process(int type, boolean wake) {
        // the Pen Pro has no single click: its double tap is the main button
        if (mPen.isParkerConnected() && type == 1) type = 0;
        final int t = type;
        int state = mType;
        boolean musicActive = false;
        if (state == TYPE_CAMERA) {
            post(() -> mKeys.sendClick(t, wake));
        } else if (state == TYPE_READING) {
            if (t == 1 || t == 6) post(() -> mKeys.injectKey(KeyEvent.KEYCODE_DPAD_RIGHT));
            else if (t == 2 || t == 5) post(() -> mKeys.injectKey(KeyEvent.KEYCODE_DPAD_LEFT));
        } else if (state == TYPE_OFFICE) {
            if (mPen.isParkerConnected() && t == 0) {
                // slide show: start (F5), then leave (ESC)
                final int key = mPptStarted ? KeyEvent.KEYCODE_ESCAPE : KeyEvent.KEYCODE_F5;
                mPptStarted = !mPptStarted;
                post(() -> mKeys.injectKey(key));
                return true;
            }
            if (t == 0 || t == 6) post(() -> mKeys.injectKey(KeyEvent.KEYCODE_DPAD_DOWN));
            else if (t == 1 || t == 5) post(() -> mKeys.injectKey(KeyEvent.KEYCODE_DPAD_UP));
        } else {
            // music / video / anything else: media keys, or a swipe in the short video apps
            final boolean swipe = mShortVideo;
            if (t == 0) {
                if (swipe) post(() -> swipe(0));
                else post(() -> mKeys.injectKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
            } else if (t == 1 || t == 6) {
                if (swipe) post(() -> swipe(2));
                else post(() -> mKeys.injectKey(KeyEvent.KEYCODE_MEDIA_NEXT));
            } else if (t == 2 || t == 5) {
                if (swipe) post(() -> swipe(1));
                else post(() -> mKeys.injectKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS));
            }
            musicActive = mContext.getSystemService(AudioManager.class).isMusicActive();
        }
        if (state > 0 && t > -1 && t < 4) return true;
        return musicActive;
    }

    /** Stock sendInjectEvents(): 0 tap, 1 swipe down, 2 swipe up at the centre of the app. */
    private void swipe(int kind) {
        InputManager im = mContext.getSystemService(InputManager.class);
        float x = mFocusBounds.centerX();
        float y = mFocusBounds.centerY();
        long down = SystemClock.uptimeMillis();
        injectTouch(im, down, down, MotionEvent.ACTION_DOWN, x, y);
        float endY = y;
        if (kind != 0) {
            endY = kind == 1 ? y + SWIPE_DISTANCE : y - SWIPE_DISTANCE;
            for (long now = SystemClock.uptimeMillis(); now < down + SWIPE_DURATION_MS;
                    now = SystemClock.uptimeMillis()) {
                float f = (now - down) / (float) SWIPE_DURATION_MS;
                injectTouch(im, down, now, MotionEvent.ACTION_MOVE, x, y + (endY - y) * f);
            }
        }
        injectTouch(im, down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, endY);
    }

    private void injectTouch(InputManager im, long downTime, long eventTime, int action, float x,
            float y) {
        MotionEvent ev = MotionEvent.obtain(downTime, eventTime, action, x, y, 0);
        ev.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        ev.setDisplayId(mFocusDisplay);
        im.injectInputEvent(ev, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
        ev.recycle();
    }

    private void post(Runnable r) {
        Safe.post(mHandler, "pen remote", r);
    }
}
