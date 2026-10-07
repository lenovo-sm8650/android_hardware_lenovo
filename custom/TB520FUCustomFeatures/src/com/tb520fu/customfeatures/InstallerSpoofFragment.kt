/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.customfeatures

import android.app.AlertDialog
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.android.settingslib.widget.SettingsBasePreferenceFragment

/**
 * The apps that see the Play Store as their installer (see InstallerSpoof).
 * Tapping an app offers to remove it from the list.
 */
class InstallerSpoofFragment : SettingsBasePreferenceFragment() {

    private lateinit var appsCategory: PreferenceCategory

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.installer_spoof_settings, rootKey)
        appsCategory = findPreference(KEY_APPS)!!
    }

    override fun onResume() {
        super.onResume()
        activity?.setTitle(R.string.installer_spoof_title)
        refresh()
    }

    private fun refresh() {
        val ctx = requireContext()
        val pm = ctx.packageManager
        InstallerSpoof.prune(ctx)

        appsCategory.removeAll()
        InstallerSpoof.load(ctx).forEach { pkg ->
            val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
            appsCategory.addPreference(Preference(ctx).apply {
                key = "app_$pkg"
                title = info?.loadLabel(pm) ?: pkg
                icon = info?.loadIcon(pm)
                summary = pkg
                setOnPreferenceClickListener { confirmRemove(pkg); true }
            })
        }
        appsCategory.addPreference(Preference(ctx).apply {
            key = "app_add"
            title = getString(R.string.game_app_add)
            setIcon(R.drawable.ic_add)
            setOnPreferenceClickListener { pickApp(); true }
        })
    }

    /** Launchable apps not yet in the list. */
    private fun pickApp() {
        val ctx = requireContext().applicationContext
        AppPicker.pick(this, getString(R.string.game_app_add), InstallerSpoof.load(ctx)) { pkg ->
            InstallerSpoof.add(ctx, pkg)
            if (isAdded) refresh()
        }
    }

    private fun confirmRemove(pkg: String) {
        val ctx = requireContext()
        AlertDialog.Builder(ctx)
            .setTitle(InstallerSpoof.label(ctx, pkg))
            .setMessage(R.string.installer_spoof_remove_message)
            .setPositiveButton(R.string.game_app_remove) { _, _ ->
                InstallerSpoof.remove(requireContext(), pkg)
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private companion object {
        const val KEY_APPS = "installer_spoof_apps"
    }
}
