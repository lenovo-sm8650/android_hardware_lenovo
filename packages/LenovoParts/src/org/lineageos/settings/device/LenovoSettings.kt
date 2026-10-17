/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.Settings
import java.io.File

/**
 * Settings keys applied by lenovo-input in system_server
 * (input/src/com/zui/server/input: BatteryController, LandscapeController, StandbyController).
 * Most are Settings.Global; the bypass charging and landscape view keys follow the stock
 * Settings.System form (current user, see [getSystemInt]). The keyboard settings are in
 * LenovoKeyboard.
 */
object LenovoSettings {
    const val BATTERY_MODE = "lenovo_battery_charge_mode"
    const val BATTERY_MAINTENANCE = "battery_maintenance_on"
    /** Settings.System int. */
    const val BYPASS_CHARGING = "global_bypass_charging_enable"
    const val STANDBY_SAVER = "lenovo_standby_saver"

    /**
     * Bypass charging range in percent (charges again at min, holds from max): one
     * Settings.System string "min_max".
     */
    const val BYPASS_RANGE = "global_bypass_charging_level_range"
    const val DEFAULT_BYPASS_RANGE = "40_60"
    const val BYPASS_LIMIT_LOW = 20
    const val BYPASS_LIMIT_HIGH = 80
    const val BYPASS_STEP = 5
    const val DEFAULT_BYPASS_MIN = 40
    const val DEFAULT_BYPASS_MAX = 60

    /** Settings.System int: 1 always, 2 this time only (bypass charging turns off when unplugged). */
    const val BYPASS_MODE = "global_bypass_charging_work_mode"
    const val BYPASS_MODE_ALWAYS = 1
    const val BYPASS_MODE_ONCE = 2

    /** Landscape view (Settings.System, default on) and the packages that keep their orientation. */
    const val LANDSCAPE_VIEW = "zui_landscape_enable"
    const val LANDSCAPE_EXCLUDED = "lenovo_landscape_excluded"

    /** Settings.Secure, AOSP adaptive white balance (DisplayWhiteBalanceController). */
    const val DISPLAY_WHITE_BALANCE = "display_white_balance_enabled"

    /** Settings.Secure, patches/frameworks_base-0002 (ColorDisplayService), 0-100. */
    const val WHITE_BALANCE_STRENGTH = "display_white_balance_strength"
    const val DEFAULT_WHITE_BALANCE_STRENGTH = 100

    /**
     * Lenovo PenService settings (pen buttons, writing vibration, ...). The action
     * also matches the capacitive pen screen, so name the Bluetooth pen one like
     * the stock DeviceUtils.startPenSettingsInSystemSettings.
     */
    const val ACTION_PEN_SETTINGS = "com.lenovo.pen.ACTION_SETTINGS"
    const val PEN_PACKAGE = "com.lenovo.penservice"
    const val PEN_SETTINGS_ACTIVITY = "com.lenovo.pen.bt.ui.BtSettingsActivity"

    fun getInt(ctx: Context, key: String, def: Int) =
        Settings.Global.getInt(ctx.contentResolver, key, def)

    fun putInt(ctx: Context, key: String, value: Int) =
        Settings.Global.putInt(ctx.contentResolver, key, value)

    fun getString(ctx: Context, key: String, def: String) =
        Settings.Global.getString(ctx.contentResolver, key) ?: def

    fun putString(ctx: Context, key: String, value: String) =
        Settings.Global.putString(ctx.contentResolver, key, value)

    /** Settings.System int of the current user (the stock form of the bypass and landscape keys). */
    fun getSystemInt(ctx: Context, key: String, def: Int) = LenovoKeyboard.getInt(ctx, key, def)

    fun putSystemInt(ctx: Context, key: String, value: Int) =
        LenovoKeyboard.putInt(ctx, key, value)

    /** Writes the bypass charging range as the stock "min_max" string. */
    fun putBypassRange(ctx: Context, min: Int, max: Int) =
        LenovoKeyboard.putString(ctx, BYPASS_RANGE, "${min}_$max")

    /**
     * The bypass charging range (min, max) from the "min_max" string, normalized like
     * BatteryController; the default when unset or malformed.
     */
    fun bypassRange(ctx: Context): Pair<Int, Int> {
        fun snap(value: Int) = Math.round(value / BYPASS_STEP.toFloat()) * BYPASS_STEP
        val parts = (LenovoKeyboard.getString(ctx, BYPASS_RANGE) ?: DEFAULT_BYPASS_RANGE)
            .split('_')
        val parsed = if (parts.size == 2) {
            val a = parts[0].trim().toIntOrNull()
            val b = parts[1].trim().toIntOrNull()
            if (a != null && b != null) a to b else null
        } else null
        val (rawMin, rawMax) = parsed ?: (DEFAULT_BYPASS_MIN to DEFAULT_BYPASS_MAX)
        val min = snap(rawMin).coerceIn(BYPASS_LIMIT_LOW, BYPASS_LIMIT_HIGH - BYPASS_STEP)
        val max = snap(rawMax).coerceIn(min + BYPASS_STEP, BYPASS_LIMIT_HIGH)
        return min to max
    }

    fun landscapeExcluded(ctx: Context): MutableSet<String> =
        getString(ctx, LANDSCAPE_EXCLUDED, "").split(',').filter { it.isNotBlank() }.toMutableSet()

    fun setLandscapeExcluded(ctx: Context, packages: Set<String>) =
        putString(ctx, LANDSCAPE_EXCLUDED, packages.sorted().joinToString(","))

    data class BatteryInfo(val cycles: Int?, val healthPercent: Int?)

    fun readBatteryInfo(ctx: Context): BatteryInfo {
        val sticky = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val cycles = sticky?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1)
            ?.takeIf { it >= 0 }
            ?: readInt("/sys/class/power_supply/battery/cycle_count")
        val health = readInt("/sys/class/qcom-battery/fg1_soh")
            ?: readInt("/sys/class/qcom-battery/soh")
        return BatteryInfo(cycles, health?.takeIf { it in 1..100 })
    }

    private fun readInt(path: String): Int? =
        runCatching { File(path).readText().trim().toInt() }.getOrNull()
}
