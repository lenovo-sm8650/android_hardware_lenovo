# Lenovo input bridge

## Pen barrel double tap

The Tab Pen Pro reports HID usage `0x0c0601`. The device keylayout maps it
to F14 so AOSP can read the event. `BluetoothPenInputPolicy` consumes that physical key and,
in `pen_touch_film_tap_two=1` mode, injects the corresponding DOWN/UP as
ZUI `KEYCODE_PEN_TWO_CLICK` (601). The focused application gets the first
opportunity to switch its pen/eraser tool. Device, timing, repeat, source,
scan code and display are preserved. DOWN/UP routing is latched per device
so a settings change cannot leave an application with a stuck key.

This requires the matching framework support: the hidden KeyEvent constant
and string conversions, and DeviceKeyHandler.handleUnhandledKeyEvent called
from PhoneWindowManager.interceptUnhandledKey. Ship the framework and bridge
together. No native keycode range or vendor HAL change is required.

If the application declines the sequence, the unhandled UP follows the
stock fallback. The stock ROW partner packages (`cn.wps.moffice_eng` and
`net.huanci.hsjpro`) receive `lenovo.partner.intent.action.BT_PEN_KEY_EVENT`
with `action=01`, addressed to the original window owner's package/user.
Other apps fall back to PenService click type 1. Mode 0 suppresses the
gesture; mode 2 retains the PenService menu shortcut (click type 11).

Previously, all physical double-tap events were consumed and sent directly
to PenService as click type 1. Stock BtPenEvents only logs this click type;
it does not forward key 601 to note apps. This broke app-side shortcuts.

Reference: TB520FU stock BluetoothPenInputPolicy.processStylusPenKeyEvent,
interceptUnhandledKey, and framework-res config_stylus_pen_partner_packages.
The touchscreen HAL eraser mode and Lenovo SDK eraser rectangles are separate
from the app-side double-tap shortcut and are not changed here.

## Pen settings that reach the pen and the touch panel

`BluetoothPenConnectPolicy` applies the pen settings of PenService when they change (stock
BluetoothPenInputManager / BluetoothPenConnectContentObserver), not only when the pen connects:

- `pen_touch_film_tap_two`, `pen_touch_film_copy_paste` and `pen_set_remote_control_on` are
  written to the pen as one touch film command; `touchfilm_mode_config` selects mode A or B.
- `parker_quick_note` arms the Pen Pro quick note: the pen command (5, 6, on), the touch panel
  quick note switch (ioctl 4), and, with the screen off, the touch panel quick note state (ioctl 1)
  that follows the pen's own armed / disarmed reports (common values 2817 / 2816).
- `lenovo.intent.action.OPENBT_AND_CONNECT_PEN` (PenService pen popup) turns Bluetooth on and
  connects the attached pen.

## Pen remote control

`PenRemoteControl` (stock processStylusPenRemoteControl): while `pen_set_remote_control_on` is 1
the pen buttons drive the foreground app instead of PenService: camera apps get the click,
slide show apps get F5 / ESC and page up / down, readers get left / right, everything else gets the
media keys (or a swipe in the short video apps). The kind of the foreground app is published as
`pen_remote_control_state`. The package lists are the stock framework-res
`config_stylus_pen_remote_control_*_packages` (TB520FU ROW).

## Host regression checks

From the Android source root:

```sh
python3 hardware/lenovo/tools/test_pen_keys.py
python3 hardware/lenovo/tools/test_pen_haptics.py
```

The pen-key test compiles production BluetoothPenInputPolicy against recording Android service
fakes. It covers event delivery, modes, pairing, independent devices, repeats,
partner and generic fallbacks, users, cancellation and existing shortcuts. It
also executes the production KeyEvent name-conversion methods with native AOSP
lookup isolated for the host JVM. Before committing the fix, `--before` can
reproduce the regression using HEAD's BluetoothPenInputPolicy.

Host checks and compilation do not replace a physical pen test in a note app.
