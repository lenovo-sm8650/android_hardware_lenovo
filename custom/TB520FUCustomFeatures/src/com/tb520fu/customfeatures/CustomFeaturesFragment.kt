/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.customfeatures

import android.os.Bundle
import androidx.preference.Preference
import com.android.settingslib.widget.SettingsBasePreferenceFragment

/**
 * Main screen of the "Custom Tweaks" app: game performance, the device
 * identity selected apps see and the apps that see the Play Store as their
 * installer. The features live here (not in TB520FUParts) because they are
 * optional customizations; the device tree builds without them.
 *
 * The switches only switch their feature on and off; the management screens
 * are opened from separate rows (the same pattern as the Lenovo features
 * app), so a tap can never toggle a feature by accident while navigating.
 */
class CustomFeaturesFragment : SettingsBasePreferenceFragment() {

    private lateinit var gamePerfPref: Preference
    private lateinit var installerSpoofPref: Preference
    private lateinit var deviceSpoofPref: Preference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.custom_features_settings, rootKey)
        // Check cooldowns of the dropped Play Integrity stack (CheckCooldown).
        requireContext().deleteSharedPreferences("integrity_cooldown")

        gamePerfPref = findPreference(KEY_GAME_PERF)!!
        installerSpoofPref = findPreference(KEY_INSTALLER_SPOOF)!!
        deviceSpoofPref = findPreference(KEY_DEVICE_SPOOF)!!
    }

    override fun onResume() {
        super.onResume()
        activity?.setTitle(R.string.app_name)
        gamePerfPref.summary = getString(
            if (LenovoSettings.getInt(requireContext(), LenovoSettings.GAME_PERF, 0) != 0) {
                R.string.game_perf_on
            } else {
                R.string.game_perf_off
            }
        )
        installerSpoofPref.summary = installerSpoofSummary()
        deviceSpoofPref.summary = deviceSpoofSummary()
    }

    /** "Off", or the model and the names of the apps. */
    private fun deviceSpoofSummary(): String {
        val ctx = requireContext()
        val apps = DeviceSpoof.load(ctx)
        if (!DeviceSpoof.isEnabled(ctx) || apps.isEmpty()) return getString(R.string.game_perf_off)
        return getString(R.string.device_spoof_summary,
                DeviceSpoof.value(ctx, DeviceSpoof.MODEL).ifEmpty { "-" },
                apps.joinToString(", ") { DeviceSpoof.label(ctx, it) })
    }

    /** "Off", or the names of the apps in the list. */
    private fun installerSpoofSummary(): String {
        val ctx = requireContext()
        InstallerSpoof.prune(ctx)
        val apps = InstallerSpoof.load(ctx)
        return if (apps.isEmpty()) {
            getString(R.string.game_perf_off)
        } else {
            apps.joinToString(", ") { InstallerSpoof.label(ctx, it) }
        }
    }

    private companion object {
        const val KEY_GAME_PERF = "game_perf"
        const val KEY_INSTALLER_SPOOF = "installer_spoof"
        const val KEY_DEVICE_SPOOF = "device_spoof"
    }
}
