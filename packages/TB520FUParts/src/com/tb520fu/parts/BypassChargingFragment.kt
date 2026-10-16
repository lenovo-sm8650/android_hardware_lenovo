/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.parts

import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.preference.PreferenceCategory
import com.android.settingslib.widget.MainSwitchPreference
import com.android.settingslib.widget.SelectorWithWidgetPreference
import com.android.settingslib.widget.SettingsBasePreferenceFragment

/**
 * Bypass charging: while plugged in, the tablet runs on charger power inside a
 * battery range (tb520fu-input BatteryController), like the stock ZUI page.
 */
class BypassChargingFragment : SettingsBasePreferenceFragment() {

    private lateinit var mainPref: MainSwitchPreference
    private lateinit var modePrefs: List<SelectorWithWidgetPreference>
    private lateinit var rangePref: RangeSliderPreference

    /** "This time only" turns bypass charging off when unplugged, also while shown. */
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = update()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.bypass_charging_settings, rootKey)

        mainPref = findPreference(KEY_MAIN)!!
        mainPref.setOnPreferenceChangeListener { _, value ->
            LenovoSettings.putInt(requireContext(), LenovoSettings.BYPASS_CHARGING,
                if (value as Boolean) 1 else 0)
            true
        }

        modePrefs = MODE_KEYS.map { findPreference<SelectorWithWidgetPreference>(it)!! }
        modePrefs.forEachIndexed { i, pref ->
            pref.setOnClickListener {
                LenovoSettings.putInt(requireContext(), LenovoSettings.BYPASS_MODE, MODES[i])
            }
        }

        rangePref = findPreference(KEY_RANGE)!!
        rangePref.setBounds(LenovoSettings.BYPASS_LIMIT_LOW, LenovoSettings.BYPASS_LIMIT_HIGH,
            LenovoSettings.BYPASS_STEP)
        rangePref.onRangeChanged = { min, max ->
            val ctx = requireContext()
            LenovoSettings.putInt(ctx, LenovoSettings.BYPASS_MIN, min)
            LenovoSettings.putInt(ctx, LenovoSettings.BYPASS_MAX, max)
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.setTitle(R.string.bypass_title)
        val resolver = requireContext().contentResolver
        for (key in OBSERVED) {
            resolver.registerContentObserver(Settings.Global.getUriFor(key), false, observer)
        }
        update()
    }

    override fun onPause() {
        requireContext().contentResolver.unregisterContentObserver(observer)
        super.onPause()
    }

    private fun update() {
        val ctx = requireContext()
        val on = LenovoSettings.getInt(ctx, LenovoSettings.BYPASS_CHARGING, 0) != 0
        mainPref.isChecked = on
        findPreference<PreferenceCategory>(KEY_MODE_CATEGORY)!!.isEnabled = on
        findPreference<PreferenceCategory>(KEY_RANGE_CATEGORY)!!.isEnabled = on

        val mode = LenovoSettings.getInt(ctx, LenovoSettings.BYPASS_MODE,
            LenovoSettings.BYPASS_MODE_ALWAYS)
        modePrefs.forEachIndexed { i, pref -> pref.isChecked = MODES[i] == mode }

        val (min, max) = LenovoSettings.bypassRange(ctx)
        rangePref.setRange(min, max)
    }

    private companion object {
        const val KEY_MAIN = "bypass_main"
        const val KEY_MODE_CATEGORY = "bypass_mode"
        const val KEY_RANGE_CATEGORY = "bypass_range_category"
        const val KEY_RANGE = "bypass_range"
        val MODE_KEYS = listOf("bypass_always", "bypass_once")
        val MODES = listOf(LenovoSettings.BYPASS_MODE_ALWAYS, LenovoSettings.BYPASS_MODE_ONCE)
        val OBSERVED = listOf(
            LenovoSettings.BYPASS_CHARGING,
            LenovoSettings.BYPASS_MODE,
            LenovoSettings.BYPASS_MIN,
            LenovoSettings.BYPASS_MAX,
        )
    }
}
