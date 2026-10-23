/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device

import android.content.Intent
import android.os.Bundle
import androidx.preference.Preference
import com.android.settingslib.widget.SettingsBasePreferenceFragment

/** Pen & keyboard: the stock Lenovo pen settings and the physical keyboard. */
class PenKeyboardFragment : SettingsBasePreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pen_keyboard_settings, rootKey)

        val penPref: Preference = findPreference(KEY_PEN_SETTINGS)!!
        // No own task, so the stock pen page opens in the Settings two-pane
        // right pane like the other sub pages. An overlay
        // (PenServiceResTarget) shrinks its pen picture and battery row to
        // fit the narrow pane.
        val penIntent = Intent(LenovoSettings.ACTION_PEN_SETTINGS)
            .setClassName(LenovoSettings.PEN_PACKAGE, LenovoSettings.PEN_SETTINGS_ACTIVITY)
        penPref.intent = penIntent
        penPref.isVisible = requireContext().packageManager.resolveActivity(penIntent, 0) != null
    }

    override fun onResume() {
        super.onResume()
        activity?.setTitle(R.string.pen_keyboard_title)
    }

    private companion object {
        const val KEY_PEN_SETTINGS = "pen_settings"
    }
}
