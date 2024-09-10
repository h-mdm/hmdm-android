package com.hmdm.launcher.helper;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.hmdm.launcher.BuildConfig;
import com.hmdm.launcher.Const;
import com.hmdm.launcher.db.DatabaseHelper;
import com.hmdm.launcher.db.RemoteFileTable;
import com.hmdm.launcher.json.Action;
import com.hmdm.launcher.json.Application;
import com.hmdm.launcher.json.DeviceInfo;
import com.hmdm.launcher.json.PushMessage;
import com.hmdm.launcher.json.RemoteFile;
import com.hmdm.launcher.json.ServerConfig;
import com.hmdm.launcher.pro.worker.DetailedInfoWorker;
import com.hmdm.launcher.server.ServerServiceKeeper;
import com.hmdm.launcher.service.PushLongPollingService;
import com.hmdm.launcher.task.ConfirmDeviceResetTask;
import com.hmdm.launcher.task.ConfirmPasswordResetTask;
import com.hmdm.launcher.task.ConfirmRebootTask;
import com.hmdm.launcher.task.GetRemoteLogConfigTask;
import com.hmdm.launcher.task.GetServerConfigTask;
import com.hmdm.launcher.util.DeviceInfoProvider;
import com.hmdm.launcher.util.InstallUtils;
import com.hmdm.launcher.util.PushNotificationMqttWrapper;
import com.hmdm.launcher.util.RemoteLogger;
import com.hmdm.launcher.util.SystemUtils;
import com.hmdm.launcher.util.Utils;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class ConfigUpdater {

    public static interface UINotifier {
        void onConfigUpdateStart();
        void onConfigUpdateServerError(String errorText);
        void onConfigUpdateNetworkError(String errorText);
        void onConfigLoaded();
        void onPoliciesUpdated();
        void onFileDownloading(final RemoteFile remoteFile);
        void onDownloadProgress(final int progress, final long total, final long current);
        void onFileDownloadError(final RemoteFile remoteFile);
        void onAppUpdateStart();
        void onAppRemoving(final Application application);
        void onAppDownloading(final Application application);
        void onAppInstalling(final Application application);
        void onAppDownloadError(final Application application);
        void onAppInstallError(final String packageName);
        void onAppInstallComplete(final String packageName);
        void onConfigUpdateComplete();
        void onAllAppInstallComplete();
    };

    private boolean configInitializing;
    private Context context;
    private UINotifier uiNotifier;
    private SettingsHelper settingsHelper;
    private Handler handler = new Handler(Looper.getMainLooper());
    private List<RemoteFile> filesForInstall = new LinkedList();
    private List< Application > applicationsForInstall = new LinkedList();
    private List< Application > applicationsForRun = new LinkedList();
    private Map<String, File> pendingInstallations = new HashMap<String,File>();
    private BroadcastReceiver appInstallReceiver;
    private boolean retry = true;
    private boolean loadOnly = false;
    private boolean userInteraction;

    public List<Application> getApplicationsForRun() {
        return applicationsForRun;
    }

    public static void notifyConfigUpdate(final Context context) {
        if (SettingsHelper.getInstance(context).isMainActivityRunning()) {
            Log.d(Const.LOG_TAG, "Main activity is running, using activity updater");
            LocalBroadcastManager.getInstance(context).
                    sendBroadcast(new Intent(Const.ACTION_UPDATE_CONFIGURATION));
        } else {
            Log.d(Const.LOG_TAG, "Main activity is not running, creating a new ConfigUpdater");
            new ConfigUpdater().updateConfig(context, null, false);
        }
    }

    public static void forceConfigUpdate(final Context context) {
        forceConfigUpdate(context, null, false);
    }

    public static void forceConfigUpdate(final Context context, final UINotifier notifier, final boolean userInteraction) {
        new ConfigUpdater().updateConfig(context, notifier, userInteraction);
    }

    public void setLoadOnly(boolean loadOnly) {
        this.loadOnly = loadOnly;
    }

    public void updateConfig(final Context context, final UINotifier uiNotifier, final boolean userInteraction) {
        if ( configInitializing ) {
            Log.i(Const.LOG_TAG, "updateConfig(): configInitializing=true, exiting");
            return;
        }

        Log.i(Const.LOG_TAG, "updateConfig(): set configInitializing=true");
        configInitializing = true;
        DetailedInfoWorker.requestConfigUpdate(context);
        this.context = context;
        this.uiNotifier = uiNotifier;
        this.userInteraction = userInteraction;

        // Work around a strange bug with stale SettingsHelper instance: re-read its value
        settingsHelper = SettingsHelper.getInstance(context.getApplicationContext());

        if (settingsHelper.getConfig() != null && settingsHelper.getConfig().getRestrictions() != null) {
            Utils.releaseUserRestrictions(context, settingsHelper.getConfig().getRestrictions());
            // Explicitly release restrictions of installing/uninstalling apps
            Utils.releaseUserRestrictions(context, "no_install_apps,no_uninstall_apps");
        }

        if (uiNotifier != null) {
            uiNotifier.onConfigUpdateStart();
        }
        new GetServerConfigTask( context ) {
            @Override
            protected void onPostExecute( Integer result ) {
                super.onPostExecute( result );
                configInitializing = false;
                Log.i(Const.LOG_TAG, "updateConfig(): set configInitializing=false after getting config");

                switch ( result ) {
                    case Const.TASK_SUCCESS:
                        RemoteLogger.log(context, Const.LOG_INFO, "Configuration updated");
                        updateRemoteLogConfig();
                        break;
                    case Const.TASK_ERROR:
                        RemoteLogger.log(context, Const.LOG_WARN, "Failed to update config: server error");
                        if (uiNotifier != null) {
                            uiNotifier.onConfigUpdateServerError(getErrorText());
                        }
                        break;
                    case Const.TASK_NETWORK_ERROR:
                        RemoteLogger.log(context, Const.LOG_WARN, "Failed to update config: network error");
                        if (retry) {
                            // Retry the request once because WiFi may not yet be initialized
                            retry = false;
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    updateConfig(context, uiNotifier, userInteraction);
                                }
                            }, 15000);
                        } else {
                            if (settingsHelper.getConfig() != null && !userInteraction) {
                                if (uiNotifier != null && settingsHelper.getConfig().isShowWifi()) {
                                    // Show network error dialog with Wi-Fi settings
                                    // if it is required by the web panel
                                    // so the user can set up WiFi even in kiosk mode
                                    uiNotifier.onConfigUpdateNetworkError(getErrorText());
                                } else {
                                    updateRemoteLogConfig();
                                }
                            } else {
                                if (uiNotifier != null) {
                                    uiNotifier.onConfigUpdateNetworkError(getErrorText());
                                }
                            }
                        }
                        break;
                }
            }
        }.execute();
    }

    public void skipConfigLoad() {
        updateRemoteLogConfig();
    }

    private void updateRemoteLogConfig() {
        Log.i(Const.LOG_TAG, "updateRemoteLogConfig(): get logging configuration");

        GetRemoteLogConfigTask task = new GetRemoteLogConfigTask(context) {
            @Override
            protected void onPostExecute( Integer result ) {
                super.onPostExecute( result );
                Log.i(Const.LOG_TAG, "updateRemoteLogConfig(): result=" + result);
                boolean deviceOwner = Utils.isDeviceOwner(context);
                RemoteLogger.log(context, Const.LOG_INFO, "Device owner: " + deviceOwner);
                if (deviceOwner) {
                    setSelfPermissions(settingsHelper.getConfig() != null ? settingsHelper.getConfig().getAppPermissions() : null);
                }
                try {
                    if (settingsHelper.getConfig() != null && uiNotifier != null) {
                        uiNotifier.onConfigLoaded();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (!loadOnly) {
                    checkServerMigration();
                } else {
                    Log.d(Const.LOG_TAG, "LoadOnly flag set, finishing the update flow");
                }
                // If loadOnly flag is set, we finish the flow here
            }
        };
        task.execute();
    }

    private void setSelfPermissions(String appPermissionStrategy) {
        Utils.autoGrantRequestedPermissions(context, context.getPackageName(),
                appPermissionStrategy, true);
    }

    private void checkServerMigration() {
        if (settingsHelper != null && settingsHelper.getConfig() != null && settingsHelper.getConfig().getNewServerUrl() != null &&
                !settingsHelper.getConfig().getNewServerUrl().trim().equals("")) {
            try {
                final MigrationHelper migrationHelper = new MigrationHelper(settingsHelper.getConfig().getNewServerUrl().trim());
                if (migrationHelper.needMigrating(context)) {
                    // Before migration, test that new URL is working well
                    migrationHelper.tryNewServer(context, new MigrationHelper.CompletionHandler() {
                        @Override
                        public void onSuccess() {
                            // Everything is OK, migrate!
                            RemoteLogger.log(context, Const.LOG_INFO, "Migrated to " + settingsHelper.getConfig().getNewServerUrl().trim());
                            settingsHelper.setBaseUrl(migrationHelper.getBaseUrl());
                            settingsHelper.setSecondaryBaseUrl(migrationHelper.getBaseUrl());
                            settingsHelper.setServerProject(migrationHelper.getServerProject());
                            ServerServiceKeeper.resetServices();
                            configInitializing = false;
                            updateConfig(context, uiNotifier, false);
                        }

                        @Override
                        public void onError(String cause) {
                            RemoteLogger.log(context, Const.LOG_WARN, "Failed to migrate to " + settingsHelper.getConfig().getNewServerUrl().trim() + ": " + cause);
                            setupPushService();
                        }
                    });
                    return;
                }
            } catch (Exception e) {
                // Malformed URL
                RemoteLogger.log(context, Const.LOG_WARN, "Failed to migrate to " + settingsHelper.getConfig().getNewServerUrl().trim() + ": malformed URL");
            }
        }
        setupPushService();
    }

    private void setupPushService() {
        Log.d(Const.LOG_TAG, "setupPushService() called");
        String pushOptions = null;
        int keepaliveTime = Const.DEFAULT_PUSH_ALARM_KEEPALIVE_TIME_SEC;
        if (settingsHelper != null && settingsHelper.getConfig() != null) {
            pushOptions = settingsHelper.getConfig().getPushOptions();
            Integer newKeepaliveTime = settingsHelper.getConfig().getKeepaliveTime();
            if (newKeepaliveTime != null && newKeepaliveTime >= 30) {
                keepaliveTime = newKeepaliveTime;
            }
        }
        if (BuildConfig.ENABLE_PUSH && pushOptions != null) {
            if (pushOptions.equals(ServerConfig.PUSH_OPTIONS_MQTT_WORKER)
                    || pushOptions.equals(ServerConfig.PUSH_OPTIONS_MQTT_ALARM)) {
                try {
                    URL url = new URL(settingsHelper.getBaseUrl());
                    Runnable nextRunnable = () -> {
                        checkFactoryReset();
                    };
                    PushNotificationMqttWrapper.getInstance().connect(context, url.getHost(), BuildConfig.MQTT_PORT,
                            pushOptions, keepaliveTime, settingsHelper.getDeviceId(), nextRunnable, nextRunnable);
                } catch (Exception e) {
                    e.printStackTrace();
                    checkFactoryReset();
                }
            } else {
                try {
                    Intent serviceStartIntent = new Intent(context, PushLongPollingService.class);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceStartIntent);
                    } else {
                        context.startService(serviceStartIntent);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                checkFactoryReset();
            }
        } else {
            checkFactoryReset();
        }
    }

    private void checkFactoryReset() {
        Log.d(Const.LOG_TAG, "checkFactoryReset() called");
        ServerConfig config = settingsHelper != null ? settingsHelper.getConfig() : null;
        if (config != null && config.getFactoryReset() != null && config.getFactoryReset()) {
            // We got a factory reset request, let's confirm and erase everything!
            RemoteLogger.log(context, Const.LOG_INFO, "Device reset by server request");
            ConfirmDeviceResetTask confirmTask = new ConfirmDeviceResetTask(context) {
                @Override
                protected void onPostExecute( Integer result ) {
                    // Do a factory reset if we can
                    if (result == null || result != Const.TASK_SUCCESS ) {
                        RemoteLogger.log(context, Const.LOG_WARN, "Failed to confirm device reset on server");
                    } else if (Utils.checkAdminMode(context)) {
                        // no_factory_reset restriction doesn't prevent against admin's reset action
                        // So we do not need to release this restriction prior to resetting the device
                        if (!Utils.factoryReset(context)) {
                            RemoteLogger.log(context, Const.LOG_WARN, "Device reset failed");
                        }
                    } else {
                        RemoteLogger.log(context, Const.LOG_WARN, "Device reset failed: no permissions");
                    }
                    // If we can't, proceed the initialization flow
                    checkRemoteReboot();
                }
            };

            DeviceInfo deviceInfo = DeviceInfoProvider.getDeviceInfo(context, true, true);
            deviceInfo.setFactoryReset(Utils.checkAdminMode(context));
            confirmTask.execute(deviceInfo);

        } else {
            checkRemoteReboot();
        }
    }

    private void checkRemoteReboot() {
        ServerConfig config = settingsHelper != null ? settingsHelper.getConfig() : null;
        if (config != null && config.getReboot() != null && config.getReboot()) {
            // Log and confirm reboot before rebooting
            RemoteLogger.log(context, Const.LOG_INFO, "Rebooting by server request");
            ConfirmRebootTask confirmTask = new ConfirmRebootTask(context) {
                @Override
                protected void onPostExecute( Integer result ) {
                    if (result == null || result != Const.TASK_SUCCESS ) {
                        RemoteLogger.log(context, Const.LOG_WARN, "Failed to confirm reboot on server");
                    } else if (Utils.checkAdminMode(context)) {
                        if (!Utils.reboot(context)) {
                            RemoteLogger.log(context, Const.LOG_WARN, "Reboot failed");
                        }
                    } else {
                        RemoteLogger.log(context, Const.LOG_WARN, "Reboot failed: no permissions");
                    }
                    checkPasswordReset();
                }
            };

            DeviceInfo deviceInfo = DeviceInfoProvider.getDeviceInfo(context, true, true);
            confirmTask.execute(deviceInfo);

        } else {
            checkPasswordReset();
        }

    }

    private void checkPasswordReset() {
        ServerConfig config = settingsHelper != null ? settingsHelper.getConfig() : null;
        if (config != null && config.getPasswordReset() != null) {
            if (Utils.passwordReset(context, config.getPasswordReset())) {
                RemoteLogger.log(context, Const.LOG_INFO, "Password successfully changed");
            } else {
                RemoteLogger.log(context, Const.LOG_WARN, "Failed to reset password");
            }

            ConfirmPasswordResetTask confirmTask = new ConfirmPasswordResetTask(context) {
                @Override
                protected void onPostExecute( Integer result ) {
                    setDefaultLauncher();
                }
            };

            DeviceInfo deviceInfo = DeviceInfoProvider.getDeviceInfo(context, true, true);
            confirmTask.execute(deviceInfo);

        } else {
            setDefaultLauncher();
        }
    }

    private void setDefaultLauncher() {
        ServerConfig config = settingsHelper != null ? settingsHelper.getConfig() : null;
        if (Utils.isDeviceOwner(context) && config != null) {
            // "Run default launcher" means we should not set Headwind MDM as a default launcher
            // and clear the setting if it has been already set
            boolean needSetLauncher = (config.getRunDefaultLauncher() == null || !config.getRunDefaultLauncher());
            String defaultLauncher = Utils.getDefaultLauncher(context);

            // As per the documentation, setting the default preferred activity should not be done on the main thread
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... voids) {
                    if (needSetLauncher && !context.getPackageName().equalsIgnoreCase(defaultLauncher)) {
                        Utils.setDefaultLauncher(context);
                    } else if (!needSetLauncher && context.getPackageName().equalsIgnoreCase(defaultLauncher)) {
                        Utils.clearDefaultLauncher(context);
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(Void v) {
                    updatePolicies();
                }
            }.execute();
            return;
        }
        updatePolicies();
    }

    private void updatePolicies() {
        // Update miscellaneous device policies here

        // Set up a proxy server
        SettingsHelper settingsHelper = SettingsHelper.getInstance(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && Utils.isDeviceOwner(context)) {
            String proxyUrl = settingsHelper.getAppPreference(context.getPackageName(), "proxy");
            if (proxyUrl != null) {
                proxyUrl = proxyUrl.trim();
                if (proxyUrl.equals("0")) {
                    // null stays for "no changes" (most users won't even know about an option to set up a proxy)
                    // "0" stays for "clear the proxy previously set up"
                    proxyUrl = null;
                }
                Utils.setProxy(context, proxyUrl);
            }
        }

        if (uiNotifier != null) {
            uiNotifier.onPoliciesUpdated();
        }
        Log.d(Const.LOG_TAG, "updatePolicies(): proceed to updating files");
        checkAndUpdateFiles();
    }

    private void checkAndUpdateFiles() {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                ServerConfig config = settingsHelper.getConfig();
                // This may be a long procedure due to checksum calculation so execute it in the background thread
                InstallUtils.generateFilesForInstallList(context, config.getFiles(), filesForInstall);
                return null;
            }

            @Override
            protected void onPostExecute(Void v) {
                loadAndInstallFiles();
            }
        }.execute();
    }

    public static class RemoteFileStatus {
        public RemoteFile remoteFile;
        public boolean installed;
    }

    private void loadAndInstallFiles() {
        if ( filesForInstall.size() > 0 ) {
            RemoteFile remoteFile = filesForInstall.remove(0);

            new AsyncTask<RemoteFile, Void, RemoteFileStatus>() {

                @Override
                protected RemoteFileStatus doInBackground(RemoteFile... remoteFiles) {
                    final RemoteFile remoteFile = remoteFiles[0];
                    RemoteFileStatus remoteFileStatus = null;

                    if (remoteFile.isRemove()) {
                        RemoteLogger.log(context, Const.LOG_DEBUG, "Removing file: " + remoteFile.getPath());
                        File file = new File(Environment.getExternalStorageDirectory(), remoteFile.getPath());
                        try {
                            file.delete();
                            RemoteFileTable.deleteByPath(DatabaseHelper.instance(context).getWritableDatabase(), remoteFile.getPath());
                        } catch (Exception e) {
                            RemoteLogger.log(context, Const.LOG_WARN, "Failed to remove file: " +
                                    remoteFile.getPath() + ": " + e.getMessage());
                            e.printStackTrace();
                        }

                    } else if (remoteFile.getUrl() != null) {
                        if (uiNotifier != null) {
                            uiNotifier.onFileDownloading(remoteFile);
                        }
                        // onFileDownloading() method contents
                        // updateMessageForFileDownloading(remoteFile.getPath());

                        File file = null;
                        try {
                            RemoteLogger.log(context, Const.LOG_DEBUG, "Downloading file: " + remoteFile.getPath());
                            file = InstallUtils.downloadFile(context, remoteFile.getUrl(),
                                    new InstallUtils.DownloadProgress() {
                                        @Override
                                        public void onDownloadProgress(final int progress, final long total, final long current) {
                                            if (uiNotifier != null) {
                                                uiNotifier.onDownloadProgress(progress, total, current);
                                            }
                                            // onDownloadProgress() method contents
                                            /*handler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    binding.progress.setMax(100);
                                                    binding.progress.setProgress(progress);

                                                    binding.setFileLength(total);
                                                    binding.setDownloadedLength(current);
                                                }
                                            });*/
                                        }
                                    });
                        } catch (Exception e) {
                            RemoteLogger.log(context, Const.LOG_WARN,
                                    "Failed to download file " + remoteFile.getPath() + ": " + e.getMessage());
                            e.printStackTrace();
                        }

                        remoteFileStatus = new RemoteFileStatus();
                        remoteFileStatus.remoteFile = remoteFile;
                        if (file != null) {
                            File finalFile = new File(Environment.getExternalStorageDirectory(), remoteFile.getPath());
                            try {
                                if (finalFile.exists()) {
                                    finalFile.delete();
                                }
                                if (!remoteFile.isVarContent()) {
                                    FileUtils.moveFile(file, finalFile);
                                } else {
                                    String imei = DeviceInfoProvider.getImei(context, 0);
                                    if (imei == null || imei.equals("")) {
                                        imei = settingsHelper.getConfig().getImei();
                                    }
                                    createFileFromTemplate(file, finalFile, settingsHelper.getDeviceId(), imei, settingsHelper.getConfig());
                                }
                                RemoteFileTable.insert(DatabaseHelper.instance(context).getWritableDatabase(), remoteFile);
                                remoteFileStatus.installed = true;
                            } catch (Exception e) {
                                RemoteLogger.log(context, Const.LOG_WARN,
                                        "Failed to create file " + remoteFile.getPath() + ": " + e.getMessage());
                                e.printStackTrace();
                                remoteFileStatus.installed = false;
                            }
                        } else {
                            remoteFileStatus.installed = false;
                        }
                    }

                    return remoteFileStatus;
                }

                @Override
                protected void onPostExecute(RemoteFileStatus fileStatus) {
                    if (fileStatus != null) {
                        if (!fileStatus.installed) {
                            filesForInstall.add( 0, fileStatus.remoteFile );
                            if (uiNotifier != null) {
                                uiNotifier.onFileDownloadError(fileStatus.remoteFile);
                            }
                            // onFileDownloadError() method contents
                            /*
                            if (!ProUtils.kioskModeRequired(context)) {
                                // Notify the error dialog that we're downloading a file, not an app
                                downloadingFile = true;
                                createAndShowFileNotDownloadedDialog(fileStatus.remoteFile.getUrl());
                                binding.setDownloading( false );
                            } else {
                                // Avoid user interaction in kiosk mode, just ignore download error and keep the old version
                                // Note: view is not used in this method so just pass null there
                                confirmDownloadFailureClicked(null);
                            }
                             */
                            return;
                        }
                    }
                    Log.i(Const.LOG_TAG, "loadAndInstallFiles(): proceed to next file");
                    loadAndInstallFiles();
                }

            }.execute(remoteFile);
        } else {
            Log.i(Const.LOG_TAG, "loadAndInstallFiles(): Proceed to certificate installation");
            installCertificates();
        }
    }

    private void installCertificates() {
        final String certPaths = settingsHelper.getAppPreference(context.getPackageName(), "certificates");
        if (certPaths != null) {
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... voids) {
                    CertInstaller.installCertificatesFromFiles(context, certPaths.trim());
                    return null;
                }

                @Override
                protected void onPostExecute(Void v) {
                    checkAndUpdateApplications();
                }
            }.execute();
        } else {
            checkAndUpdateApplications();
        }
    }

    private void checkAndUpdateApplications() {
        Log.i(Const.LOG_TAG, "checkAndUpdateApplications(): starting update applications");
        if (uiNotifier != null) {
            uiNotifier.onAppUpdateStart();
        }
        // onAppUpdateStart() method contents
        /*
        binding.setMessage( getString( R.string.main_activity_applications_update ) );
        configInitialized = true;
         */
        configInitializing = false;

        ServerConfig config = settingsHelper.getConfig();
        InstallUtils.generateApplicationsForInstallList(context, config.getApplications(), applicationsForInstall, pendingInstallations);

        Log.i(Const.LOG_TAG, "checkAndUpdateApplications(): list size=" + applicationsForInstall.size());

        registerAppInstallReceiver(config != null ? config.getAppPermissions() : null);
        loadAndInstallApplications();
    }

    private class ApplicationStatus {
        public Application application;
        public boolean installed;
    }

    // Here we avoid ConcurrentModificationException by executing all operations with applicationForInstall list in a main thread
    private void loadAndInstallApplications() {
        boolean isGoodTimeForAppUpdate = userInteraction || checkAppUpdateTimeRestriction(settingsHelper.getConfig());
        if (applicationsForInstall.size() > 0 && !isGoodTimeForAppUpdate) {
            RemoteLogger.log(context, Const.LOG_DEBUG, "Application update not enabled. Scheduled time: " + settingsHelper.getConfig().getAppUpdateFrom());
        }
        if (applicationsForInstall.size() > 0 && isGoodTimeForAppUpdate) {
            Application application = applicationsForInstall.remove(0);

            new AsyncTask<Application, Void, ApplicationStatus>() {

                @Override
                protected ApplicationStatus doInBackground(Application... applications) {
                    final Application application = applications[0];
                    ApplicationStatus applicationStatus = null;

                    if (application.isRemove()) {
                        // Remove the app
                        RemoteLogger.log(context, Const.LOG_DEBUG, "Removing app: " + application.getPkg());
                        if (uiNotifier != null) {
                            uiNotifier.onAppRemoving(application);
                        }
                        // onAppRemoving() method contents
                        //updateMessageForApplicationRemoving( application.getName() );
                        uninstallApplication(application.getPkg());

                    } else if (application.getUrl() == null) {
                        handler.post( new Runnable() {
                            @Override
                            public void run() {
                                Log.i(Const.LOG_TAG, "loadAndInstallApplications(): proceed to next app");
                                loadAndInstallApplications();
                            }
                        } );

                    } else if (application.getUrl().startsWith("market://details")) {
                        RemoteLogger.log(context, Const.LOG_INFO, "Installing app " + application.getPkg() + " from Google Play");
                        installApplicationFromPlayMarket(application.getUrl(), application.getPkg());
                        applicationStatus = new ApplicationStatus();
                        applicationStatus.application = application;
                        applicationStatus.installed = true;

                    } else if (application.getUrl().startsWith("file:///")) {
                        RemoteLogger.log(context, Const.LOG_INFO, "Installing app " + application.getPkg() + " from SD card");
                        applicationStatus = new ApplicationStatus();
                        applicationStatus.application = application;
                        File file = null;
                        try {
                            Log.d(Const.LOG_TAG, "URL: " + application.getUrl());
                            file = new File(new URL(application.getUrl()).toURI());
                            if (file != null) {
                                Log.d(Const.LOG_TAG, "Path: " + file.getAbsolutePath());
                                if (uiNotifier != null) {
                                    uiNotifier.onAppInstalling(application);
                                }
                                // onAppInstalling() method contents
                                //updateMessageForApplicationInstalling(application.getName());
                                installApplication(file, application.getPkg(), application.getVersion());
                                applicationStatus.installed = true;
                            } else {
                                applicationStatus.installed = false;
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            applicationStatus.installed = false;
                        }

                    } else {
                        if (uiNotifier != null) {
                            uiNotifier.onAppDownloading(application);
                        }
                        // onAppDownloading() method contents
                        //updateMessageForApplicationDownloading(application.getName());

                        File file = null;
                        try {
                            RemoteLogger.log(context, Const.LOG_DEBUG, "Downloading app: " + application.getPkg());
                            file = InstallUtils.downloadFile(context, application.getUrl(),
                                    new InstallUtils.DownloadProgress() {
                                        @Override
                                        public void onDownloadProgress(final int progress, final long total, final long current) {
                                            if (uiNotifier != null) {
                                                uiNotifier.onDownloadProgress(progress, total, current);
                                            }
                                            /*
                                            handler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    binding.progress.setMax(100);
                                                    binding.progress.setProgress(progress);

                                                    binding.setFileLength(total);
                                                    binding.setDownloadedLength(current);
                                                }
                                            });
                                             */
                                        }
                                    });
                        } catch (Exception e) {
                            RemoteLogger.log(context, Const.LOG_WARN, "Failed to download app " + application.getPkg() + ": " + e.getMessage());
                            e.printStackTrace();
                        }

                        applicationStatus = new ApplicationStatus();
                        applicationStatus.application = application;
                        if (file != null) {
                            if (uiNotifier != null) {
                                uiNotifier.onAppInstalling(application);
                            }
                            // onAppInstalling() method contents
                            //updateMessageForApplicationInstalling(application.getName());
                            installApplication(file, application.getPkg(), application.getVersion());
                            applicationStatus.installed = true;
                        } else {
                            applicationStatus.installed = false;
                        }
                    }

                    return applicationStatus;
                }

                @Override
                protected void onPostExecute(ApplicationStatus applicationStatus) {
                    if (applicationStatus != null) {
                        if (applicationStatus.installed) {
                            if (applicationStatus.application.isRunAfterInstall()) {
                                applicationsForRun.add(applicationStatus.application);
                            }
                        } else {
                            applicationsForInstall.add( 0, applicationStatus.application );
                            if (uiNotifier != null) {
                                uiNotifier.onAppDownloadError(applicationStatus.application);
                            }
                            // onAppDownloadError() method contents
                            /*
                            if (!ProUtils.kioskModeRequired(MainActivity.this)) {
                                // Notify the error dialog that we're downloading an app
                                downloadingFile = false;
                                createAndShowFileNotDownloadedDialog(applicationStatus.application.getName());
                                binding.setDownloading( false );
                            } else {
                                // Avoid user interaction in kiosk mode, just ignore download error and keep the old version
                                // Note: view is not used in this method so just pass null there
                                confirmDownloadFailureClicked(null);
                            }
                             */
                        }
                    }
                }

            }.execute(application);
        } else {
            // App install receiver is unregistered after all apps are installed or a timeout happens
            //unregisterAppInstallReceiver();
            lockRestrictions();
        }
    }

    private void lockRestrictions() {
        if (settingsHelper.getConfig() != null && settingsHelper.getConfig().getRestrictions() != null) {
            Utils.lockUserRestrictions(context, settingsHelper.getConfig().getRestrictions());
        }
        notifyThreads();
    }

    private void notifyThreads() {
        ServerConfig config = settingsHelper.getConfig();
        if (config != null) {
            Intent intent = new Intent(Const.ACTION_TOGGLE_PERMISSIVE);
            intent.putExtra(Const.EXTRA_ENABLED, config.isPermissive() || config.isKioskMode());
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
        }
        setActions();
    }

    private void setActions() {
        final ServerConfig config = settingsHelper.getConfig();
        // As per the documentation, setting the default preferred activity should not be done on the main thread
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                // If kiosk browser is installed, make it a default browser
                // This is a temporary solution! Perhaps user wants only to open specific hosts / schemes
                if (Utils.isDeviceOwner(context)) {
                    if (config.getActions() != null && config.getActions().size() > 0) {
                        for (Action action : config.getActions()) {
                            Utils.setAction(context, action);
                        }
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void v) {
                if (uiNotifier != null) {
                    uiNotifier.onConfigUpdateComplete();
                }

                // Send notification about the configuration update to all plugins
                Intent intent = new Intent(Const.INTENT_PUSH_NOTIFICATION_PREFIX + PushMessage.TYPE_CONFIG_UPDATED);
                context.sendBroadcast(intent);

                RemoteLogger.log(context, Const.LOG_VERBOSE, "Update flow completed");
                if (pendingInstallations.size() > 0) {
                    // Some apps are still pending installation
                    // Let's wait until they're all installed
                    // Then notify UI about that so it could refresh the screen
                    waitForInstallComplete();
                } else {
                    unregisterAppInstallReceiver();
                }

                // onConfigUpdateComplete() method contents
                /*
                Log.i(Const.LOG_TAG, "Showing content from setActions()");
                showContent(settingsHelper.getConfig());
                 */
            }
        }.execute();
    }

    private void waitForInstallComplete() {
        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... voids) {
                for (int n = 0; n < 60; n++) {
                    if (pendingInstallations.size() == 0) {
                        break;
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                unregisterAppInstallReceiver();
                if (uiNotifier != null) {
                    uiNotifier.onAllAppInstallComplete();
                }
                return null;
            }
        }.execute();
    }


    @SuppressLint("WrongConstant,UnspecifiedRegisterReceiverFlag")
    private void registerAppInstallReceiver(final String appPermissionStrategy) {
        // Here we handle the completion of the silent app installation in the device owner mode
        // These intents are not delivered to LocalBroadcastManager
        if (appInstallReceiver == null) {
            Log.d(Const.LOG_TAG, "Install completion receiver prepared");
            appInstallReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent.getAction().equals(Const.ACTION_INSTALL_COMPLETE)) {
                        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, 0);
                        switch (status) {
                            case PackageInstaller.STATUS_PENDING_USER_ACTION:
                                RemoteLogger.log(context, Const.LOG_INFO, "Request user confirmation to install");
                                Intent confirmationIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);

                                // Fix the Intent Redirection vulnerability
                                // https://support.google.com/faqs/answer/9267555
                                ComponentName name = confirmationIntent.resolveActivity(context.getPackageManager());
                                int flags = confirmationIntent.getFlags();
                                if (name != null && !name.getPackageName().equals(context.getPackageName()) &&
                                        (flags & Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0 &&
                                        (flags & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == 0) {
                                    confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    try {
                                        context.startActivity(confirmationIntent);
                                    } catch (Exception e) {
                                    }
                                } else {
                                    Log.e(Const.LOG_TAG, "Intent redirection detected, ignoring the fault intent!");
                                }
                                break;
                            case PackageInstaller.STATUS_SUCCESS:
                                RemoteLogger.log(context, Const.LOG_DEBUG, "App installed successfully");
                                String packageName = intent.getStringExtra(Const.PACKAGE_NAME);
                                if (packageName != null) {
                                    Log.i(Const.LOG_TAG, "Install complete: " + packageName);
                                    File file = pendingInstallations.get(packageName);
                                    if (file != null) {
                                        pendingInstallations.remove(packageName);
                                        InstallUtils.deleteTempApk(file);
                                    }
                                    if (BuildConfig.SYSTEM_PRIVILEGES || Utils.isDeviceOwner(context)) {
                                        // Always grant all dangerous rights to the app
                                        Utils.autoGrantRequestedPermissions(context, packageName,
                                                appPermissionStrategy, false);
                                        if (packageName.equals(Const.APUPPET_PACKAGE_NAME)) {
                                            // Automatically grant required permissions to aPuppet if we can
                                            SystemUtils.autoSetAccessibilityPermission(context,
                                                    Const.APUPPET_PACKAGE_NAME, Const.APUPPET_SERVICE_CLASS_NAME);
                                            SystemUtils.autoSetOverlayPermission(context,
                                                    Const.APUPPET_PACKAGE_NAME);
                                        }
                                    }
                                    if (uiNotifier != null) {
                                        uiNotifier.onAppInstallComplete(packageName);
                                    }
                                }
                                break;
                            default:
                                // Installation failure
                                String extraMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                                String statusMessage = InstallUtils.getPackageInstallerStatusMessage(status);
                                String logRecord = "Install failed: " + statusMessage;
                                if (extraMessage != null && extraMessage.length() > 0) {
                                    logRecord += ", extra: " + extraMessage;
                                }
                                RemoteLogger.log(context, Const.LOG_ERROR, logRecord);
                                packageName = intent.getStringExtra(Const.PACKAGE_NAME);
                                if (packageName != null) {
                                    File file = pendingInstallations.get(packageName);
                                    if (file != null) {
                                        pendingInstallations.remove(packageName);
                                        InstallUtils.deleteTempApk(file);
                                    }
                                }

                                break;
                        }
                        loadAndInstallApplications();
                    }
                }
            };
        } else {
            // Renewed the configuration multiple times?
            unregisterAppInstallReceiver();
        }

        try {
            Log.d(Const.LOG_TAG, "Install completion receiver registered");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.registerReceiver(appInstallReceiver, new IntentFilter(Const.ACTION_INSTALL_COMPLETE), Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(appInstallReceiver, new IntentFilter(Const.ACTION_INSTALL_COMPLETE));
            }
        } catch (Exception e) {
            // On earlier Android versions (4, 5):
            // Fatal Exception: android.content.ReceiverCallNotAllowedException
            // BroadcastReceiver components are not allowed to register to receive intents
            e.printStackTrace();
        }
    }

    private void unregisterAppInstallReceiver() {
        if (appInstallReceiver != null) {
            try {
                Log.d(Const.LOG_TAG, "Install completion receiver unregistered");
                context.unregisterReceiver(appInstallReceiver);
            } catch (Exception e) {
                // Receiver not registered
                e.printStackTrace();
            }
            appInstallReceiver = null;
        }
    }

    private void installApplicationFromPlayMarket(final String uri, final String packageName) {
        RemoteLogger.log(context, Const.LOG_DEBUG, "Asking user to install app " + packageName);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(uri));
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            RemoteLogger.log(context, Const.LOG_DEBUG, "Failed to run app install activity for " + packageName);
        }
    }

    // This function is called from a background thread
    private void installApplication( File file, final String packageName, final String version ) {
        if (packageName.equals(context.getPackageName()) &&
                context.getPackageManager().getLaunchIntentForPackage(Const.LAUNCHER_RESTARTER_PACKAGE_ID) != null) {
            // Restart self in EMUI: there's no auto restart after update in EMUI, we must use a helper app
            startLauncherRestarter();
        }
        String versionData = version == null || version.equals("0") ? "" : " " + version;
        if (Utils.isDeviceOwner(context) || BuildConfig.SYSTEM_PRIVILEGES) {
            pendingInstallations.put(packageName, file);
            RemoteLogger.log(context, Const.LOG_INFO, "Silently installing app " + packageName + versionData);
            InstallUtils.silentInstallApplication(context, file, packageName, new InstallUtils.InstallErrorHandler() {
                @Override
                public void onInstallError() {
                    Log.i(Const.LOG_TAG, "installApplication(): error installing app " + packageName);
                    pendingInstallations.remove(packageName);
                    if (file.exists()) {
                        file.delete();
                    }
                    if (uiNotifier != null) {
                        uiNotifier.onAppInstallError(packageName);
                    }
                    /*
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            new AlertDialog.Builder(MainActivity.this)
                                    .setMessage(getString(R.string.install_error) + " " + packageName)
                                    .setPositiveButton(R.string.dialog_administrator_mode_continue, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            checkAndStartLauncher();
                                        }
                                    })
                                    .create()
                                    .show();
                        }
                    });
                     */
                }
            });
        } else {
            RemoteLogger.log(context, Const.LOG_INFO, "Asking user to install app " + packageName + versionData);
            InstallUtils.requestInstallApplication(context, file, new InstallUtils.InstallErrorHandler() {
                @Override
                public void onInstallError() {
                    pendingInstallations.remove(packageName);
                    if (file.exists()) {
                        file.delete();
                    }
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            loadAndInstallApplications();
                        }
                    });
                }
            });
        }
    }

    private void uninstallApplication(final String packageName) {
        if (Utils.isDeviceOwner(context) || BuildConfig.SYSTEM_PRIVILEGES) {
            RemoteLogger.log(context, Const.LOG_INFO, "Silently uninstall app " + packageName);
            InstallUtils.silentUninstallApplication(context, packageName);
        } else {
            RemoteLogger.log(context, Const.LOG_INFO, "Asking user to uninstall app " + packageName);
            InstallUtils.requestUninstallApplication(context, packageName);
        }
    }

    // The following algorithm of launcher restart works in EMUI:
    // Run EMUI_LAUNCHER_RESTARTER activity once and send the old version number to it.
    // The restarter application will check the launcher version each second, and restart it
    // when it is changed.
    private void startLauncherRestarter() {
        // Sending an intent before updating, otherwise the launcher may be terminated at any time
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(Const.LAUNCHER_RESTARTER_PACKAGE_ID);
        if (intent == null) {
            Log.i("LauncherRestarter", "No restarter app, please add it in the config!");
            return;
        }
        intent.putExtra(Const.LAUNCHER_RESTARTER_OLD_VERSION, BuildConfig.VERSION_NAME);
        context.startActivity(intent);
        Log.i("LauncherRestarter", "Calling launcher restarter from the launcher");
    }

    // Create a new file from the template file
    // (replace DEVICE_NUMBER, IMEI, CUSTOM* by their values)
    private void createFileFromTemplate(File srcFile, File dstFile, String deviceId, String imei, ServerConfig config) throws IOException {
        // We are supposed to process only small text files
        // So here we are reading the whole file, replacing variables, and save the content
        // It is not optimal for large files - it would be better to replace in a stream (how?)
        String content = FileUtils.readFileToString(srcFile);
        content = content.replace("DEVICE_NUMBER", deviceId)
                .replace("IMEI", imei != null ? imei : "")
                .replace("CUSTOM1", config.getCustom1() != null ? config.getCustom1() : "")
                .replace("CUSTOM2", config.getCustom2() != null ? config.getCustom2() : "")
                .replace("CUSTOM3", config.getCustom3() != null ? config.getCustom3() : "");
        FileUtils.writeStringToFile(dstFile, content);
    }

    public boolean isPendingAppInstall() {
        return applicationsForInstall.size() > 0;
    }

    public void repeatDownloadFiles() {
        loadAndInstallFiles();
    }

    public void repeatDownloadApps() {
        loadAndInstallApplications();
    }

    public void skipDownloadFiles() {
        Log.d(Const.LOG_TAG, "File download skipped, continue updating files");
        if (filesForInstall.size() > 0) {
            RemoteFile remoteFile = filesForInstall.remove(0);
            settingsHelper.removeRemoteFile(remoteFile);
        }
        loadAndInstallFiles();
    }

    public void skipDownloadApps() {
        Log.d(Const.LOG_TAG, "App download skipped, continue updating applications");
        if (applicationsForInstall.size() > 0) {
            Application application = applicationsForInstall.remove(0);
            // Mark this app not to download any more until the config is refreshed
            // But we should not remove the app from a list because it may be
            // already installed!
            settingsHelper.removeApplicationUrl(application);
        }
        loadAndInstallApplications();
    }

    public static boolean checkAppUpdateTimeRestriction(ServerConfig config) {
        if (config.getAppUpdateFrom() == null || config.getAppUpdateTo() == null) {
            return true;
        }

        Date date = new Date();
        Calendar calendar = GregorianCalendar.getInstance();
        calendar.setTime(date);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);

        int appUpdateFromHour = 0;
        try {
            appUpdateFromHour = Integer.parseInt(config.getAppUpdateFrom().substring(0, 2));
        } catch (Exception e) {
            e.printStackTrace();
        }
        int appUpdateFromMinute = 0;
        try {
            appUpdateFromMinute = Integer.parseInt(config.getAppUpdateFrom().substring(3));
        } catch (Exception e) {
            e.printStackTrace();
        }

        int appUpdateToHour = 0;
        try {
            appUpdateToHour = Integer.parseInt(config.getAppUpdateTo().substring(0, 2));
        } catch (Exception e) {
            e.printStackTrace();
        }
        int appUpdateToMinute = 0;
        try {
            appUpdateToMinute = Integer.parseInt(config.getAppUpdateTo().substring(3));
        } catch (Exception e) {
            e.printStackTrace();
        }

        minute += 60 * hour;
        appUpdateFromMinute += 60 * appUpdateFromHour;
        appUpdateToMinute += 60 * appUpdateToHour;

        if (appUpdateFromMinute == appUpdateToMinute) {
            // This is incorrect. Perhaps the admin meant "24 hours" so return true
            return true;
        }

        if (appUpdateFromMinute < appUpdateToMinute) {
            // Midnight not included
            return appUpdateFromMinute <= minute && minute <= appUpdateToMinute;
        }

        // Midnight included
        return minute >= appUpdateFromMinute || minute <= appUpdateToMinute;
    }
}
