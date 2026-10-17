/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.zui.server.input;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;

/**
 * System side of the Lenovo pen ink mode ({@code Settings.Global.pen_ink_mode}, written by
 * PenService, whose "InkWindow" overlay does the drawing).
 *
 * <p>What stock ZuiHandWritingService and the native input dispatcher keep track of and
 * what AOSP can do here:
 * <ul>
 *   <li>{@code stylus_drawn_and_screenlock}: set to 1 on screen-on when the stylus drew while
 *       the ink mode was open (and not yet saved/closed), cleared on unlock. WindowManager's
 *       DisplayRotation keeps the current rotation while it is 1 so the ink does not rotate
 *       away under the user (frameworks/base). The dispatcher's "stylus down during ink mode"
 *       flag is replaced by the stylus monitor's ACTION_DOWN.</li>
 *   <li>{@code pen_ink_mode} == 1 also blocks the rotation suggestion button (DisplayRotation).</li>
 *   <li>{@code inkmode_intercept_events} (Settings.System, written for the current user by
 *       ZuiHandWritingManager.setInkModeInterceptEvents) is only logged: routing the stylus
 *       events to the non-touchable InkWindow instead of the app is done by the ZUI
 *       InputDispatcher, which AOSP does not have.</li>
 * </ul>
 */
final class ZuiHandWritingService {
    private static final String TAG = "LenovoPenInk";

    private static final String SETTING_INK_MODE = "pen_ink_mode";
    private static final String SETTING_INTERCEPT = "inkmode_intercept_events";
    private static final String SETTING_DRAWN_LOCKED = "stylus_drawn_and_screenlock";

    private final Context mContext;
    private final Handler mHandler;
    private boolean mOpen;
    private boolean mDrawn;

    ZuiHandWritingService(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
    }

    void start() {
        ContentResolver cr = mContext.getContentResolver();
        cr.registerContentObserver(Settings.Global.getUriFor(SETTING_INK_MODE), false,
                Safe.observer(mHandler, "ink mode", uri -> update()));
        cr.registerContentObserver(Settings.System.getUriFor(SETTING_INTERCEPT), false,
                Safe.observer(mHandler, "ink intercept", uri -> Log.d(TAG, "intercept events "
                        + Settings.System.getInt(cr, SETTING_INTERCEPT, 0))));
        mContext.registerReceiver(Safe.receiver("ink screen on", (c, i) -> publishDrawn()),
                new IntentFilter(Intent.ACTION_SCREEN_ON), null, mHandler);
        mContext.registerReceiver(Safe.receiver("ink unlock", (c, i) -> putDrawnLocked(false)),
                new IntentFilter(Intent.ACTION_USER_PRESENT), null, mHandler);
        update();
    }

    private void update() {
        boolean open = Settings.Global.getInt(mContext.getContentResolver(), SETTING_INK_MODE, 0)
                == 1;
        if (open == mOpen) return;
        mOpen = open;
        Log.i(TAG, "ink mode " + (open ? "opened" : "closed"));
        if (!open) {
            mDrawn = false;
            putDrawnLocked(false);
        }
    }

    /** Called on our handler for every stylus ACTION_DOWN. */
    void onStylusDown() {
        if (mOpen) mDrawn = true;
    }

    private void publishDrawn() {
        putDrawnLocked(mOpen && mDrawn);
    }

    private void putDrawnLocked(boolean locked) {
        ContentResolver cr = mContext.getContentResolver();
        if (Settings.Global.getInt(cr, SETTING_DRAWN_LOCKED, 0) != (locked ? 1 : 0)) {
            Settings.Global.putInt(cr, SETTING_DRAWN_LOCKED, locked ? 1 : 0);
        }
    }
}
