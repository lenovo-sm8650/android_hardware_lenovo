/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.input;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.display.BrightnessInfo;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;

/**
 * The high brightness mode of the panel in sunlight, the way the stock ZUI display
 * service (ZuiDisplayService, setHbmLux / setHbmBrightness) does it, through the Lenovo
 * display HAL (IDisplay.setHbmState):
 *
 * - only with automatic brightness and the brightness at or near its maximum (the
 *   top of the automatic curve, not lowered by the thermal throttling),
 * - step 1 from 5000 lux and step 2 from 10000 lux (config_zui_defaultBrightnessThreshold
 *   of the stock LapisRowFrameworksOverlay: 5000, 10000),
 * - off as soon as one of those stops, and with the screen.
 *
 * The stock service follows the filtered lux of the automatic brightness; the light
 * sensor is filtered here with the debounce time of the stock service (3 s) both ways.
 * The stock firmware has no temperature limit for it (config_zui_enterHbmTemperature 0).
 */
final class SunlightHbmController implements SensorEventListener,
        DisplayManager.DisplayListener {
    private static final String TAG = "TB520FUSunlightHbm";

    private static final float LUX_STEP_1 = 5000f;
    private static final float LUX_STEP_2 = 10000f;
    private static final long DEBOUNCE_MS = 3000;
    /** The stock service wants the brightness at 1.0, see brightnessAtMax(). */
    private static final float BRIGHTNESS_NEAR_MAX = 0.95f;

    private final Context mContext;
    private final Handler mHandler;
    private final DisplayManager mDisplayManager;
    private final SensorManager mSensorManager;
    private final Sensor mLightSensor;

    private boolean mScreenOn;
    private boolean mAutoBrightness;
    private boolean mBrightnessAtMax;
    private boolean mListening;

    /** The step the lux asks for, and since when. */
    private int mLuxStep;
    private int mPendingStep = -1;
    private int mState = -1;

    private final Runnable mApplyPending = Safe.run("sunlight hbm debounce", this::applyPending);

    SunlightHbmController(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
        mDisplayManager = context.getSystemService(DisplayManager.class);
        mSensorManager = context.getSystemService(SensorManager.class);
        mLightSensor = mSensorManager != null
                ? mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) : null;
    }

    void start() {
        if (!LenovoHal.available(LenovoHal.DISPLAY) || mLightSensor == null
                || mDisplayManager == null) {
            Log.w(TAG, "no Lenovo display HAL or light sensor");
            return;
        }
        // The panel keeps the mode over a restart of system_server
        setState(0);

        final Uri mode = Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE);
        mContext.getContentResolver().registerContentObserver(mode, false,
                new ContentObserver(mHandler) {
                    @Override
                    public void onChange(boolean selfChange) {
                        Safe.run("sunlight hbm mode", SunlightHbmController.this::update).run();
                    }
                });

        mDisplayManager.registerDisplayListener(this, mHandler,
                DisplayManager.EVENT_TYPE_DISPLAY_BRIGHTNESS
                        | DisplayManager.EVENT_TYPE_DISPLAY_CHANGED);

        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        mContext.registerReceiver(Safe.receiver("sunlight hbm screen", (c, i) -> update()),
                filter, null, mHandler);

        update();
    }

    /** Listens to the light sensor only while the other conditions hold. */
    private void update() {
        mScreenOn = mContext.getSystemService(PowerManager.class).isInteractive();
        mAutoBrightness = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
                == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
        mBrightnessAtMax = brightnessAtMax();

        final boolean listen = mScreenOn && mAutoBrightness && mBrightnessAtMax;
        if (listen != mListening) {
            mListening = listen;
            if (listen) {
                mSensorManager.registerListener(this, mLightSensor,
                        SensorManager.SENSOR_DELAY_NORMAL, mHandler);
            } else {
                mSensorManager.unregisterListener(this);
                mLuxStep = 0;
                mPendingStep = -1;
                mHandler.removeCallbacks(mApplyPending);
            }
        }
        if (!listen) {
            setState(0);
        }
    }

    private boolean brightnessAtMax() {
        final Display display = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
        final BrightnessInfo info = display != null ? display.getBrightnessInfo() : null;
        if (info == null) {
            return false;
        }
        // Near the top: the automatic curve with the adjustment of the user may end a little
        // below the maximum (0.95: about 620 of the 650 nits). Throttled by the temperature,
        // the maximum is lower than the one of the panel.
        return info.brightnessMaximum >= 1f && info.brightness >= BRIGHTNESS_NEAR_MAX;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        final float lux = event.values[0];
        final int step = lux >= LUX_STEP_2 ? 2 : lux >= LUX_STEP_1 ? 1 : 0;
        if (step == mLuxStep) {
            return;
        }
        mLuxStep = step;
        if (step == mState) {
            // back to the applied step before the debounce ended
            mPendingStep = -1;
            mHandler.removeCallbacks(mApplyPending);
            return;
        }
        mPendingStep = step;
        mHandler.removeCallbacks(mApplyPending);
        mHandler.postDelayed(mApplyPending, DEBOUNCE_MS);
    }

    private void applyPending() {
        if (mPendingStep >= 0 && mListening) {
            setState(mPendingStep);
        }
        mPendingStep = -1;
    }

    private void setState(int state) {
        if (state == mState) {
            return;
        }
        if (LenovoHal.setHbmState(state)) {
            Log.i(TAG, "hbm " + state + " (screen " + mScreenOn + ", auto " + mAutoBrightness
                    + ", max " + mBrightnessAtMax + ")");
            mState = state;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onDisplayAdded(int displayId) {}

    @Override
    public void onDisplayRemoved(int displayId) {}

    @Override
    public void onDisplayChanged(int displayId) {
        if (displayId == Display.DEFAULT_DISPLAY) {
            Safe.run("sunlight hbm brightness", this::update).run();
        }
    }
}
