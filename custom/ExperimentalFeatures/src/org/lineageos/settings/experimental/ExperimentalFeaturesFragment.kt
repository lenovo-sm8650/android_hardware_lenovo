/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.experimental

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.preference.Preference
import com.android.settingslib.widget.SettingsBasePreferenceFragment

/**
 * Main screen of the Experimental features app: app compatibility (the device identity
 * selected apps see and the apps that see the Play Store as their installer) and the
 * Play Integrity Fix row (`play_integrity`), which opens the separate Play Integrity Fix
 * app and is hidden while that app is not installed. The features
 * live here, not in LenovoParts, because they are optional customizations; the device tree
 * builds without them.
 *
 * The management screens are opened from separate rows (the same pattern as the Advanced
 * features app), so a tap can never toggle a feature by accident while navigating.
 */
class ExperimentalFeaturesFragment : SettingsBasePreferenceFragment() {

    private lateinit var installerSpoofPref: Preference
    private lateinit var deviceSpoofPref: Preference
    private var pifPref: Preference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.experimental_features_settings, rootKey)

        installerSpoofPref = findPreference(KEY_INSTALLER_SPOOF)!!
        deviceSpoofPref = findPreference(KEY_DEVICE_SPOOF)!!

        val pif = findPreference<Preference>(KEY_PLAY_INTEGRITY)!!
        findPreference<Preference>(KEY_PLAY_INTEGRITY_CATEGORY)!!.isVisible = try {
            requireContext().packageManager.getActivityInfo(PIF_ACTIVITY, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
        pif.setOnPreferenceClickListener {
            startActivity(Intent().setComponent(PIF_ACTIVITY))
            true
        }
        pifPref = pif
    }

    override fun onResume() {
        super.onResume()
        activity?.setTitle(R.string.app_name)
        installerSpoofPref.summary = installerSpoofSummary()
        deviceSpoofPref.summary = deviceSpoofSummary()
        pifPref?.summary = getString(
            if (Settings.Global.getInt(requireContext().contentResolver, PIF_SWITCH_SETTING, 1) != 0) {
                R.string.state_on
            } else {
                R.string.state_off
            }
        )
    }

    /** "On" while an app is in the list, otherwise "Off". */
    private fun deviceSpoofSummary(): String = getString(
        if (DeviceSpoof.load(requireContext()).isEmpty()) R.string.state_off else R.string.state_on
    )

    /** "Off", or the names of the apps in the list. */
    private fun installerSpoofSummary(): String {
        val ctx = requireContext()
        InstallerSpoof.prune(ctx)
        val apps = InstallerSpoof.load(ctx)
        return if (apps.isEmpty()) {
            getString(R.string.state_off)
        } else {
            apps.joinToString(", ") { InstallerSpoof.label(ctx, it) }
        }
    }

    private companion object {
        const val KEY_INSTALLER_SPOOF = "installer_spoof"
        const val KEY_DEVICE_SPOOF = "device_spoof"
        const val KEY_PLAY_INTEGRITY = "play_integrity"
        const val KEY_PLAY_INTEGRITY_CATEGORY = "play_integrity_category"
        const val PIF_SWITCH_SETTING = "lenovo_pif_module"
        val PIF_ACTIVITY = ComponentName(
            "io.github.wnduddld0513.pif", "io.github.wnduddld0513.pif.PifActivity"
        )
    }
}
