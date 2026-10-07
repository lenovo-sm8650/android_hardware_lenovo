/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.customfeatures

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.android.settingslib.widget.SettingsBasePreferenceFragment

/**
 * The apps that see another device (see DeviceSpoof). Tapping an app edits
 * its brand, manufacturer and model or removes it from the list.
 */
class DeviceSpoofFragment : SettingsBasePreferenceFragment() {

    private lateinit var appsCategory: PreferenceCategory

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.device_spoof_settings, rootKey)
        appsCategory = findPreference(KEY_APPS)!!
    }

    override fun onResume() {
        super.onResume()
        activity?.setTitle(R.string.device_spoof_title)
        refresh()
    }

    private fun refresh() {
        val ctx = requireContext()
        val pm = ctx.packageManager

        appsCategory.removeAll()
        DeviceSpoof.load(ctx).forEach { (pkg, identity) ->
            val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
            appsCategory.addPreference(Preference(ctx).apply {
                key = "app_$pkg"
                title = info?.loadLabel(pm) ?: pkg
                icon = info?.loadIcon(pm)
                summary = listOf(identity.brand, identity.model)
                    .filter { it.isNotEmpty() }.joinToString(" ")
                    .ifEmpty { getString(R.string.device_spoof_unchanged) }
                setOnPreferenceClickListener { edit(pkg, identity); true }
            })
        }
        appsCategory.addPreference(Preference(ctx).apply {
            key = "app_add"
            title = getString(R.string.game_app_add)
            setIcon(R.drawable.ic_add)
            setOnPreferenceClickListener { pickApp(); true }
        })
    }

    /** Launchable apps not yet in the list, without Play services and the Play Store. */
    private fun pickApp() {
        val ctx = requireContext().applicationContext
        AppPicker.pick(this, getString(R.string.game_app_add),
                DeviceSpoof.load(ctx).keys + DeviceSpoof.EXCLUDED) { pkg ->
            DeviceSpoof.add(ctx, pkg)
            if (isAdded) refresh()
        }
    }

    /** Brand, manufacturer and model of one app; an empty field keeps the real value. */
    private fun edit(pkg: String, identity: DeviceSpoof.Identity) {
        val ctx = requireContext()
        val pad = (24 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }
        fun field(label: Int, value: String): EditText {
            layout.addView(TextView(ctx).apply {
                setText(label)
                setPadding(0, pad / 2, 0, 0)
            })
            return EditText(ctx).apply {
                setText(value)
                hint = getString(R.string.device_spoof_unchanged)
                inputType = InputType.TYPE_CLASS_TEXT
                isSingleLine = true
                layout.addView(this)
            }
        }
        val brand = field(R.string.device_spoof_brand, identity.brand)
        val manufacturer = field(R.string.device_spoof_manufacturer, identity.manufacturer)
        val model = field(R.string.device_spoof_model, identity.model)

        AlertDialog.Builder(ctx)
            .setTitle(DeviceSpoof.label(ctx, pkg))
            .setView(layout)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                DeviceSpoof.put(requireContext(), pkg, DeviceSpoof.Identity(
                    brand.text.toString(), manufacturer.text.toString(), model.text.toString()))
                refresh()
            }
            .setNeutralButton(R.string.game_app_remove) { _, _ ->
                DeviceSpoof.remove(requireContext(), pkg)
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private companion object {
        const val KEY_APPS = "device_spoof_apps"
    }
}
