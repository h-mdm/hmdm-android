package com.hmdm.launcher.receiver;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.hmdm.launcher.Const;
import com.hmdm.launcher.helper.Initializer;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.pro.ProUtils;
import com.hmdm.launcher.util.RemoteLogger;
import com.hmdm.launcher.util.Utils;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // The context the system hands to a BroadcastReceiver is receiver-restricted: it cannot
        // bindService() (ReceiverCallNotAllowedException), and that restriction stays attached to
        // this exact Context object even when it's passed into async work below, not just for the
        // duration of this call. Everything downstream (Initializer, ConfigUpdater, MQTT connect)
        // needs a Context that can bind services, so switch to the Application context up front.
        // (A separate final variable, rather than reassigning the parameter, because the lambda
        // below captures it, and a captured variable must be effectively final.)
        final Context appContext = context.getApplicationContext();

        Log.i(Const.LOG_TAG, "Got the BOOT_RECEIVER broadcast");
        RemoteLogger.log(appContext, Const.LOG_DEBUG, "Got the BOOT_RECEIVER broadcast");

        SettingsHelper settingsHelper = SettingsHelper.getInstance();
        if (!settingsHelper.isBaseUrlSet()) {
            // We're here before initializing after the factory reset! Let's ignore this call
            return;
        }

        long lastAppStartTime = settingsHelper.getAppStartTime();
        long bootTime = System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime();
        Log.d(Const.LOG_TAG, "appStartTime=" + lastAppStartTime + ", bootTime=" + bootTime);
        if (lastAppStartTime < bootTime) {
            Log.i(Const.LOG_TAG, "Headwind MDM wasn't started since boot, start initializing services");
        } else {
            Log.i(Const.LOG_TAG, "Headwind MDM is already started, ignoring BootReceiver");
            return;
        }

        Initializer.init(appContext, () -> {
            Initializer.startServicesAndLoadConfig(appContext);

            SettingsHelper.getInstance().setMainActivityRunning(false);
            if (Utils.isUserUnlocked(appContext) && ProUtils.kioskModeRequired(appContext)) {
                Log.i(Const.LOG_TAG, "Kiosk mode required, forcing Headwind MDM to run in the foreground");
                // If kiosk mode is required, then we just simulate clicking Home and starting MainActivity
                Intent homeIntent = new Intent(Intent.ACTION_MAIN);
                homeIntent.addCategory(Intent.CATEGORY_HOME);
                homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                appContext.startActivity(homeIntent);
            }
        });
    }
}
