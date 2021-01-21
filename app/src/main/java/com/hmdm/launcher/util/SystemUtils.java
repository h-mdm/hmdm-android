package com.hmdm.launcher.util;

import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.hmdm.launcher.BuildConfig;
import com.hmdm.launcher.Const;
import com.hmdm.launcher.helper.SettingsHelper;

import java.io.BufferedReader;
import java.io.InputStreamReader;

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
        String result = executeShellCommand(command);
        RemoteLogger.log(context, Const.LOG_INFO, "DPM command output: " + result);
        return result.startsWith("Active admin component set");
    }

    public static String executeShellCommand(String command) {
        StringBuffer output = new StringBuffer();

        Process p;
        try {
            p = Runtime.getRuntime().exec(command);
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
        String deviceId = null;
        if (BuildConfig.DEVICE_ID_CHOICE.equals("imei")) {
            deviceId = DeviceInfoProvider.getImei(context);
        } else if (BuildConfig.DEVICE_ID_CHOICE.equals("serial")) {
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
}
