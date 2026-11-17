/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.experimental

import android.os.Bundle
import android.provider.Settings
import androidx.preference.Preference
import com.android.settingslib.widget.SettingsBasePreferenceFragment

/**
 * Main screen of the Experimental features app: app compatibility (the device identity
 * selected apps see). The feature lives here, not in LenovoParts, because it is an
 * optional customization; the device tree builds without it.
 *
 * The management screen is opened from a separate row (the same pattern as the Advanced
 * features app), so a tap can never toggle the feature by accident while navigating.
 */
class ExperimentalFeaturesFragment : SettingsBasePreferenceFragment() {

    private lateinit var deviceSpoofPref: Preference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.experimental_features_settings, rootKey)
        deviceSpoofPref = findPreference(KEY_DEVICE_SPOOF)!!
    }

    override fun onResume() {
        super.onResume()
        activity?.setTitle(R.string.app_name)
        deviceSpoofPref.summary = getString(
            if (Settings.Global.getInt(requireContext().contentResolver, DeviceSpoof.ENABLED, 1)
                    != 0) {
                R.string.state_on
            } else {
                R.string.state_off
            }
        )
    }

    private companion object {
        const val KEY_DEVICE_SPOOF = "device_spoof"
    }
}
