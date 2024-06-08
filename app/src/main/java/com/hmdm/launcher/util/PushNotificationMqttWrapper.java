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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.hmdm.launcher.BuildConfig;
import com.hmdm.launcher.Const;
import com.hmdm.launcher.helper.CryptoHelper;
import com.hmdm.launcher.json.PushMessageJson;
import com.hmdm.launcher.json.ServerConfig;
import com.hmdm.launcher.worker.PushNotificationProcessor;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.android.service.MqttAndroidConnectOptions;
import org.eclipse.paho.android.service.PingDeathDetector;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONObject;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class PushNotificationMqttWrapper {
    private static PushNotificationMqttWrapper instance;

    private MqttAndroidClient client;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Handler connectHangupMonitorHandler = new Handler(Looper.getMainLooper());
    private BroadcastReceiver debugReceiver;
    private Context context;
    private boolean needProcessConnectExtended;

    private static final String WORKER_TAG_MQTT_RECONNECT = "com.hmdm.launcher.WORK_TAG_MQTT_RECONNECT";
    private static final int MQTT_RECONNECT_INTERVAL_SEC = 900;

    // If more than 20 connections per minute, we are stopping:
    // this is a sign that two devices with the same ID are registered
    private List<Long> connectionLoopProtectionArray = new LinkedList<>();
    private static final int CONNECTION_LOOP_PROTECTION_TIME_MS = 60000;
    private static final int CONNECTION_LOOP_CRITICAL_COUNT = 15;

    private PushNotificationMqttWrapper() {
    }

    public static PushNotificationMqttWrapper getInstance() {
        if (instance == null) {
            instance = new PushNotificationMqttWrapper();
        }
        return instance;
    }

    public void connect(final Context context, String host, int port, String pushType, int keepaliveTime,
                        final String deviceId, final Runnable onSuccess, final Runnable onFailure) {
        this.context = context;
        cancelReconnectionAfterFailure(context);
        if (client != null && client.isConnected()) {
            if (onSuccess != null) {
                handler.post(onSuccess);
            }
            return;
        }
        MqttAndroidConnectOptions connectOptions = new MqttAndroidConnectOptions();
        connectOptions.setAutomaticReconnect(true);
        connectOptions.setKeepAliveInterval(keepaliveTime);
        connectOptions.setCleanSession(false);
        if (pushType.equals(ServerConfig.PUSH_OPTIONS_MQTT_WORKER)) {
            connectOptions.setPingType(MqttAndroidConnectOptions.PING_WORKER);
            // For worker, keepalive time cannot be less than 15 minutes
            connectOptions.setKeepAliveInterval(Const.DEFAULT_PUSH_WORKER_KEEPALIVE_TIME_SEC);
        } else {
            connectOptions.setPingType(MqttAndroidConnectOptions.PING_ALARM);
            connectOptions.setKeepAliveInterval(keepaliveTime);
        }
        connectOptions.setUserName("hmdm");
        connectOptions.setPassword(CryptoHelper.getSHA1String("hmdm" + BuildConfig.REQUEST_SIGNATURE).toCharArray());
        String serverUri = "tcp://" + host + ":" + port;

        if (client != null) {
            // Here we go after reconnection.
            // Previous client is disconnected and in "Failure" state, but it listens for broadcasts
            // We need to clean it up before registering a new client
            client.unregisterResources();
        }

        client = new MqttAndroidClient(context, serverUri, deviceId);
        client.setTraceEnabled(true);
        client.setDefaultMessageListener(mqttMessageListener);
        setupDebugging(context);

        // We need to re-subscribe after reconnection. This is required because server may be not persistent
        // so after the server restart all subscription info is lost
        client.setCallback(new MqttCallbackExtended() {

            @Override
            public void connectionLost(Throwable cause) {
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
            }

            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                if (reconnect || needProcessConnectExtended) {
                    RemoteLogger.log(context, Const.LOG_VERBOSE, "Reconnect complete");
                    if (checkConnectionLoop()) {
                        subscribe(context, deviceId, null, null);
                    } else {
                        RemoteLogger.log(context, Const.LOG_ERROR, "Reconnection loop detected! You have multiple devices with ID=" + deviceId + "! MQTT service stopped.");
                        disconnect(context);
                    }
                }
            }
        });

        try {
            needProcessConnectExtended = false;

            // If connection hangs up, consider it as failure and continue the flow
            connectHangupMonitorHandler.postDelayed(() -> {
                RemoteLogger.log(context, Const.LOG_WARN, "MQTT connection timeout, disconnecting");
                try {
                    client.disconnect();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                scheduleReconnectionAfterFailure(context, host, port, pushType, keepaliveTime, deviceId);
                if (onFailure != null) {
                    handler.post(onFailure);
                }
            }, 30000);

            client.connect(connectOptions, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    // We believe that if connect is successful, subscribe() won't hang up
                    connectHangupMonitorHandler.removeCallbacksAndMessages(null);
                    subscribe(context, deviceId, onSuccess, onFailure);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable e) {
                    e.printStackTrace();
                    connectHangupMonitorHandler.removeCallbacksAndMessages(null);
                    RemoteLogger.log(context, Const.LOG_WARN, "MQTT connection failure");
                    scheduleReconnectionAfterFailure(context, host, port, pushType, keepaliveTime, deviceId);
                    // We fail here but Mqtt client tries to reconnect and we need to subscribe
                    // after connection succeeds. This is done in the extended callback client.
                    // The flag needProcessConnectExtended prevents duplicate subscribe after
                    // successful connection
                    needProcessConnectExtended = true;
                    if (onFailure != null) {
                        handler.post(onFailure);
                    }
                }
            });


        } catch (MqttException e) {
            e.printStackTrace();
            if (onFailure != null) {
                handler.post(onFailure);
            }
        }
    }

    private boolean checkConnectionLoop() {
        Long now = System.currentTimeMillis();
        // Remove old items
        for (int n = 0; n < connectionLoopProtectionArray.size(); n++) {
            if (connectionLoopProtectionArray.get(n) < now - CONNECTION_LOOP_PROTECTION_TIME_MS) {
                connectionLoopProtectionArray.remove(n);
                n--;
            }
        }
        connectionLoopProtectionArray.add(now);
        return connectionLoopProtectionArray.size() <= CONNECTION_LOOP_CRITICAL_COUNT;
    }

    private IMqttMessageListener mqttMessageListener = new IMqttMessageListener() {
        @Override
        public void messageArrived(String topic, final MqttMessage message) throws Exception {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        JSONObject obj = new JSONObject(new String(message.getPayload()));
                        String messageType = obj.getString("messageType");
                        PushMessageJson msg = new PushMessageJson(messageType, obj.optJSONObject("payload"));
                        PushNotificationProcessor.process(msg, context);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });

        }
    };

    private void subscribe(final Context context, final String deviceId, final Runnable onSuccess, final Runnable onFailure) {
        try {
            // Topic is deviceId
            client.subscribe(deviceId, 2, mqttMessageListener);
            if (onSuccess != null) {
                RemoteLogger.log(context, Const.LOG_DEBUG, "MQTT connection established");
                handler.post(onSuccess);
            }
        } catch (Exception e) {
            e.printStackTrace();
            RemoteLogger.log(context, Const.LOG_DEBUG, "Exception while subscribing: " + e.getMessage());
            if (onFailure != null) {
                handler.post(onFailure);
            }
        }
    }

    private void setupDebugging(Context context) {
        if (debugReceiver == null) {
            debugReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String errorMessage = intent.getStringExtra("MqttService.errorMessage");
                    if (errorMessage != null) {
                        Log.d(Const.LOG_TAG, intent.getStringExtra("MqttService.traceTag") + " " + errorMessage);
                    }
                }
            };
            LocalBroadcastManager.getInstance(context).registerReceiver(debugReceiver, new IntentFilter("MqttService.callbackToActivity.v0"));
        }
    }

    public void disconnect(Context context) {
        try {
            cancelReconnectionAfterFailure(context);
            RemoteLogger.log(context, Const.LOG_DEBUG, "MQTT client disconnected by user request");
            client.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
        client = null;
        LocalBroadcastManager.getInstance(context).unregisterReceiver(debugReceiver);
        debugReceiver = null;
    }

    private void cancelReconnectionAfterFailure(Context context) {
        WorkManager.getInstance(context.getApplicationContext()).cancelUniqueWork(WORKER_TAG_MQTT_RECONNECT);
    }

    private void scheduleReconnectionAfterFailure(Context context, String host, int port,
                                                  String pushType, int keepaliveTime, final String deviceId) {
        RemoteLogger.log(context, Const.LOG_INFO, "Scheduling MQTT reconnection in " + MQTT_RECONNECT_INTERVAL_SEC + " sec");
        Data data = new Data.Builder()
                .putString("host", host)
                .putInt("port", port)
                .putString("pushType", pushType)
                .putInt("keepalive", keepaliveTime)
                .putString("deviceId", deviceId)
                .build();
        OneTimeWorkRequest queryRequest =
                new OneTimeWorkRequest.Builder(PushNotificationMqttWrapper.ReconnectAfterFailureWorker.class)
                        .addTag(Const.WORK_TAG_COMMON)
                        .setInitialDelay(MQTT_RECONNECT_INTERVAL_SEC, TimeUnit.SECONDS)
                        .setInputData(data)
                        .build();
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(WORKER_TAG_MQTT_RECONNECT,
                ExistingWorkPolicy.REPLACE, queryRequest);
    }

    public static class ReconnectAfterFailureWorker extends Worker {

        private Context context;

        public ReconnectAfterFailureWorker(
                @NonNull final Context context,
                @NonNull WorkerParameters params) {
            super(context, params);
            this.context = context;
        }

        @NonNull
        @Override
        public Result doWork() {
            Data data = getInputData();
            PushNotificationMqttWrapper.getInstance().connect(context, data.getString("host"),
                    data.getInt("port", 0), data.getString("pushType"),
                    data.getInt("keepalive", Const.DEFAULT_PUSH_ALARM_KEEPALIVE_TIME_SEC),
                    data.getString("deviceId"), null, null);
            return Result.success();
        }
    }

    public boolean checkPingDeath(Context context) {
        // If not connected, ping is not working so we return false
        return client != null && client.isConnected() && PingDeathDetector.getInstance().detectPingDeath(context);
    }

}
