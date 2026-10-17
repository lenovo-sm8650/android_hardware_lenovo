/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.zui.server.input;

import android.content.Context;
import android.util.Log;
import android.view.KeyEvent;

import com.android.internal.os.DeviceKeyHandler;

/**
 * Entry point, instantiated by PhoneWindowManager.init() in system_server.
 * Everything here must fail soft: an exception escaping into a system_server
 * thread would take the whole system down.
 */
public final class KeyHandler implements DeviceKeyHandler {
    private static final String TAG = "LenovoInput";

    public KeyHandler(Context context) {
        try {
            InputCore.start(context);
        } catch (Throwable t) {
            Log.e(TAG, "failed to start", t);
        }
    }

    @Override
    public boolean handleUnhandledKeyEvent(KeyEvent event, int focusedUid) {
        InputCore core = InputCore.get();
        if (core == null) return false;
        try {
            return core.handleUnhandledKey(event, focusedUid);
        } catch (Throwable t) {
            Log.e(TAG, "handleUnhandledKeyEvent " + event, t);
            return false;
        }
    }

    @Override
    public KeyEvent handleKeyEvent(KeyEvent event) {
        InputCore core = InputCore.get();
        if (core == null) return event;
        try {
            return core.handleKey(event) ? null : event;
        } catch (Throwable t) {
            Log.e(TAG, "handleKeyEvent " + event, t);
            return event;
        }
    }
}
