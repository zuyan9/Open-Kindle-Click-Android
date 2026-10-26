# BLE-HID-Peripheral-for-Android
[![Build Status](https://travis-ci.org/kshoji/BLE-HID-Peripheral-for-Android.svg?branch=master)](https://travis-ci.org/kshoji/BLE-HID-Peripheral-for-Android)

## BLE HID over GATT Profile for Android

This library provides BLE HID Peripheral feature to Android devices. <br/>
Android device will behave as:

- BLE Mouse (relative position / absolute position)
- BLE Keyboard
- BLE Joystick
- Kindle Click page turner

Tested connection:

- Android(Peripheral) <--> Android(Central)
    - Relative Position Mouse, Keyboard
- Android(Peripheral) <--> OS X(Central)
    - Absolute Position Mouse, Relative Position Mouse, Keyboard
- Android(Peripheral) <--> Kindle(Central)
    - Kindle Click: Kindle Scribe (3rd gen), Kindle Paperwhite (12th gen), firmware 5.20.1

Currently, connection with iOS central is not tested yet.

Kindle Click
------------

The sample app's **Kindle Click** screen turns the phone into a Kindle Click remote: page forward / back (also the volume keys), light −/+, and sleep (hold). The Kindle shows the phone's battery level, or one you choose. The APK is on the [Releases](https://github.com/zuyan9/Open-Kindle-Click-Android/releases) page.

The volume keys keep turning pages with the phone's screen off, if the Kindle Click screen was open when it went off. When you wake the phone, the remote shows over the lock screen, so you can turn pages without unlocking it.

It should work with any Kindle that supports the Kindle Click; the tested ones are listed above.

To pair, open Settings › Wi-Fi & Bluetooth › Bluetooth devices on the Kindle, choose the remote, and confirm the code on both devices.

Known limits:

- The remote can't wake the Kindle. After you wake it, the remote works again in about 3 seconds.
- On some phones (seen on a Samsung), the Kindle keeps its old connection when the app is closed and reopened, and ignores the remote until it reconnects. The app says so; turn the Kindle's Bluetooth off and on.
- ~~Kindle firmware 5.20.1 doesn't count remote page turns as activity, so the Kindle still sleeps when its sleep timer runs out after the last screen touch. A longer timer helps (Settings › Device options › Sleep timer).~~ Fixed in firmware version 5.20.1.0.2
- To get the volume keys with the screen off, the remote tells Android it's playing to another device, as casting apps do. No player shows, but apps that list what's playing, like some smartwatch music controls, may show it.
- With the screen off, the volume keys turn pages instead of changing the volume of music you're playing. If the music is paused and resumed, or skips, while the screen is off, it takes the keys back.
- During a call, the volume keys change its volume, with the screen on or off. On Android 11 or older, screen-off page turns then come back the next time the phone sleeps with the remote open.
- On Android 14 to 16, after the phone rings or plays a system sound such as the charging sound, the volume keys don't reach the remote with the screen off until another app plays a sound.
- With the always-on display on, the volume keys may not reach the remote with the screen off; turning the always-on display off helps.
- Pixel phones before Android 15's March 2025 update, and phones on the first release of Android 12, don't pass the volume keys on with the screen off.

Not affiliated with Amazon. Kindle is a trademark of Amazon.com, Inc. or its affiliates.

Requirements
------------

- **API Level 21 or later** and **Bluetooth LE Peripheral feature** will be needed.

Repository Overview
-------------------

- Library Project: `lib`
- Sample Project: `app`

LICENSE
=======
[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)

## Reference
[Open-Kindle-Click-nRF52840](https://github.com/zuyan9/Open-Kindle-Click-nRF52840)

