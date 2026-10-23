/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.zui.server.input.custom;

import android.content.Context;
import android.os.Handler;

import com.zui.server.input.InputExtension;
import com.zui.server.input.Safe;

/**
 * Entry point of the optional customizations jar
 * (/system_ext/framework/lenovo-input-custom.jar). InputCore loads it through
 * com.zui.server.input.InputExtension; this class starts the game performance
 * enforcement inside system_server.
 */
public final class CustomInput implements InputExtension {
    private static final String TAG = "LenovoCustom";

    private GamePerfController mGamePerf;

    @Override
    public void init(Context context, Handler handler) {
        mGamePerf = new GamePerfController(context, handler);
    }

    @Override
    public void start() {
        Safe.run("game performance", mGamePerf::start).run();
    }

    @Override
    public boolean handleKey(android.view.KeyEvent event) {
        return false;
    }
}
