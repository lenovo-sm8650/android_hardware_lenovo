/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.input;

import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;

/**
 * Stylus gestures the stock ZUI split over services.jar (CapacitorPenInputPolicy) and the
 * ZUI launcher (WritePenCornerInputConsumer), fed by [StylusMonitor]:
 *
 * <ul>
 *   <li>Stylus barrel buttons of a capacitor pen. Releasing the lower button (BUTTON_STYLUS_PRIMARY)
 *       while hovering runs the {@code handwriting_pen_down_key} action (back / home / none);
 *       releasing the upper one (BUTTON_STYLUS_SECONDARY) runs {@code handwriting_pen_up_key}
 *       (show_hide_menu = toolbox, none, or the package to open); drawing with the upper
 *       button held creates a note. All of them are broadcasts / starts for PenService.</li>
 *   <li>Swipe up from the bottom-left / bottom-right corner with the stylus
 *       ({@code swipe_bottom_left} / {@code swipe_bottom_right}: easy_jot, screenshot,
 *       create_note, none), at 22..67 degrees (left) or 113..158 degrees (right) and at least
 *       400 px. The stylus pointer is pilfered from the apps once the swipe is recognized.</li>
 * </ul>
 */
final class PenGestures {
    private static final String TAG = "TB520FUPenGestures";

    private static final String PEN_PACKAGE = "com.lenovo.penservice";
    private static final String ACTION_CONTROL_MENU = "lenovo.intent.odm.action.PEN_CONTROL_MEMU";
    private static final String ACTION_CREATE_NOTE = "lenovo.intent.odm.action.PEN_CREATE_NOTE";
    private static final String NOTES_PACKAGE = "com.zui.notes";

    private static final String SETTING_UP_KEY = "handwriting_pen_up_key";
    private static final String SETTING_DOWN_KEY = "handwriting_pen_down_key";
    private static final String SETTING_SWIPE_LEFT = "swipe_bottom_left";
    private static final String SETTING_SWIPE_RIGHT = "swipe_bottom_right";
    private static final String SETTING_WRITING = "lenovo_pen_writing";

    private static final String KEY_SHOW_HIDE_MENU = "show_hide_menu";
    private static final String KEY_NONE = "none";
    private static final String KEY_BACK = "back";
    private static final String KEY_HOME = "home";

    private static final String SWIPE_CREATE_NOTE = "create_note";
    private static final String SWIPE_EASY_JOT = "easy_jot";
    private static final String SWIPE_SCREENSHOT = "screenshot";
    private static final ComponentName EASY_JOT = new ComponentName(PEN_PACKAGE,
            "com.lenovo.pen.easyjot.EasyJotService");
    private static final String ACTION_EASY_JOT = "com.lenovo.pen.easyjot.SCREENSHOT";

    /** Stock WritePenCornerInputConsumer: corner width 100dp, 400 px travel. */
    private static final float CORNER_WIDTH_DP = 100f;
    private static final float CORNER_HEIGHT_DP = 80f;
    private static final float MIN_TRAVEL_SQUARED = 160000f;
    private static final long BUTTON_RELEASE_DELAY_MS = 50;

    private final Context mContext;
    private final Handler mHandler;
    private final PenScreenshot mScreenshot;

    // capacitor pen buttons (stock processNonBtPenKeyEvents)
    private boolean mPrimaryDown;
    private boolean mSecondaryDown;
    private boolean mProcessed;
    private boolean mRealAction;

    // corner swipe
    private boolean mTracking;
    private boolean mPilfered;
    private boolean mLeftCorner;
    private String mSwipeAction;
    private float mStartX;
    private float mStartY;

    PenGestures(Context context, Handler handler, PenScreenshot screenshot) {
        mContext = context;
        mHandler = handler;
        mScreenshot = screenshot;
    }

    /** Called on our handler for every stylus (not eraser) event of the default display. */
    void onStylusEvent(MotionEvent ev, Runnable pilferPointers) {
        onButtons(ev);
        onCornerSwipe(ev, pilferPointers);
    }

    // ---- barrel buttons ----

    private void onButtons(MotionEvent ev) {
        int action = ev.getActionMasked();
        int buttons = ev.getButtonState()
                & (MotionEvent.BUTTON_STYLUS_PRIMARY | MotionEvent.BUTTON_STYLUS_SECONDARY);
        switch (action) {
            case MotionEvent.ACTION_MOVE:
                mRealAction = true;
                if (mSecondaryDown && buttons != 0 && !(mPrimaryDown && buttons == 0)) {
                    if (!mProcessed) {
                        mProcessed = true;
                        post(this::createNote);
                    }
                }
                mPrimaryDown = buttons == MotionEvent.BUTTON_STYLUS_PRIMARY;
                mSecondaryDown = buttons == MotionEvent.BUTTON_STYLUS_SECONDARY;
                break;
            case MotionEvent.ACTION_HOVER_MOVE:
                mRealAction = true;
                if (mPrimaryDown && buttons == 0) {
                    if (!mProcessed) {
                        mProcessed = true;
                        mHandler.postDelayed(Safe.run("pen lower button", this::onLowerButton),
                                BUTTON_RELEASE_DELAY_MS);
                    }
                } else if (mSecondaryDown && buttons == 0) {
                    mProcessed = true;
                    mHandler.postDelayed(Safe.run("pen upper button", this::onUpperButton),
                            BUTTON_RELEASE_DELAY_MS);
                }
                mPrimaryDown = buttons == MotionEvent.BUTTON_STYLUS_PRIMARY;
                mSecondaryDown = buttons == MotionEvent.BUTTON_STYLUS_SECONDARY;
                break;
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_HOVER_ENTER:
            case MotionEvent.ACTION_HOVER_EXIT:
                mRealAction = false;
                mPrimaryDown = false;
                mSecondaryDown = false;
                mProcessed = false;
                break;
            default:
                break;
        }
    }

    private void onUpperButton() {
        if (!mRealAction) return;
        String key = Settings.System.getStringForUser(mContext.getContentResolver(),
                SETTING_UP_KEY, UserHandle.USER_CURRENT);
        if (TextUtils.isEmpty(key)) {
            Log.d(TAG, "no pen up key configured");
        } else if (KEY_SHOW_HIDE_MENU.equals(key)) {
            // toolbox (floating menu) of PenService
            sendToPenService(ACTION_CONTROL_MENU);
        } else if (!KEY_NONE.equals(key)) {
            openApp(key);
        }
        mProcessed = false;
    }

    private void onLowerButton() {
        if (!mRealAction) return;
        String key = Settings.System.getStringForUser(mContext.getContentResolver(),
                SETTING_DOWN_KEY, UserHandle.USER_CURRENT);
        if (isKeyguardLocked() || isWritingApp() || TextUtils.isEmpty(key)) {
            mProcessed = false;
            return;
        }
        if (KEY_BACK.equals(key)) {
            injectKey(KeyEvent.KEYCODE_BACK);
        } else if (KEY_HOME.equals(key)) {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addCategory(Intent.CATEGORY_DEFAULT).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivityAsUser(home, UserHandle.CURRENT_OR_SELF);
        }
        mProcessed = false;
    }

    private void createNote() {
        if (!mRealAction) return;
        sendToPenService(ACTION_CREATE_NOTE);
    }

    private void openApp(String pkg) {
        if (isKeyguardLocked() || isWritingApp()) return;
        Intent launch = mContext.getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch == null) {
            Log.i(TAG, "no launch intent for " + pkg);
            return;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivityAsUser(launch, UserHandle.CURRENT_OR_SELF);
    }

    private void sendToPenService(String action) {
        Intent i = new Intent(action);
        i.setPackage(PEN_PACKAGE);
        i.addFlags(Intent.FLAG_RECEIVER_FOREGROUND | Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
        mContext.sendBroadcastAsUser(i, UserHandle.CURRENT_OR_SELF);
        Log.d(TAG, "sent " + action);
    }

    private boolean isKeyguardLocked() {
        return mContext.getSystemService(KeyguardManager.class).isKeyguardLocked();
    }

    /** lenovo_pen_writing == 1: a writing app is in front (big palm list). */
    private boolean isWritingApp() {
        return Settings.System.getInt(mContext.getContentResolver(), SETTING_WRITING, -1) == 1;
    }

    private void injectKey(int code) {
        InputManager im = mContext.getSystemService(InputManager.class);
        long now = SystemClock.uptimeMillis();
        for (int action : new int[] {KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP}) {
            KeyEvent ev = new KeyEvent(now, now, action, code, 0, 0,
                    KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD);
            im.injectInputEvent(ev, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
        }
    }

    // ---- bottom corner swipes ----

    private void onCornerSwipe(MotionEvent ev, Runnable pilferPointers) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startTracking(ev);
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                mTracking = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (mTracking && !mPilfered && inRange(ev, false)) {
                    mPilfered = true;
                    pilferPointers.run();
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mTracking && inRange(ev, true)) {
                    final String action = mSwipeAction;
                    post(() -> runSwipeAction(action));
                }
                mTracking = false;
                break;
            case MotionEvent.ACTION_CANCEL:
                mTracking = false;
                break;
            default:
                break;
        }
    }

    private void startTracking(MotionEvent ev) {
        mTracking = false;
        mPilfered = false;
        Display display = mContext.getSystemService(DisplayManager.class)
                .getDisplay(Display.DEFAULT_DISPLAY);
        DisplayMetrics metrics = new DisplayMetrics();
        display.getRealMetrics(metrics);
        float x = ev.getRawX();
        float y = ev.getRawY();
        float cornerWidth = CORNER_WIDTH_DP * metrics.density;
        if (y < metrics.heightPixels - CORNER_HEIGHT_DP * metrics.density) return;
        boolean left = x < cornerWidth;
        boolean right = x > metrics.widthPixels - cornerWidth;
        if (!left && !right) return;
        String action = Settings.System.getStringForUser(mContext.getContentResolver(),
                left ? SETTING_SWIPE_LEFT : SETTING_SWIPE_RIGHT, UserHandle.USER_CURRENT);
        if (TextUtils.isEmpty(action) || KEY_NONE.equals(action)) return;
        mLeftCorner = left;
        mSwipeAction = action;
        mStartX = x;
        mStartY = y;
        mTracking = true;
    }

    /** The swipe went far enough (when {@code complete}) in the corner's direction. */
    private boolean inRange(MotionEvent ev, boolean complete) {
        float dx = ev.getRawX() - mStartX;
        float dy = mStartY - ev.getRawY();
        float squared = dx * dx + dy * dy;
        float slop = 48f * mContext.getResources().getDisplayMetrics().density;
        if (squared < (complete ? MIN_TRAVEL_SQUARED : slop * slop)) return false;
        double degrees = Math.toDegrees(Math.atan2(dy, dx));
        return mLeftCorner ? degrees > 22 && degrees < 67 : degrees > 113 && degrees < 158;
    }

    private void runSwipeAction(String action) {
        Log.i(TAG, "bottom " + (mLeftCorner ? "left" : "right") + " corner swipe: " + action);
        switch (action) {
            case SWIPE_SCREENSHOT:
                mScreenshot.takeScreenshot();
                break;
            case SWIPE_EASY_JOT: {
                Intent i = new Intent(ACTION_EASY_JOT).setComponent(EASY_JOT);
                i.putExtra("from", "swipe_bottom_corner");
                mContext.startServiceAsUser(i, UserHandle.CURRENT_OR_SELF);
                break;
            }
            case SWIPE_CREATE_NOTE:
                // The note lives in ZUI Notes; without it PenService resets the setting itself.
                try {
                    mContext.getPackageManager().getPackageInfo(NOTES_PACKAGE, 0);
                    sendToPenService(ACTION_CREATE_NOTE);
                } catch (Exception e) {
                    Log.i(TAG, "ZUI Notes missing, no note to create");
                }
                break;
            default:
                break;
        }
    }

    private void post(Runnable r) {
        Safe.post(mHandler, "pen gesture", r);
    }
}
