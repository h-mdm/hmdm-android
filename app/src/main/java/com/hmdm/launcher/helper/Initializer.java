package com.hmdm.launcher.helper;

import static android.content.Context.MODE_PRIVATE;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.hmdm.launcher.BuildConfig;
import com.hmdm.launcher.Const;
import com.hmdm.launcher.json.Application;
import com.hmdm.launcher.json.DeviceInfo;
import com.hmdm.launcher.json.RemoteFile;
import com.hmdm.launcher.json.ServerConfig;
import com.hmdm.launcher.pro.ProUtils;
import com.hmdm.launcher.pro.service.CheckForegroundAppAccessibilityService;
import com.hmdm.launcher.pro.service.CheckForegroundApplicationService;
import com.hmdm.launcher.pro.worker.DetailedInfoWorker;
import com.hmdm.launcher.service.PushLongPollingService;
import com.hmdm.launcher.service.StatusControlService;
import com.hmdm.launcher.task.SendDeviceInfoTask;
import com.hmdm.launcher.util.ConnectionWaiter;
import com.hmdm.launcher.util.DeviceInfoProvider;
import com.hmdm.launcher.util.InstallUtils;
import com.hmdm.launcher.util.RemoteLogger;
import com.hmdm.launcher.util.Utils;
import com.hmdm.launcher.worker.PushNotificationWorker;
import com.hmdm.launcher.worker.ScheduledAppUpdateWorker;
import com.hmdm.launcher.worker.SendDeviceInfoWorker;

import org.eclipse.paho.android.service.MqttAndroidClient;

import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Shared initialization code which should run either by MainActivity (in foreground mode)
// or by InitialSetupActivity (in background mode)
public class Initializer {
    private static ExecutorService executor = Executors.newSingleThreadExecutor();
    private static Handler uiHandler = new Handler(Looper.getMainLooper());

    public static void init(Context context, Runnable completion) {
        // Background work
        executor.execute(() -> {
            // Crashlytics is not included in the open-source version
            ProUtils.initCrashlytics(context);

            if (BuildConfig.TRUST_ANY_CERTIFICATE) {
                InstallUtils.initUnsafeTrustManager();
            }

            Utils.lockSafeBoot(context);
            Utils.initPasswordReset(context);

            RemoteLogger.log(context, Const.LOG_INFO, "MDM Launcher " + BuildConfig.VERSION_NAME + "-" + Utils.getLauncherVariant() + " started");

            InstallUtils.clearTempFiles(context);

            // Install the certificates (repeat the action from InitialSetupActivity because
            // the customer may wish to install new certificates without re-enrolling the device
            CertInstaller.installCertificatesFromAssets(context);

            ConnectionWaiter.waitForConnect(context, () -> {
                DetailedInfoWorker.schedule(context);
                if (BuildConfig.ENABLE_PUSH) {
                    PushNotificationWorker.schedule(context);
                }
                ScheduledAppUpdateWorker.schedule(context);

                // Run completion in the UI thread
                uiHandler.post(completion);
            });
        });
    }

    public static void startServicesAndLoadConfig(Context context) {
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
        if (BuildConfig.MQTT_SERVICE_FOREGROUND && BuildConfig.ENABLE_PUSH && pushOptions != null) {
            if (pushOptions.equals(ServerConfig.PUSH_OPTIONS_MQTT_WORKER)
                    || pushOptions.equals(ServerConfig.PUSH_OPTIONS_MQTT_ALARM)) {
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
            } else if (pushOptions.equals(ServerConfig.PUSH_OPTIONS_POLLING)) {
                try {
                    Intent serviceStartIntent = new Intent(context, PushLongPollingService.class);
                    serviceStartIntent.putExtra(Const.EXTRA_ENABLED, true);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceStartIntent);
                    } else {
                        context.startService(serviceStartIntent);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        // Start required services here instead of MainActivity (because it's not running)
        // Notice: some devices do not allow starting background services from boot receiver
        // java.lang.IllegalStateException
        // Not allowed to start service Intent { cmp=com.hmdm.launcher/.service.StatusControlService }: app is in background
        // Let's just ignore these exceptions for now
        SharedPreferences preferences = context.getApplicationContext().getSharedPreferences(Const.PREFERENCES, MODE_PRIVATE);
        // Foreground apps checks are not available in a free version: services are the stubs
        if (preferences.getInt(Const.PREFERENCES_USAGE_STATISTICS, Const.PREFERENCES_OFF) == Const.PREFERENCES_ON) {
            try {
                context.startService(new Intent(context, CheckForegroundApplicationService.class));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (BuildConfig.USE_ACCESSIBILITY &&
            preferences.getInt(Const.PREFERENCES_ACCESSIBILITY_SERVICE, Const.PREFERENCES_OFF) == Const.PREFERENCES_ON) {
            try {
                context.startService(new Intent(context, CheckForegroundAppAccessibilityService.class));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        try {
            context.startService(new Intent(context, StatusControlService.class));
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Send pending logs to server
        RemoteLogger.sendLogsToServer(context);

        final ConfigUpdater.UINotifier uiNotifier = new ConfigUpdater.UINotifier() {
            @Override
            public void onConfigUpdateStart() {
            }

            @Override
            public void onConfigUpdateServerError(String errorText) {
            }

            @Override
            public void onConfigUpdateNetworkError(String errorText) {
            }

            @Override
            public void onConfigLoaded() {
            }

            @Override
            public void onPoliciesUpdated() {
            }

            @Override
            public void onFileDownloading(RemoteFile remoteFile) {
            }

            @Override
            public void onDownloadProgress(int progress, long total, long current) {
            }

            @Override
            public void onFileDownloadError(RemoteFile remoteFile) {
            }

            @Override
            public void onFileInstallError(RemoteFile remoteFile) {
            }

            @Override
            public void onAppUpdateStart() {
            }

            @Override
            public void onAppRemoving(Application application) {
            }

            @Override
            public void onAppDownloading(Application application) {
            }

            @Override
            public void onAppInstalling(Application application) {
            }

            @Override
            public void onAppDownloadError(Application application) {
            }

            @Override
            public void onAppInstallError(String packageName) {
            }

            @Override
            public void onAppInstallComplete(String packageName) {
            }

            @Override
            public void onConfigUpdateComplete() {
                // In background mode, we need to send the information to the server once update is complete
                SendDeviceInfoTask sendDeviceInfoTask = new SendDeviceInfoTask(context);
                DeviceInfo deviceInfo = DeviceInfoProvider.getDeviceInfo(context, true, true);
                sendDeviceInfoTask.execute(deviceInfo);
                SendDeviceInfoWorker.scheduleDeviceInfoSending(context);
            }

            @Override
            public void onAllAppInstallComplete() {
            }
        };
        ConfigUpdater.forceConfigUpdate(context, uiNotifier, false);
    }

    // Used by InitialSetupActivity
    public static void applyEarlyNonInteractivePolicies(Context context, ServerConfig config) {
        if (config.getSystemUpdateType() != null &&
                config.getSystemUpdateType() != ServerConfig.SYSTEM_UPDATE_DEFAULT &&
                Utils.isDeviceOwner(context)) {
            Utils.setSystemUpdatePolicy(context, config.getSystemUpdateType(), config.getSystemUpdateFrom(), config.getSystemUpdateTo());
        }

        if (config.getBluetooth() != null) {
            try {
                BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                if (bluetoothAdapter != null) {
                    boolean enabled = bluetoothAdapter.isEnabled();
                    if (config.getBluetooth() && !enabled) {
                        bluetoothAdapter.enable();
                    } else if (!config.getBluetooth() && enabled) {
                        bluetoothAdapter.disable();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (config.getTimeZone() != null) {
            Utils.setTimeZone(config.getTimeZone(), context);
        }

        if (config.getUsbStorage() != null) {
            Utils.lockUsbStorage(config.getUsbStorage(), context);
        }

        // Null value is processed here, it means unlock brightness
        Utils.setBrightnessPolicy(config.getAutoBrightness(), config.getBrightness(), context);
        Utils.setScreenTimeoutPolicy(config.getManageTimeout(), config.getTimeout(), context);

        if (config.getManageVolume() != null && config.getManageVolume() && config.getVolume() != null) {
            Utils.lockVolume(false, context);
            if (!Utils.setVolume(config.getVolume(), context)) {
                RemoteLogger.log(context, Const.LOG_WARN, "Failed to set the device volume");
            }
        }

        if (config.getLockVolume() != null) {
            Utils.lockVolume(config.getLockVolume(), context);
        }

        Utils.disableScreenshots(config.isDisableScreenshots(), context);
    }

}
