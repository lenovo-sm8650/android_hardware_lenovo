/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.input;

import android.compat.Compatibility.ChangeConfig;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;
import android.view.Display;
import android.view.IWindowManager;
import android.view.WindowManagerGlobal;

import com.android.internal.compat.CompatibilityChangeConfig;
import com.android.internal.compat.IPlatformCompat;

import java.util.Collections;
import java.util.Set;

/**
 * Landscape view of the stock "Landscape view" setting, done with AOSP window manager
 * mechanisms only:
 * <ul>
 *   <li>{@code tb520fu_landscape_view} (default 1) maps to
 *       {@link IWindowManager#setIgnoreOrientationRequest(int, boolean)} on the default display:
 *       1 ignores the orientation apps request (they follow the device rotation), 0 restores
 *       the apps' own orientation. Applied at boot and whenever the setting changes.</li>
 *   <li>{@code tb520fu_landscape_excluded} (comma separated package names) keeps the
 *       packages' own orientation even while the display ignores requests: each listed
 *       package gets the compat override
 *       {@link ActivityInfo#OVERRIDE_RESPECT_REQUESTED_ORIENTATION} through
 *       {@link IPlatformCompat#setOverrides}, packages dropped from the list lose it again
 *       ({@link IPlatformCompat#clearOverride}). The platform restarts a package whose
 *       override changes, so it is only touched when its state differs. Packages that are not
 *       installed yet get the override when they are added.</li>
 * </ul>
 * {@code tb520fu_landscape_excluded_applied} remembers the packages this controller put the
 * override on, so entries removed from the list while this was not running get cleaned up.
 * Per-app full screen vs letterbox is the AOSP user aspect ratio
 * (PackageManager#setUserMinAspectRatio), set by the settings UI itself.
 */
final class LandscapeController {
    private static final String TAG = "TB520FULandscape";

    static final String SETTING_VIEW = "tb520fu_landscape_view";
    static final String SETTING_EXCLUDED = "tb520fu_landscape_excluded";
    static final String SETTING_APPLIED = "tb520fu_landscape_excluded_applied";

    private final Context mContext;
    private final Handler mHandler;
    private IPlatformCompat mCompat;

    LandscapeController(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
    }

    void start() {
        ContentResolver cr = mContext.getContentResolver();
        cr.registerContentObserver(Settings.Global.getUriFor(SETTING_VIEW), false,
                Safe.observer(mHandler, "landscape view", uri -> applyView()));
        cr.registerContentObserver(Settings.Global.getUriFor(SETTING_EXCLUDED), false,
                Safe.observer(mHandler, "landscape excluded", uri -> applyExcluded()));
        IntentFilter added = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        added.addDataScheme("package");
        mContext.registerReceiverForAllUsers(Safe.receiver("landscape package added", (c, i) -> {
            Uri data = i.getData();
            String pkg = data == null ? null : data.getSchemeSpecificPart();
            if (pkg != null && readList(SETTING_EXCLUDED).contains(pkg)) applyExcluded();
        }), added, null, mHandler);
        applyView();
        applyExcluded();
    }

    private void applyView() {
        boolean ignore = Settings.Global.getInt(mContext.getContentResolver(), SETTING_VIEW, 1) != 0;
        try {
            IWindowManager wm = WindowManagerGlobal.getWindowManagerService();
            if (wm.getIgnoreOrientationRequest(Display.DEFAULT_DISPLAY) != ignore) {
                wm.setIgnoreOrientationRequest(Display.DEFAULT_DISPLAY, ignore);
            }
            Log.i(TAG, "ignore orientation request " + ignore);
        } catch (RemoteException e) {
            Log.e(TAG, "setIgnoreOrientationRequest", e);
        }
    }

    private void applyExcluded() {
        Set<String> wanted = readList(SETTING_EXCLUDED);
        Set<String> applied = readList(SETTING_APPLIED);
        Set<String> nowApplied = new ArraySet<>();
        for (String pkg : applied) {
            if (!wanted.contains(pkg) && !setRespected(pkg, false)) nowApplied.add(pkg);
        }
        for (String pkg : wanted) {
            if (setRespected(pkg, true) || isRespected(pkg)) nowApplied.add(pkg);
        }
        writeList(SETTING_APPLIED, nowApplied);
    }

    /** Returns true if the package now has the requested state. */
    private boolean setRespected(String pkg, boolean respect) {
        try {
            IPlatformCompat compat = compat();
            if (isRespected(pkg) == respect) return true;
            long change = ActivityInfo.OVERRIDE_RESPECT_REQUESTED_ORIENTATION;
            if (respect) {
                ChangeConfig config = new ChangeConfig(Collections.singleton(change),
                        Collections.emptySet());
                compat.setOverrides(new CompatibilityChangeConfig(config), pkg);
            } else {
                compat.clearOverride(change, pkg);
            }
            Log.i(TAG, (respect ? "respect" : "ignore") + " requested orientation: " + pkg);
            return true;
        } catch (RemoteException | RuntimeException e) {
            Log.w(TAG, "compat override for " + pkg, e);
            return false;
        }
    }

    private boolean isRespected(String pkg) {
        try {
            return compat().isChangeEnabledByPackageName(
                    ActivityInfo.OVERRIDE_RESPECT_REQUESTED_ORIENTATION, pkg,
                    UserHandle.USER_SYSTEM);
        } catch (RemoteException | RuntimeException e) {
            return false;
        }
    }

    private IPlatformCompat compat() {
        if (mCompat == null) {
            mCompat = IPlatformCompat.Stub.asInterface(
                    ServiceManager.getService(Context.PLATFORM_COMPAT_SERVICE));
        }
        return mCompat;
    }

    private Set<String> readList(String key) {
        Set<String> out = new ArraySet<>();
        String value = Settings.Global.getString(mContext.getContentResolver(), key);
        if (TextUtils.isEmpty(value)) return out;
        for (String pkg : value.split(",")) {
            pkg = pkg.trim();
            if (!pkg.isEmpty()) out.add(pkg);
        }
        return out;
    }

    private void writeList(String key, Set<String> packages) {
        String value = TextUtils.join(",", packages);
        ContentResolver cr = mContext.getContentResolver();
        if (!value.equals(Settings.Global.getString(cr, key))
                && !(value.isEmpty() && Settings.Global.getString(cr, key) == null)) {
            Settings.Global.putString(cr, key, value);
        }
    }
}
