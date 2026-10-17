/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device

import android.content.Context
import android.provider.Settings

/**
 * Settings keys applied by lenovo-input in system_server
 * (input/src/com/zui/server/input: LandscapeController). Most are
 * Settings.Global; the landscape view key follows the stock Settings.System form (current
 * user, see [getSystemInt]). The keyboard settings are in LenovoKeyboard. The battery
 * (charging mode, bypass charging, standby saver) and white balance settings are on the
 * Settings battery and display pages.
 */
object LenovoSettings {
    /** Landscape view (Settings.System, default on) and the packages that keep their orientation. */
    const val LANDSCAPE_VIEW = "zui_landscape_enable"
    const val LANDSCAPE_EXCLUDED = "lenovo_landscape_excluded"

    /**
     * Lenovo PenService settings (pen buttons, writing vibration, ...). The action
     * also matches the capacitive pen screen, so name the Bluetooth pen one like
     * the stock DeviceUtils.startPenSettingsInSystemSettings.
     */
    const val ACTION_PEN_SETTINGS = "com.lenovo.pen.ACTION_SETTINGS"
    const val PEN_PACKAGE = "com.lenovo.penservice"
    const val PEN_SETTINGS_ACTIVITY = "com.lenovo.pen.bt.ui.BtSettingsActivity"

    /**
     * The separate apps of the optional customizations (hardware/lenovo/custom): game
     * performance management (LenovoGamePerformance) and Experimental features
     * (ExperimentalFeatures). They are not part of this build unless that repository is
     * synced; the Advanced features rows are hidden while their activity cannot be
     * resolved.
     */
    const val GAME_PERF_PACKAGE = "org.lineageos.settings.gameperf"
    const val GAME_PERF_ACTIVITY = "org.lineageos.settings.gameperf.GamePerfActivity"
    const val EXPERIMENTAL_PACKAGE = "org.lineageos.settings.experimental"
    const val EXPERIMENTAL_ACTIVITY = "org.lineageos.settings.experimental.ExperimentalFeaturesActivity"

    fun getInt(ctx: Context, key: String, def: Int) =
        Settings.Global.getInt(ctx.contentResolver, key, def)

    fun putInt(ctx: Context, key: String, value: Int) =
        Settings.Global.putInt(ctx.contentResolver, key, value)

    fun getString(ctx: Context, key: String, def: String) =
        Settings.Global.getString(ctx.contentResolver, key) ?: def

    fun putString(ctx: Context, key: String, value: String) =
        Settings.Global.putString(ctx.contentResolver, key, value)

    /** Settings.System int of the current user (the stock form of the landscape view key). */
    fun getSystemInt(ctx: Context, key: String, def: Int) = LenovoKeyboard.getInt(ctx, key, def)

    fun putSystemInt(ctx: Context, key: String, value: Int) =
        LenovoKeyboard.putInt(ctx, key, value)

    fun landscapeExcluded(ctx: Context): MutableSet<String> =
        getString(ctx, LANDSCAPE_EXCLUDED, "").split(',').filter { it.isNotBlank() }.toMutableSet()

    fun setLandscapeExcluded(ctx: Context, packages: Set<String>) =
        putString(ctx, LANDSCAPE_EXCLUDED, packages.sorted().joinToString(","))
}
