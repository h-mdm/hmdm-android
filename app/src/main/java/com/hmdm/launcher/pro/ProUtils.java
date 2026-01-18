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

package com.hmdm.launcher.pro;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Build;
import android.view.View;

import androidx.annotation.RequiresApi;

import com.hmdm.launcher.R;
import com.hmdm.launcher.json.ServerConfig;

import java.util.Calendar;

/**
 * These functions are available in Pro-version only
 * In a free version, the class contains stubs
 */
public class ProUtils {

    private static final String PRO_PREFERENCES_ID = ".helpers.PRO";
    private static final String PREF_KEY_APP_NAME = ".helpers.APP_NAME";
    private static final String PREF_KEY_VENDOR = ".helpers.VENDOR";

    public static boolean isPro() {
        return true;
    }

    public static boolean kioskModeRequired(Context context) {
        return false;
    }

    public static void initCrashlytics(Context context) {
        // Stub
    }

    public static void sendExceptionToCrashlytics(Throwable e) {
        // Stub
    }

    // Start the service checking if the foreground app is allowed to the user (by usage statistics)
    public static boolean checkAccessibilityService(Context context) {
        // Stub
        return true;
    }

    // Pro-version
    public static boolean checkUsageStatistics(Context context) {
        // Stub
        return true;
    }

    // Add a transparent view on top of the status bar which prevents user interaction with the status bar
    public static View preventStatusBarExpansion(Activity activity) {
        // Stub
        return null;
    }

    // Add a transparent view on top of a swipeable area at the right (opens app list on Samsung tablets)
    public static View preventApplicationsList(Activity activity) {
        // Stub
        return null;
    }

    public static View createKioskUnlockButton(Activity activity) {
        // Stub
        return null;
    }

    public static boolean isKioskAppInstalled(Context context) {
        // Stub
        return false;
    }

    public static boolean isKioskModeRunning(Context context) {
        // Stub
        return false;
    }

    public static Intent getKioskAppIntent(String kioskApp, Activity activity) {
        // Stub
        return null;
    }

    // Start COSU kiosk mode
    public static boolean startCosuKioskMode(String kioskApp, Activity activity, boolean enableSettings) {
        // Stub
        return false;
    }

    // Set/update kiosk mode options (lock tack features)
    public static void updateKioskOptions(Activity activity) {
        // Stub
    }

    // Update app list in the kiosk mode
    public static void updateKioskAllowedApps(String kioskApp, Activity activity, boolean enableSettings) {
        // Stub
    }

    public static void unlockKiosk(Activity activity) {
        // Stub
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public static void processConfig(Context context, ServerConfig config) {
        Context deviceContext = context.createDeviceProtectedStorageContext();

        SharedPreferences.Editor editor = getProPreferences(deviceContext).edit();
        if (config.getAppName() != null) {
            editor.putString(PREF_KEY_APP_NAME, config.getAppName());
        }
        if (config.getVendor() != null) {
            editor.putString(PREF_KEY_VENDOR, config.getVendor());
        }
        editor.apply();
    }
    @RequiresApi(api = Build.VERSION_CODES.N)
    private static SharedPreferences getProPreferences(Context context) {
        Context deviceContext = context.createDeviceProtectedStorageContext();
        String PACKAGE_NAME = context.getPackageName();
        return deviceContext.getSharedPreferences(
                PACKAGE_NAME + PRO_PREFERENCES_ID,
                Context.MODE_PRIVATE
        );
    }

    public static void processLocation(Context context, Location location, String provider) {
        // Stub    
    }

    public static String getAppName(Context context) {
        return context.getString(R.string.app_name);
    }

    public static String getCopyright(Context context) {
        return "(c) " + Calendar.getInstance().get(Calendar.YEAR) + " " + context.getString(R.string.vendor);
    }
}