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
 * selected apps see) and the integrity category. The features live here, not in
 * LenovoParts, because they are optional customizations; the device tree builds without
 * them.
 *
 * The integrity category has Specter, the page of the separate Play Integrity Fix app that
 * sets up and updates the PixelOS spoofing (hidden while that app is not installed), and
 * the two PixelOS spoofing pages of Developer options, Play Integrity Fix and TrickyStore.
 *
 * The management screens are opened from separate rows (the same pattern as the Advanced
 * features app), so a tap can never toggle a feature by accident while navigating.
 */
class ExperimentalFeaturesFragment : SettingsBasePreferenceFragment() {

    private lateinit var deviceSpoofPref: Preference
    private lateinit var specterPref: Preference
    private lateinit var pifPref: Preference
    private lateinit var trickyStorePref: Preference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.experimental_features_settings, rootKey)
        deviceSpoofPref = findPreference(KEY_DEVICE_SPOOF)!!

        specterPref = findPreference(KEY_SPECTER)!!
        specterPref.isVisible = try {
            requireContext().packageManager.getActivityInfo(SPECTER_ACTIVITY, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
        specterPref.setOnPreferenceClickListener {
            startActivity(Intent().setComponent(SPECTER_ACTIVITY))
            true
        }

        pifPref = findPreference(KEY_PLAY_INTEGRITY)!!
        pifPref.setOnPreferenceClickListener {
            startActivity(spoofPage(PIF_PAGE))
            true
        }
        trickyStorePref = findPreference(KEY_TRICKYSTORE)!!
        trickyStorePref.setOnPreferenceClickListener {
            startActivity(spoofPage(TRICKYSTORE_PAGE))
            true
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.setTitle(R.string.app_name)
        val cr = requireContext().contentResolver
        deviceSpoofPref.summary = stateSummary(Settings.Global.getInt(cr, DeviceSpoof.ENABLED, 1))
        specterPref.summary = stateSummary(Settings.Global.getInt(cr, SPECTER_SWITCH, 1))
        pifPref.summary = stateSummary(Settings.System.getInt(cr, PIF_SWITCH, 1))
        trickyStorePref.summary = stateSummary(Settings.System.getInt(cr, TRICKYSTORE_SWITCH, 1))
    }

    /** "On" unless the switch setting is 0 (every feature here is on by default). */
    private fun stateSummary(value: Int): String =
        getString(if (value != 0) R.string.state_on else R.string.state_off)

    /** A PixelOS spoofing page, shown by Settings like any of its sub pages. */
    private fun spoofPage(fragment: String): Intent = Intent()
        .setComponent(ComponentName(SETTINGS_PACKAGE, SUB_SETTINGS))
        .putExtra(EXTRA_SHOW_FRAGMENT, fragment)

    private companion object {
        const val KEY_DEVICE_SPOOF = "device_spoof"
        const val KEY_SPECTER = "specter"
        const val KEY_PLAY_INTEGRITY = "play_integrity"
        const val KEY_TRICKYSTORE = "trickystore"

        const val SPECTER_SWITCH = "specter"
        const val PIF_SWITCH = "spoof_pif_enabled"
        const val TRICKYSTORE_SWITCH = "spoof_trickystore_enabled"

        const val SETTINGS_PACKAGE = "com.android.settings"
        const val SUB_SETTINGS = "com.android.settings.SubSettings"
        const val EXTRA_SHOW_FRAGMENT = ":settings:show_fragment"
        const val PIF_PAGE = "com.android.settings.development.spoof.PifSettings"
        const val TRICKYSTORE_PAGE = "com.android.settings.development.spoof.TrickyStoreSettings"

        val SPECTER_ACTIVITY = ComponentName(
            "io.github.wnduddld0513.pif", "io.github.wnduddld0513.pif.SpecterActivity"
        )
    }
}
