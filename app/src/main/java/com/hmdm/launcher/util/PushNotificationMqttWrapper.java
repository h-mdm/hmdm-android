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

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.hmdm.launcher.Const;
import com.hmdm.launcher.json.PushMessageJson;
import com.hmdm.launcher.json.ServerConfig;
import com.hmdm.launcher.worker.PushNotificationProcessor;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.android.service.MqttAndroidConnectOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONObject;

public class PushNotificationMqttWrapper {
    private static PushNotificationMqttWrapper instance;

    private MqttAndroidClient client;
    private Handler handler;
    private BroadcastReceiver debugReceiver;
    private Context context;
    private boolean needProcessConnectExtended;

    private PushNotificationMqttWrapper() {
    }

    public static PushNotificationMqttWrapper getInstance() {
        if (instance == null) {
            instance = new PushNotificationMqttWrapper();
        }
        return instance;
    }

    public void connect(final Context context, String host, int port, String pushType, final String deviceId, final Runnable onSuccess, final Runnable onFailure) {
        handler = new Handler(Looper.getMainLooper());
        if (client != null && client.isConnected()) {
            if (onSuccess != null) {
                handler.post(onSuccess);
            }
            return;
        }
        this.context = context;
        MqttAndroidConnectOptions connectOptions = new MqttAndroidConnectOptions();
        connectOptions.setAutomaticReconnect(true);
        connectOptions.setKeepAliveInterval(15 * 60);
        connectOptions.setCleanSession(false);
        connectOptions.setPingType(pushType.equals(ServerConfig.PUSH_OPTIONS_MQTT_WORKER) ?
                        MqttAndroidConnectOptions.PING_WORKER :
                        MqttAndroidConnectOptions.PING_ALARM);
        String serverUri = "tcp://" + host + ":" + port;
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
                    RemoteLogger.log(context, Const.LOG_DEBUG, "Reconnect complete");
                    subscribe(context, deviceId, null, null);
                }
            }
        });

        try {
            needProcessConnectExtended = false;
            client.connect(connectOptions, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    subscribe(context, deviceId, onSuccess, onFailure);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable e) {
                    e.printStackTrace();
                    RemoteLogger.log(context, Const.LOG_WARN, "MQTT connection failure");
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
        } catch (MqttException e) {
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
            RemoteLogger.log(context, Const.LOG_DEBUG, "MQTT client disconnected by user request");
            client.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
        client = null;
        LocalBroadcastManager.getInstance(context).unregisterReceiver(debugReceiver);
        debugReceiver = null;
    }
}
