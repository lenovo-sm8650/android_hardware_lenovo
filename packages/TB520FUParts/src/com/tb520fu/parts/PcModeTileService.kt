/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.parts

import android.database.ContentObserver
import android.hardware.display.DisplayManager
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.Display
import android.view.InputDevice

/**
 * Quick settings tile for PC mode (desktop windowing on the tablet screen).
 *
 * WM Shell makes the default display desktop-first (frameworks_base-0004)
 * when Settings.Global [PC_MODE] is 1, or when a keyboard and a touchpad are
 * attached, the Lenovo keyboard setting allows it and the user has not left
 * it for this attachment ([KEYBOARD_EXITED], reset on detach by tb520fu-input),
 * or when an external display (a monitor or Miracast) is connected and the
 * user has not left it for this connection ([EXTERNAL_EXITED], reset by the
 * shell when the display goes away). The tile shows every case and turns them
 * all off.
 */
class PcModeTileService : TileService() {
    private val handler = Handler(Looper.getMainLooper())

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = updateTile()
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = updateTile()
        override fun onDisplayRemoved(displayId: Int) = updateTile()
        override fun onDisplayChanged(displayId: Int) = Unit
    }

    private val inputListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = updateTile()
        override fun onInputDeviceRemoved(deviceId: Int) = updateTile()
        override fun onInputDeviceChanged(deviceId: Int) = updateTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        contentResolver.registerContentObserver(
            Settings.Global.getUriFor(PC_MODE), false, observer)
        contentResolver.registerContentObserver(
            Settings.Global.getUriFor(KEYBOARD_EXITED), false, observer)
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(LenovoKeyboard.SYSTEM_MODE), false, observer)
        contentResolver.registerContentObserver(
            Settings.Global.getUriFor(EXTERNAL_EXITED), false, observer)
        getSystemService(InputManager::class.java).registerInputDeviceListener(inputListener, handler)
        getSystemService(DisplayManager::class.java).registerDisplayListener(displayListener, handler)
        updateTile()
    }

    override fun onStopListening() {
        contentResolver.unregisterContentObserver(observer)
        getSystemService(InputManager::class.java).unregisterInputDeviceListener(inputListener)
        getSystemService(DisplayManager::class.java).unregisterDisplayListener(displayListener)
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        // Without the desktop-first opt-out (patches/frameworks_base-0004) the
        // shell ignores tb520fu_pc_mode, so leave the settings untouched.
        if (!FrameworkPatches.desktopOptOut) return
        if (isPcMode()) {
            Settings.Global.putInt(contentResolver, PC_MODE, 0)
            if (isKeyboardPcMode()) Settings.Global.putInt(contentResolver, KEYBOARD_EXITED, 1)
            if (isExternalDisplayPcMode()) Settings.Global.putInt(contentResolver, EXTERNAL_EXITED, 1)
        } else {
            // Back to the automatic PC mode of an attached keyboard or external display when
            // there is one (it ends with them); otherwise PC mode stays on until turned off.
            Settings.Global.putInt(contentResolver, KEYBOARD_EXITED, 0)
            Settings.Global.putInt(contentResolver, EXTERNAL_EXITED, 0)
            if (!isKeyboardPcMode() && !isExternalDisplayPcMode()) {
                Settings.Global.putInt(contentResolver, PC_MODE, 1)
            }
        }
        updateTile()
    }

    private fun isPcMode() =
        Settings.Global.getInt(contentResolver, PC_MODE, 0) != 0 || isKeyboardPcMode() ||
            isExternalDisplayPcMode()

    /** An external display connected and desktop mode not left for it (see the shell). */
    private fun isExternalDisplayPcMode(): Boolean {
        if (Settings.Global.getInt(contentResolver, EXTERNAL_EXITED, 0) != 0) return false
        return getSystemService(DisplayManager::class.java).displays.any {
            it.displayId != Display.DEFAULT_DISPLAY &&
                (it.type == Display.TYPE_EXTERNAL || it.type == Display.TYPE_WIFI)
        }
    }

    /** Same condition as DesktopDisplayModeController for keyboard + touchpad. */
    private fun isKeyboardPcMode(): Boolean {
        if (Settings.System.getInt(contentResolver, LenovoKeyboard.SYSTEM_MODE, 0) == 0 ||
            Settings.Global.getInt(contentResolver, KEYBOARD_EXITED, 0) != 0
        ) {
            return false
        }
        val im = getSystemService(InputManager::class.java)
        val devices = im.inputDeviceIds.asList()
            .mapNotNull { im.getInputDevice(it) }
            .filter { it.isEnabled }
        return devices.any { it.supportsSource(InputDevice.SOURCE_TOUCHPAD) } &&
            devices.any { !it.isVirtual && it.isFullKeyboard }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        tile.state = when {
            !FrameworkPatches.desktopOptOut -> Tile.STATE_UNAVAILABLE
            isPcMode() -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }

    companion object {
        const val PC_MODE = "tb520fu_pc_mode"
        const val KEYBOARD_EXITED = "tb520fu_keyboard_desktop_mode_exited"
        const val EXTERNAL_EXITED = "tb520fu_external_display_desktop_mode_exited"
    }
}
