/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.input;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.Log;

/**
 * Charge limit / battery protection / bypass charging, as the stock
 * ZuiBatteryManagerService drives the Lenovo battery HAL. The HAL writes the
 * charger nodes protection_setting (protected level) and protection_setting_eu
 * (max charging level), both encoded as 100000 + low * 1000 + high, or 95100
 * (recharge at 95, charge to 100) when off. The kernel does not keep them
 * across reboots, so they are applied on every boot.
 *
 * Bypass charging (stock LgsiGlobalBypassCharging, "global bypass charging"):
 * the charger powers the tablet while battery charging is disabled, which keeps
 * the battery cool and unstressed during long plugged-in sessions. While it is
 * enabled and a charger is plugged in, with [min, max] the configured range:
 *   level <= min  charge (until max),
 *   level >= max  stop charging and run from the charger; the charger input is
 *                 suspended (the battery powers the tablet) only while level > max,
 *   min < level < max keep the current state (hold, or charge on to max),
 *   enabling it, changing the range, or a fresh plug-in below max charges to max first.
 * Work mode 2 ("this time only") turns the switch off on unplug (and at boot).
 * Until the battery level is known it charges.
 *
 * While one of them holds charging, PROP_CHARGE_HOLD is true: only then does the
 * health HAL (device lapis health/main.cpp) report a plugged-in NOT_CHARGING
 * battery as LONG_LIFE, the "charging on hold" state of SystemUI and Settings.
 * "Charge to full" of that Settings tip sends ACTION_RESUME_CHARGING, which
 * lifts them all until the charger is unplugged, like a Pixel.
 *
 * Settings.Global (written by TB520FUParts):
 *   tb520fu_battery_mode         0 normal, 1 stop at 80 %, 2 keep 40-60 %
 *   tb520fu_battery_maintenance  1 lower the charge voltage as the battery ages
 *   tb520fu_bypass_charging      1 power the tablet from the charger only
 *   tb520fu_bypass_min / _max    level range, 20..80 in steps of 5, max - min >= 5
 *                                (defaults 40 / 60, normalized when invalid)
 *   tb520fu_bypass_mode          1 always, 2 this time only (default 1)
 *   tb520fu_bypass_min / _max    level range, 20..80 in steps of 5, max - min >= 5
 *                                (defaults 40 / 60, normalized when invalid)
 *   tb520fu_bypass_mode          1 always, 2 this time only (default 1)
 */
final class BatteryController {
    private static final String TAG = "TB520FUBattery";

    static final String SETTING_MODE = "tb520fu_battery_mode";
    static final String SETTING_MAINTENANCE = "tb520fu_battery_maintenance";
    static final String SETTING_BYPASS = "tb520fu_bypass_charging";
   static final String SETTING_BYPASS_MIN = "tb520fu_bypass_min";
    static final String SETTING_BYPASS_MAX = "tb520fu_bypass_max";
    static final String SETTING_BYPASS_MODE = "tb520fu_bypass_mode";

    static final int BYPASS_MODE_ALWAYS = 1;
    static final int BYPASS_MODE_ONCE = 2;
    // stock DEFAULT_MIN_LEVEL / DEFAULT_MAX_LEVEL
    static final int BYPASS_DEFAULT_MIN = 40;
    static final int BYPASS_DEFAULT_MAX = 60;
    static final int BYPASS_LOWEST = 20;
    static final int BYPASS_HIGHEST = 80;
    static final int BYPASS_STEP = 5;

    static final String ACTION_RESUME_CHARGING = "com.tb520fu.action.RESUME_CHARGING";
    private static final String PROP_CHARGE_HOLD = "vendor.tb520fu.charge_hold";

    static final int MODE_NORMAL = 0;
    static final int MODE_LIMIT_80 = 1;
    static final int MODE_PROTECT = 2;

    private static final int LEVEL_OFF = 95100;
    // stock config_recharging_level_percent 95 of MAX_CHARGING_LEVEL_FOR_ECO 80
    private static final int LEVEL_LIMIT_80 = encode(76, 80);
    // stock config_min/max_battery_protection_level
    private static final int LEVEL_PROTECT = encode(40, 60);
    private static final int RECHARGE_NORMAL = 95;

    private static final int MAX_RETRIES = 6;
    private static final long RETRY_MS = 10000;

    private final Context mContext;
    private final Handler mHandler;
    private int mRetries;

    private boolean mBypassEnabled;
    private int mBypassMin = BYPASS_DEFAULT_MIN;
    private int mBypassMax = BYPASS_DEFAULT_MAX;
    private int mBypassMode = BYPASS_MODE_ALWAYS;
    // stock mIsContinueCharging / mForceCharingToMax
    private boolean mContinueCharging = true;
    private boolean mForceChargeToMax;
    private boolean mLimitsHold;
    private boolean mResumed;
    private boolean mPlugged;
    private int mLevel = -1;
    private Boolean mChargeDisabledApplied;
    private Boolean mInputSuspendApplied;
    private Boolean mHoldReported;

    BatteryController(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
    }

    static int encode(int low, int high) {
        return 100000 + low * 1000 + high;
    }

    void start() {
        ContentResolver cr = mContext.getContentResolver();
        ContentObserver observer = Safe.observer(mHandler, "battery settings", uri -> {
            mRetries = 0;
            apply();
        });
        cr.registerContentObserver(Settings.Global.getUriFor(SETTING_MODE), false, observer);
        cr.registerContentObserver(Settings.Global.getUriFor(SETTING_MAINTENANCE), false, observer);
        cr.registerContentObserver(Settings.Global.getUriFor(SETTING_BYPASS), false, observer);
        cr.registerContentObserver(Settings.Global.getUriFor(SETTING_BYPASS_MIN), false, observer);
        cr.registerContentObserver(Settings.Global.getUriFor(SETTING_BYPASS_MAX), false, observer);
        cr.registerContentObserver(Settings.Global.getUriFor(SETTING_BYPASS_MODE), false, observer);
        if (Settings.Global.getInt(cr, SETTING_BYPASS, 0) != 0
                && Settings.Global.getInt(cr, SETTING_BYPASS_MODE, BYPASS_MODE_ALWAYS)
                        == BYPASS_MODE_ONCE) {
            // stock systemReady(): "this time only" does not survive a reboot
            Settings.Global.putInt(cr, SETTING_BYPASS, 0);
        }
        cr.registerContentObserver(Settings.Global.getUriFor(SETTING_BYPASS_MIN), false, observer);
        cr.registerContentObserver(Settings.Global.getUriFor(SETTING_BYPASS_MAX), false, observer);
        cr.registerContentObserver(Settings.Global.getUriFor(SETTING_BYPASS_MODE), false, observer);
        if (Settings.Global.getInt(cr, SETTING_BYPASS, 0) != 0
                && Settings.Global.getInt(cr, SETTING_BYPASS_MODE, BYPASS_MODE_ALWAYS)
                        == BYPASS_MODE_ONCE) {
            // stock systemReady(): "this time only" does not survive a reboot
            Settings.Global.putInt(cr, SETTING_BYPASS, 0);
        }
        mContext.registerReceiver(Safe.receiver("battery level", (c, i) -> onBatteryChanged(i)),
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED), null, mHandler,
                Context.RECEIVER_EXPORTED);
        mContext.registerReceiver(Safe.receiver("resume charging", (c, i) -> onResumeCharging()),
                new IntentFilter(ACTION_RESUME_CHARGING), Manifest.permission.DEVICE_POWER,
                mHandler, Context.RECEIVER_EXPORTED);
        apply();
    }

    private void apply() {
        ContentResolver cr = mContext.getContentResolver();
        int mode = Settings.Global.getInt(cr, SETTING_MODE, MODE_NORMAL);
        boolean maintenance = Settings.Global.getInt(cr, SETTING_MAINTENANCE, 1) != 0;
        boolean bypass = Settings.Global.getInt(cr, SETTING_BYPASS, 0) != 0;
        int rawMin = Settings.Global.getInt(cr, SETTING_BYPASS_MIN, BYPASS_DEFAULT_MIN);
        int rawMax = Settings.Global.getInt(cr, SETTING_BYPASS_MAX, BYPASS_DEFAULT_MAX);
        int bypassMin = normalizeMin(rawMin, rawMax);
        int bypassMax = normalizeMax(rawMin, rawMax);
        mBypassMode = Settings.Global.getInt(cr, SETTING_BYPASS_MODE, BYPASS_MODE_ALWAYS)
                == BYPASS_MODE_ONCE ? BYPASS_MODE_ONCE : BYPASS_MODE_ALWAYS;
        // stock updateBypassProtectedLevel() / updateBypassChargingStatus()
        if ((bypass && !mBypassEnabled) || bypassMin != mBypassMin || bypassMax != mBypassMax) {
            mForceChargeToMax = mLevel < bypassMax;
        }
        mBypassEnabled = bypass;
        mBypassMin = bypassMin;
        mBypassMax = bypassMax;
        if (!LenovoHal.available(LenovoHal.BATTERY)) {
            if (mRetries++ < MAX_RETRIES) {
                Safe.postDelayed(mHandler, "battery retry", this::apply, RETRY_MS);
            }
            return;
        }
        mLimitsHold = mode != MODE_NORMAL && !mResumed;
        int max = mLimitsHold && mode == MODE_LIMIT_80 ? LEVEL_LIMIT_80 : LEVEL_OFF;
        int protect = mLimitsHold && mode == MODE_PROTECT ? LEVEL_PROTECT : LEVEL_OFF;
        boolean ok = LenovoHal.setMaxBatteryChargingLevel(max);
        ok &= LenovoHal.setBatteryProtectedLevel(protect);
        ok &= LenovoHal.setBatteryRechargingPercent(RECHARGE_NORMAL);
        if (LenovoHal.isBatteryMaintenanceEnabled() != maintenance) {
            ok &= LenovoHal.setBatteryMaintenanceEnabled(maintenance);
        }
        Log.i(TAG, "mode " + mode + " (max " + max + ", protect " + protect + "), maintenance "
                + maintenance + ", bypass " + mBypassEnabled + " [" + mBypassMin + ", " + mBypassMax
                + "] mode " + mBypassMode
                + (mResumed ? ", resumed until unplugged" : "")
                + (ok ? "" : " - HAL reported failure"));
        mChargeDisabledApplied = null;
        mInputSuspendApplied = null;
        applyBypass();
    }

    private void onResumeCharging() {
        if (!mPlugged || mResumed) return;
        mResumed = true;
        mRetries = 0;
        apply();
    }

    private void onBatteryChanged(Intent intent) {
        boolean plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
        if (plugged != mPlugged) {
            mPlugged = plugged;
            if (plugged) {
                mForceChargeToMax = true;
            } else if (mBypassEnabled && mBypassMode == BYPASS_MODE_ONCE) {
                Log.i(TAG, "unplugged, bypass charging was for this time only");
                Settings.Global.putInt(mContext.getContentResolver(), SETTING_BYPASS, 0);
            }
            if (!plugged && mResumed) {
                mResumed = false;
                mRetries = 0;
                apply();
            }
        }
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        if (level < 0 || scale <= 0) return;
        level = level * 100 / scale;
        if (level == mLevel) return;
        mLevel = level;
        applyBypass();
    }

    /** Both bounds clamped to 20..80 and floored to steps of 5, at least one step apart. */
    static int normalizeMin(int min, int max) {
        return normalize(min, max)[0];
    }

    static int normalizeMax(int min, int max) {
        return normalize(min, max)[1];
    }

    private static int[] normalize(int min, int max) {
        min = snap(min);
        max = snap(max);
        if (max - min < BYPASS_STEP) {
            max = Math.min(BYPASS_HIGHEST, min + BYPASS_STEP);
            min = max - BYPASS_STEP;
        }
        return new int[] {min, max};
    }

    private static int snap(int value) {
        value = Math.max(BYPASS_LOWEST, Math.min(BYPASS_HIGHEST, value));
        return value - value % BYPASS_STEP;
    }

    /**
     * Stock LgsiGlobalBypassCharging.updateBatteryIntent()/setBatteryProtect(): votes
     * (chargingDisabled, inputSuspend) for the plugged-in battery level.
     */
    private void applyBypass() {
        boolean active = mBypassEnabled && !mResumed && mPlugged && mLevel >= 0;
        boolean disable = false;
        boolean suspend = false;
        if (!active) {
            mContinueCharging = true;
        } else if (mLevel <= mBypassMin) {
            mContinueCharging = true;
        } else if (mLevel >= mBypassMax) {
            disable = true;
            suspend = mLevel > mBypassMax;
            mContinueCharging = false;
            mForceChargeToMax = false;
        } else if (mForceChargeToMax) {
            mContinueCharging = true;
            mForceChargeToMax = false;
        } else if (!mContinueCharging) {
            disable = true;
        }
        reportHold(mLimitsHold || disable);
        if (mChargeDisabledApplied == null || mChargeDisabledApplied != disable) {
            if (LenovoHal.setBatteryChargeDisabled(disable)) {
                mChargeDisabledApplied = disable;
                Log.i(TAG, "battery charging " + (disable ? "paused (bypass)" : "enabled")
                        + ", level " + mLevel);
            }
        }
        if (mInputSuspendApplied == null || mInputSuspendApplied != suspend) {
            if (LenovoHal.setBatteryInputSuspended(suspend)) {
                mInputSuspendApplied = suspend;
                Log.i(TAG, "charger input " + (suspend ? "suspended (bypass, above max)" : "enabled")
                        + ", level " + mLevel);
            }
        }
    }

    private void reportHold(boolean hold) {
        if (mHoldReported != null && mHoldReported == hold) return;
        SystemProperties.set(PROP_CHARGE_HOLD, Boolean.toString(hold));
        mHoldReported = hold;
    }
}
