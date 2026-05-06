# Headwind MDM — Custom Android Fork

A powerful open-source Android MDM launcher, forked from [h-mdm/hmdm-android](https://github.com/h-mdm/hmdm-android) with added call whitelisting, a built-in custom dialer, and Device Owner enforcement.

[![Get it on F-Droid](https://fdroid.gitlab.io/artwork/badge/get-it-on.png)](https://f-droid.org/packages/com.hmdm.launcher/)

---

## What This Fork Adds

This fork extends the upstream Headwind MDM Android client with:

- **Call whitelisting** — incoming calls are screened via `CallScreeningService`; only numbers in the configured whitelist are allowed through
- **Custom dialer** — built-in dialer app that shows all contacts but blocks outgoing calls to non-whitelisted numbers, with auto-formatted phone number input
- **Device Owner enforcement** — whitelist and dialer default are set silently on enrollment (API 29+); no user prompts needed
- **Offline resilience** — the whitelist is stored locally from the last policy sync and remains enforced even when the server is unreachable

---

## License

Licensed under the **Apache 2.0 License**. You may fork, modify, and distribute freely, including commercial use, with no copyleft requirement. See `LICENSE` for full terms.

---

## Prerequisites

- Android Studio (latest stable)
- Android SDK
- A signing keystore for APK release builds
- A running Headwind MDM server (see [hmdm-server](https://github.com/h-mdm/hmdm-server) or the Docker setup below)
- ADB installed and on your PATH for device enrollment


---

## Development Setup

1. Clone this repository
2. Open the project directory in Android Studio using the default import settings
3. Let Gradle sync complete — if you are on a corporate network and see SSL errors, see the note above
4. The project is ready to build

---

## Debugging on a Device

1. Connect the device by USB and enable USB debugging
2. Click **Run > Run 'app'** in Android Studio
3. Once the app is running, grant Device Owner rights via ADB (required for full lockdown and silent permission grants):

```bash
adb shell
```

Then in the ADB shell:

```bash
dpm set-device-owner com.hmdm.launcher/.AdminReceiver
```

> **Emulator tip:** Device Owner mode prevents Android Studio from hot-swapping the APK during development. For fast iteration, keep a snapshot without Device Owner active and restore it between runs. Use the emulator's Extended Controls → Snapshots to manage this.
>
> If you need to clear Device Owner to unblock the Run button:
> ```bash
> adb shell dpm clear-device-owner
> ```

---

## Configuring the Call Whitelist (Server Side)

After enrolling the device, configure the whitelist in the Headwind MDM admin panel:

1. Go to **Configurations → your config → Application Settings**
2. Add a new entry:

| Field   | Value                                  |
|---------|----------------------------------------|
| Name    | `allowed_numbers`                      |
| Type    | String                                 |
| Value   | `+14155551234,+14155555678` (comma-separated E.164 format) |
| Package | `com.hmdm.launcher`                    |

3. Save. The device will pick this up on its next config sync (typically within a few minutes). You can also trigger a sync manually from the device list.

> **Emulator testing:** The emulator reports a fake number when you place a test call from Extended Controls. Check your Logcat for the exact string the screening service sees:
> ```
> D/CallScreening: Screening call from: XXXX
> ```
> Use that exact value in `allowed_numbers` when testing on emulator.

---

## Building a Release APK

### In Android Studio

1. Set up a signing keystore if you do not already have one: **Build → Generate Signed Bundle / APK → Create new keystore**
2. Select **Build → Generate Signed Bundle / APK**
3. Choose **APK**, select your keystore, and choose a destination
4. The signed APK will be saved to your chosen location

### From the Command Line

1. Install the Android SDK (via Android Studio or standalone download)
2. Create `local.properties` in the project root:

```
sdk.dir=/path/to/android/sdk
```

3. Run:

```bash
./gradlew assembleRelease
```

4. Find the output APK at:

```
app/build/outputs/apk/release/app-release.apk
```

> **Note:** The command-line build requires Gradle 5.1.1 or later. On Windows, use `gradlew.bat`.

---

## Device Enrollment with Your Custom APK

Because this is a fork with a custom signing key, devices must be enrolled using your APK rather than the official Headwind launcher. Any devices previously enrolled with the upstream APK will need to be factory reset and re-enrolled.

Enrollment steps:

1. Factory reset the target device (or use a fresh device)
2. Sideload your APK, or host it on your Headwind server and use the QR code enrollment flow
3. Grant Device Owner via ADB as shown in the Debugging section above
4. The device will contact your server, pull the configuration, and enforce the whitelist automatically

---

## Building the Library Module

If you need to build only the `lib` module (e.g., to use it as a dependency in another project):

1. Select the `lib` item in the Android Studio project tree
2. Select **Build → Make Module 'lib'**
3. Find the output AAR at:

```
lib/build/outputs/aar/
```

---

## Keeping Up with Upstream

This fork tracks [h-mdm/hmdm-android](https://github.com/h-mdm/hmdm-android). To pull upstream security and feature updates:

```bash
git remote add upstream https://github.com/h-mdm/hmdm-android.git
git fetch upstream
git merge upstream/master
```

Resolve any conflicts in the custom files (`CallWhitelistManager.java`, `CallWhitelistScreeningService.java`, the custom dialer activity, and `AndroidManifest.xml`) before rebuilding.

---

## Related

- [Headwind MDM Server (Docker setup)](https://github.com/h-mdm/hmdm-server) — the companion server this client connects to
- [Upstream Android client](https://github.com/h-mdm/hmdm-android) — original source this fork is based on
- [Headwind MDM docs](https://h-mdm.com/documentation/) — official documentation for the server and admin panel