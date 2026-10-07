/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.customfeatures

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.provider.Settings

/**
 * The device identity (brand, manufacturer, model) that selected apps see
 * instead of the real one, for example so that Netflix streams HDR and Dolby
 * Vision. The framework (PropImitationHooks in the frameworks/base fork) sets
 * it when one of the apps starts; Google Play services and the Play Store
 * never get it.
 *
 * Stored as Settings.Global tb520fu_device_spoof_*. While a key is unset, the
 * framework default applies (config_tb520fuDeviceSpoof*, set by the
 * FrameworksResTB520FUCustom overlay: on, Netflix, OnePlus Pad Go 2).
 */
object DeviceSpoof {

    const val ENABLED = "tb520fu_device_spoof_enabled"
    const val APPS = "tb520fu_device_spoof_apps"
    const val BRAND = "tb520fu_device_spoof_brand"
    const val MANUFACTURER = "tb520fu_device_spoof_manufacturer"
    const val MODEL = "tb520fu_device_spoof_model"

    /** Never spoofed by the framework, so not offered either. */
    val EXCLUDED = setOf("com.google.android.gms", "com.google.android.gsf", "com.android.vending")

    private fun sysId(name: String, type: String): Int =
        Resources.getSystem().getIdentifier(name, type, "android")

    private fun defaultString(name: String): String {
        val id = sysId(name, "string")
        return if (id != 0) Resources.getSystem().getString(id) else ""
    }

    fun defaultEnabled(): Boolean {
        val id = sysId("config_tb520fuDeviceSpoofEnabled", "bool")
        return id != 0 && Resources.getSystem().getBoolean(id)
    }

    fun defaultApps(): List<String> {
        val id = sysId("config_tb520fuDeviceSpoofApps", "array")
        return if (id != 0) Resources.getSystem().getStringArray(id).toList() else emptyList()
    }

    fun defaultValue(key: String): String = when (key) {
        BRAND -> defaultString("config_tb520fuDeviceSpoofBrand")
        MANUFACTURER -> defaultString("config_tb520fuDeviceSpoofManufacturer")
        MODEL -> defaultString("config_tb520fuDeviceSpoofModel")
        else -> ""
    }

    fun isEnabled(ctx: Context): Boolean =
        Settings.Global.getString(ctx.contentResolver, ENABLED)?.let { it == "1" } ?: defaultEnabled()

    fun setEnabled(ctx: Context, enabled: Boolean) {
        Settings.Global.putString(ctx.contentResolver, ENABLED, if (enabled) "1" else "0")
    }

    /** The value the framework uses: the saved one, otherwise the default. */
    fun value(ctx: Context, key: String): String =
        Settings.Global.getString(ctx.contentResolver, key) ?: defaultValue(key)

    /** Saves a value; an empty one leaves that field of the real device. */
    fun setValue(ctx: Context, key: String, value: String) {
        Settings.Global.putString(ctx.contentResolver, key, value.trim())
    }

    /** Package names, in the order the apps were added. */
    fun load(ctx: Context): LinkedHashSet<String> {
        val saved = Settings.Global.getString(ctx.contentResolver, APPS)
            ?: return LinkedHashSet(defaultApps())
        return saved.split(';').filterTo(LinkedHashSet()) { it.isNotEmpty() }
    }

    /** Saves the list; an empty list is kept as an empty value (no app), not the default. */
    fun save(ctx: Context, apps: Set<String>) {
        Settings.Global.putString(ctx.contentResolver, APPS, apps.joinToString(";"))
    }

    fun add(ctx: Context, pkg: String) = save(ctx, load(ctx).apply { add(pkg) })

    fun remove(ctx: Context, pkg: String) = save(ctx, load(ctx).apply { remove(pkg) })

    /** Back to the defaults: deletes every key. */
    fun reset(ctx: Context) {
        for (key in listOf(ENABLED, APPS, BRAND, MANUFACTURER, MODEL)) {
            ctx.contentResolver.call(
                Settings.Global.CONTENT_URI,
                Settings.CALL_METHOD_DELETE_GLOBAL,
                key,
                null,
            )
        }
    }

    fun isInstalled(ctx: Context, pkg: String): Boolean =
        runCatching {
            ctx.packageManager.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
        }.isSuccess

    fun label(ctx: Context, pkg: String): CharSequence =
        runCatching { ctx.packageManager.getApplicationInfo(pkg, 0).loadLabel(ctx.packageManager) }
            .getOrDefault(pkg)
}
