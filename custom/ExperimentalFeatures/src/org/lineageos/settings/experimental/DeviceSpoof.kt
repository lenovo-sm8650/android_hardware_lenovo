/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.experimental

import android.content.Context
import android.content.res.Resources
import android.provider.Settings

/**
 * Apps that see another device (brand, manufacturer, model) instead of this
 * one, for example so that Netflix streams HDR and Dolby Vision. The
 * framework (PropImitationHooks in the frameworks/base fork) sets the identity
 * when one of the apps starts; Google Play services and the Play Store never
 * get it. A field left empty keeps the real value, so an app with all three
 * empty sees this device. A new app starts empty: nothing is pre-filled.
 *
 * Stored as Settings.Global lenovo_device_spoof_apps =
 * "pkg=brand|manufacturer|model;...". While the key is unset, the framework
 * default applies (config_deviceSpoofApps, set by the
 * FrameworksResCustom overlay: Netflix as the OnePlus Pad Go 2).
 * An empty value means no app.
 */
object DeviceSpoof {

    const val APPS = "lenovo_device_spoof_apps"

    /** Main switch of the feature: on unless 0. */
    const val ENABLED = "lenovo_device_spoof"

    fun isEnabled(ctx: Context) = Settings.Global.getInt(ctx.contentResolver, ENABLED, 1) != 0

    fun setEnabled(ctx: Context, on: Boolean) =
        Settings.Global.putInt(ctx.contentResolver, ENABLED, if (on) 1 else 0)

    /** Never spoofed by the framework, so not offered either. */
    val EXCLUDED = setOf("com.google.android.gms", "com.google.android.gsf", "com.android.vending")

    data class Identity(val brand: String, val manufacturer: String, val model: String) {
        override fun toString() = listOf(brand, manufacturer, model).joinToString("|") { clean(it) }
    }

    private fun clean(value: String) = value.trim().replace(Regex("[;=|]"), "")

    private fun sysString(name: String): String {
        val res = Resources.getSystem()
        val id = res.getIdentifier(name, "string", "android")
        return if (id != 0) res.getString(id) else ""
    }

    /** The identity a newly added app starts with. */
    fun defaultIdentity() = Identity(
        sysString("config_deviceSpoofBrand"),
        sysString("config_deviceSpoofManufacturer"),
        sysString("config_deviceSpoofModel"),
    )

    private fun defaultEntries(): List<String> {
        val res = Resources.getSystem()
        val id = res.getIdentifier("config_deviceSpoofApps", "array", "android")
        return if (id != 0) res.getStringArray(id).toList() else emptyList()
    }

    private fun parse(entry: String): Pair<String, Identity>? {
        if (entry.isEmpty()) return null
        val eq = entry.indexOf('=')
        if (eq < 0) return entry to defaultIdentity()
        val f = entry.substring(eq + 1).split('|')
        return entry.substring(0, eq) to Identity(
            f.getOrElse(0) { "" }, f.getOrElse(1) { "" }, f.getOrElse(2) { "" })
    }

    /** Apps and their identities, in the order the apps were added. */
    fun load(ctx: Context): LinkedHashMap<String, Identity> {
        val entries = Settings.Global.getString(ctx.contentResolver, APPS)?.split(';')
            ?: defaultEntries()
        return entries.mapNotNull { parse(it) }.toMap(LinkedHashMap())
    }

    /** Saves the list; an empty list is kept as an empty value (no app), not the default. */
    fun save(ctx: Context, apps: Map<String, Identity>) {
        Settings.Global.putString(ctx.contentResolver, APPS,
            apps.entries.joinToString(";") { "${it.key}=${it.value}" })
    }

    fun put(ctx: Context, pkg: String, identity: Identity) =
        save(ctx, load(ctx).apply { put(pkg, identity) })

    fun remove(ctx: Context, pkg: String) = save(ctx, load(ctx).apply { remove(pkg) })

    /** Apps of the default list that are shown by name before they are installed. */
    private val KNOWN_NAMES = mapOf("com.netflix.mediaclient" to "Netflix")

    fun label(ctx: Context, pkg: String): CharSequence =
        runCatching { ctx.packageManager.getApplicationInfo(pkg, 0).loadLabel(ctx.packageManager) }
            .getOrDefault(KNOWN_NAMES[pkg] ?: pkg)
}
