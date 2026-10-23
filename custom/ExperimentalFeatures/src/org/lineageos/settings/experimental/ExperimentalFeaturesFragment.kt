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
 * integrity category (Play Integrity Fix, TEESimulator and Specter), whose rows open the
 * pages of the separate Play Integrity Fix app and which is hidden while that app is not
 * installed. The features
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
    private var teePref: Preference? = null
    private var specterPref: Preference? = null

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
        val tee = findPreference<Preference>(KEY_TEE_SIMULATOR)!!
        tee.setOnPreferenceClickListener {
            startActivity(Intent().setComponent(TEE_ACTIVITY))
            true
        }
        teePref = tee
        val specter = findPreference<Preference>(KEY_SPECTER)!!
        specter.setOnPreferenceClickListener {
            startActivity(Intent().setComponent(SPECTER_ACTIVITY))
            true
        }
        specterPref = specter
    }

    override fun onResume() {
        super.onResume()
        activity?.setTitle(R.string.app_name)
        installerSpoofPref.summary = switchSummary(InstallerSpoof.ENABLED)
        deviceSpoofPref.summary = switchSummary(DeviceSpoof.ENABLED)
        pifPref?.summary = switchSummary(PIF_SWITCH_SETTING)
        teePref?.summary = switchSummary(TEE_SWITCH_SETTING)
        specterPref?.summary = switchSummary(SPECTER_SWITCH_SETTING)
    }

    /** "On" unless the switch setting is 0 (all three integrity features are on by default). */
    private fun switchSummary(setting: String): String = getString(
        if (Settings.Global.getInt(requireContext().contentResolver, setting, 1) != 0) {
            R.string.state_on
        } else {
            R.string.state_off
        }
    )

    private companion object {
        const val KEY_INSTALLER_SPOOF = "installer_spoof"
        const val KEY_DEVICE_SPOOF = "device_spoof"
        const val KEY_PLAY_INTEGRITY = "play_integrity"
        const val KEY_PLAY_INTEGRITY_CATEGORY = "play_integrity_category"
        const val KEY_TEE_SIMULATOR = "tee_simulator"
        const val KEY_SPECTER = "specter"
        const val PIF_SWITCH_SETTING = "lenovo_pif_module"
        const val TEE_SWITCH_SETTING = "lenovo_tee_simulator"
        const val SPECTER_SWITCH_SETTING = "lenovo_specter"
        val TEE_ACTIVITY = ComponentName(
            "io.github.wnduddld0513.pif", "io.github.wnduddld0513.pif.TeeSimulatorActivity"
        )
        val SPECTER_ACTIVITY = ComponentName(
            "io.github.wnduddld0513.pif", "io.github.wnduddld0513.pif.SpecterActivity"
        )
        val PIF_ACTIVITY = ComponentName(
            "io.github.wnduddld0513.pif", "io.github.wnduddld0513.pif.PifActivity"
        )
    }
}
