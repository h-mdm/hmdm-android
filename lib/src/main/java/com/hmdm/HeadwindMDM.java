package com.hmdm;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;

import java.net.MalformedURLException;
import java.net.URL;

import dalvik.system.PathClassLoader;

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
    private String custom1;
    private String custom2;
    private String custom3;
    private boolean isManaged;
    private boolean isKiosk;
    private String imei;
    private String serial;
    private int version;
    private String apiKey;

    private BroadcastReceiver configUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Const.NOTIFICATION_CONFIG_UPDATED) && eventHandler != null) {
                eventHandler.onHeadwindMDMConfigChanged();
            }
        }
    };

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * Connect to Headwind MDM service
     * @param context
     * @param eventHandler
     * @return true if Headwind MDM exists, false otherwise
     */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.registerReceiver(configUpdateReceiver, new IntentFilter(Const.NOTIFICATION_CONFIG_UPDATED), Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(configUpdateReceiver, new IntentFilter(Const.NOTIFICATION_CONFIG_UPDATED));
            }
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

    public String getCustom(int number) {
        switch (number) {
            case 1:
                return custom1;
            case 2:
                return custom2;
            case 3:
                return custom3;
            default:
                return null;
        }
    }

    public boolean isManaged() {
        return isManaged;
    }

    public boolean isKiosk() {
        return isKiosk;
    }

    public String getSerial() {
        return serial;
    }

    public String getImei() {
        return imei;
    }

    public int getVersion() {
        return version;
    }

    public void forceConfigUpdate() {
        if (mdmConnected) {
            try {
                mdmService.forceConfigUpdate();
            } catch (MDMException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean setCustom(int number, String value) {
        if (!mdmConnected) {
            return false;
        }
        try {
            mdmService.setCustom(number, value);
        } catch (MDMException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    // Get the admin component for applications using "com.hmdm" shared user ID
    // This component could be used to use DevicePolicyManager in those applications
    public ComponentName getAdminComponent(Context context) {
        // We must use the context of Device Owner
        // Since we're using the same shared user, it should be returned without any security issues
        Context deviceOwnerContext = null;
        try {
            deviceOwnerContext = context.createPackageContext(Const.PACKAGE, 0);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        // Android SDK is not supposed to load classes of another application
        // However since we're using the same shared user ID (so it's the same application underhood),
        // we can use a hack to get the required class
        String apkName = null;
        try {
            apkName = context.getPackageManager().getApplicationInfo(Const.PACKAGE, 0).sourceDir;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return null;
        }

        PathClassLoader pathClassLoader = new dalvik.system.PathClassLoader(
                apkName,
                ClassLoader.getSystemClassLoader());

        Class adminReceiverClass = null;
        try {
            adminReceiverClass = Class.forName(Const.ADMIN_RECEIVER_CLASS, true, pathClassLoader);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }

        return new ComponentName(deviceOwnerContext, adminReceiverClass);
    }

    /* Automatic reconnection mechanism */

    public MDMService.ResultHandler resultHandler = new MDMService.ResultHandler() {
        @Override
        public void onMDMConnected() {
            mdmConnected = true;

            Bundle data = null;
            try {
                version = mdmService.getVersion();
                if (version > MDMService.INITIAL_VERSION && apiKey != null) {
                    data = mdmService.queryConfig(apiKey);
                } else {
                    data = mdmService.queryConfig();
                }

                // NPE can be here! queryConfig() may return null if Headwind MDM
                // is not configured. Not sure how to handle this, though
                serverHost = data.getString(MDMService.KEY_SERVER_HOST);
                secondaryServerHost = data.getString(MDMService.KEY_SECONDARY_SERVER_HOST);
                serverPath = data.getString(MDMService.KEY_SERVER_PATH);
                deviceId = data.getString(MDMService.KEY_DEVICE_ID);
                custom1 = data.getString(MDMService.KEY_CUSTOM_1);
                custom2 = data.getString(MDMService.KEY_CUSTOM_2);
                custom3 = data.getString(MDMService.KEY_CUSTOM_3);
                // null / false values for older launcher API versions or wrong API key
                isManaged = data.getBoolean(MDMService.KEY_IS_MANAGED);
                isKiosk = data.getBoolean(MDMService.KEY_IS_KIOSK);
                imei = data.getString(MDMService.KEY_IMEI);
                serial = data.getString(MDMService.KEY_SERIAL);
            } catch (MDMException e) {
                e.printStackTrace();
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
