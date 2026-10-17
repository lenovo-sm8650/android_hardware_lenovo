/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.zui.server.input;

import android.content.Context;
import android.content.Intent;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

/**
 * Lenovo pen button / barrel gestures. The pen keylayouts
 * (Vendor_17ef_Product_*.kl) map the ZUI-only PEN_* usages onto F13..F23;
 * shortcuts are sent to PenService, while the barrel double tap is delivered
 * to the focused app as ZUI keycode 601 before any unhandled-key fallback
 * (stock BluetoothPenInputPolicy).
 */
final class BluetoothPenInputPolicy {
    private static final String TAG = "LenovoPenKeys";

    private static final int VENDOR_LENOVO = 0x17ef;

    private static final String ACTION_CLICK = "lenovo.intent.action.INPUT_DEVICE_CLICK_STATE_CHANGED";
    private static final String EXTRA_DEVICE_TYPE = "lenovo.intent.extra.DEVICE_TYPE";
    private static final String EXTRA_LENOVO_DEVICE = "lenovo.intent.extra.LENOVO_DEVICE";
    private static final String EXTRA_REMOTE_STATE = "remote_state";
    private static final String EXTRA_KEYTIMES = "lenovo.intent.extra.DEVICE_KEYTIMES_STATE";
    private static final String EXTRA_LONG_CLICK = "lenovo.intent.extra.LENOVO_LONGLICK_STATE";
    private static final String EXTRA_CLICK_TYPE = "lenovo.intent.extra.LENOVO_KEY_TYPE";
    private static final String EXTRA_WAKE_UP = "lenovo.intent.extra.LENOVO_WAKEUP";

    private static final String SETTING_TAP_TWO = "pen_touch_film_tap_two";
    private static final String SETTING_COPY_PASTE = "pen_touch_film_copy_paste";

    // keylayout mapping, see keylayout/Vendor_17ef_Product_61a1.kl
    static final int KEY_ONE_CLICK = KeyEvent.KEYCODE_F13;
    static final int KEY_TWO_CLICK = KeyEvent.KEYCODE_F14;
    static final int KEY_THREE_CLICK = KeyEvent.KEYCODE_F15;
    static final int KEY_LONG_CLICK = KeyEvent.KEYCODE_F16;
    static final int KEY_PRESS_CLICK = KeyEvent.KEYCODE_F17;
    static final int KEY_BT_DISCONNECT = KeyEvent.KEYCODE_F18;
    static final int KEY_360_THREE_CLICK = KeyEvent.KEYCODE_F19;
    static final int KEY_D_LONG_PRESS = KeyEvent.KEYCODE_F20;
    static final int KEY_D_SLIDE_DOWN = KeyEvent.KEYCODE_F21;
    static final int KEY_D_SLIDE_UP = KeyEvent.KEYCODE_F22;
    static final int KEY_D_CLICK = KeyEvent.KEYCODE_F23;

    private final Context mContext;
    private final Handler mHandler;
    private final BluetoothPenConnectPolicy mPen;
    // Pair DOWN/UP even if the setting changes while a gesture is in flight.
    private final SparseBooleanArray mAppDoubleTaps = new SparseBooleanArray();

    BluetoothPenInputPolicy(Context context, Handler handler, BluetoothPenConnectPolicy pen) {
        mContext = context;
        mHandler = handler;
        mPen = pen;
    }

    static boolean isLenovoDevice(KeyEvent event) {
        InputDevice dev = event.getDevice();
        return dev != null && dev.getVendorId() == VENDOR_LENOVO;
    }

    boolean handle(KeyEvent event) {
        int code = event.getKeyCode();
        if (code < KEY_ONE_CLICK || code > KEY_D_CLICK) return false;
        if (!isLenovoDevice(event)) return false;
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        boolean first = down && event.getRepeatCount() == 0;
        boolean wake = !mContext.getSystemService(android.os.PowerManager.class).isInteractive();
        switch (code) {
            case KEY_ONE_CLICK:
                if (first) post(() -> sendClick(0, wake));
                break;
            case KEY_TWO_CLICK: {
                // Stock delivers 601 to the app first. Broadcasting click type 1
                // directly only logs "double click" in PenService and loses the
                // pen/eraser shortcut implemented by note apps.
                int id = event.getDeviceId();
                if (first) {
                    int mode = Settings.Global.getInt(mContext.getContentResolver(),
                            SETTING_TAP_TWO, 1);
                    mAppDoubleTaps.put(id, mode == 1);
                    if (mode == 2) post(() -> sendClick(11, wake));
                }
                boolean forward = mAppDoubleTaps.get(id);
                if (!down) mAppDoubleTaps.delete(id);
                if (forward) {
                    KeyEvent appEvent = new KeyEvent(event.getDownTime(), event.getEventTime(),
                            event.getAction(), KeyEvent.KEYCODE_PEN_TWO_CLICK,
                            event.getRepeatCount(), event.getMetaState(), id,
                            event.getScanCode(), event.getFlags(), event.getSource());
                    appEvent.setDisplayId(event.getDisplayId());
                    post(() -> mContext.getSystemService(InputManager.class).injectInputEvent(
                            appEvent, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC));
                }
                break;
            }
            case KEY_THREE_CLICK:
                if (first) post(() -> sendClick(2, wake));
                break;
            case KEY_LONG_CLICK:
                if (first) post(() -> sendClick(3, wake));
                break;
            case KEY_PRESS_CLICK:
                if (first) post(() -> sendClick(4, wake));
                break;
            case KEY_BT_DISCONNECT:
                if (down) post(() -> mPen.setLost(true));
                break;
            case KEY_D_SLIDE_UP:
            case KEY_D_SLIDE_DOWN:
                if (!down && Settings.Global.getInt(mContext.getContentResolver(),
                        SETTING_COPY_PASTE, 0) == 1) {
                    int inject = code == KEY_D_SLIDE_UP ? KeyEvent.KEYCODE_COPY : KeyEvent.KEYCODE_PASTE;
                    post(() -> injectKey(inject));
                }
                break;
            default:
                // PEN_360_THREE_CLICK, PEN_D_LONG_PRESS, PEN_D_CLICK: other pen models only.
                break;
        }
        return true;
    }

    /** Stock fallback, reached only if the app did not consume the DOWN. */
    boolean handleUnhandled(KeyEvent event, int focusedUid) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_PEN_TWO_CLICK
                || !isLenovoDevice(event) || focusedUid < 0
                || event.getAction() != KeyEvent.ACTION_UP || event.isCanceled()
                || Settings.Global.getInt(mContext.getContentResolver(), SETTING_TAP_TWO, 1) != 1) {
            return false;
        }
        post(() -> {
            String[] packages = mContext.getPackageManager().getPackagesForUid(focusedUid);
            if (packages != null) {
                for (String pkg : packages) {
                    // Stock config_stylus_pen_partner_packages (TB520FU ROW).
                    if ("cn.wps.moffice_eng".equals(pkg) || "net.huanci.hsjpro".equals(pkg)) {
                        Intent i = new Intent("lenovo.partner.intent.action.BT_PEN_KEY_EVENT");
                        i.setPackage(pkg);
                        i.addFlags(Intent.FLAG_RECEIVER_FOREGROUND
                                | Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
                        i.putExtra("action", "01");
                        mContext.sendBroadcastAsUser(i, UserHandle.getUserHandleForUid(focusedUid));
                        return;
                    }
                }
            }
            sendClick(1, false);
        });
        return true;
    }

    private void post(Runnable r) {
        Safe.post(mHandler, "pen key", r);
    }

    /** keytimes: 0 one, 1 two, 2 three, 3 long (sent as 0 + long flag), 4 press, 11 double tap launch */
    private void sendClick(int keytimes, boolean wake) {
        boolean longClick = keytimes == 3;
        if (longClick) keytimes = 0;
        Intent i = new Intent(ACTION_CLICK);
        i.addFlags(Intent.FLAG_RECEIVER_FOREGROUND | Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
        i.putExtra(EXTRA_DEVICE_TYPE, 0);
        i.putExtra(EXTRA_LENOVO_DEVICE, true);
        i.putExtra(EXTRA_REMOTE_STATE, 0);
        i.putExtra(EXTRA_KEYTIMES, keytimes);
        i.putExtra(EXTRA_LONG_CLICK, longClick);
        i.putExtra(EXTRA_CLICK_TYPE, 0);
        i.putExtra(EXTRA_WAKE_UP, wake);
        Log.d(TAG, "pen click " + keytimes + " long " + longClick);
        mContext.sendBroadcastAsUser(i, UserHandle.CURRENT_OR_SELF);
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
}
