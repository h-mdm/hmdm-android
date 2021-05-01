package com.hmdm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Higher level Headwind MDM integration API incapsulating reconnection to the service and configuration update
 */
public class HeadwindMDM {

    public interface EventHandler {
        // This method is called when Headwind MDM is ready to answer
        // Get your app settings in this method using MDMService.Preferences.get()
        void onHeadwindMDMConnected();
        // This is just an informative method which doesn't need any actions
        // It should be followed by Connected() method shortly
        void onHeadwindMDMDisconnected();
        // Notification about the configuration change
        // Refresh your app settings in this method using MDMService.Preferences.get()
        void onHeadwindMDMConfigChanged();
    }

    private static HeadwindMDM instance;

    public static HeadwindMDM getInstance() {
        if (instance == null) {
            instance = new HeadwindMDM();
        }
        return instance;
    }

    private MDMService mdmService;
    private boolean mdmConnected = false;
    private boolean mustRun = false;

    private EventHandler eventHandler;
    private Context context;

    private String serverHost;
    private String secondaryServerHost;
    private String serverPath;
    private String deviceId;

    private BroadcastReceiver configUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Const.NOTIFICATION_CONFIG_UPDATED) && eventHandler != null) {
                eventHandler.onHeadwindMDMConfigChanged();
            }
        }
    };

    /**
     * Connect to Headwind MDM service
     * @param context
     * @param eventHandler
     * @return true if Headwind MDM exists, false otherwise
     */
    public boolean connect(final Context context, final EventHandler eventHandler) {
        mdmService = MDMService.getInstance();
        this.eventHandler = eventHandler;
        this.context = context;

        boolean wasConnected = mdmConnected;
        if (!mdmService.connect(context, resultHandler)) {
            mdmConnected = false;
            return false;
        }

        if (!wasConnected) {
            // We register the receiver only once
            // so connect() method can be called multiple times
            context.registerReceiver(configUpdateReceiver, new IntentFilter(Const.NOTIFICATION_CONFIG_UPDATED));
        }

        mustRun = true;
        return true;
    }

    public boolean isConnected() {
        return mdmConnected;
    }

    public void disconnect(Context context) {
        mustRun = false;
        mdmConnected = false;
        try {
            context.unregisterReceiver(configUpdateReceiver);
        } catch (Exception e) {
            // Ignore exception if receiver was not registered
        }
    }

    public String getServerHost() {
        try {
            URL url = new URL(serverHost);
            return url.getHost();
        } catch (MalformedURLException e) {
            return null;
        }
    }

    public String getSecondaryServerHost() {
        try {
            URL url = new URL(secondaryServerHost);
            return url.getHost();
        } catch (MalformedURLException e) {
            return null;
        }
    }

    public String getServerPath() {
        return serverPath;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getServerUrl() {
        if (serverPath != null && !serverPath.equals("")) {
            return serverHost + "/" + serverPath;
        } else {
            return serverHost;
        }
    }

    public String getSecondaryServerUrl() {
        if (!serverPath.equals("")) {
            return serverHost + "/" + serverPath;
        } else {
            return serverHost;
        }
    }

    /* Automatic reconnection mechanism */

    public MDMService.ResultHandler resultHandler = new MDMService.ResultHandler() {
        @Override
        public void onMDMConnected() {
            mdmConnected = true;

            Bundle data = null;
            try {
                data = mdmService.queryConfig();
                serverHost = data.getString(MDMService.KEY_SERVER_HOST);
                secondaryServerHost = data.getString(MDMService.KEY_SECONDARY_SERVER_HOST);
                serverPath = data.getString(MDMService.KEY_SERVER_PATH);
                deviceId = data.getString(MDMService.KEY_DEVICE_ID);
            } catch (MDMException e) {
            }

            if (eventHandler != null) {
                eventHandler.onHeadwindMDMConnected();
            }
        }

        @Override
        public void onMDMDisconnected() {
            // This may be raised when Headwind MDM launcher is updated or due to a launcher crash
            mdmConnected = false;
            if (mustRun) {
                if (eventHandler != null) {
                    eventHandler.onHeadwindMDMDisconnected();
                }
                new Handler().postDelayed(new MDMReconnectRunnable(), Const.HMDM_RECONNECT_DELAY_FIRST);
            }
        }
    };

    public class MDMReconnectRunnable implements Runnable {
        @Override
        public void run() {
            if (!mustRun) {
                return;
            }
            if (!mdmService.connect(context, resultHandler)) {
                // Retry in 1 minute
                new Handler().postDelayed(this, Const.HMDM_RECONNECT_DELAY_NEXT);
            }
        }
    }

}
