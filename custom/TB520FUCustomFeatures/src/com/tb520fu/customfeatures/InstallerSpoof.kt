/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.customfeatures

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Apps that see the Play Store as their own installer, so apps that require a
 * Play install source (for example Notein) keep working. The framework
 * (patches/frameworks_base-0009) reports com.android.vending only to the app
 * itself; other apps, the Play Store included, still get the real installer.
 *
 * Stored as Settings.Global tb520fu_play_installer_apps = "pkg;pkg", read
 * live by the framework, so a change applies immediately. Empty by default.
 */
object InstallerSpoof {

    const val APPS = "tb520fu_play_installer_apps"

    /** Package names, in the order the apps were added. */
    fun load(ctx: Context): LinkedHashSet<String> =
        (Settings.Global.getString(ctx.contentResolver, APPS) ?: "")
            .split(';')
            .filterTo(LinkedHashSet()) { it.isNotEmpty() }

    /** Saves the list; an empty list deletes the setting instead of leaving an empty value. */
    fun save(ctx: Context, apps: Set<String>) {
        if (apps.isEmpty()) {
            ctx.contentResolver.call(
                Settings.Global.CONTENT_URI,
                Settings.CALL_METHOD_DELETE_GLOBAL,
                APPS,
                null,
            )
        } else {
            Settings.Global.putString(ctx.contentResolver, APPS, apps.joinToString(";"))
        }
    }

    fun add(ctx: Context, pkg: String) = save(ctx, load(ctx).apply { add(pkg) })

    fun remove(ctx: Context, pkg: String) = save(ctx, load(ctx).apply { remove(pkg) })

    /** Drops apps that are no longer installed. */
    fun prune(ctx: Context) {
        val apps = load(ctx)
        val pm = ctx.packageManager
        val installed = apps.filterTo(LinkedHashSet()) { pkg ->
            runCatching { pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0)) }
                .isSuccess
        }
        if (installed.size != apps.size) save(ctx, installed)
    }

    fun label(ctx: Context, pkg: String): CharSequence =
        runCatching { ctx.packageManager.getApplicationInfo(pkg, 0).loadLabel(ctx.packageManager) }
            .getOrDefault(pkg)
}
