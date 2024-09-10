package com.hmdm.launcher.util;

import android.annotation.TargetApi;
import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import com.hmdm.launcher.BuildConfig;
import com.hmdm.launcher.Const;
import com.hmdm.launcher.helper.SettingsHelper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

/**
 * These utils are used only in the 'system' flavor
 * when Headwind MDM is installed as a system app and
 * signed by OS keys
 */
public class SystemUtils {

    /**
     * This command requires system privileges: MANAGE_DEVICE_ADMINS, MANAGE_PROFILE_AND_DEVICE_OWNERS
     * The MANAGE_PROFILE_AND_DEVICE_OWNERS is only provided to system apps!
     *
     * Also, there's the following restriction in the DevicePolicyManagerService.java:
     * The device owner can only be set before the setup phase of the primary user has completed,
     * except for adb command if no accounts or additional users are present on the device.
     *
     * So it looks like Headwind MDM can never declare itself as a device owner,
     * except when it is running from inside a setup wizard (or declares itself as a setup wizard!)
     * To become a setup wizard, Headwind MDM should be preinstalled in the system, and
     * handle the following intent: android.intent.action.DEVICE_INITIALIZATION_WIZARD
     *
     * @param context
     * @return
     */
    public static boolean becomeDeviceOwnerByCommand(Context context) {
        String command = "dpm set-device-owner " + context.getPackageName() + "/.AdminReceiver";
        String result = executeShellCommand(command, false);
        RemoteLogger.log(context, Const.LOG_INFO, "DPM command output: " + result);
        return result.startsWith("Active admin component set");
    }

    public static String executeShellCommand(String command, boolean useShell) {
        StringBuffer output = new StringBuffer();

        Process p;
        try {
            if (useShell) {
                String[] cmdArray = {"sh", "-c", command};
                p = Runtime.getRuntime().exec(cmdArray);
            } else {
                p = Runtime.getRuntime().exec(command);
            }
            p.waitFor();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));

            String line = "";
            while ((line = reader.readLine()) != null) {
                output.append(line + "\n");
            }

            if (output.toString().trim().equalsIgnoreCase("")) {
                // No output, try to read an error!
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
                while ((line = errorReader.readLine()) != null) {
                    output.append(line + "\n");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        String response = output.toString();
        return response;
    }

    public static boolean autoSetDeviceId(Context context) {
        String deviceIdUse = SettingsHelper.getInstance(context).getDeviceIdUse();
        String deviceId = null;
        Log.d(Const.LOG_TAG, "Device ID choice: " + deviceIdUse);
        if (BuildConfig.DEVICE_ID_CHOICE.equals("imei") || "imei".equals(deviceIdUse)) {
            deviceId = DeviceInfoProvider.getImei(context);
        } else if (BuildConfig.DEVICE_ID_CHOICE.equals("serial") || "serial".equals(deviceIdUse)) {
            deviceId = DeviceInfoProvider.getSerialNumber();
            if (deviceId.equals(Build.UNKNOWN)) {
                deviceId = null;
            }
        } else if (BuildConfig.DEVICE_ID_CHOICE.equals("mac")) {
            deviceId = DeviceInfoProvider.getMacAddress();
        }

        if (deviceId == null || deviceId.length() == 0) {
            return false;
        }

        return SettingsHelper.getInstance(context.getApplicationContext()).setDeviceId(deviceId);
    }

    public static boolean becomeDeviceOwnerByXmlFile(Context context) {
        ComponentName cn = LegacyUtils.getAdminComponentName(context);

        final String deviceOwnerFileName = "/data/system/device_owner_2.xml";
        final String deviceOwnerFileContent = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n" +
                "<root>\n" +
                "<device-owner package=\"" + cn.getPackageName() + "\" name=\"\" " +
                "component=\"" + cn.getPackageName() + "/" + cn.getClassName() + "\" userRestrictionsMigrated=\"true\" canAccessDeviceIds=\"true\" />\n" +
                "<device-owner-context userId=\"0\" />\n" +
                "</root>";

        final String devicePoliciesFileName = "/data/system/device_policies.xml";
        final String devicePoliciesFileContent = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n" +
                "<policies setup-complete=\"true\" provisioning-state=\"3\">\n" +
                "<admin name=\"" + cn.getPackageName() + "/" + cn.getClassName() + "\">\n" +
                "<policies flags=\"17\" />\n" +
                "<strong-auth-unlock-timeout value=\"0\" />\n" +
                "<user-restrictions no_add_managed_profile=\"true\" />\n" +
                "<default-enabled-user-restrictions>\n" +
                "<restriction value=\"no_add_managed_profile\" />\n" +
                "</default-enabled-user-restrictions>\n" +
                "<cross-profile-calendar-packages />\n" +
                "</admin>\n" +
                "<password-validity value=\"true\" />\n" +
                "<lock-task-features value=\"16\" />\n" +
                "</policies>";

        if (!Utils.writeStringToFile(deviceOwnerFileName, deviceOwnerFileContent, false)) {
            Log.e(Const.LOG_TAG, "Could not create device owner file " + deviceOwnerFileName);
            return false;
        }

        // Now when we succeeded to create the device owner file, let's update device policies file
        if (!Utils.writeStringToFile(devicePoliciesFileName, devicePoliciesFileContent, true)) {
            Log.e(Const.LOG_TAG, "Could not update device policies file " + devicePoliciesFileName);
            return false;
        }
        return true;
    }

    // https://stackoverflow.com/questions/10061154/how-to-programmatically-enable-disable-accessibility-service-in-android
    public static void autoSetAccessibilityPermission(Context context, String packageName, String className) {
        Settings.Secure.putString(context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, packageName + "/" + className);
        Settings.Secure.putString(context.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_ENABLED, "1");
    }

    static final int OP_WRITE_SETTINGS = 23;
    static final int OP_SYSTEM_ALERT_WINDOW = 24;
    static final int APP_OP_GET_USAGE_STATS = 43;
    static final int OP_MANAGE_EXTERNAL_STORAGE = 92;

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static boolean autoSetOverlayPermission(Context context, String packageName) {
        return autoSetPermission(context, packageName, OP_SYSTEM_ALERT_WINDOW, "Overlay");
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static boolean autoSetUsageStatsPermission(Context context, String packageName) {
        return autoSetPermission(context, packageName, APP_OP_GET_USAGE_STATS, "Usage history");
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static boolean autoSetStoragePermission(Context context, String packageName) {
        return autoSetPermission(context, packageName, OP_MANAGE_EXTERNAL_STORAGE, "Manage storage");
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static boolean autoSetPermission(Context context, String packageName, int permission, String permText) {
        PackageManager packageManager = context.getPackageManager();
        int uid = 0;
        try {
            ApplicationInfo applicationInfo = packageManager.getApplicationInfo(packageName, 0);
            uid = applicationInfo.uid;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return false;
        }

        AppOpsManager appOpsManager = (AppOpsManager)context.getSystemService(Context.APP_OPS_SERVICE);

        // src/com/android/settings/applications/DrawOverlayDetails.java
        // See method: void setCanDrawOverlay(boolean newState)
        try {
            Class clazz = AppOpsManager.class;
            Method method = clazz.getDeclaredMethod("setMode", int.class, int.class, String.class, int.class);
            method.invoke(appOpsManager, permission, uid, packageName, AppOpsManager.MODE_ALLOWED);
            Log.d(Const.LOG_TAG, permText + " permission granted to " + packageName);
            return true;
        } catch (Exception e) {
            Log.e(Const.LOG_TAG, Log.getStackTraceString(e));
            return false;
        }
    }
}
