/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.input;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.LocaleList;
import android.util.Log;

import java.util.Locale;

/**
 * Default Wi-Fi country from the region of the system language (Korean
 * (South Korea) -> KR), the way stock ZUI takes the country picked in its
 * setup wizard. The tablet has no telephony, and the stock Lenovo factory
 * country code service is not in this build, so without it the driver kept
 * its default country (US) everywhere.
 *
 * It is the framework's default country (WifiManager.setDefaultCountryCode),
 * the lowest priority: the country the access points around agree on
 * (config_wifiUpdateCountryCodeFromScanResultGeneric, as on the Pixel
 * Tablet) still wins, for example abroad. A language without a region leaves
 * the last default in place.
 */
final class WifiCountryFromLocale {
    private static final String TAG = "TB520FUWifiCountry";

    private final Context mContext;
    private final Handler mHandler;
    private String mApplied;

    WifiCountryFromLocale(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
    }

    void start() {
        mContext.registerReceiver(Safe.receiver("wifi country", (c, i) -> apply()),
                new IntentFilter(Intent.ACTION_LOCALE_CHANGED), null, mHandler,
                Context.RECEIVER_EXPORTED);
        apply();
    }

    private void apply() {
        LocaleList locales = Resources.getSystem().getConfiguration().getLocales();
        String region = locales.isEmpty() ? "" : locales.get(0).getCountry();
        // Two letter ISO 3166 regions only (no "419" style UN M.49 codes).
        if (region == null || !region.matches("[A-Za-z]{2}")) {
            Log.i(TAG, "No region in " + locales.toLanguageTags() + ", default unchanged");
            return;
        }
        String country = region.toUpperCase(Locale.ROOT);
        if (country.equals(mApplied)) {
            return;
        }
        mContext.getSystemService(WifiManager.class).setDefaultCountryCode(country);
        mApplied = country;
        Log.i(TAG, "Default Wi-Fi country " + country + " from " + locales.toLanguageTags());
    }
}
