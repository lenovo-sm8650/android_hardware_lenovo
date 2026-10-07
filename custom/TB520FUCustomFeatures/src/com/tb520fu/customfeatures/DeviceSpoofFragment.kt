/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.customfeatures

import android.app.AlertDialog
import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.android.settingslib.widget.MainSwitchPreference
import com.android.settingslib.widget.SettingsBasePreferenceFragment

/**
 * The device identity selected apps see (see DeviceSpoof): on/off, brand,
 * manufacturer, model and the apps. Nothing is stored in the app itself.
 */
class DeviceSpoofFragment : SettingsBasePreferenceFragment() {

    private lateinit var enabledPref: MainSwitchPreference
    private lateinit var appsCategory: PreferenceCategory
    private val fields = linkedMapOf(
        KEY_BRAND to DeviceSpoof.BRAND,
        KEY_MANUFACTURER to DeviceSpoof.MANUFACTURER,
        KEY_MODEL to DeviceSpoof.MODEL,
    )

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.device_spoof_settings, rootKey)
        enabledPref = findPreference(KEY_ENABLED)!!
        appsCategory = findPreference(KEY_APPS)!!

        enabledPref.addOnSwitchChangeListener { _, checked ->
            DeviceSpoof.setEnabled(requireContext(), checked)
        }
        fields.forEach { (prefKey, setting) ->
            findPreference<EditTextPreference>(prefKey)!!.setOnPreferenceChangeListener { pref, value ->
                DeviceSpoof.setValue(requireContext(), setting, value as String)
                updateField(pref as EditTextPreference, setting)
                false
            }
        }
        findPreference<Preference>(KEY_RESET)!!.setOnPreferenceClickListener {
            confirmReset()
            true
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.setTitle(R.string.device_spoof_title)
        refresh()
    }

    private fun refresh() {
        val ctx = requireContext()
        enabledPref.isChecked = DeviceSpoof.isEnabled(ctx)
        fields.forEach { (prefKey, setting) ->
            updateField(findPreference(prefKey)!!, setting)
        }

        val pm = ctx.packageManager
        appsCategory.removeAll()
        DeviceSpoof.load(ctx).forEach { pkg ->
            val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
            appsCategory.addPreference(Preference(ctx).apply {
                key = "app_$pkg"
                title = info?.loadLabel(pm) ?: pkg
                icon = info?.loadIcon(pm)
                summary = if (info != null) pkg else getString(R.string.device_spoof_not_installed, pkg)
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

    private fun updateField(pref: EditTextPreference, setting: String) {
        val value = DeviceSpoof.value(requireContext(), setting)
        pref.text = value
        pref.summary = value.ifEmpty { getString(R.string.device_spoof_unchanged) }
    }

    /** Launchable apps not yet in the list, without Play services and the Play Store. */
    private fun pickApp() {
        val ctx = requireContext().applicationContext
        AppPicker.pick(this, getString(R.string.game_app_add),
                DeviceSpoof.load(ctx) + DeviceSpoof.EXCLUDED) { pkg ->
            DeviceSpoof.add(ctx, pkg)
            if (isAdded) refresh()
        }
    }

    private fun confirmRemove(pkg: String) {
        val ctx = requireContext()
        AlertDialog.Builder(ctx)
            .setTitle(DeviceSpoof.label(ctx, pkg))
            .setMessage(R.string.device_spoof_remove_message)
            .setPositiveButton(R.string.game_app_remove) { _, _ ->
                DeviceSpoof.remove(requireContext(), pkg)
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmReset() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.device_spoof_reset)
            .setMessage(R.string.device_spoof_reset_message)
            .setPositiveButton(R.string.device_spoof_reset) { _, _ ->
                DeviceSpoof.reset(requireContext())
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private companion object {
        const val KEY_ENABLED = "device_spoof_enabled"
        const val KEY_BRAND = "device_spoof_brand"
        const val KEY_MANUFACTURER = "device_spoof_manufacturer"
        const val KEY_MODEL = "device_spoof_model"
        const val KEY_APPS = "device_spoof_apps"
        const val KEY_RESET = "device_spoof_reset"
    }
}
