/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.input.custom;

import android.content.Context;
import android.os.Handler;
import android.os.SystemProperties;

import com.tb520fu.input.InputExtension;
import com.tb520fu.input.Safe;

/**
 * Entry point of the optional customizations jar
 * (/system_ext/framework/tb520fu-input-custom.jar). InputCore loads it through
 * com.tb520fu.input.InputExtension; this class starts the game performance
 * enforcement inside system_server.
 */
public final class CustomInput implements InputExtension {
    private static final String TAG = "TB520FUCustom";
    // The old global "Play Store as the installer" switch; the framework now
    // reads a per-app list (Settings.Global tb520fu_play_installer_apps).
    private static final String OLD_INSTALLER_SPOOF_PROP = "persist.sys.tb520fu.spoof_installer";

    private Context mContext;
    private GamePerfController mGamePerf;

    @Override
    public void init(Context context, Handler handler) {
        mContext = context;
        mGamePerf = new GamePerfController(context, handler);
    }

    @Override
    public void start() {
        Safe.run("game performance", mGamePerf::start).run();
        Safe.run("integrity leftovers", () -> IntegrityLeftovers.clean(mContext)).run();
        Safe.run("installer spoof switch", () -> {
            // An empty value deletes the persistent property.
            if (!SystemProperties.get(OLD_INSTALLER_SPOOF_PROP).isEmpty()) {
                SystemProperties.set(OLD_INSTALLER_SPOOF_PROP, "");
            }
        }).run();
    }

    @Override
    public boolean handleKey(android.view.KeyEvent event) {
        return false;
    }
}
