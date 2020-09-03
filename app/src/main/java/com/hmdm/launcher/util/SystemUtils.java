package com.hmdm.launcher.util;

import android.content.Context;

import com.hmdm.launcher.Const;

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
    public static boolean becomeDeviceOwner(Context context) {
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
        } catch (Exception e) {
            e.printStackTrace();
        }
        String response = output.toString();
        return response;
    }

}
