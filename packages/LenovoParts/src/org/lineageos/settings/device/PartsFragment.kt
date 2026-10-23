/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.text.format.Formatter
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.android.settingslib.widget.SettingsBasePreferenceFragment

/**
 * Advanced features: landscape view and the folio case mode (Display), game performance
 * management, memory expansion and the current memory (Performance), and the Experimental
 * features row. The game performance and Experimental features rows start the separate
 * apps of the optional customizations and are hidden while those are not installed. The
 * order is the one of parts_settings.xml.
 */
class PartsFragment : SettingsBasePreferenceFragment(), Preference.OnPreferenceChangeListener {

    private lateinit var landscapePref: Preference
    private lateinit var vramPref: ListPreference
    private lateinit var statusPref: Preference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.parts_settings, rootKey)
        val ctx = requireContext()

        landscapePref = findPreference(KEY_LANDSCAPE)!!

        // Folio case mode (stock Settings.System key, per user), applied by
        // lenovo-input (FolioCover) when the hall sensor reports the cover.
        findPreference<SwitchPreferenceCompat>(KEY_FOLIO)!!.apply {
            isChecked = LenovoKeyboard.getInt(ctx, LenovoKeyboard.FOLIO_CASE_MODE, 1) != 0
            setOnPreferenceChangeListener { _, value ->
                LenovoKeyboard.putInt(requireContext(), LenovoKeyboard.FOLIO_CASE_MODE,
                    if (value as Boolean) 1 else 0)
                true
            }
        }

        vramPref = findPreference(KEY_VRAM)!!
        vramPref.entries = MemoryExtension.SIZES_GB.map { sizeLabel(it) }.toTypedArray()
        vramPref.entryValues = MemoryExtension.SIZES_GB.map { it.toString() }.toTypedArray()
        vramPref.value = MemoryExtension.selectedGb.toString()
        vramPref.onPreferenceChangeListener = this
        statusPref = findPreference(KEY_STATUS)!!

        showIfInstalled(KEY_GAME_PERF, LenovoSettings.GAME_PERF_PACKAGE,
            LenovoSettings.GAME_PERF_ACTIVITY)
        showIfInstalled(KEY_EXPERIMENTAL, LenovoSettings.EXPERIMENTAL_PACKAGE, LenovoSettings.EXPERIMENTAL_ACTIVITY)
    }

    /** Makes the row start the activity of another app, or hides it while that is missing. */
    private fun showIfInstalled(key: String, pkg: String, cls: String) {
        val pref: Preference = findPreference(key)!!
        val intent = Intent().setClassName(pkg, cls)
        pref.intent = intent
        pref.isVisible = requireContext().packageManager.resolveActivity(intent, 0) != null
    }

    override fun onResume() {
        super.onResume()
        activity?.setTitle(R.string.app_name)
        landscapePref.setSummary(
            if (LenovoSettings.getSystemInt(requireContext(), LenovoSettings.LANDSCAPE_VIEW, 1) != 0) {
                R.string.state_on
            } else {
                R.string.state_off
            }
        )
        updateVramSummary()
        updateStatus()
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        if (preference != vramPref) return false
        val gb = (newValue as String).toInt()
        MemoryExtension.selectedGb = gb
        vramPref.value = newValue
        updateVramSummary()
        if (gb != MemoryExtension.activeGb) askReboot()
        return true
    }

    private fun sizeLabel(gb: Int) =
        if (gb == 0) getString(R.string.vram_off) else getString(R.string.vram_size, gb)

    private fun updateVramSummary() {
        val selected = MemoryExtension.selectedGb
        val active = MemoryExtension.activeGb
        vramPref.summary = if (active == null || active == selected) {
            getString(R.string.vram_summary, sizeLabel(selected))
        } else {
            getString(R.string.vram_summary_pending, sizeLabel(selected), sizeLabel(active))
        }
    }

    private fun updateStatus() {
        val status = MemoryExtension.readStatus() ?: return
        val ctx = requireContext()
        val ram = Formatter.formatShortFileSize(ctx, status.ramBytes)
        val used = Formatter.formatShortFileSize(
            ctx,
            status.ramBytes - status.availableBytes + status.writebackBytes,
        )
        val active = MemoryExtension.activeGb ?: 0
        statusPref.summary = if (active > 0) {
            getString(R.string.memory_status_summary, ram, sizeLabel(active), used)
        } else {
            getString(R.string.memory_status_summary_off, ram, used)
        }
    }

    private fun askReboot() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.reboot_title)
            .setMessage(R.string.reboot_message)
            .setPositiveButton(R.string.reboot_now) { _, _ ->
                requireContext().getSystemService(PowerManager::class.java).reboot(null)
            }
            .setNegativeButton(R.string.reboot_later, null)
            .show()
    }

    private companion object {
        const val KEY_LANDSCAPE = "landscape_view"
        const val KEY_VRAM = "vram_gb"
        const val KEY_STATUS = "memory_status"
        const val KEY_FOLIO = "folio_case_mode"
        const val KEY_GAME_PERF = "game_perf"
        const val KEY_EXPERIMENTAL = "experimental"
    }
}
