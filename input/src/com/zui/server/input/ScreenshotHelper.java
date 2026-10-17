/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.zui.server.input;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.input.KeyGestureEvent;
import android.os.Handler;
import android.util.Log;
import android.view.Display;
import android.view.KeyCharacterMap;

/**
 * Handles the "screenshot" entry of the stock Lenovo stylus toolbox.
 *
 * <p>The toolbox (com.lenovo.penservice, kept as a prebuilt, uid system)
 * broadcasts {@code lenovo.styluspen.action.PEN_OPERATION} with {@code type=0}
 * and {@code source="styluspen"}. Stock ZUI picked that up inside services.jar
 * and forwarded it to ScreenshotHelper; here the same happens from
 * system_server. The receiver requires {@link #SENDER_PERMISSION}, which the
 * system-uid PenService holds and ordinary apps do not.
 *
 * <p>The toolbox item, and the pen corner swipe set to screenshot, offer a
 * region selection: the Android 17 large-screen screen capture toolbar of
 * SystemUI (config_enableLargeScreenScreencapture, on in SystemUIResTarget),
 * opened through KEY_GESTURE_TYPE_TAKE_PARTIAL_SCREENSHOT like the keyboard
 * shortcut.
 */
final class ScreenshotHelper {
    private static final String TAG = "LenovoPenScreenshot";

    private static final String ACTION_PEN_OPERATION = "lenovo.styluspen.action.PEN_OPERATION";
    private static final String EXTRA_SOURCE = "source";
    private static final String EXTRA_TYPE = "type";
    private static final String SOURCE_STYLUSPEN = "styluspen";
    private static final String SENDER_PERMISSION = "android.permission.STATUS_BAR_SERVICE";

    /** type 0 == screenshot; other values belong to toolbox items we do not handle. */
    private static final int TYPE_SCREENSHOT = 0;

    /**
     * The toolbox window is being torn down while the broadcast is in flight;
     * give it a moment so the region selector does not appear under a stale
     * floating window (stock waited 200 ms in its handler).
     */
    private static final long TOOLBOX_DISMISS_DELAY_MS = 300L;

    private final Context mContext;
    private final Handler mHandler;

    ScreenshotHelper(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
    }

    void start() {
        IntentFilter filter = new IntentFilter(ACTION_PEN_OPERATION);
        mContext.registerReceiver(Safe.receiver("pen operation", (c, i) -> onOperation(i)),
                filter, SENDER_PERMISSION, mHandler, Context.RECEIVER_EXPORTED);
        Log.i(TAG, "listening for " + ACTION_PEN_OPERATION);
    }

    private void onOperation(Intent intent) {
        if (intent == null) return;

        String source = intent.getStringExtra(EXTRA_SOURCE);
        if (source != null && !SOURCE_STYLUSPEN.equals(source)) {
            // Some other producer reusing the same action.
            return;
        }
        if (!intent.hasExtra(EXTRA_TYPE)) return;

        int type = intent.getIntExtra(EXTRA_TYPE, -1);
        if (type != TYPE_SCREENSHOT) {
            Log.i(TAG, "ignoring stylus operation type " + type);
            return;
        }

        Log.i(TAG, "stylus screenshot requested");
        Safe.postDelayed(mHandler, "stylus screenshot", this::takeScreenshot,
                TOOLBOX_DISMISS_DELAY_MS);
    }

    /**
     * Opens the screen capture toolbar in region mode: dispatches
     * KEY_GESTURE_TYPE_TAKE_PARTIAL_SCREENSHOT to the key gesture handler
     * SystemUI registers for it (services.jar is not on this jar's compile
     * path, hence the reflection; at run time it shares system_server's class
     * loader).
     */
    void takeScreenshot() {
        try {
            Class<?> internal = Class.forName("com.android.server.input.InputManagerInternal");
            Object service = Class.forName("com.android.server.LocalServices")
                    .getMethod("getService", Class.class).invoke(null, internal);
            KeyGestureEvent event = new KeyGestureEvent.Builder()
                    .setDeviceId(KeyCharacterMap.VIRTUAL_KEYBOARD)
                    .setKeyGestureType(KeyGestureEvent.KEY_GESTURE_TYPE_TAKE_PARTIAL_SCREENSHOT)
                    .setAction(KeyGestureEvent.ACTION_GESTURE_COMPLETE)
                    .setDisplayId(Display.DEFAULT_DISPLAY)
                    .build();
            internal.getMethod("handleKeyGestureInKeyGestureController", KeyGestureEvent.class)
                    .invoke(service, event);
            Log.i(TAG, "partial screenshot gesture dispatched to SystemUI");
        } catch (ReflectiveOperationException e) {
            Log.e(TAG, "cannot open the partial screenshot toolbar", e);
        }
    }
}
