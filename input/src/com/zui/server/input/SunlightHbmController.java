/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.zui.server.input;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import java.lang.reflect.Field;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;

/**
 * The high brightness mode of the panel in sunlight, ported from the stock ZUI display
 * service (services.jar ZuiDisplayService: setHbmLux, setHbmBrightness, sendHbmStatusMsg,
 * ScreenStatusReceiver, the zui_screen_enhance_enabled observer), through the Lenovo
 * display HAL (IDisplay.setHbmState).
 *
 * The stock display power controller reports to it (ZuiDisplayServiceInternal): the filtered
 * ambient lux of the automatic brightness, the brightness it applies and whether the automatic
 * brightness is on. Here that is com.android.server.display.ZuiDisplayHooks of frameworks/base,
 * set by reflection (this library cannot link to services.jar).
 *
 * Stock logic:
 * - only with the "High brightness mode" switch of Settings > Display on
 *   (Settings.System zui_screen_enhance_enabled, 0 by default) and the automatic brightness on,
 * - level 1 from the first lux of config_zui_defaultBrightnessThreshold (5000) and level 2 from
 *   its second one (10000), with the brightness at its maximum (1.0),
 * - a level is set 3 s after the last report, each report restarting the wait; off is immediate,
 * - off with the screen, or when the switch or the automatic brightness goes off.
 *
 * Not ported, because they are inactive on this device: the temperature limits
 * (config_zui_enterHbmTemperature and config_zui_exitHbmTemperature are 0), SVI, SLR and the
 * transition point (persist.sys.zui.gamma.space.transition, not set). The toast,
 * the persist.vendor.overlay.zui.hdr property (nothing reads it) and the big data event are not.
 */
final class SunlightHbmController {
    private static final String TAG = "LenovoSunlightHbm";

    private static final String HOOKS = "com.android.server.display.ZuiDisplayHooks";
    /** Stock ZuiDisplayService.ZUI_SETTINGS_SCREEN_BOOST. */
    private static final String SETTING_HIGH_BRIGHTNESS = "zui_screen_enhance_enabled";

    /** Stock AutomaticBrightnessController.BRIGHTNESS_MAX_HBM (ZuiHighBrightnessApl is off). */
    private static final float BRIGHTNESS_MAX_HBM = 1.0f;
    /** Stock sendHbmStatusMsg: delay of a level. */
    private static final long LEVEL_DELAY_MS = 3000;

    private final Context mContext;
    private final Handler mHandler;

    private int mLuxConfig;
    private int mLuxSecondConfig;

    private float mLux;
    private float mBrightness;
    private boolean mAutoBrightness;
    /** Stock mHbmStatusEnable. */
    private boolean mHbmEnabled;
    private int mPendingLevel;

    private final Runnable mApplyLevel = Safe.run("sunlight hbm level", this::applyLevel);

    SunlightHbmController(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
    }

    void start() {
        if (!LenovoHal.available(LenovoHal.DISPLAY)) {
            Log.w(TAG, "no Lenovo display HAL");
            return;
        }

        final int[] lux = Resources.getSystem().getIntArray(
                com.android.internal.R.array.config_zui_defaultBrightnessThreshold);
        if (lux == null || lux.length == 0) {
            Log.w(TAG, "no config_zui_defaultBrightnessThreshold");
            return;
        }
        mLuxConfig = lux[0];
        mLuxSecondConfig = lux.length > 1 ? lux[1] : 0;

        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(SETTING_HIGH_BRIGHTNESS), false,
                new ContentObserver(mHandler) {
                    @Override
                    public void onChange(boolean selfChange, Uri uri) {
                        Safe.run("sunlight hbm switch", SunlightHbmController.this::onSwitchChanged)
                                .run();
                    }
                }, UserHandle.USER_ALL);

        mContext.registerReceiverForAllUsers(
                Safe.receiver("sunlight hbm screen off", (c, i) -> onScreenOff()),
                new IntentFilter(Intent.ACTION_SCREEN_OFF), null, mHandler);

        try {
            final Class<?> hooks = Class.forName(HOOKS);
            set(hooks, "sLux", (DoubleConsumer) value -> Safe.post(mHandler, "sunlight hbm lux",
                    () -> setLux((float) value)));
            set(hooks, "sBrightness", (DoubleConsumer) value -> Safe.post(mHandler,
                    "sunlight hbm brightness", () -> setBrightness((float) value)));
            set(hooks, "sAutoBrightness", (IntConsumer) value -> Safe.post(mHandler,
                    "sunlight hbm auto", () -> setAutoBrightnessEnable(value != 0)));
        } catch (ReflectiveOperationException e) {
            Log.e(TAG, "ZuiDisplayHooks of the display service not found", e);
        }
    }

    private static void set(Class<?> cls, String field, Object value)
            throws ReflectiveOperationException {
        final Field f = cls.getField(field);
        f.set(null, value);
    }

    /** Stock isZuiLenovoHbmSwitchOn (ZuiHighBrightness is on): the switch must be on. */
    private boolean isSwitchOn() {
        return Settings.System.getInt(mContext.getContentResolver(), SETTING_HIGH_BRIGHTNESS, 0)
                == 1;
    }

    /** Stock ZuiDisplayServiceInternalImpl.setAutoBrightnessEnable. */
    private void setAutoBrightnessEnable(boolean enable) {
        if (!enable) {
            mLux = 0;
            mHandler.removeCallbacks(mApplyLevel);
        }
        mAutoBrightness = enable;
    }

    /** Stock setHbmLux. */
    private void setLux(float lux) {
        mLux = lux;
        evaluate();
    }

    /** Stock setHbmBrightness. */
    private void setBrightness(float brightness) {
        mBrightness = brightness;
        evaluate();
    }

    private void evaluate() {
        if (!mAutoBrightness || !isSwitchOn()) {
            if (mHbmEnabled) {
                sendLevel(0);
            }
            return;
        }
        if (mLux < mLuxConfig || mBrightness < BRIGHTNESS_MAX_HBM) {
            if (mHbmEnabled) {
                sendLevel(0);
            }
            return;
        }
        if (mLuxSecondConfig == 0 || mLux < mLuxSecondConfig) {
            sendLevel(1);
        } else {
            sendLevel(2);
        }
    }

    /** Stock SettingsObserver for zui_screen_enhance_enabled. */
    private void onSwitchChanged() {
        if (!mAutoBrightness || !isSwitchOn()) {
            if (mHbmEnabled) {
                sendLevel(0);
            }
            return;
        }
        if (mHbmEnabled || mLux < mLuxConfig) {
            return;
        }
        sendLevel(1);
    }

    /** Stock ScreenStatusReceiver. */
    private void onScreenOff() {
        if (isSwitchOn()) {
            mBrightness = 0;
            mLux = 0;
            sendLevel(-2);
        }
    }

    /** Stock sendHbmStatusMsg: -2 is off at once, 0 is off, 1 and 2 are the levels. */
    private void sendLevel(int level) {
        mHandler.removeCallbacks(mApplyLevel);
        if (level == -2) {
            mPendingLevel = 0;
            mHandler.post(mApplyLevel);
            return;
        }
        mPendingLevel = level;
        mHandler.postDelayed(mApplyLevel, level != 0 ? LEVEL_DELAY_MS : 0);
    }

    private void applyLevel() {
        final int level = mPendingLevel;
        if (LenovoHal.setHbmState(level)) {
            Log.i(TAG, "hbm " + level + " (lux " + mLux + ", brightness " + mBrightness + ")");
        }
        mHbmEnabled = level != 0;
    }
}
