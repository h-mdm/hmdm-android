# Headwind MDM: free and open-source MDM launcher

## Starting work
=============

Open the project directory in Android Studio (use default settings).

## Debugging on the device
=======================

1. Connect the device by USB
2. Click "Run 'App'" icon in Android Studio
3. After successful run, add device owner rights to the app (optional).

   3.1. Run in the console

`adb shell`

   3.2. Run the command in the adb console

`dpm set-device-owner com.hmdm.launcher/.AdminReceiver`

## Building the APK
================

Build the APK after you successfully build the app.

1. Setup the keys for signing the app
2. Select Build - Generate signed Bundle / APK
3. Select the place you'd like to save APK

## Building the library
====================

1. Select the 'lib' item in the project tree
2. Select Build - Make Module 'lib'
3. Find the library in the 'lib/build/outputs/aar' directory

