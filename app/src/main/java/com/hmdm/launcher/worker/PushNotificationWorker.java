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

package com.hmdm.launcher.worker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.hmdm.launcher.BuildConfig;
import com.hmdm.launcher.Const;
import com.hmdm.launcher.helper.ConfigUpdater;
import com.hmdm.launcher.helper.CryptoHelper;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.json.PushMessage;
import com.hmdm.launcher.json.PushResponse;
import com.hmdm.launcher.json.ServerConfig;
import com.hmdm.launcher.server.ServerService;
import com.hmdm.launcher.server.ServerServiceKeeper;
import com.hmdm.launcher.util.PushNotificationMqttWrapper;
import com.hmdm.launcher.util.RemoteLogger;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import retrofit2.Response;

public class PushNotificationWorker extends Worker {

    // Minimal interval is 15 minutes as per docs
    public static final int FIRE_PERIOD_MINS = 15;

    // Interval to update configuration to avoid losing device due to push failure
    public static final long CONFIG_UPDATE_INTERVAL = 3600000l;

    private static final String WORK_TAG_PERIODIC = "com.hmdm.launcher.WORK_TAG_PUSH_PERIODIC";

    public static void schedule(Context context) {
        RemoteLogger.log(context, Const.LOG_DEBUG, "Push notifications enqueued: " + FIRE_PERIOD_MINS + " mins");
        PeriodicWorkRequest queryRequest =
                new PeriodicWorkRequest.Builder(PushNotificationWorker.class, FIRE_PERIOD_MINS, TimeUnit.MINUTES)
                        .addTag(Const.WORK_TAG_COMMON)
                        .build();
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniquePeriodicWork(WORK_TAG_PERIODIC, ExistingPeriodicWorkPolicy.REPLACE, queryRequest);
    }

    private Context context;
    private SettingsHelper settingsHelper;

    public PushNotificationWorker(
            @NonNull final Context context,
            @NonNull WorkerParameters params) {
        super(context, params);
        this.context = context;
        settingsHelper = SettingsHelper.getInstance(context);
    }

    @Override
    // This is running in a background thread by WorkManager
    public Result doWork() {
        if (settingsHelper == null || settingsHelper.getConfig() == null) {
            return Result.failure();
        }

        String pushOptions = settingsHelper.getConfig().getPushOptions();

        if (pushOptions.equals(ServerConfig.PUSH_OPTIONS_MQTT_WORKER) ||
                pushOptions.equals(ServerConfig.PUSH_OPTIONS_MQTT_ALARM)) {
            // Note: MQTT client is automatically reconnected if connection is broken during launcher running,
            // and re-initializing it may cause looped errors
            // In particular, MQTT client is reconnected after turning Wi-Fi off and back on.
            // Re-connection of MQTT client at Headwind MDM startup is implemented in MainActivity
            // So by now, just request configuration update some times per day to avoid "device lost" issues
            return doMqttWork();
        } else {
            // PUSH_OPTIONS_POLLING by default
            //return doPollingWork();
            // Long polling is done in a related service
            // This is just a reserve task to prevent devices from being lost just in case
            return doLongPollingWork();
        }
    }

    // Query server for incoming messages each 15 minutes
    private Result doPollingWork() {
        ServerService serverService = ServerServiceKeeper.getServerServiceInstance(context);
        ServerService secondaryServerService = ServerServiceKeeper.getSecondaryServerServiceInstance(context);
        Response<PushResponse> response = null;

        // Calculate request signature
        String encodedDeviceId = settingsHelper.getDeviceId();
        try {
            encodedDeviceId = URLEncoder.encode(encodedDeviceId, "utf8");
        } catch (UnsupportedEncodingException e) {
        }
        String path = settingsHelper.getServerProject() + "/rest/notifications/device/" + encodedDeviceId;
        String signature = null;
        try {
            signature = CryptoHelper.getSHA1String(BuildConfig.REQUEST_SIGNATURE + path);
        } catch (Exception e) {
        }

        RemoteLogger.log(context, Const.LOG_DEBUG, "Querying push notifications");
        try {
            response = serverService.
                    queryPushNotifications(settingsHelper.getServerProject(), settingsHelper.getDeviceId(), signature).execute();
        } catch (Exception e) {
            RemoteLogger.log(context, Const.LOG_WARN, "Failed to query push notifications: " + e.getMessage());
            e.printStackTrace();
        }

        try {
            if (response == null) {
                response = secondaryServerService.
                        queryPushNotifications(settingsHelper.getServerProject(), settingsHelper.getDeviceId(), signature).execute();
            }

            if ( response.isSuccessful() ) {
                if ( Const.STATUS_OK.equals( response.body().getStatus() ) && response.body().getData() != null ) {
                    Map<String, PushMessage> filteredMessages = new HashMap<String, PushMessage>();
                    for (PushMessage message : response.body().getData()) {
                        // Filter out multiple configuration update requests
                        if (!message.getMessageType().equals(PushMessage.TYPE_CONFIG_UPDATED) ||
                                !filteredMessages.containsKey(PushMessage.TYPE_CONFIG_UPDATED)) {
                            filteredMessages.put(message.getMessageType(), message);
                        }
                    }
                    for (Map.Entry<String, PushMessage> entry : filteredMessages.entrySet()) {
                        PushNotificationProcessor.process(entry.getValue(), context);
                    }
                    return Result.success();
                } else {
                    return Result.failure();
                }
            }
        } catch ( Exception e ) {
            e.printStackTrace();
        }

        return Result.failure();
    }

    // Periodic configuration update requests
    private Result doLongPollingWork() {
        return forceConfigUpdateWork();
    }

    // Periodic configuration update requests
    private Result doMqttWork() {

        if (PushNotificationMqttWrapper.getInstance().checkPingDeath(context)) {
            RemoteLogger.log(context, Const.LOG_INFO, "MQTT ping death detected, reconnecting!");
            mqttReconnect();
        }

        return forceConfigUpdateWork();
    }

    private Result forceConfigUpdateWork() {
        long lastConfigUpdateTimestamp = settingsHelper.getConfigUpdateTimestamp();
        long now = System.currentTimeMillis();
        if (lastConfigUpdateTimestamp == 0) {
            settingsHelper.setConfigUpdateTimestamp(now);
            return Result.success();
        }
        if (lastConfigUpdateTimestamp + CONFIG_UPDATE_INTERVAL > now) {
            return Result.success();
        }
        RemoteLogger.log(context, Const.LOG_DEBUG, "Forcing configuration update");
        settingsHelper.setConfigUpdateTimestamp(now);
        ConfigUpdater.forceConfigUpdate(context);
        return Result.success();
    }

    // We assume we're running in the background!
    // https://stackoverflow.com/questions/57552955/is-possible-backgroundworker-dowork-in-main-thread
    private void mqttReconnect() {
        int keepaliveTime = Const.DEFAULT_PUSH_ALARM_KEEPALIVE_TIME_SEC;
        String pushOptions = settingsHelper.getConfig().getPushOptions();
        Integer newKeepaliveTime = settingsHelper.getConfig().getKeepaliveTime();
        if (newKeepaliveTime != null && newKeepaliveTime >= 30) {
            keepaliveTime = newKeepaliveTime;
        }
        try {
            PushNotificationMqttWrapper.getInstance().disconnect(context);
            Thread.sleep(5000);
            URL url = new URL(settingsHelper.getBaseUrl());
            PushNotificationMqttWrapper.getInstance().connect(context, url.getHost(), BuildConfig.MQTT_PORT,
                    pushOptions, keepaliveTime, settingsHelper.getDeviceId(), null, null);
        } catch (Exception e) {
            RemoteLogger.log(context, Const.LOG_DEBUG, "Reconnection failure: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
