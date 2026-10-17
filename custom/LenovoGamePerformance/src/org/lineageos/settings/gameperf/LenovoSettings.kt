/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.gameperf

import android.content.Context
import android.provider.Settings

/**
 * Settings.Global keys applied by lenovo-input in system_server
 * (custom/input/src/com/zui/server/input/custom/GamePerfController.java in this
 * repository).
 */
object LenovoSettings {
    const val GAME_PERF = "lenovo_game_perf"
    const val GAME_PERF_APPS = "lenovo_game_perf_apps"
    const val GAME_PERF_CUSTOM = "lenovo_game_perf_custom"
    const val GAME_MEM_CLEAN = "key_game_assistant_memory_accelerate"

    fun getInt(ctx: Context, key: String, def: Int) =
        Settings.Global.getInt(ctx.contentResolver, key, def)

    fun putInt(ctx: Context, key: String, value: Int) =
        Settings.Global.putInt(ctx.contentResolver, key, value)

    fun getString(ctx: Context, key: String, def: String) =
        Settings.Global.getString(ctx.contentResolver, key) ?: def

    fun putString(ctx: Context, key: String, value: String) =
        Settings.Global.putString(ctx.contentResolver, key, value)
}
