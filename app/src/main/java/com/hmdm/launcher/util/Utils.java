/*
 * Headwind MDM: Open Source Android MDM Software
 * https://h-mdm.com
 *
 * Copyright (C) 2019 Headwind Solutions LLC (http://h-sms.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hmdm.launcher.util;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.app.admin.SystemUpdatePolicy;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.ProxyInfo;
import android.os.Build;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.hmdm.launcher.BuildConfig;
import com.hmdm.launcher.Const;
import com.hmdm.launcher.json.Action;
import com.hmdm.launcher.json.ServerConfig;
import com.hmdm.launcher.ui.MainActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Utils {
    public static boolean isDeviceOwner(Context context) {
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        return dpm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && dpm.isDeviceOwnerApp(context.getPackageName());
    }

    // In the open source variant, there are no flavors, so by default it's "opensource"
    public static String getLauncherVariant() {
        return BuildConfig.FLAVOR == null || BuildConfig.FLAVOR.equals("") ? "opensource" : BuildConfig.FLAVOR;
    }

    // Automatically grant permission to get phone state (for IMEI and serial)
    @TargetApi(Build.VERSION_CODES.M)
    public static boolean autoGrantPhonePermission(Context context) {
        try {
            DevicePolicyManager devicePolicyManager = (DevicePolicyManager) context.getSystemService(
                    Context.DEVICE_POLICY_SERVICE);
            ComponentName adminComponentName = LegacyUtils.getAdminComponentName(context);

            if (devicePolicyManager.getPermissionGrantState(adminComponentName,
                    context.getPackageName(), Manifest.permission.READ_PHONE_STATE) != DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED) {
                boolean success = devicePolicyManager.setPermissionGrantState(adminComponentName,
                        context.getPackageName(), Manifest.permission.READ_PHONE_STATE, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
                if (!success) {
                    return false;
                }
            }
        } catch (NoSuchMethodError e) {
            // This exception is raised on Android 5.1
            e.printStackTrace();
            return false;
        } catch (/* SecurityException */ Exception e) {
            // No active admin ComponentInfo (not sure why could that happen)
            e.printStackTrace();
            return false;
        }
        Log.i(Const.LOG_TAG, "READ_PHONE_STATE automatically granted");
        return true;
    }

    // Automatically get dangerous permissions
    // Notice: default (null) app permission strategy is "Grant all"
    @TargetApi(Build.VERSION_CODES.M)
    public static boolean autoGrantRequestedPermissions(Context context, String packageName,
                                                        @Nullable String appPermissionStrategy,
                                                        boolean forceSdCardPermissions) {
        int locationPermissionState = DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED;
        int otherPermissionsState = DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED;

        // Determine the app permission strategy
        if (ServerConfig.APP_PERMISSIONS_ASK_LOCATION.equals(appPermissionStrategy)) {
            locationPermissionState = DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT;
        } else if (ServerConfig.APP_PERMISSIONS_DENY_LOCATION.equals(appPermissionStrategy)) {
            locationPermissionState = DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED;
        } else if (ServerConfig.APP_PERMISSIONS_ASK_ALL.equals(appPermissionStrategy)) {
            locationPermissionState = DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT;
            if (!packageName.equals(context.getPackageName())) {
                otherPermissionsState = DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT;
            }
        }

        DevicePolicyManager devicePolicyManager = (DevicePolicyManager) context.getSystemService(
                Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponentName = LegacyUtils.getAdminComponentName(context);

        try {
            List<String> permissions = getRuntimePermissions(context.getPackageManager(), packageName);

            // Some devices do not include SD card permissions in the list of runtime permissions
            // So the files could not be read or written.
            // Here we add SD card permissions manually (device owner can grant them!)
            // This is done for the Headwind MDM launcher only
            if (forceSdCardPermissions) {
                boolean hasReadExtStorage = false;
                boolean hasWriteExtStorage = false;
                for (String s : permissions) {
                    if (s.equals(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                        hasReadExtStorage = true;
                    }
                    if (s.equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                        hasWriteExtStorage = true;
                    }
                }
                if (!hasReadExtStorage) {
                    permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
                }
                if (!hasWriteExtStorage) {
                    permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                }
            }

            for (String permission : permissions) {
                int permissionState = isLocationPermission(permission) ? locationPermissionState : otherPermissionsState;
                if (devicePolicyManager.getPermissionGrantState(adminComponentName,
                        packageName, permission) != permissionState) {
                    boolean success = devicePolicyManager.setPermissionGrantState(adminComponentName,
                            packageName, permission, permissionState);
                    if (!success) {
                        return false;
                    }
                }
            }
        } catch (NoSuchMethodError e) {
            // This exception is raised on Android 5.1
            e.printStackTrace();
            return false;
        } catch (/* SecurityException */ Exception e) {
            // No active admin ComponentInfo (not sure why could that happen)
            e.printStackTrace();
            return false;
        }
        Log.i(Const.LOG_TAG, "Permissions automatically granted");
        return true;
    }

    public static boolean isLocationPermission(String permission) {
        return Manifest.permission.ACCESS_COARSE_LOCATION.equals(permission) ||
               Manifest.permission.ACCESS_FINE_LOCATION.equals(permission) ||
               Manifest.permission.ACCESS_BACKGROUND_LOCATION.equals(permission);
    }

    private static List<String> getRuntimePermissions(PackageManager packageManager, String packageName) {
        List<String> permissions = new ArrayList<>();
        PackageInfo packageInfo;
        try {
            packageInfo =
                    packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
        } catch (PackageManager.NameNotFoundException e) {
            return permissions;
        }

        boolean manageStorage = false;
        if (packageInfo != null && packageInfo.requestedPermissions != null) {
            for (String requestedPerm : packageInfo.requestedPermissions) {
                if (requestedPerm.equals(Manifest.permission.MANAGE_EXTERNAL_STORAGE)) {
                    manageStorage = true;
                }
                if (isRuntimePermission(packageManager, requestedPerm)) {
                    permissions.add(requestedPerm);
                }
            }
            // There's a bug in Android 11+: MANAGE_EXTERNAL_STORAGE can't be automatically granted
            // but if Headwind MDM is granting WRITE_EXTERNAL_STORAGE, then the app can't request
            // MANAGE_EXTERNAL_STORAGE, it's locked!
            // So the workaround is do not request WRITE_EXTERNAL_STORAGE in this case
            if (manageStorage && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                permissions.removeIf(s -> (s.equals(Manifest.permission.WRITE_EXTERNAL_STORAGE) ||
                        s.equals(Manifest.permission.READ_EXTERNAL_STORAGE)));
            }
        }
        return permissions;
    }

    private static boolean isRuntimePermission(PackageManager packageManager, String permission) {
        try {
            PermissionInfo pInfo = packageManager.getPermissionInfo(permission, 0);
            if (pInfo != null) {
                if ((pInfo.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE)
                        == PermissionInfo.PROTECTION_DANGEROUS) {
                    return true;
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
        }
        return false;
    }

    public static int OverlayWindowType() {
        // https://stackoverflow.com/questions/45867533/system-alert-window-permission-on-api-26-not-working-as-expected-permission-den
        if (  Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            return WindowManager.LayoutParams.TYPE_SYSTEM_ERROR;
        }
    }

    public static boolean isLightColor(int color) {
        final int THRESHOLD = 0xA0;
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        return red >= THRESHOLD && green >= THRESHOLD && blue >= THRESHOLD;
    }

    @TargetApi(Build.VERSION_CODES.M)
    public static void setSystemUpdatePolicy(Context context, int systemUpdateType, String scheduledFrom, String scheduledTo) {
        DevicePolicyManager devicePolicyManager = (DevicePolicyManager)context.getSystemService(
                Context.DEVICE_POLICY_SERVICE);
        ComponentName deviceAdmin = LegacyUtils.getAdminComponentName(context);

        SystemUpdatePolicy currentPolicy = null;
        try {
            currentPolicy = devicePolicyManager.getSystemUpdatePolicy();
        } catch (NoSuchMethodError e) {
            // This exception is raised on Android 5.1
            Log.e(Const.LOG_TAG, "Failed to set system update policy: " + e.getMessage());
            return;
        }
        if (currentPolicy != null) {
            // Check if policy type shouldn't be changed
            if (systemUpdateType == ServerConfig.SYSTEM_UPDATE_INSTANT && currentPolicy.getPolicyType() == SystemUpdatePolicy.TYPE_INSTALL_AUTOMATIC ||
                systemUpdateType == ServerConfig.SYSTEM_UPDATE_MANUAL && currentPolicy.getPolicyType() == SystemUpdatePolicy.TYPE_POSTPONE) {
                return;
            }
        }
        SystemUpdatePolicy newPolicy = null;
        switch (systemUpdateType) {
            case ServerConfig.SYSTEM_UPDATE_INSTANT:
                newPolicy = SystemUpdatePolicy.createAutomaticInstallPolicy();
                break;
            case ServerConfig.SYSTEM_UPDATE_SCHEDULE:
                // Here we use update window times
                if (scheduledFrom != null && scheduledTo != null) {
                    int windowStart = getMinutesFromString(scheduledFrom);
                    int windowEnd = getMinutesFromString(scheduledTo);
                    if (windowStart == -1) {
                        Log.e(Const.LOG_TAG, "Ignoring scheduled system update policy: wrong start time: " + scheduledFrom);
                        return;
                    }
                    if (windowEnd == -1) {
                        Log.e(Const.LOG_TAG, "Ignoring scheduled system update policy: wrong end time: " + scheduledFrom);
                        return;
                    }
                    newPolicy = SystemUpdatePolicy.createWindowedInstallPolicy(windowStart, windowEnd);
                } else {
                    Log.e(Const.LOG_TAG, "Ignoring scheduled system update policy: update window is not set on server");
                    return;
                }
                break;
            case ServerConfig.SYSTEM_UPDATE_MANUAL:
                newPolicy = SystemUpdatePolicy.createPostponeInstallPolicy();
                break;
        }
        try {
            devicePolicyManager.setSystemUpdatePolicy(deviceAdmin, newPolicy);
        } catch (Exception e) {
            Log.e(Const.LOG_TAG, "Failed to set system update policy: " + e.getMessage());
        }
    }

    private static int getMinutesFromString(String s) {
        try {
            // s has a fixed format: hh:mm with heading zeroes
            String hours = s.substring(0, 2);
            String minutes = s.substring(3, 5);
            int h = Integer.parseInt(hours);
            int m = Integer.parseInt(minutes);
            return h * 60 + m;
        } catch (Exception e) {
            return -1;
        }
    }

    public static boolean canInstallPackages(Context context) {
        if (BuildConfig.SYSTEM_PRIVILEGES) {
            return true;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // Global setting works for Android 7 and below
            try {
                return Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.INSTALL_NON_MARKET_APPS) == 1;
            } catch (Settings.SettingNotFoundException e) {
                return true;
            }
        } else {
            return context.getPackageManager().canRequestPackageInstalls();
        }
    }

    public static boolean canDrawOverlays(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                Settings.canDrawOverlays(context);
    }

    public static boolean checkAdminMode(Context context) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName adminComponentName = LegacyUtils.getAdminComponentName(context);
            boolean isAdminActive = dpm.isAdminActive(adminComponentName);
//            RemoteLogger.log(context, Const.LOG_DEBUG, "Admin component active: " + isAdminActive);
            return isAdminActive;
        } catch (Exception e) {
//            RemoteLogger.log(context, Const.LOG_WARN, "Failed to get device administrator status: " + e.getMessage());
            return true;
        }
    }

    public static boolean factoryReset(Context context) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                dpm.wipeData(0);
            } else {
                dpm.wipeDevice(0);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean reboot(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false;
        }
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName adminComponentName = LegacyUtils.getAdminComponentName(context);
            dpm.reboot(adminComponentName);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String getDataToken(Context context) {
        String token = context.getSharedPreferences(Const.PREFERENCES, Context.MODE_PRIVATE).getString(Const.PREFERENCES_DATA_TOKEN, null);
        if (token == null) {
            token = java.util.UUID.randomUUID().toString();
            context.getSharedPreferences(Const.PREFERENCES, Context.MODE_PRIVATE)
                    .edit()
                    .putString(Const.PREFERENCES_DATA_TOKEN, token)
                    .commit();
        }
        return token;
    }

    public static void initPasswordReset(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                String token = getDataToken(context);
                DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
                ComponentName adminComponentName = LegacyUtils.getAdminComponentName(context);
                if (dpm.setResetPasswordToken(adminComponentName, token.getBytes())) {
                    if (!dpm.isResetPasswordTokenActive(adminComponentName)) {
                        RemoteLogger.log(context, Const.LOG_WARN, "Password reset token will be activated once the user enters the current password next time.");
                    }
                } else {
                    RemoteLogger.log(context, Const.LOG_WARN, "Failed to setup password reset token, password reset requests will fail");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static boolean passwordReset(Context context, String password) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ComponentName adminComponentName = LegacyUtils.getAdminComponentName(context);
                boolean tokenActive = dpm.isResetPasswordTokenActive(adminComponentName);
                if (!tokenActive) {
                    return false;
                }
                return dpm.resetPasswordWithToken(adminComponentName, password, getDataToken(context).getBytes(), 0);
            } else {
                return dpm.resetPassword(password, 0);
            }
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isMobileDataEnabled(Context context) {
        ConnectivityManager cm = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        // A hack: use private API
        // https://stackoverflow.com/questions/12686899/test-if-background-data-and-packet-data-is-enabled-or-not?rq=1
        try {
            Class clazz = Class.forName(cm.getClass().getName());
            Method method = clazz.getDeclaredMethod("getMobileDataEnabled");
            method.setAccessible(true); // Make the method callable
            // get the setting for "mobile data"
            return (Boolean) method.invoke(cm);
        } catch (Exception e) {
            // Let it will be true by default
            return true;
        }

    }

    public static boolean isPackageInstalled(Context context, String targetPackage){
        PackageManager pm = context.getPackageManager();
        try {
            PackageInfo info = pm.getPackageInfo(targetPackage,PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
        return true;
    }

    public static boolean isMiui(Context context) {
        return isPackageInstalled(context, "com.miui.home") ||
                isPackageInstalled(context, "com.miui.securitycenter");
    }

    public static boolean lockSafeBoot(Context context) {
        if (!isDeviceOwner(context) || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false;
        }

        DevicePolicyManager devicePolicyManager = (DevicePolicyManager) context.getSystemService(
                Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponentName = LegacyUtils.getAdminComponentName(context);

        try {
            devicePolicyManager.addUserRestriction(adminComponentName, UserManager.DISALLOW_SAFE_BOOT);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public static boolean lockUsbStorage(boolean lock, Context context) {
        if (!isDeviceOwner(context) || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            try {
                // Deprecated way to lock USB
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    Settings.Secure.putInt(context.getContentResolver(), Settings.Secure.USB_MASS_STORAGE_ENABLED, 0);
                } else {
                    Settings.Global.putInt(context.getContentResolver(), Settings.Global.USB_MASS_STORAGE_ENABLED, 0);
                }
            } catch (Exception e) {
                return false;
            }
            return true;
        }

        DevicePolicyManager devicePolicyManager = (DevicePolicyManager) context.getSystemService(
                Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponentName = LegacyUtils.getAdminComponentName(context);

        try {
            if (lock) {
                devicePolicyManager.addUserRestriction(adminComponentName, UserManager.DISALLOW_USB_FILE_TRANSFER);
                devicePolicyManager.addUserRestriction(adminComponentName, UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA);
            } else {
                devicePolicyManager.clearUserRestriction(adminComponentName, UserManager.DISALLOW_USB_FILE_TRANSFER);
                devicePolicyManager.clearUserRestriction(adminComponentName, UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public static boolean setBrightnessPolicy(Boolean auto, Integer brightness, Context context) {
        if (!isDeviceOwner(context) || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false;
        }

        DevicePolicyManager devicePolicyManager = (DevicePolicyManager) context.getSystemService(
                Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponentName = LegacyUtils.getAdminComponentName(context);

        try {
            if (auto == null) {
                // This means we should unlock brightness
                devicePolicyManager.clearUserRestriction(adminComponentName, UserManager.DISALLOW_CONFIG_BRIGHTNESS);
            } else {
                // Managed brightness
                devicePolicyManager.addUserRestriction(adminComponentName, UserManager.DISALLOW_CONFIG_BRIGHTNESS);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    // This option is available in Android 9 and above
                    if (auto) {
                        devicePolicyManager.setSystemSetting(adminComponentName, Settings.System.SCREEN_BRIGHTNESS_MODE, "1");
                    } else {
                        devicePolicyManager.setSystemSetting(adminComponentName, Settings.System.SCREEN_BRIGHTNESS_MODE, "0");
                        if (brightness != null) {
                            devicePolicyManager.setSystemSetting(adminComponentName, Settings.System.SCREEN_BRIGHTNESS, "" + brightness);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public static boolean setScreenTimeoutPolicy(Boolean lock, Integer timeout, Context context) {
        if (!isDeviceOwner(context) || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false;
        }

        DevicePolicyManager devicePolicyManager = (DevicePolicyManager) context.getSystemService(
                Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponentName = LegacyUtils.getAdminComponentName(context);

        try {
            if (lock == null || !lock) {
                // This means we should unlock screen timeout
                devicePolicyManager.clearUserRestriction(adminComponentName, UserManager.DISALLOW_CONFIG_SCREEN_TIMEOUT);
            } else {
                // Managed screen timeout
                devicePolicyManager.addUserRestriction(adminComponentName, UserManager.DISALLOW_CONFIG_SCREEN_TIMEOUT);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && timeout != null) {
                    // This option is available in Android 9 and above
                    devicePolicyManager.setSystemSetting(adminComponentName, Settings.System.SCREEN_OFF_TIMEOUT, "" + (timeout * 1000));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public static boolean lockVolume(Boolean lock, Context context) {
        if (!isDeviceOwner(context) || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false;
        }

        DevicePolicyManager devicePolicyManager = (DevicePolicyManager) context.getSystemService(
                Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponentName = LegacyUtils.getAdminComponentName(context);

        try {
            if (lock == null || !lock) {
                Log.d(Const.LOG_TAG, "Unlocking volume");
                devicePolicyManager.clearUserRestriction(adminComponentName, UserManager.DISALLOW_ADJUST_VOLUME);
            } else {
                Log.d(Const.LOG_TAG, "Locking volume");
                devicePolicyManager.addUserRestriction(adminComponentName, UserManager.DISALLOW_ADJUST_VOLUME);
            }
        } catch (Exception e) {
            Log.w(Const.LOG_TAG, "Failed to lock/unlock volume: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public static boolean setVolume(int percent, Context context) {
        int[] streams = {
            AudioManager.STREAM_VOICE_CALL,
            AudioManager.STREAM_SYSTEM,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_ALARM
        };
        try {
            AudioManager audioManager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
            for (int s : streams) {
                setVolumeInternal(audioManager, s, percent);

                int v = audioManager.getStreamVolume(s);
                if (v == 0) {
                    v = 1;
                }
            }
            return true;
        } catch (Exception e) {
            Log.w(Const.LOG_TAG, "Failed to set volume: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private static void setVolumeInternal(AudioManager audioManager, int stream, int percent) throws Exception {
        int maxVolume = audioManager.getStreamMaxVolume(stream);
        int volume = (maxVolume * percent) / 100;
        audioManager.setStreamVolume(stream, volume, 0);
    }

    public static boolean disableScreenshots(Boolean disabled, Context context) {
        if (!isDeviceOwner(context) || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false;
        }

        DevicePolicyManager devicePolicyManager = (DevicePolicyManager) context.getSystemService(
                Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponentName = LegacyUtils.getAdminComponentName(context);

        try {
            devicePolicyManager.setScreenCaptureDisabled(adminComponentName, disabled);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    // Returns true if the current password is good enough, or false elsewhere
    public static boolean setPasswordMode(String passwordMode, Context context) {
        // This function works with a (deprecated) device admin as well
        // So we don't check that it has device owner rights!
        try {
            DevicePolicyManager devicePolicyManager = (DevicePolicyManager) context.getSystemService(
                    Context.DEVICE_POLICY_SERVICE);
            ComponentName adminComponentName = LegacyUtils.getAdminComponentName(context);

            if (passwordMode == null) {
                devicePolicyManager.setPasswordQuality(adminComponentName, DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED);
            } else if (passwordMode.equals(Const.PASSWORD_QUALITY_PRESENT)) {
                devicePolicyManager.setPasswordQuality(adminComponentName, DevicePolicyManager.PASSWORD_QUALITY_SOMETHING);
                devicePolicyManager.setPasswordMinimumLength(adminComponentName, 1);
            } else if (passwordMode.equals(Const.PASSWORD_QUALITY_EASY)) {
                devicePolicyManager.setPasswordQuality(adminComponentName, DevicePolicyManager.PASSWORD_QUALITY_SOMETHING);
                devicePolicyManager.setPasswordMinimumLength(adminComponentName, 6);
            } else if (passwordMode.equals(Const.PASSWORD_QUALITY_MODERATE)) {
                devicePolicyManager.setPasswordQuality(adminComponentName, DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC);
                devicePolicyManager.setPasswordMinimumLength(adminComponentName, 8);
            } else if (passwordMode.equals(Const.PASSWORD_QUALITY_STRONG)) {
                devicePolicyManager.setPasswordQuality(adminComponentName, DevicePolicyManager.PASSWORD_QUALITY_COMPLEX);
                devicePolicyManager.setPasswordMinimumLowerCase(adminComponentName, 1);
                devicePolicyManager.setPasswordMinimumUpperCase(adminComponentName, 1);
                devicePolicyManager.setPasswordMinimumNumeric(adminComponentName, 1);
                devicePolicyManager.setPasswordMinimumSymbols(adminComponentName, 1);
                devicePolicyManager.setPasswordMinimumLength(adminComponentName, 8);
            }
            boolean result = devicePolicyManager.isActivePasswordSufficient();
            if (passwordMode != null) {
                RemoteLogger.log(context, Const.LOG_DEBUG, "Active password quality sufficient: " + result);
            }
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            // If the app doesn't have enough rights, let's leave password quality as is
            if (passwordMode != null) {
                RemoteLogger.log(context, Const.LOG_WARN, "Failed to update password quality: " + e.getMessage());
            }
            return true;
        }
    }

    public static boolean setTimeZone(String timeZone, Context context) {
        if (!Utils.isDeviceOwner(context) || timeZone == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return true;
        }

        try {
            DevicePolicyManager devicePolicyManager = (DevicePolicyManager) context.getSystemService(
                    Context.DEVICE_POLICY_SERVICE);
            ComponentName adminComponentName = LegacyUtils.getAdminComponentName(context);

            if (timeZone.equals("auto")) {
                // Note: in Android 11, there is a special method for setting auto time zone
                devicePolicyManager.setGlobalSetting(adminComponentName, Settings.Global.AUTO_TIME_ZONE, "1");
            } else {
                devicePolicyManager.setGlobalSetting(adminComponentName, Settings.Global.AUTO_TIME_ZONE, "0");
                return devicePolicyManager.setTimeZone(adminComponentName, timeZone);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return true;
        }
        return true;
    }

    @SuppressLint("SourceLockedOrientationActivity")
    public static void setOrientation(Activity activity, ServerConfig config) {
        String loggedOrientation = "unspecified";
        if (config.getOrientation() != null && config.getOrientation() != 0) {
            switch (config.getOrientation()) {
                case Const.SCREEN_ORIENTATION_PORTRAIT:
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    loggedOrientation = "portrait";
                    break;
                case Const.SCREEN_ORIENTATION_LANDSCAPE:
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                    loggedOrientation = "landscape";
                    break;
                default:
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                    break;
            }
        } else {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
        Log.i(Const.LOG_TAG, "Set orientation: " + loggedOrientation);
    }

    public static boolean isLauncherIntent(Intent intent) {
        if (intent == null) {
            return false;
        }
        Set<String> categories = intent.getCategories();
        if (categories == null) {
            return false;
        }
        for (String c : categories) {
            if (c.equals(Intent.CATEGORY_LAUNCHER)) {
                return true;
            }
        }
        return false;
    }

    public static String getDefaultLauncher(Context context) {
        ActivityInfo defaultLauncherInfo = getDefaultLauncherInfo(context);
        if (defaultLauncherInfo != null) {
            return defaultLauncherInfo.packageName;
        } else {
            return null;
        }
    }

    public static ActivityInfo getDefaultLauncherInfo(Context context) {
        PackageManager localPackageManager = context.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo info = localPackageManager.resolveActivity(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        if (info == null || info.activityInfo == null) {
            return null;
        }
        return info.activityInfo;
    }

    public static boolean isServiceRunning(Context context, Class<?> serviceClass) {
        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningServiceInfo> runningServices = manager.getRunningServices(Integer.MAX_VALUE);
            for (ActivityManager.RunningServiceInfo service : runningServices) {
                if (serviceClass.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static void setDefaultLauncher(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        IntentFilter filter = new IntentFilter(Intent.ACTION_MAIN);
        filter.addCategory(Intent.CATEGORY_HOME);
        filter.addCategory(Intent.CATEGORY_DEFAULT);

        ComponentName activity = new ComponentName(context, MainActivity.class);
        setPreferredActivity(context, filter, activity, "Set Headwind MDM as default launcher");
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static void clearDefaultLauncher(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        IntentFilter filter = new IntentFilter(Intent.ACTION_MAIN);
        filter.addCategory(Intent.CATEGORY_HOME);
        filter.addCategory(Intent.CATEGORY_DEFAULT);

        setPreferredActivity(context, filter, null, "Reset default launcher");
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static void setAction(Context context, Action action) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        try {
            IntentFilter filter = new IntentFilter("android.intent.action." + action.getAction());

            if (action.getCategories() != null && action.getCategories().length() > 0) {
                String[] categories = action.getCategories().split(",");
                for (String category : categories) {
                    filter.addCategory("android.intent.category." + category);
                }
            }

            if (action.getMimeTypes() != null && action.getMimeTypes().length() > 0) {
                String[] mimeTypes = action.getMimeTypes().split(",");
                for (String mimeType : mimeTypes) {
                    try {
                        filter.addDataType(mimeType);
                    } catch (IntentFilter.MalformedMimeTypeException e) {
                    }
                }
            }

            if (action.getSchemes() != null && action.getSchemes().length() > 0) {
                String[] schemes = action.getSchemes().split(",");
                for (String scheme : schemes) {
                    filter.addDataScheme(scheme);
                }

                if (action.getHosts() != null && action.getHosts().length() > 0) {
                    String[] hosts = action.getHosts().split(",");
                    for (String host : hosts) {
                        String[] hostport = host.split(":");
                        switch (hostport.length) {
                            case 0:
                                break;
                            case 1:
                                filter.addDataAuthority(hostport[0], null);
                                break;
                            case 2:
                                filter.addDataAuthority(hostport[0], hostport[1]);
                                break;
                        }
                    }
                }
            }

            ComponentName activity = new ComponentName(action.getPackageId(), action.getActivity());
            if (activity != null) {
                setPreferredActivity(context, filter, activity, "Set " + action.getPackageId() + "/" + action.getActivity() + " as default for " + action.getAction());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static void setPreferredActivity(Context context, IntentFilter filter, ComponentName activity, String logMessage) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }

        // Set the activity as the preferred option for the device.
        ComponentName adminComponentName = LegacyUtils.getAdminComponentName(context);
        DevicePolicyManager dpm =
                (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        try {
            if (activity != null) {
                dpm.addPersistentPreferredActivity(adminComponentName, filter, activity);
            } else {
                dpm.clearPackagePersistentPreferredActivities(adminComponentName, context.getPackageName());
            }
            RemoteLogger.log(context, Const.LOG_DEBUG, logMessage + " - success");
        } catch (Exception e) {
            e.printStackTrace();
            RemoteLogger.log(context, Const.LOG_WARN, logMessage + " - failure: " + e.getMessage());
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static void releaseUserRestrictions(Context context, String restrictions) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }

        ComponentName adminComponentName = LegacyUtils.getAdminComponentName(context);
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm == null || !dpm.isDeviceOwnerApp(context.getPackageName())) {
            return;
        }

        String[] restrictionList = restrictions.split(",");
        for (String r : restrictionList) {
            try {
                dpm.clearUserRestriction(adminComponentName, r.trim());
            } catch (Exception e) {
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static void lockUserRestrictions(Context context, String restrictions) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }

        ComponentName adminComponentName = LegacyUtils.getAdminComponentName(context);
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm == null || !dpm.isDeviceOwnerApp(context.getPackageName())) {
            return;
        }

        String[] restrictionList = restrictions.split(",");
        for (String r : restrictionList) {
            try {
                dpm.addUserRestriction(adminComponentName, r.trim());
            } catch (Exception e) {
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static void unlockUserRestrictions(Context context, String restrictions) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }

        ComponentName adminComponentName = LegacyUtils.getAdminComponentName(context);
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm == null || !dpm.isDeviceOwnerApp(context.getPackageName())) {
            return;
        }

        String[] restrictionList = restrictions.split(",");
        for (String r : restrictionList) {
            try {
                dpm.clearUserRestriction(adminComponentName, r.trim());
            } catch (Exception e) {
            }
        }
    }

    // Setting proxyUrl=null clears the proxy previously set up
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static boolean setProxy(Context context, String proxyUrl) {
        ComponentName adminComponentName = LegacyUtils.getAdminComponentName(context);
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        try {
            ProxyInfo proxyInfo = null;
            if (proxyUrl != null) {
                String[] parts = proxyUrl.split(":");
                if (parts.length != 2) {
                    Log.d(Const.LOG_TAG, "Invalid proxy URL: " + proxyUrl);
                    return false;
                }
                int port = Integer.parseInt(parts[1]);
                proxyInfo = ProxyInfo.buildDirectProxy(parts[0], port);
            }
            dpm.setRecommendedGlobalProxy(adminComponentName, proxyInfo);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Load file content to String
     */
    public static String loadFileAsString(String filePath) throws java.io.IOException {
        StringBuffer fileData = new StringBuffer();
        BufferedReader reader = new BufferedReader(new FileReader(filePath));
        char[] buf = new char[1024];
        int numRead = 0;
        while((numRead = reader.read(buf)) != -1){
            String readData = String.valueOf(buf, 0, numRead);
            fileData.append(readData);
        }
        reader.close();
        return fileData.toString();
    }


    /**
     * Load input stream as String
     */
    public static String loadStreamAsString(InputStreamReader inputStreamReader) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(inputStreamReader);
            StringBuilder sb = new StringBuilder();
            String s;
            while ((s = reader.readLine()) != null) {
                sb.append(s + "\n");
            }
            return sb.toString();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    /**
     * Write String to file
     */
    public static boolean writeStringToFile(String fileName, String fileContent, boolean overwrite) {
        try {
            File file = new File(fileName);
            if (file.exists()) {
                if (overwrite) {
                    file.delete();
                } else {
                    return false;
                }
            }

            file.createNewFile();
            FileOutputStream fos = new FileOutputStream(file);
            OutputStreamWriter writer = new OutputStreamWriter(fos);
            writer.append(fileContent);
            writer.close();
            fos.close();
            return true;
        } catch (Exception e) {
            return false;
        }

    }

}
