# Headwind MDM: free and open-source MDM launcher

A Powerful Open Source Platform to Manage your Enterprise Android Devices

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/com.hmdm.launcher/)

## Starting work

Open the project directory in Android Studio (use default settings).

## Debugging on the device

1. Connect the device by USB
2. Click "Run 'App'" icon in Android Studio
3. After successful run, add device owner rights to the app (optional).

    Run in the console
   
    `adb shell`

    Run the command in the adb console
   
    `dpm set-device-owner com.hmdm.launcher/.AdminReceiver`

## Building the APK

Build the APK after you successfully build the app.

1. Setup the keys for signing the app
2. Select Build - Generate signed Bundle / APK
3. Select the place you'd like to save APK

## Building the library

1. Select the 'lib' item in the project tree
2. Select Build - Make Module 'lib'
3. Find the library in the 'lib/build/outputs/aar' directory

## Building the project in the command line

1. Install the Gradle plugin v5.1.1 (Linux only)
2. Install Android Studio or download the standalone Android SDK
3. Create the file local.properties and store the SDK location in this file:

sdk.dir=/path/to/sdk

4. Run the command

gradlew build

5. Find the resulting APK in the app/build/outputs/apk/release/ directory.

