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
import androidx.preference.PreferenceCategory
import com.android.settingslib.widget.SettingsBasePreferenceFragment

/**
 * Main screen of the Experimental features app: app compatibility (the device identity
 * selected apps see) and the integrity category (Specter, hidden while its app is not
 * installed). The features live here, not in LenovoParts, because they are optional
 * customizations; the device tree builds without them.
 */
class ExperimentalFeaturesFragment : SettingsBasePreferenceFragment() {

    private lateinit var deviceSpoofPref: Preference
    private lateinit var specterPref: Preference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.experimental_features_settings, rootKey)
        deviceSpoofPref = findPreference(KEY_DEVICE_SPOOF)!!

        specterPref = findPreference(KEY_SPECTER)!!
        val hasSpecter = try {
            requireContext().packageManager.getActivityInfo(SPECTER_ACTIVITY, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
        specterPref.isVisible = hasSpecter
        findPreference<PreferenceCategory>(KEY_INTEGRITY)!!.isVisible = hasSpecter
        specterPref.setOnPreferenceClickListener {
            startActivity(Intent().setComponent(SPECTER_ACTIVITY))
            true
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.setTitle(R.string.app_name)
        val cr = requireContext().contentResolver
        deviceSpoofPref.summary = stateSummary(Settings.Global.getInt(cr, DeviceSpoof.ENABLED, 1))
        specterPref.summary = stateSummary(Settings.Global.getInt(cr, SPECTER_SWITCH, 1))
    }

    /** "On" unless the switch setting is 0 (every feature here is on by default). */
    private fun stateSummary(value: Int): String =
        getString(if (value != 0) R.string.state_on else R.string.state_off)

    private companion object {
        const val KEY_DEVICE_SPOOF = "device_spoof"
        const val KEY_INTEGRITY = "integrity"
        const val KEY_SPECTER = "specter"

        const val SPECTER_SWITCH = "specter"

        val SPECTER_ACTIVITY = ComponentName(
            "io.github.wnduddld0513.pif", "io.github.wnduddld0513.pif.SpecterActivity"
        )
    }
}
