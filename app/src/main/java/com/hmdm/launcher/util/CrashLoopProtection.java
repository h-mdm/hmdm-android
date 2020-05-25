package com.hmdm.launcher.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.hmdm.launcher.Const;

public class CrashLoopProtection {
    // Crash loop protection
    // We consider it to be an unrecoverable fault if Headwind MDM crashes 5 times or more within a minute
    private static final long LOOP_TIME_SPAN = 60000;
    private static final long LOOP_CRASHES = 3;
    private static final String FAULT_PREFERENCE_NAME = "com.hmdm.launcher.fault";
    private static final String LAST_FAULT_TIME_PREFERENCE = "last_fault_time";
    private static final String FAULT_COUNTER_PREFERENCE = "fault_counter";

    // Register crash
    public static void registerFault(Context context) {
        SharedPreferences preferences = context.getApplicationContext().getSharedPreferences(FAULT_PREFERENCE_NAME, Context.MODE_PRIVATE);
        long faultTime = System.currentTimeMillis();
        long lastFaultTime = preferences.getLong(LAST_FAULT_TIME_PREFERENCE, 0);
        if (faultTime - lastFaultTime > LOOP_TIME_SPAN) {
            Log.i(Const.LOG_TAG, "Crash registered once");
            preferences.edit()
                    .putInt(FAULT_COUNTER_PREFERENCE, 1)
                    .putLong(LAST_FAULT_TIME_PREFERENCE, faultTime)
                    .commit();
            return;
        }
        int crashCounter = preferences.getInt(FAULT_COUNTER_PREFERENCE, 0);
        crashCounter++;

        Log.i(Const.LOG_TAG, "Crash registered " + crashCounter + " times within " + LOOP_TIME_SPAN + " ms");
        preferences.edit().putInt(FAULT_COUNTER_PREFERENCE, crashCounter).commit();
    }

    // Protection against looping
    // Returns false if loop is detected
    public static boolean isCrashLoopDetected(Context context) {
        SharedPreferences preferences = context.getApplicationContext().getSharedPreferences(FAULT_PREFERENCE_NAME, Context.MODE_PRIVATE);
        long faultTime = System.currentTimeMillis();
        long lastFaultTime = preferences.getLong(LAST_FAULT_TIME_PREFERENCE, 0);
        if (lastFaultTime == 0) {
            return false;
        }
        if (faultTime - lastFaultTime > LOOP_TIME_SPAN) {
            Log.i(Const.LOG_TAG, "No recent crashes registered");
            preferences.edit()
                    .putInt(FAULT_COUNTER_PREFERENCE, 0)
                    .putLong(LAST_FAULT_TIME_PREFERENCE, 0)
                    .commit();
            return false;
        }
        int crashCounter = preferences.getInt(FAULT_COUNTER_PREFERENCE, 0);
        if (crashCounter > LOOP_CRASHES) {
            Log.i(Const.LOG_TAG, "Crash loop detected!");
            return true;
        }
        return false;
    }
}
