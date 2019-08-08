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

package com.hmdm.launcher.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.databinding.DataBindingUtil;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.hmdm.launcher.BuildConfig;
import com.hmdm.launcher.Const;
import com.hmdm.launcher.R;
import com.hmdm.launcher.databinding.ActivityMainBinding;
import com.hmdm.launcher.databinding.DialogAccessibilityServiceBinding;
import com.hmdm.launcher.databinding.DialogAdministratorModeBinding;
import com.hmdm.launcher.databinding.DialogDeviceInfoBinding;
import com.hmdm.launcher.databinding.DialogEnterPasswordBinding;
import com.hmdm.launcher.databinding.DialogFileDownloadingFailedBinding;
import com.hmdm.launcher.databinding.DialogHistorySettingsBinding;
import com.hmdm.launcher.databinding.DialogOverlaySettingsBinding;
import com.hmdm.launcher.databinding.DialogSystemSettingsBinding;
import com.hmdm.launcher.databinding.DialogUnknownSourcesBinding;
import com.hmdm.launcher.helper.CryptoHelper;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.json.Application;
import com.hmdm.launcher.json.DeviceInfo;
import com.hmdm.launcher.json.ServerConfig;
import com.hmdm.launcher.pro.ProUtils;
import com.hmdm.launcher.pro.service.CheckForegroundAppAccessibilityService;
import com.hmdm.launcher.pro.service.CheckForegroundApplicationService;
import com.hmdm.launcher.server.ServerService;
import com.hmdm.launcher.server.ServerServiceKeeper;
import com.hmdm.launcher.service.PluginApiService;
import com.hmdm.launcher.service.PushNotificationPollingService;
import com.hmdm.launcher.task.GetRemoteLogConfigTask;
import com.hmdm.launcher.task.GetServerConfigTask;
import com.hmdm.launcher.util.AppInfo;
import com.hmdm.launcher.util.DeviceInfoProvider;
import com.hmdm.launcher.util.RemoteLogger;
import com.hmdm.launcher.util.Utils;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.ResponseBody;
import retrofit2.Response;

public class MainActivity
        extends BaseActivity
        implements View.OnLongClickListener, AppListAdapter.OnAppChooseListener, View.OnClickListener {

    private static final String LOG_TAG = "HeadwindMDM";
    private static final int PERMISSIONS_REQUEST = 1000;

    private ActivityMainBinding binding;
    private SettingsHelper settingsHelper;

    private Dialog fileNotDownloadedDialog;
    private DialogFileDownloadingFailedBinding dialogFileDownloadingFailedBinding;

    private Dialog enterPasswordDialog;
    private DialogEnterPasswordBinding dialogEnterPasswordBinding;

    private Dialog overlaySettingsDialog;
    private DialogOverlaySettingsBinding dialogOverlaySettingsBinding;

    private Dialog historySettingsDialog;
    private DialogHistorySettingsBinding dialogHistorySettingsBinding;

    private Dialog unknownSourcesDialog;
    private DialogUnknownSourcesBinding dialogUnknownSourcesBinding;

    private Dialog administratorModeDialog;
    private DialogAdministratorModeBinding dialogAdministratorModeBinding;

    private Dialog deviceInfoDialog;
    private DialogDeviceInfoBinding dialogDeviceInfoBinding;

    private Dialog accessibilityServiceDialog;
    private DialogAccessibilityServiceBinding dialogAccessibilityServiceBinding;

    private Dialog systemSettingsDialog;
    private DialogSystemSettingsBinding dialogSystemSettingsBinding;

    private Handler handler = new Handler();
    private View applicationNotAllowed;

    private SharedPreferences preferences;

    private static boolean configInitialized = false;
    private static boolean configInitializing = false;
    private static boolean interruptResumeFlow = false;
    private static List< Application > applicationsForInstall = new LinkedList();
    private static List< Application > applicationsForRun = new LinkedList();
    private static final int PAUSE_BETWEEN_AUTORUNS_SEC = 5;
    private static final int SEND_DEVICE_INFO_PERIOD_MINS = 15;
    private static final String WORK_TAG_DEVICEINFO = "com.hmdm.launcher.WORK_TAG_DEVICEINFO";
    private boolean sendDeviceInfoScheduled = false;

    private Handler emuiRestarterHandler;

    private int kioskUnlockCounter = 0;

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive( Context context, Intent intent ) {
            switch ( intent.getAction() ) {
                case Const.ACTION_UPDATE_CONFIGURATION:
                    Log.i(LOG_TAG, "Updating configuration from broadcast receiver, force=true");
                    updateConfig( true );
                    interruptResumeFlow = true;
                    binding.setShowContent( false );
                    break;
                case Const.ACTION_HIDE_SCREEN:
                    if ( applicationNotAllowed != null ) {
                        TextView textView = ( TextView ) applicationNotAllowed.findViewById( R.id.message );
                        textView.setText( String.format( getString(R.string.access_to_app_denied),
                                intent.getStringExtra( Const.PACKAGE_NAME ) ) );

                        applicationNotAllowed.setVisibility( View.VISIBLE );
                        handler.postDelayed( new Runnable() {
                            @Override
                            public void run() {
                                applicationNotAllowed.setVisibility( View.GONE );
                            }
                        }, 5000 );
                    }

                    break;
                case Const.ACTION_EXIT:
                    finish();
                    break;
            }

        }
    };

    private ImageView exitView;
    private ImageView infoView;
    private ImageView updateView;

    private View statusBarView;
    private View rightToolbarView;

    @Override
    protected void onCreate( Bundle savedInstanceState ) {
        super.onCreate( savedInstanceState );

        // Crashlytics is not included in the open-source version
        ProUtils.initCrashlytics(this);

        binding = DataBindingUtil.setContentView( this, R.layout.activity_main );
        binding.setMessage( getString( R.string.main_start_preparations ) );
        binding.setLoading( true );

        startService(new Intent( this, PluginApiService.class ));
        if (BuildConfig.PUSH_NOTIFICATION_POLLING) {
            startService(new Intent(this, PushNotificationPollingService.class));
        }

        settingsHelper = SettingsHelper.getInstance( this );
        preferences = getSharedPreferences( Const.PREFERENCES, MODE_PRIVATE );
        initReceiver();
    }

    @Override
    protected void onSaveInstanceState( Bundle outState ) {
        int orientation = getResources().getConfiguration().orientation;
        outState.putInt( Const.ORIENTATION, orientation );

        super.onSaveInstanceState( outState );
    }

    private void initReceiver() {
        IntentFilter intentFilter = new IntentFilter( Const.ACTION_UPDATE_CONFIGURATION );
        intentFilter.addAction( Const.ACTION_HIDE_SCREEN );
        intentFilter.addAction( Const.ACTION_EXIT );
        LocalBroadcastManager.getInstance( this ).registerReceiver( receiver, intentFilter );

        // Here we handle the completion of the silent app installation in the device owner mode
        // For some reason, it doesn't work in a common broadcast receiver
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(Const.ACTION_INSTALL_COMPLETE)) {
                    stopEmuiRestarter();
                    // Always grant all dangerous rights to the app
                    // TODO: in the future, the rights must be configurable on the server
                    String packageName = intent.getStringExtra(Const.PACKAGE_NAME);
                    if (packageName != null) {
                        Log.i(LOG_TAG, "Install complete: " + packageName);
                        Utils.autoGrantRequestedPermissions(MainActivity.this, packageName);
                    }
                    checkAndStartLauncher();
                }
            }
        }, new IntentFilter(Const.ACTION_INSTALL_COMPLETE));

    }

    @Override
    protected void onResume() {
        super.onResume();

        // Foreground apps checks are not available in a free version: services are the stubs
        startService( new Intent( this, CheckForegroundApplicationService.class ) );
        startService( new Intent( this, CheckForegroundAppAccessibilityService.class ) );

        // Send pending logs to server
        RemoteLogger.sendLogsToServer(this);

        if (interruptResumeFlow) {
            interruptResumeFlow = false;
            return;
        }

        checkAndStartLauncher();
    }

    private void checkAndStartLauncher() {

        boolean deviceOwner = Utils.isDeviceOwner(this);
        preferences.edit().putInt(Const.PREFERENCES_DEVICE_OWNER, deviceOwner ?
            Const.PREFERENCES_DEVICE_OWNER_ON : Const.PREFERENCES_DEVICE_OWNER_OFF).commit();
        if (deviceOwner) {
            Utils.autoGrantRequestedPermissions(this, getPackageName());
        }

        int unknownSourceMode = preferences.getInt(Const.PREFERENCES_UNKNOWN_SOURCES, -1);
        if (!deviceOwner && unknownSourceMode == -1) {
            if (checkUnknownSources()) {
                preferences.
                        edit().
                        putInt( Const.PREFERENCES_UNKNOWN_SOURCES, Const.PREFERENCES_UNKNOWN_SOURCES_ON ).
                        commit();
            } else {
                return;
            }
        }

        int administratorMode = preferences.getInt( Const.PREFERENCES_ADMINISTRATOR, - 1 );
        if ( administratorMode == -1 ) {
            createAndShowAdministratorDialog();
            return;
        }

        int overlayMode = preferences.getInt( Const.PREFERENCES_OVERLAY, - 1 );
        if ( overlayMode == -1 ) {
            if ( checkAlarmWindow() ) {
                preferences.
                        edit().
                        putInt( Const.PREFERENCES_OVERLAY, Const.PREFERENCES_OVERLAY_ON ).
                        commit();
            } else {
                return;
            }
        }

        int usageStatisticsMode = preferences.getInt( Const.PREFERENCES_USAGE_STATISTICS, - 1 );
        if ( usageStatisticsMode == -1 ) {
            if ( checkUsageStatistics() ) {
                preferences.
                        edit().
                        putInt( Const.PREFERENCES_USAGE_STATISTICS, Const.PREFERENCES_USAGE_STATISTICS_ON ).
                        commit();
            } else {
                return;
            }
        }

        int accessibilityService = preferences.getInt( Const.PREFERENCES_ACCESSIBILITY_SERVICE, - 1 );
        if ( accessibilityService == -1 ) {
            if ( checkAccessibilityService() ) {
                preferences.
                        edit().
                        putInt( Const.PREFERENCES_ACCESSIBILITY_SERVICE, Const.PREFERENCES_ACCESSIBILITY_SERVICE_ON ).
                        commit();
            } else {
                createAndShowAccessibilityServiceDialog();
                return;
            }
        }

        if ( usageStatisticsMode == Const.PREFERENCES_USAGE_STATISTICS_OFF ||
                (settingsHelper != null && settingsHelper.getConfig() != null && settingsHelper.getConfig().getLockStatusBar() != null && settingsHelper.getConfig().getLockStatusBar())) {
            // If usage statistics is not available (some early Samsung devices), block the status bar and right bar (App list) expansion
            statusBarView = ProUtils.preventStatusBarExpansion(this);
            rightToolbarView = ProUtils.preventApplicationsList(this);
        }

        createApplicationNotAllowedScreen();
        startLauncher();
    }

    private void createAndShowAccessibilityServiceDialog() {
        dismissDialog(accessibilityServiceDialog);
        accessibilityServiceDialog = new Dialog( this );
        dialogAccessibilityServiceBinding = DataBindingUtil.inflate(
                LayoutInflater.from( this ),
                R.layout.dialog_accessibility_service,
                null,
                false );
        accessibilityServiceDialog.setCancelable( false );
        accessibilityServiceDialog.requestWindowFeature( Window.FEATURE_NO_TITLE );

        accessibilityServiceDialog.setContentView( dialogAccessibilityServiceBinding.getRoot() );
        accessibilityServiceDialog.show();
    }

    public void skipAccessibilityService( View view ) {
        try { accessibilityServiceDialog.dismiss(); }
        catch ( Exception e ) { e.printStackTrace(); }
        accessibilityServiceDialog = null;

        preferences.
                edit().
                putInt( Const.PREFERENCES_ACCESSIBILITY_SERVICE, Const.PREFERENCES_ACCESSIBILITY_SERVICE_OFF ).
                commit();

        checkAndStartLauncher();
    }

    public void setAccessibilityService( View view ) {
        try { accessibilityServiceDialog.dismiss(); }
        catch ( Exception e ) { e.printStackTrace(); }
        accessibilityServiceDialog = null;

        Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivityForResult(intent, 0);
    }

    // Accessibility services are needed in the Pro-version only
    private boolean checkAccessibilityService() {
        return ProUtils.checkAccessibilityService(this);
    }

    private void createLauncherButtons() {
        createExitButton();
        createInfoButton();
        createUpdateButton();
    }

    private void createButtons() {
        ServerConfig config = settingsHelper.getConfig();
        if (ProUtils.kioskModeRequired(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    !Settings.canDrawOverlays( this )) {
                Toast.makeText(this, R.string.kiosk_mode_requires_overlays, Toast.LENGTH_LONG).show();
                config.setKioskMode(false);
                settingsHelper.updateConfig(config);
                createLauncherButtons();
                return;
            }
            View kioskUnlockButton = ProUtils.createKioskUnlockButton(this);
            kioskUnlockButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    kioskUnlockCounter++;
                    if (kioskUnlockCounter >= Const.KIOSK_UNLOCK_CLICK_COUNT ) {
                        // We are in the main app: let's open launcher activity
                        interruptResumeFlow = true;
                        Intent restoreLauncherIntent = new Intent( MainActivity.this, MainActivity.class );
                        restoreLauncherIntent.addFlags( Intent.FLAG_ACTIVITY_NEW_TASK );
                        startActivity( restoreLauncherIntent );
                        createAndShowEnterPasswordDialog();
                        kioskUnlockCounter = 0;
                    }
                }
            });
        } else {
            createLauncherButtons();
        }
    }

    private void startLauncher() {
        createButtons();

        if ( applicationsForInstall.size() > 0 ) {
            loadAndInstallApplications();
        } else if ( !checkPermissions(true)) {
            // Permissions are requested inside checkPermissions, so do nothing here
            Log.i(LOG_TAG, "startLauncher: requesting permissions");
        } else if ( settingsHelper.getDeviceId().length() == 0 ) {
            createAndShowEnterDeviceIdDialog( false, null );
        } else if ( ! configInitialized ) {
            Log.i(LOG_TAG, "Updating configuration in startLauncher()");
            updateConfig( false );
        } else if ( ! configInitializing ) {
            Log.i(LOG_TAG, "Showing content");
            showContent( settingsHelper.getConfig() );
        } else {
            Log.i(LOG_TAG, "Do nothing in startLauncher: configInitializing=true");
        }
    }

    // Access to usage statistics is required in the Pro-version only
    private boolean checkUsageStatistics() {
        if (!ProUtils.checkUsageStatistics(this)) {
            createAndShowHistorySettingsDialog();
            return false;
        }
        return true;
    }

    private boolean checkAlarmWindow() {
        if (  Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                !Settings.canDrawOverlays( this ) ) {
            createAndShowOverlaySettingsDialog();

            return false;
        } else {
            return true;
        }
    }

    private boolean checkUnknownSources() {
        try {
        if ( Settings.Secure.getInt(getContentResolver(), Settings.Secure.INSTALL_NON_MARKET_APPS) != 1 ) {
            createAndShowUnknownSourcesDialog();
            return false;
        } else {
            return true;
        }
        } catch (Settings.SettingNotFoundException e) {
            return true;
        }
    }

    private void createApplicationNotAllowedScreen() {
        if ( applicationNotAllowed != null ) {
            return;
        }

        WindowManager manager = ((WindowManager)getApplicationContext().getSystemService(Context.WINDOW_SERVICE));

        WindowManager.LayoutParams localLayoutParams = new WindowManager.LayoutParams();
        localLayoutParams.type = Utils.OverlayWindowType();
        localLayoutParams.gravity = Gravity.RIGHT;
        localLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

        localLayoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        localLayoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        localLayoutParams.format = PixelFormat.TRANSPARENT;

        applicationNotAllowed = LayoutInflater.from( this ).inflate( R.layout.layout_application_not_allowed, null );
        applicationNotAllowed.findViewById( R.id.layout_application_not_allowed_continue ).setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick( View v ) {
                applicationNotAllowed.setVisibility( View.GONE );
            }
        } );
        applicationNotAllowed.findViewById( R.id.layout_application_not_allowed_admin ).setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick( View v ) {
                applicationNotAllowed.setVisibility( View.GONE );
                createAndShowEnterPasswordDialog();
            }
        } );

        applicationNotAllowed.setVisibility( View.GONE );

        try {
            manager.addView( applicationNotAllowed, localLayoutParams );
        } catch ( Exception e ) { e.printStackTrace(); }
    }

    private ImageView createManageButton(int imageResource, int imageResourceBlack, int offset) {
        WindowManager manager = ((WindowManager)getApplicationContext().getSystemService(Context.WINDOW_SERVICE));

        WindowManager.LayoutParams localLayoutParams = new WindowManager.LayoutParams();
        localLayoutParams.type = Utils.OverlayWindowType();
        localLayoutParams.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        localLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

        localLayoutParams.height = getResources().getDimensionPixelOffset( R.dimen.activity_main_exit_button_size );
        localLayoutParams.width = getResources().getDimensionPixelOffset( R.dimen.activity_main_exit_button_size );
        localLayoutParams.format = PixelFormat.TRANSPARENT;
        localLayoutParams.y = offset;

        boolean dark = true;
        try {
            ServerConfig config = settingsHelper.getConfig();
            if (config.getBackgroundColor() != null) {
                int color = Color.parseColor(config.getBackgroundColor());
                dark = !Utils.isLightColor(color);
            }
        } catch (Exception e) {
        }

        ImageView manageButton = new ImageView( this );
        manageButton.setImageResource(dark ? imageResource : imageResourceBlack);

        try {
            manager.addView( manageButton, localLayoutParams );
        } catch ( Exception e ) { e.printStackTrace(); }
        return manageButton;
    }

    private void createExitButton() {
        if ( exitView != null ) {
            return;
        }
        exitView = createManageButton(R.drawable.ic_vpn_key_opaque_24dp, R.drawable.ic_vpn_key_black_24dp, 0);
        exitView.setOnLongClickListener(this);
    }

    private void createInfoButton() {
        if ( infoView != null ) {
            return;
        }
        infoView = createManageButton(R.drawable.ic_info_opaque_24dp, R.drawable.ic_info_black_24dp,
                getResources().getDimensionPixelOffset(R.dimen.info_icon_margin));
        infoView.setOnClickListener(this);
    }

    private void createUpdateButton() {
        if ( updateView != null ) {
            return;
        }
        updateView = createManageButton(R.drawable.ic_system_update_opaque_24dp, R.drawable.ic_system_update_black_24dp,
                (int)(2.05f * getResources().getDimensionPixelOffset(R.dimen.info_icon_margin)));
        updateView.setOnClickListener(this);
    }

    private void updateConfig( final boolean force ) {
        if ( configInitializing ) {
            Log.i(LOG_TAG, "updateConfig(): configInitializing=true, exiting");
            return;
        }

        Log.i(LOG_TAG, "updateConfig(): set configInitializing=true");
        configInitializing = true;
        binding.setMessage( getString( R.string.main_activity_update_config ) );
        GetServerConfigTask task = new GetServerConfigTask( this ) {
            @Override
            protected void onPostExecute( Integer result ) {
                super.onPostExecute( result );
                configInitializing = false;
                Log.i(LOG_TAG, "updateConfig(): set configInitializing=false after getting config");

                switch ( result ) {
                    case Const.TASK_SUCCESS:
                        updateRemoteLogConfig();
                        break;
                    case Const.TASK_ERROR:
                        if ( enterDeviceIdDialog != null ) {
                            enterDeviceIdDialogBinding.setError( true );
                            enterDeviceIdDialog.show();
                        } else {
                            createAndShowEnterDeviceIdDialog( true, settingsHelper.getDeviceId() );
                        }
                        break;
                    case Const.TASK_NETWORK_ERROR:
                        if ( settingsHelper.getConfig() != null && !force ) {
                            showContent( settingsHelper.getConfig() );
                        } else {
                            createAndShowNetworkErrorDialog();
                        }
                        break;
                }
            }
        };
        task.execute();
    }

    private void updateRemoteLogConfig() {
        Log.i(LOG_TAG, "updateRemoteLogConfig(): get logging configuration");

        GetRemoteLogConfigTask task = new GetRemoteLogConfigTask( this ) {
            @Override
            protected void onPostExecute( Integer result ) {
                super.onPostExecute( result );
                Log.i(LOG_TAG, "updateRemoteLogConfig(): result=" + result);
                checkAndUpdateApplications();
            }
        };
        task.execute();
    }

    private void checkAndUpdateApplications() {
        Log.i(LOG_TAG, "checkAndUpdateApplications(): starting update applications");
        binding.setMessage( getString( R.string.main_activity_applications_update ) );

        configInitialized = true;
        configInitializing = false;

        ServerConfig config = settingsHelper.getConfig();
        generateApplicationsForInstallList( config.getApplications() );

        Log.i(LOG_TAG, "checkAndUpdateApplications(): list size=" + applicationsForInstall.size());

        loadAndInstallApplications();
    }

    private void generateApplicationsForInstallList( List< Application > applications ) {
        PackageManager packageManager = getPackageManager();

        // First handle apps to be removed, then apps to be installed
        for (Application a : applications) {
            if (a.isRemove()) {
                applicationsForInstall.add(a);
            }
        }
        for (Application a : applications) {
            if (!a.isRemove()) {
                applicationsForInstall.add(a);
            }
        }
        Iterator< Application > it = applicationsForInstall.iterator();

        while ( it.hasNext() ) {
            Application application = it.next();
            if ( (application.getUrl() == null || application.getUrl().trim().equals("")) && !application.isRemove() ) {
                // An app without URL is a system app which doesn't require installation
                it.remove();
                continue;
            }

            try {
                PackageInfo packageInfo = packageManager.getPackageInfo( application.getPkg(), 0 );

                if (application.isRemove() && !application.getVersion().equals("0") &&
                        !areVersionsEqual(packageInfo.versionName, application.getVersion())) {
                    // If a removal is required, but the app version doesn't match, do not remove
                    it.remove();
                    continue;
                }

                if (!application.isRemove() &&
                        (application.getVersion().equals("0") || areVersionsEqual(packageInfo.versionName, application.getVersion()))) {
                    // If installation is required, but the app of the same version already installed, do not install
                    it.remove();
                    continue;
                }
            } catch ( PackageManager.NameNotFoundException e ) {
                // The app isn't installed, let's keep it in the "To be installed" list
                if (application.isRemove()) {
                    // The app requires removal but already removed, remove from the list so do nothing with the app
                    it.remove();
                    continue;
                }
            }
        }
    }

    private boolean areVersionsEqual(String v1, String v2) {
        // Compare only digits (in Android 9 EMUI on Huawei Honor 8A, getPackageInfo doesn't get letters!)
        String v1d = v1.replaceAll("[^\\d.]", "");
        String v2d = v2.replaceAll("[^\\d.]", "");
        return v1d.equals(v2d);
    }

    private void loadAndInstallApplications() {
        AsyncTask.execute( new Runnable() {
            @Override
            public void run() {
                if ( applicationsForInstall.size() > 0 ) {
                    final Application application = applicationsForInstall.remove( 0 );

                    if (application.isRemove()) {
                        // Remove the app
                        Log.i(LOG_TAG, "loadAndInstallApplications(): remove app " + application.getPkg());
                        updateMessageForApplicationRemoving( application.getName() );
                        uninstallApplication(application.getPkg());

                    } else if ( application.getUrl() != null ) {
                        updateMessageForApplicationDownloading( application.getName() );

                        File file = null;
                        try {
                            Log.i(LOG_TAG, "loadAndInstallApplications(): downloading app " + application.getPkg());
                            file = Utils.downloadApplication(application.getUrl(),
                                    new Utils.DownloadApplicationProgress() {
                                        @Override
                                        public void onDownloadProgress(final int progress, final long total, final long current) {
                                                handler.post( new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        binding.progress.setMax( 100 );
                                                        binding.progress.setProgress( progress );

                                                        binding.setFileLength( total );
                                                        binding.setDownloadedLength( current );
                                                    }
                                                } );
                                            }
                                    });
                        } catch ( Exception e ) {
                            Log.i(LOG_TAG, "loadAndInstallApplications(): failed to download app " + application.getPkg() + ": " + e.getMessage());
                            e.printStackTrace();
                        }

                        if ( file != null ) {
                            updateMessageForApplicationInstalling( application.getName() );
                            installApplication( file, application.getPkg() );
                            if (application.isRunAfterInstall()) {
                                applicationsForRun.add(application);
                            }
                        } else {
                            applicationsForInstall.add( 0, application );
                            handler.post( new Runnable() {
                                @Override
                                public void run() {
                                    createAndShowFileNotDownloadedDialog( application );
                                    binding.setDownloading( false );
                                }
                            } );
                        }
                    } else {
                        handler.post( new Runnable() {
                            @Override
                            public void run() {
                                Log.i(LOG_TAG, "loadAndInstallApplications(): proceed to next app");
                                loadAndInstallApplications();
                            }
                        } );
                    }
                } else {
                    handler.post( new Runnable() {
                        @Override
                        public void run() {
                            Log.i(LOG_TAG, "Showing content from loadAndInstallApplications()");
                            showContent( settingsHelper.getConfig() );
                        }
                    } );
                }
            }
        } );
    }

    // This function is called from a background thread
    private void installApplication( File file, final String packageName ) {
            if (Utils.isDeviceOwner(this)) {
                Log.i(LOG_TAG, "installApplication(): silently installing app " + packageName);
                if (packageName.equals(getPackageName()) &&
                        getPackageManager().getLaunchIntentForPackage(Const.EMUI_LAUNCHER_RESTARTER_PACKAGE_ID) != null) {
                    // Restart self in EMUI: there's no auto restart after update in EMUI, we must use a helper app
                    startMiuiRestarter();
                }
                Utils.silentInstallApplication(this, file, packageName, new Utils.SilentInstallErrorHandler() {
                    @Override
                    public void onInstallError() {
                        stopEmuiRestarter();
                        Log.i(LOG_TAG, "installApplication(): error installing app " + packageName);
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
                    }
                });
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = FileProvider.getUriForFile( this,
                    getApplicationContext().getPackageName() + ".provider",
                    file );
            intent.setDataAndType( uri, "application/vnd.android.package-archive" );
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } else {
            Uri apkUri = Uri.fromFile( file );
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }

    }

    private void uninstallApplication(final String packageName) {
        if (Utils.isDeviceOwner(this)) {
            Utils.silentUninstallApplication(this, packageName);
        } else {
            Uri packageUri = Uri.parse("package:" + packageName);
            startActivity(new Intent(Intent.ACTION_UNINSTALL_PACKAGE, packageUri));
        }
    }

    private void updateMessageForApplicationInstalling( final String name ) {
        handler.post( new Runnable() {
            @Override
            public void run() {
                binding.setMessage( getString(R.string.main_app_installing) + " " + name );
                binding.setDownloading( false );
            }
        } );
    }

    private void updateMessageForApplicationDownloading( final String name ) {
        handler.post( new Runnable() {
            @Override
            public void run() {
                binding.setMessage( getString(R.string.main_app_downloading) + " " + name );
                binding.setDownloading( true );
            }
        } );
    }

    private void updateMessageForApplicationRemoving( final String name ) {
        handler.post( new Runnable() {
            @Override
            public void run() {
                binding.setMessage( getString(R.string.main_app_removing) + " " + name );
                binding.setDownloading( false );
            }
        } );
    }

    private boolean checkSystemSettings(ServerConfig config) {
        if (config.getSystemUpdateType() != null &&
                config.getSystemUpdateType() != ServerConfig.SYSTEM_UPDATE_DEFAULT &&
                Utils.isDeviceOwner(this)) {
            Utils.setSystemUpdatePolicy(this, config.getSystemUpdateType(), config.getSystemUpdateFrom(), config.getSystemUpdateTo());
        }

        if (config.getBluetooth() != null) {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter != null) {
                boolean enabled = bluetoothAdapter.isEnabled();
                if (config.getBluetooth() && !enabled) {
                    bluetoothAdapter.enable();
                } else if (!config.getBluetooth() && enabled) {
                    bluetoothAdapter.disable();
                }
            }
        }

        if (config.getWifi() != null) {
            WifiManager wifiManager = (WifiManager) this.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                boolean enabled = wifiManager.isWifiEnabled();
                if (config.getWifi() && !enabled) {
                    wifiManager.setWifiEnabled(true);
                } else if (!config.getWifi() && enabled) {
                    wifiManager.setWifiEnabled(false);
                }
            }
        }

        // To delay opening the settings activity
        boolean dialogWillShow = false;

        if (config.getGps() != null) {
            LocationManager lm = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
            if (lm != null) {
                boolean enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
                if (config.getGps() && !enabled) {
                    dialogWillShow = true;
                    postDelayedSystemSettingDialog(getString(R.string.message_turn_on_gps), new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));

                } else if (!config.getGps() && enabled) {
                    dialogWillShow = true;
                    postDelayedSystemSettingDialog(getString(R.string.message_turn_off_gps), new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                }
            }
        }

        if (config.getMobileData() != null) {
            ConnectivityManager cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null && !dialogWillShow) {
                try {
                    // A hack: use private API
                    // https://stackoverflow.com/questions/12686899/test-if-background-data-and-packet-data-is-enabled-or-not?rq=1
                    Class clazz = Class.forName(cm.getClass().getName());
                    Method method = clazz.getDeclaredMethod("getMobileDataEnabled");
                    method.setAccessible(true); // Make the method callable
                    // get the setting for "mobile data"
                    boolean enabled = (Boolean)method.invoke(cm);
                    final Intent mobileDataSettingsIntent = new Intent();
                    // One more hack: open the data transport activity
                    // https://stackoverflow.com/questions/31700842/which-intent-should-open-data-usage-screen-from-settings
                    mobileDataSettingsIntent.setComponent(new ComponentName("com.android.settings",
                            "com.android.settings.Settings$DataUsageSummaryActivity"));
                    if (config.getMobileData() && !enabled) {
                        postDelayedSystemSettingDialog(getString(R.string.message_turn_on_mobile_data), mobileDataSettingsIntent);
                    } else if (!config.getMobileData() && enabled) {
                        postDelayedSystemSettingDialog(getString(R.string.message_turn_off_mobile_data), mobileDataSettingsIntent);
                    }
                } catch (Exception e) {
                    // Some problem accessible private API
                }
            }
        }
        return true;
    }

    private void showContent( ServerConfig config ) {
        if (!checkSystemSettings(config)) {
            // Here we go when the settings window is opened;
            // Next time we're here after we returned from the Android settings through onResume()
            return;
        }

        scheduleDeviceInfoSending();
        scheduleInstalledAppsRun();

        if (ProUtils.kioskModeRequired(this)) {
            String kioskApp = settingsHelper.getConfig().getMainApp();
            if (kioskApp != null && kioskApp.trim().length() > 0) {
                if (ProUtils.startCosuKioskMode(kioskApp, this)) {
                    return;
                } else {
                    Log.e(Const.LOG_TAG, "Kiosk mode failed, proceed with the default flow");
                }
            } else {
                Log.e(Const.LOG_TAG, "Kiosk mode disabled: please setup the main app!");
            }
        }

        if ( config.getBackgroundColor() != null ) {
            binding.activityMainContentWrapper.setBackgroundColor( Color.parseColor( config.getBackgroundColor() ) );
        } else {
            binding.activityMainContentWrapper.setBackgroundColor( getResources().getColor(R.color.defaultBackground));
        }
        updateTitle(config);

        if ( config.getBackgroundImageUrl() != null && config.getBackgroundImageUrl().length() > 0 ) {
            Picasso.with( this ).
                    load( config.getBackgroundImageUrl() ).
                    into( binding.activityMainBackground );
        } else {
            binding.activityMainBackground.setImageDrawable(null);
        }

        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize( size );

        int width = size.x;
        int itemWidth = getResources().getDimensionPixelSize( R.dimen.app_list_item_size );

        int spanCount = ( int ) ( width * 1.0f / itemWidth );

        binding.activityMainContent.setLayoutManager( new GridLayoutManager( this, spanCount ) );
        binding.activityMainContent.setAdapter( new AppListAdapter( this, this ) );
        binding.setShowContent( true );
    }

    public static class SendDeviceInfoWorker extends Worker {

        private Context context;
        private SettingsHelper settingsHelper;

        public SendDeviceInfoWorker(
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

            DeviceInfo deviceInfo = new DeviceInfo(context);

            ServerService serverService = ServerServiceKeeper.getServerServiceInstance(context);
            ServerService secondaryServerService = ServerServiceKeeper.getSecondaryServerServiceInstance(context);
            Response<ResponseBody> response = null;

            try {
                response = serverService.sendDevice(settingsHelper.getServerProject(), deviceInfo).execute();
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                if (response == null) {
                    response = secondaryServerService.sendDevice(settingsHelper.getServerProject(), deviceInfo).execute();
                }
                if ( response.isSuccessful() ) {
                    return Result.success();
                }
            }
            catch ( Exception e ) { e.printStackTrace(); }

            return Result.failure();
        }
    }

    private void scheduleDeviceInfoSending() {
        if (sendDeviceInfoScheduled) {
            return;
        }
        sendDeviceInfoScheduled = true;
        PeriodicWorkRequest request =
                new PeriodicWorkRequest.Builder(SendDeviceInfoWorker.class, SEND_DEVICE_INFO_PERIOD_MINS, TimeUnit.MINUTES)
                        .addTag(Const.WORK_TAG_COMMON)
                        .build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(WORK_TAG_DEVICEINFO, ExistingPeriodicWorkPolicy.REPLACE, request);
    }

    private void scheduleInstalledAppsRun() {
        if (applicationsForRun.size() == 0) {
            return;
        }
        Handler handler = new Handler();
        int pause = PAUSE_BETWEEN_AUTORUNS_SEC;
        while (applicationsForRun.size() > 0) {
            final Application application = applicationsForRun.get(0);
            applicationsForRun.remove(0);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Intent launchIntent = getPackageManager().getLaunchIntentForPackage(application.getPkg());
                    if (launchIntent != null) {
                        startActivity(launchIntent);
                    }
                }
            }, pause * 1000);
            pause += PAUSE_BETWEEN_AUTORUNS_SEC;
        }
    }

    private void updateTitle(ServerConfig config) {
        String titleType = config.getTitle();
        if (titleType != null && titleType.equals(ServerConfig.TITLE_DEVICE_ID)) {
            if (config.getTextColor() != null) {
                binding.activityMainTitle.setTextColor(Color.parseColor(settingsHelper.getConfig().getTextColor()));
            }
            binding.activityMainTitle.setVisibility(View.VISIBLE);
            binding.activityMainTitle.setText(SettingsHelper.getInstance(this).getDeviceId());
        } else {
            binding.activityMainTitle.setVisibility(View.GONE);
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        WindowManager manager = ((WindowManager)getApplicationContext().getSystemService(Context.WINDOW_SERVICE));
        if ( applicationNotAllowed != null ) {
            try { manager.removeView( applicationNotAllowed ); }
            catch ( Exception e ) { e.printStackTrace(); }
        }

        if ( statusBarView != null ) {
            try { manager.removeView( statusBarView ); }
            catch ( Exception e ) { e.printStackTrace(); }
        }

        if ( rightToolbarView != null ) {
            try { manager.removeView( rightToolbarView ); }
            catch ( Exception e ) { e.printStackTrace(); }
        }

        if ( exitView != null ) {
            try { manager.removeView( exitView ); }
            catch ( Exception e ) { e.printStackTrace(); }
        }

        if ( infoView != null ) {
            try { manager.removeView( infoView ); }
            catch ( Exception e ) { e.printStackTrace(); }
        }

        if ( updateView != null ) {
            try { manager.removeView( updateView ); }
            catch ( Exception e ) { e.printStackTrace(); }
        }

        LocalBroadcastManager.getInstance( this ).unregisterReceiver( receiver );
    }

    @Override
    protected void onPause() {
        super.onPause();

        dismissDialog(fileNotDownloadedDialog);
        dismissDialog(enterDeviceIdDialog);
        dismissDialog(networkErrorDialog);
        dismissDialog(enterPasswordDialog);
        dismissDialog(historySettingsDialog);
        dismissDialog(unknownSourcesDialog);
        dismissDialog(overlaySettingsDialog);
        dismissDialog(administratorModeDialog);
        dismissDialog(deviceInfoDialog);
        dismissDialog(accessibilityServiceDialog);
        dismissDialog(systemSettingsDialog);

        LocalBroadcastManager.getInstance( this ).sendBroadcast( new Intent( Const.ACTION_SHOW_LAUNCHER ) );
    }

    private void createAndShowAdministratorDialog() {
        dismissDialog(administratorModeDialog);
        administratorModeDialog = new Dialog( this );
        dialogAdministratorModeBinding = DataBindingUtil.inflate(
                LayoutInflater.from( this ),
                R.layout.dialog_administrator_mode,
                null,
                false );
        administratorModeDialog.setCancelable( false );
        administratorModeDialog.requestWindowFeature( Window.FEATURE_NO_TITLE );

        administratorModeDialog.setContentView( dialogAdministratorModeBinding.getRoot() );
        administratorModeDialog.show();
    }

    public void skipAdminMode( View view ) {
        dismissDialog(administratorModeDialog);

        preferences.
                edit().
                putInt( Const.PREFERENCES_ADMINISTRATOR, Const.PREFERENCES_ADMINISTRATOR_OFF ).
                commit();

        checkAndStartLauncher();
    }

    public void setAdminMode( View view ) {
        dismissDialog(administratorModeDialog);

        preferences.
                edit().
                putInt( Const.PREFERENCES_ADMINISTRATOR, Const.PREFERENCES_ADMINISTRATOR_ON ).
                commit();

        Intent intent = new Intent( android.provider.Settings.ACTION_SECURITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void createAndShowFileNotDownloadedDialog( Application application ) {
        dismissDialog(fileNotDownloadedDialog);
        fileNotDownloadedDialog = new Dialog( this );
        dialogFileDownloadingFailedBinding = DataBindingUtil.inflate(
                LayoutInflater.from( this ),
                R.layout.dialog_file_downloading_failed,
                null,
                false );
        dialogFileDownloadingFailedBinding.title.setText( getString(R.string.main_app_downloading_error) + " " + application.getName() );
        fileNotDownloadedDialog.setCancelable( false );
        fileNotDownloadedDialog.requestWindowFeature( Window.FEATURE_NO_TITLE );

        fileNotDownloadedDialog.setContentView( dialogFileDownloadingFailedBinding.getRoot() );
        try {
            fileNotDownloadedDialog.show();
        } catch (Exception e) {
            // BadTokenException ignored
        }
    }

    public void repeatDownloadClicked( View view ) {
        dismissDialog(fileNotDownloadedDialog);
        loadAndInstallApplications();
    }

    public void confirmDownloadFailureClicked( View view ) {
        if (applicationsForInstall.size() > 0) {
            Application application = applicationsForInstall.remove( 0 );
            settingsHelper.removeApplication( application );
        }

        dismissDialog(fileNotDownloadedDialog);
        loadAndInstallApplications();
    }

    private void createAndShowHistorySettingsDialog() {
        dismissDialog(historySettingsDialog);
        historySettingsDialog = new Dialog( this );
        dialogHistorySettingsBinding = DataBindingUtil.inflate(
                LayoutInflater.from( this ),
                R.layout.dialog_history_settings,
                null,
                false );
        historySettingsDialog.setCancelable( false );
        historySettingsDialog.requestWindowFeature( Window.FEATURE_NO_TITLE );

        historySettingsDialog.setContentView( dialogHistorySettingsBinding.getRoot() );
        historySettingsDialog.show();
    }

    public void historyWithoutPermission( View view ) {
        dismissDialog(historySettingsDialog);

        preferences.
                edit().
                putInt( Const.PREFERENCES_USAGE_STATISTICS, Const.PREFERENCES_USAGE_STATISTICS_OFF ).
                commit();
        checkAndStartLauncher();
    }

    public void continueHistory( View view ) {
        dismissDialog(historySettingsDialog);

        startActivity( new Intent( Settings.ACTION_USAGE_ACCESS_SETTINGS ) );
    }

    private void createAndShowOverlaySettingsDialog() {
        dismissDialog(overlaySettingsDialog);
        overlaySettingsDialog = new Dialog( this );
        dialogOverlaySettingsBinding = DataBindingUtil.inflate(
                LayoutInflater.from( this ),
                R.layout.dialog_overlay_settings,
                null,
                false );
        overlaySettingsDialog.setCancelable( false );
        overlaySettingsDialog.requestWindowFeature( Window.FEATURE_NO_TITLE );

        overlaySettingsDialog.setContentView( dialogOverlaySettingsBinding.getRoot() );
        overlaySettingsDialog.show();
    }

    public void overlayWithoutPermission( View view ) {
        dismissDialog(overlaySettingsDialog);

        preferences.
                edit().
                putInt( Const.PREFERENCES_OVERLAY, Const.PREFERENCES_OVERLAY_OFF ).
                commit();
        checkAndStartLauncher();
    }

    public void continueOverlay( View view ) {
        dismissDialog(overlaySettingsDialog);

        Intent intent = new Intent( Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse( "package:" + getPackageName() ) );
        startActivityForResult( intent, 1001 );
    }

    public void showDeviceIdVariants( View view ) {
        enterDeviceIdDialogBinding.deviceId.showDropDown();
    }

    public void saveDeviceId( View view ) {
        String deviceId = enterDeviceIdDialogBinding.deviceId.getText().toString();
        if ( "".equals( deviceId ) ) {
            return;
        } else {
            settingsHelper.setDeviceId( deviceId );
            enterDeviceIdDialogBinding.setError( false );

            dismissDialog(enterDeviceIdDialog);

            if ( checkPermissions( true ) ) {
                Log.i(LOG_TAG, "saveDeviceId(): calling updateConfig()");
                updateConfig( false );
            }
        }
    }

    public void networkErrorRepeatClicked( View view ) {
        dismissDialog(networkErrorDialog);

        Log.i(LOG_TAG, "networkErrorRepeatClicked(): calling updateConfig()");
        updateConfig( false );
    }

    public void networkErrorCancelClicked(View view) {
        dismissDialog(networkErrorDialog);

        Log.i(LOG_TAG, "networkErrorCancelClicked()");
        if ( settingsHelper.getConfig() != null ) {
            showContent( settingsHelper.getConfig() );
        } else {
            Log.i(LOG_TAG, "networkErrorCancelClicked(): no configuration available, retrying");
            Toast.makeText(this, R.string.empty_configuration, Toast.LENGTH_LONG).show();
            updateConfig( false );
        }
    }

    private boolean checkPermissions( boolean startSettings ) {
        if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                ( checkSelfPermission( android.Manifest.permission.READ_EXTERNAL_STORAGE ) != PackageManager.PERMISSION_GRANTED ||
                  checkSelfPermission( android.Manifest.permission.WRITE_EXTERNAL_STORAGE ) != PackageManager.PERMISSION_GRANTED ||
                  checkSelfPermission( Manifest.permission.READ_PHONE_STATE ) != PackageManager.PERMISSION_GRANTED ) ) {

            if ( startSettings ) {
                requestPermissions( new String[] {
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_PHONE_STATE
                }, PERMISSIONS_REQUEST );
            }

            return false;
        } else {
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults ) {
        super.onRequestPermissionsResult( requestCode, permissions, grantResults );

        if ( requestCode == PERMISSIONS_REQUEST && grantResults.length > 0 &&
                grantResults[ 0 ] == PackageManager.PERMISSION_GRANTED ) {
            if ( !configInitialized ) {
                Log.i(LOG_TAG, "onRequestPermissionsResult(): calling updateConfig()");
                updateConfig( false );
            }
        }
    }

    private void createAndShowEnterPasswordDialog() {
        dismissDialog(enterPasswordDialog);
        enterPasswordDialog = new Dialog( this );
        dialogEnterPasswordBinding = DataBindingUtil.inflate(
                LayoutInflater.from( this ),
                R.layout.dialog_enter_password,
                null,
                false );
        enterPasswordDialog.requestWindowFeature( Window.FEATURE_NO_TITLE );
        enterPasswordDialog.setCancelable( false );

        enterPasswordDialog.setContentView( dialogEnterPasswordBinding.getRoot() );
        dialogEnterPasswordBinding.setLoading( false );
        try {
            enterPasswordDialog.show();
        } catch (Exception e) {
            // Sometimes here we get a Fatal Exception: android.view.WindowManager$BadTokenException
            // Unable to add window -- token android.os.BinderProxy@f307de for displayid = 0 is not valid; is your activity running?
            Toast.makeText(getApplicationContext(), R.string.internal_error, Toast.LENGTH_LONG).show();
        }
    }

    public void closeEnterPasswordDialog( View view ) {
        dismissDialog(enterPasswordDialog);
        if (ProUtils.kioskModeRequired(this)) {
            checkAndStartLauncher();
        }
    }

    public void checkAdministratorPassword( View view ) {
        dialogEnterPasswordBinding.setLoading( true );
        GetServerConfigTask task = new GetServerConfigTask( this ) {
            @Override
            protected void onPostExecute( Integer result ) {
                dialogEnterPasswordBinding.setLoading( false );

                String masterPassword = CryptoHelper.getMD5String( "12345678" );
                if ( settingsHelper.getConfig() != null && settingsHelper.getConfig().getPassword() != null ) {
                    masterPassword = settingsHelper.getConfig().getPassword();
                }

                if ( CryptoHelper.getMD5String( dialogEnterPasswordBinding.password.getText().toString() ).
                        equals( masterPassword ) ) {
                    dismissDialog(enterPasswordDialog);
                    dialogEnterPasswordBinding.setError( false );
                    if (ProUtils.kioskModeRequired(MainActivity.this)) {
                        ProUtils.unlockKiosk(MainActivity.this);
                    }
                    startActivity( new Intent( MainActivity.this, AdminActivity.class ) );
                } else {
                    dialogEnterPasswordBinding.setError( true );
                }
            }
        };
        task.execute();
    }

    private void createAndShowUnknownSourcesDialog() {
        dismissDialog(unknownSourcesDialog);
        unknownSourcesDialog = new Dialog( this );
        dialogUnknownSourcesBinding = DataBindingUtil.inflate(
                LayoutInflater.from( this ),
                R.layout.dialog_unknown_sources,
                null,
                false );
        unknownSourcesDialog.setCancelable( false );
        unknownSourcesDialog.requestWindowFeature( Window.FEATURE_NO_TITLE );

        unknownSourcesDialog.setContentView( dialogUnknownSourcesBinding.getRoot() );
        unknownSourcesDialog.show();
    }

    public void continueUnknownSources( View view ) {
        dismissDialog(unknownSourcesDialog);
        startActivity(new Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS));
    }

    @Override
    public void onBackPressed() {}

    @Override
    public void onAppChoose( @NonNull AppInfo resolveInfo ) {

    }

    @Override
    public boolean onLongClick( View v ) {
        createAndShowEnterPasswordDialog();
        return true;

    }

    @Override
    public void onClick( View v ) {
        if ( v.equals( infoView ) ) {
            createAndShowInfoDialog();
        } else {
            if (enterDeviceIdDialog != null && enterDeviceIdDialog.isShowing()) {
                Log.i(LOG_TAG, "Occasional update request when device info is entered, ignoring!");
                return;
            }
            Log.i(LOG_TAG, "updating config on request");
            binding.setShowContent( false );
            updateConfig( true );
        }
    }

    @SuppressLint( { "MissingPermission" } )
    private void createAndShowInfoDialog() {
        dismissDialog(deviceInfoDialog);
        deviceInfoDialog = new Dialog( this );
        dialogDeviceInfoBinding = DataBindingUtil.inflate(
                LayoutInflater.from( this ),
                R.layout.dialog_device_info,
                null,
                false );
        deviceInfoDialog.requestWindowFeature( Window.FEATURE_NO_TITLE );
        deviceInfoDialog.setCancelable( false );

        deviceInfoDialog.setContentView( dialogDeviceInfoBinding.getRoot() );

        dialogDeviceInfoBinding.setSerialNumber(DeviceInfoProvider.getSerialNumber());

        String phone = DeviceInfoProvider.getPhoneNumber(this);
        if (phone == null || phone.equals("")) {
            phone = settingsHelper.getConfig() != null ? settingsHelper.getConfig().getPhone() : "";
        }
        dialogDeviceInfoBinding.setPhone(phone);

        String imei = DeviceInfoProvider.getImei(this);
        if (imei == null || imei.equals("")) {
            imei = settingsHelper.getConfig() != null ? settingsHelper.getConfig().getImei() : "";
        }
        dialogDeviceInfoBinding.setImei(imei);

        dialogDeviceInfoBinding.setDeviceId(SettingsHelper.getInstance(this).getDeviceId());
        dialogDeviceInfoBinding.setVersion( BuildConfig.FLAVOR.length() > 0 ?
                BuildConfig.VERSION_NAME + "-" + BuildConfig.FLAVOR : BuildConfig.VERSION_NAME );

        deviceInfoDialog.show();
    }

    public void closeDeviceInfoDialog( View view ) {
        dismissDialog(deviceInfoDialog);
    }

    private void postDelayedSystemSettingDialog(final String message, final Intent settingsIntent) {
        LocalBroadcastManager.getInstance( this ).sendBroadcast( new Intent( Const.ACTION_ENABLE_SETTINGS ) );
        // Delayed start prevents the race of ENABLE_SETTINGS handle and tapping "Next" button
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                createAndShowSystemSettingDialog(message, settingsIntent);
            }
        }, 5000);
    }

    private void createAndShowSystemSettingDialog(final String message, final Intent settingsIntent) {
        dismissDialog(systemSettingsDialog);
        systemSettingsDialog = new Dialog( this );
        dialogSystemSettingsBinding = DataBindingUtil.inflate(
                LayoutInflater.from( this ),
                R.layout.dialog_system_settings,
                null,
                false );
        systemSettingsDialog.requestWindowFeature( Window.FEATURE_NO_TITLE );
        systemSettingsDialog.setCancelable( false );

        systemSettingsDialog.setContentView( dialogSystemSettingsBinding.getRoot() );

        dialogSystemSettingsBinding.setMessage(message);

        // Since we need to send Intent to the listener, here we don't use "event" attribute in XML resource as everywhere else
        systemSettingsDialog.findViewById(R.id.continueButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismissDialog(systemSettingsDialog);
                // Enable settings once again, because the dialog may be shown more than 3 minutes
                // This is not necessary: the problem is resolved by clicking "Continue" in a popup window
                /*LocalBroadcastManager.getInstance( MainActivity.this ).sendBroadcast( new Intent( Const.ACTION_ENABLE_SETTINGS ) );
                // Open settings with a slight delay so Broadcast would certainly be handled
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        startActivity(settingsIntent);
                    }
                }, 300);*/
                startActivity(settingsIntent);
            }
        });

        systemSettingsDialog.show();
    }

    // The following algorithm of launcher restart works in EMUI:
    // Run EMUI_LAUNCHER_RESTARTER activity; every received intent in this app
    // delays the launcher restart by one second.
    // If this instance of the launcher dies, the new one will be started within a second
    private void startMiuiRestarter() {
        if (emuiRestarterHandler != null) {
            return;
        }

        // Sending an intent synchronous BEFORE starting update, otherwise we may not succeed to start Handler
        Intent intent = getPackageManager().getLaunchIntentForPackage(Const.EMUI_LAUNCHER_RESTARTER_PACKAGE_ID);
        startActivity(intent);
        Log.i("LauncherRestarter", "Calling launcher restarter from the launcher");

        emuiRestarterHandler = new Handler();
        final Runnable restarterAppRunnable = new Runnable() {
            @Override
            public void run() {
                Intent intent = getPackageManager().getLaunchIntentForPackage(Const.EMUI_LAUNCHER_RESTARTER_PACKAGE_ID);
                startActivity(intent);
                Log.i("LauncherRestarter", "Calling launcher restarter from the launcher");
                Toast.makeText(MainActivity.this, "Called restarter", Toast.LENGTH_SHORT).show();
                emuiRestarterHandler.postDelayed(this, 200);
            }
        };
        emuiRestarterHandler.postDelayed(restarterAppRunnable, 200);
    }

    private void stopEmuiRestarter() {
        if (emuiRestarterHandler == null) {
            return;
        }
        Log.i("LauncherRestarter", "Stopping restarter loop");
        emuiRestarterHandler.removeCallbacksAndMessages(null);
        emuiRestarterHandler = null;
    }
}
