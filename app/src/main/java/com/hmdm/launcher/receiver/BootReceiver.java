package com.hmdm.launcher.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import com.hmdm.launcher.BuildConfig;
import com.hmdm.launcher.Const;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.json.ServerConfig;
import com.hmdm.launcher.pro.service.CheckForegroundAppAccessibilityService;
import com.hmdm.launcher.pro.service.CheckForegroundApplicationService;
import com.hmdm.launcher.service.StatusControlService;
import com.hmdm.launcher.util.RemoteLogger;
import com.hmdm.launcher.util.Utils;

import org.eclipse.paho.android.service.MqttAndroidClient;

import java.net.URL;

import static android.content.Context.MODE_PRIVATE;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(Const.LOG_TAG, "Got the BOOT_RECEIVER broadcast");

/*        SettingsHelper settingsHelper = SettingsHelper.getInstance(context);
        if (settingsHelper != null) {
            ServerConfig config = settingsHelper.getConfig();
            if (config != null) {
                try {
                    if (config.getRunDefaultLauncher() == null || !config.getRunDefaultLauncher() ||
                            context.getPackageName().equals(Utils.getDefaultLauncher(context))) {
                        Log.i(Const.LOG_TAG, "Headwind MDM doesn't need to force running at boot");
                        return;
                    }
                } catch (Exception e) {
                    Log.i(Const.LOG_TAG, "Unexpected error in BootReceiver");
                    e.printStackTrace();
                    return;
                }
            }
        }*/

        try {
            if (context.getPackageName().equals(Utils.getDefaultLauncher(context))) {
                Log.i(Const.LOG_TAG, "Headwind MDM is set as default launcher and doesn't need to force running at boot");
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Start Push service
        String pushOptions = null;
        int keepaliveTime = Const.DEFAULT_PUSH_ALARM_KEEPALIVE_TIME_SEC;
        SettingsHelper settingsHelper = SettingsHelper.getInstance(context);
        if (settingsHelper != null && settingsHelper.getConfig() != null) {
            pushOptions = settingsHelper.getConfig().getPushOptions();
            Integer newKeepaliveTime = settingsHelper.getConfig().getKeepaliveTime();
            if (newKeepaliveTime != null && newKeepaliveTime >= 30) {
                keepaliveTime = newKeepaliveTime;
            }
        }
        if (BuildConfig.MQTT_SERVICE_FOREGROUND && BuildConfig.ENABLE_PUSH && pushOptions != null &&
                (pushOptions.equals(ServerConfig.PUSH_OPTIONS_MQTT_WORKER)
                || pushOptions.equals(ServerConfig.PUSH_OPTIONS_MQTT_ALARM))) {
            try {
                URL url = new URL(settingsHelper.getBaseUrl());
                // Broadcast receivers are not allowed to bind to services
                // Therefore we start a service, and it binds to itself using
                // PushNotificationMqttWrapper.getInstance().connect()
                Intent serviceStartIntent = new Intent();
                serviceStartIntent.setClassName(context, MqttAndroidClient.SERVICE_NAME);
                serviceStartIntent.putExtra(MqttAndroidClient.EXTRA_START_AT_BOOT, true);
                serviceStartIntent.putExtra(MqttAndroidClient.EXTRA_DOMAIN, url.getHost());
                serviceStartIntent.putExtra(MqttAndroidClient.EXTRA_KEEPALIVE_TIME, keepaliveTime);
                serviceStartIntent.putExtra(MqttAndroidClient.EXTRA_PUSH_OPTIONS, pushOptions);
                serviceStartIntent.putExtra(MqttAndroidClient.EXTRA_DEVICE_ID, settingsHelper.getDeviceId());
                Object service = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        context.startForegroundService(serviceStartIntent) :
                        context.startService(serviceStartIntent);
                Log.i(Const.LOG_TAG, "Starting Push service from BootReceiver");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Start required services here instead of MainActivity (because it's not running)
        SharedPreferences preferences = context.getApplicationContext().getSharedPreferences(Const.PREFERENCES, MODE_PRIVATE);
        // Foreground apps checks are not available in a free version: services are the stubs
        if (preferences.getInt(Const.PREFERENCES_USAGE_STATISTICS, Const.PREFERENCES_OFF) == Const.PREFERENCES_ON) {
            context.startService(new Intent(context, CheckForegroundApplicationService.class));
        }
        if (preferences.getInt(Const.PREFERENCES_ACCESSIBILITY_SERVICE, Const.PREFERENCES_OFF) == Const.PREFERENCES_ON) {
            context.startService(new Intent(context, CheckForegroundAppAccessibilityService.class));
        }
        context.startService(new Intent(context, StatusControlService.class));

        // Send pending logs to server
        RemoteLogger.sendLogsToServer(context);
    }
}
