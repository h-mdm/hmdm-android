package com.hmdm.launcher.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.hmdm.launcher.BuildConfig;
import com.hmdm.launcher.Const;
import com.hmdm.launcher.R;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.json.PushMessage;
import com.hmdm.launcher.json.PushResponse;
import com.hmdm.launcher.pro.ProUtils;
import com.hmdm.launcher.server.ServerService;
import com.hmdm.launcher.server.ServerServiceKeeper;
import com.hmdm.launcher.util.RemoteLogger;
import com.hmdm.launcher.worker.PushNotificationProcessor;

import org.eclipse.paho.android.service.MqttService;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Response;

public class PushLongPollingService extends Service {

    private boolean enabled = true;
    private boolean threadActive = false;
    private Thread pollingThread;
    // If we get an exception, we have to delay, otherwise there would be a looping
    private final long DELAY_AFTER_EXCEPTION_MS = 60000;
    // Delay between polling requests to avoid looping if the server would respond instantly
    private final long DELAY_AFTER_REQUEST_MS = 5000;
    public static String CHANNEL_ID = MqttService.class.getName();
    // A flag preventing multiple notifications for the foreground service
    boolean started = false;
    // Notification ID for the foreground service
    private static final int NOTIFICATION_ID = 113;
    private ServerService serverService;
    private ServerService secondaryServerService;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive( Context context, Intent intent ) {
            if (intent != null && intent.getAction() != null &&
                    intent.getAction().equals(Const.ACTION_SERVICE_STOP)) {
                enabled = false;
                stopSelf();
            }
        }
    };

    @Override
    public void onDestroy() {
        LocalBroadcastManager.getInstance( this ).unregisterReceiver(receiver);
        Log.i(Const.LOG_TAG, "PushLongPollingService: service stopped");
        started = false;
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
        enabled = true;

        if (BuildConfig.MQTT_SERVICE_FOREGROUND && !started) {
            startAsForeground();
            started = true;
        }

        Log.i(Const.LOG_TAG, "PushLongPolling: service started. ");

        IntentFilter intentFilter = new IntentFilter(Const.ACTION_SERVICE_STOP);
        LocalBroadcastManager.getInstance( this ).registerReceiver( receiver, intentFilter );

        if (!threadActive) {
            pollingThread = new Thread(pollingRunnable);
            pollingThread.start();
        }

        return Service.START_STICKY;
    }

    private Runnable pollingRunnable = () -> {
        Context context = PushLongPollingService.this;
        SettingsHelper settingsHelper = SettingsHelper.getInstance(context);
        if (serverService == null) {
            serverService = ServerServiceKeeper.createServerService(settingsHelper.getBaseUrl(), Const.LONG_POLLING_READ_TIMEOUT);
        }
        if (secondaryServerService == null) {
            secondaryServerService = ServerServiceKeeper.createServerService(settingsHelper.getSecondaryBaseUrl(), Const.LONG_POLLING_READ_TIMEOUT);
        }

        threadActive = true;
        while (enabled) {
            Response<PushResponse> response = null;

            RemoteLogger.log(context, Const.LOG_VERBOSE, "Push long polling inquiry");
            try {
                // This is the long operation
                response = serverService.
                        queryPushLongPolling(settingsHelper.getServerProject(), settingsHelper.getDeviceId()).execute();
            } catch (Exception e) {
                RemoteLogger.log(context, Const.LOG_WARN, "Failed to query push notifications from "
                        + settingsHelper.getBaseUrl() + " : " + e.getMessage());
                e.printStackTrace();
            }

            try {
                if (response == null) {
                    response = secondaryServerService.
                            queryPushLongPolling(settingsHelper.getServerProject(), settingsHelper.getDeviceId()).execute();
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
                    }
                }
                // Avoid looping by adding some pause
                Thread.sleep(DELAY_AFTER_REQUEST_MS);

            } catch ( Exception e ) {
                RemoteLogger.log(context, Const.LOG_WARN, "Failed to query push notifications from "
                        + settingsHelper.getSecondaryBaseUrl() + " : " + e.getMessage());
                e.printStackTrace();
                try {
                    // On exception, we need to wait to avoid looping
                    Thread.sleep(DELAY_AFTER_EXCEPTION_MS);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
            }
        }
        threadActive = false;
    };


    @SuppressLint("WrongConstant")
    private void startAsForeground() {
        NotificationCompat.Builder builder;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Notification Channel", NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(channel);
            builder = new NotificationCompat.Builder(this, CHANNEL_ID);
        } else {
            builder = new NotificationCompat.Builder( this );
        }
        Notification notification = builder
                .setContentTitle(ProUtils.getAppName(this))
                .setTicker(ProUtils.getAppName(this))
                .setContentText(getString(R.string.mqtt_service_text))
                .setSmallIcon(R.drawable.ic_mqtt_service).build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

}
