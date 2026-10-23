/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device

import android.app.ActivityManager
import android.app.AlertDialog
import android.app.AppGlobals
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.UserHandle
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.android.settingslib.PrimarySwitchPreference
import com.android.settingslib.widget.MainSwitchPreference
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import java.text.Collator
import java.util.concurrent.Executors

/**
 * Landscape view: portrait-only apps open in landscape instead of rotating the
 * screen (lenovo-input applies zui_landscape_enable). Per app, the switch
 * lets it keep its own orientation and the layout is the AOSP user aspect
 * ratio: the app's own (letterboxed) or full screen.
 */
class LandscapeViewFragment : SettingsBasePreferenceFragment() {

    private class App(val pkg: String, val label: String, val icon: Drawable, var ratio: Int)

    private lateinit var mainPref: MainSwitchPreference
    private lateinit var appsCategory: PreferenceCategory
    private lateinit var footerPref: Preference
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.landscape_settings, rootKey)
        appsCategory = findPreference(KEY_APPS)!!
        footerPref = findPreference(KEY_APPS_FOOTER)!!
        mainPref = findPreference(KEY_MAIN)!!
        mainPref.setOnPreferenceChangeListener { _, value ->
            val on = value as Boolean
            LenovoSettings.putSystemInt(requireContext(), LenovoSettings.LANDSCAPE_VIEW, if (on) 1 else 0)
            appsCategory.isEnabled = on
            true
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.setTitle(R.string.landscape_title)
        val on = LenovoSettings.getSystemInt(requireContext(), LenovoSettings.LANDSCAPE_VIEW, 1) != 0
        mainPref.isChecked = on
        appsCategory.isEnabled = on
        loadApps()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    /** Every launcher app, by label, like the stock list; apps not turned off are on. */
    private fun loadApps() {
        val ctx = requireContext().applicationContext
        executor.execute {
            val pm = ctx.packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0))
                .distinctBy { it.activityInfo.packageName }
                .map {
                    val pkg = it.activityInfo.packageName
                    App(pkg, it.loadLabel(pm).toString(), it.loadIcon(pm), aspectRatio(pkg))
                }
                .sortedWith(compareBy(Collator.getInstance()) { it.label })
            activity?.runOnUiThread { if (isAdded) showApps(apps) }
        }
    }

    private fun showApps(apps: List<App>) {
        val ctx = requireContext()
        val excluded = LenovoSettings.landscapeExcluded(ctx)
        appsCategory.removeAll()
        for (app in apps) {
            appsCategory.addPreference(PrimarySwitchPreference(ctx).apply {
                key = app.pkg
                title = app.label
                icon = app.icon
                isPersistent = false
                isChecked = app.pkg !in excluded
                summary = appSummary(app, isChecked)
                setOnPreferenceChangeListener { _, value ->
                    val included = value as Boolean
                    val set = LenovoSettings.landscapeExcluded(requireContext())
                    if (included) set.remove(app.pkg) else set.add(app.pkg)
                    LenovoSettings.setLandscapeExcluded(requireContext(), set)
                    summary = appSummary(app, included)
                    true
                }
                setOnPreferenceClickListener {
                    showLayoutDialog(app, this)
                    true
                }
            })
        }
        appsCategory.isVisible = apps.isNotEmpty()
        footerPref.isVisible = apps.isNotEmpty()
    }

    private fun appSummary(app: App, included: Boolean) = getString(
        when {
            !included -> R.string.landscape_app_off
            app.ratio == PackageManager.USER_MIN_ASPECT_RATIO_FULLSCREEN ->
                R.string.landscape_mode_fullscreen
            app.ratio == PackageManager.USER_MIN_ASPECT_RATIO_UNSET ||
                app.ratio == PackageManager.USER_MIN_ASPECT_RATIO_APP_DEFAULT ->
                R.string.landscape_mode_default
            else -> R.string.landscape_mode_custom
        }
    )

    private fun showLayoutDialog(app: App, pref: PrimarySwitchPreference) {
        val checked = LAYOUTS.indexOf(
            if (app.ratio == PackageManager.USER_MIN_ASPECT_RATIO_APP_DEFAULT) {
                PackageManager.USER_MIN_ASPECT_RATIO_UNSET
            } else {
                app.ratio
            }
        )
        val labels = arrayOf<CharSequence>(
            getString(R.string.landscape_mode_default),
            getString(R.string.landscape_mode_fullscreen),
        )
        AlertDialog.Builder(requireContext())
            .setTitle(app.label)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                setAspectRatio(app, LAYOUTS[which])
                pref.summary = appSummary(app, pref.isChecked)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Like Settings > Apps > Aspect ratio: the app restarts with the new ratio. */
    private fun setAspectRatio(app: App, ratio: Int) {
        if (ratio == app.ratio) return
        val userId = UserHandle.myUserId()
        AppGlobals.getPackageManager().setUserMinAspectRatio(app.pkg, userId, ratio)
        ActivityManager.getService().stopAppForUser(app.pkg, userId)
        app.ratio = ratio
    }

    private fun aspectRatio(pkg: String) =
        AppGlobals.getPackageManager().getUserMinAspectRatio(pkg, UserHandle.myUserId())

    private companion object {
        const val KEY_MAIN = "landscape_main"
        const val KEY_APPS = "landscape_apps"
        const val KEY_APPS_FOOTER = "landscape_apps_footer"
        val LAYOUTS = listOf(
            PackageManager.USER_MIN_ASPECT_RATIO_UNSET,
            PackageManager.USER_MIN_ASPECT_RATIO_FULLSCREEN,
        )
    }
}
