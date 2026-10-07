/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.input.custom;

import android.content.ContentResolver;
import android.content.Context;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.Log;

import java.io.File;

/**
 * Removes what the dropped keybox / TEE simulator / Play Integrity Fix stack
 * left on devices that had it: the keyboxes, revocation list and target lists
 * in /data/system/tb520fu, the persist.sys.tb520fu.integrity_* switches and
 * the keybox auto rotate setting. The stack itself is gone from the build, so
 * nothing else would ever delete them. Runs on every boot and does nothing
 * once the data is gone; it only touches names this repository created.
 */
final class IntegrityLeftovers {
    private static final String TAG = "TB520FUCustom";

    private static final File DIR = new File("/data/system/tb520fu");
    private static final String[] PROPS = {
        "persist.sys.tb520fu.integrity_keybox",
        "persist.sys.tb520fu.integrity_teesim",
        "persist.sys.tb520fu.integrity_pif",
    };
    private static final String AUTO_ROTATE = "tb520fu_integrity_auto_rotate";

    private IntegrityLeftovers() {}

    static void clean(Context context) {
        boolean cleaned = false;
        if (DIR.exists()) {
            deleteRecursively(DIR);
            cleaned = true;
        }
        for (String prop : PROPS) {
            if (!SystemProperties.get(prop).isEmpty()) {
                // An empty value removes a persistent property.
                SystemProperties.set(prop, "");
                cleaned = true;
            }
        }
        ContentResolver cr = context.getContentResolver();
        if (Settings.Global.getString(cr, AUTO_ROTATE) != null) {
            cr.call(Settings.Global.CONTENT_URI, Settings.CALL_METHOD_DELETE_GLOBAL,
                    AUTO_ROTATE, null);
            cleaned = true;
        }
        if (cleaned) {
            Log.i(TAG, "removed the data of the dropped Play Integrity stack");
        }
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        if (!file.delete()) {
            Log.w(TAG, "cannot delete " + file);
        }
    }
}
