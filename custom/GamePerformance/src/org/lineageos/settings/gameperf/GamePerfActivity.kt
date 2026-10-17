/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.gameperf

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity

/**
 * Hosts one screen of the app. Sub screens are opened as a new instance of
 * this activity (see [open]), like the Settings sub pages, so they get the
 * system activity transition and predictive back instead of an instant
 * fragment swap; inside the Settings two-pane layout they stay in the right
 * pane.
 */
class GamePerfActivity :
    CollapsingToolbarBaseActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            val name = intent.getStringExtra(EXTRA_FRAGMENT)?.takeIf { it in SUB_SCREENS }
            val fragment = if (name != null) {
                supportFragmentManager.fragmentFactory.instantiate(classLoader, name).apply {
                    arguments = intent.getBundleExtra(EXTRA_ARGS)
                }
            } else {
                GamePerfFragment()
            }
            supportFragmentManager
                .beginTransaction()
                .replace(
                    com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                    fragment,
                    TAG,
                )
                .commit()
        }
    }

    /** Preferences with android:fragment open their screen as a sub page. */
    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference,
    ): Boolean {
        val name = pref.fragment ?: return false
        open(this, name, pref.extras)
        return true
    }

    companion object {
        private const val TAG = "GamePerfActivity"
        private const val EXTRA_FRAGMENT = "org.lineageos.settings.gameperf.FRAGMENT"
        private const val EXTRA_ARGS = "org.lineageos.settings.gameperf.ARGS"

        /**
         * The screens [open] may show. The activity is exported (Settings
         * injection), so only these can be requested through the extra.
         */
        private val SUB_SCREENS = setOf(
            GamePerfFragment::class.java.name,
            GameAppFragment::class.java.name,
        )

        /** Opens [fragment] (one of [SUB_SCREENS]) as a sub page. */
        fun open(context: Context, fragment: String, args: Bundle? = null) {
            context.startActivity(
                Intent(context, GamePerfActivity::class.java)
                    .putExtra(EXTRA_FRAGMENT, fragment)
                    .putExtra(EXTRA_ARGS, args)
            )
        }

        /** Opens [fragment] as a sub page. */
        fun open(context: Context, fragment: Fragment) {
            open(context, fragment.javaClass.name, fragment.arguments)
        }

        /**
         * Leaves a sub page: pops it when it was stacked in the same activity,
         * otherwise closes its activity.
         */
        fun close(fragment: Fragment) {
            val fm = fragment.parentFragmentManager
            if (fm.backStackEntryCount > 0) {
                fm.popBackStack()
            } else {
                fragment.activity?.finish()
            }
        }
    }
}
