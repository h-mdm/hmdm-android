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
import android.app.Dialog;
import android.app.admin.DevicePolicyManager;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.databinding.DataBindingUtil;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.GridLayoutManager;

import com.github.anrwatchdog.ANRWatchDog;
import com.hmdm.launcher.BuildConfig;
import com.hmdm.launcher.Const;
import com.hmdm.launcher.R;
import com.hmdm.launcher.databinding.ActivityMainBinding;
import com.hmdm.launcher.databinding.DialogAccessibilityServiceBinding;
import com.hmdm.launcher.databinding.DialogAdministratorModeBinding;
import com.hmdm.launcher.databinding.DialogEnterPasswordBinding;
import com.hmdm.launcher.databinding.DialogFileDownloadingFailedBinding;
import com.hmdm.launcher.databinding.DialogHistorySettingsBinding;
import com.hmdm.launcher.databinding.DialogManageStorageBinding;
import com.hmdm.launcher.databinding.DialogMiuiPermissionsBinding;
import com.hmdm.launcher.databinding.DialogOverlaySettingsBinding;
import com.hmdm.launcher.databinding.DialogPermissionsBinding;
import com.hmdm.launcher.databinding.DialogSystemSettingsBinding;
import com.hmdm.launcher.databinding.DialogUnknownSourcesBinding;
import com.hmdm.launcher.helper.ConfigUpdater;
import com.hmdm.launcher.helper.CryptoHelper;
import com.hmdm.launcher.helper.Initializer;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.json.Application;
import com.hmdm.launcher.json.DeviceInfo;
import com.hmdm.launcher.json.RemoteFile;
import com.hmdm.launcher.json.ServerConfig;
import com.hmdm.launcher.pro.ProUtils;
import com.hmdm.launcher.pro.service.CheckForegroundAppAccessibilityService;
import com.hmdm.launcher.pro.service.CheckForegroundApplicationService;
import com.hmdm.launcher.server.ServerServiceKeeper;
import com.hmdm.launcher.server.UnsafeOkHttpClient;
import com.hmdm.launcher.service.LocationService;
import com.hmdm.launcher.service.PluginApiService;
import com.hmdm.launcher.service.StatusControlService;
import com.hmdm.launcher.task.GetServerConfigTask;
import com.hmdm.launcher.task.SendDeviceInfoTask;
import com.hmdm.launcher.util.AppInfo;
import com.hmdm.launcher.util.CrashLoopProtection;
import com.hmdm.launcher.util.DeviceInfoProvider;
import com.hmdm.launcher.util.InstallUtils;
import com.hmdm.launcher.util.PreferenceLogger;
import com.hmdm.launcher.util.RemoteLogger;
import com.hmdm.launcher.util.SystemUtils;
import com.hmdm.launcher.util.Utils;
import com.hmdm.launcher.worker.SendDeviceInfoWorker;
import com.jakewharton.picasso.OkHttp3Downloader;
import com.squareup.picasso.NetworkPolicy;
import com.squareup.picasso.Picasso;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import okhttp3.Cache;
import okhttp3.OkHttpClient;

public class MainActivity
        extends BaseActivity
        implements View.OnLongClickListener, BaseAppListAdapter.OnAppChooseListener,
        BaseAppListAdapter.SwitchAdapterListener, View.OnClickListener,
        ConfigUpdater.UINotifier {

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

    private Dialog manageStorageDialog;
    private DialogManageStorageBinding dialogManageStorageBinding;

    private Dialog miuiPermissionsDialog;
    private DialogMiuiPermissionsBinding dialogMiuiPermissionsBinding;

    private Dialog unknownSourcesDialog;
    private DialogUnknownSourcesBinding dialogUnknownSourcesBinding;

    private Dialog administratorModeDialog;
    private DialogAdministratorModeBinding dialogAdministratorModeBinding;

    private Dialog accessibilityServiceDialog;
    private DialogAccessibilityServiceBinding dialogAccessibilityServiceBinding;

    private Dialog systemSettingsDialog;
    private DialogSystemSettingsBinding dialogSystemSettingsBinding;

    private Dialog permissionsDialog;
    private DialogPermissionsBinding dialogPermissionsBinding;

    private Handler handler = new Handler();
    private View applicationNotAllowed;
    private View lockScreen;

    private SharedPreferences preferences;

    private MainAppListAdapter mainAppListAdapter;
    private BottomAppListAdapter bottomAppListAdapter;
    private int spanCount;

    private static boolean configInitialized = false;
    // This flag is used to exit kiosk to avoid looping in onResume()
    private static boolean interruptResumeFlow = false;
    private static final int BOOT_DURATION_SEC = 120;
    private static final int PAUSE_BETWEEN_AUTORUNS_SEC = 5;
    private boolean sendDeviceInfoScheduled = false;
    // This flag notifies "download error" dialog what we're downloading: application or file
    // We cannot send this flag as the method parameter because dialog calls MainActivity methods
    private boolean downloadingFile = false;

    private int kioskUnlockCounter = 0;

    private boolean configFault = false;

    private boolean needSendDeviceInfoAfterReconfigure = false;
    private boolean needRedrawContentAfterReconfigure = false;
    private boolean orientationLocked = false;

    private int REQUEST_CODE_GPS_STATE_CHANGE = 1;

    // This flag is used by the broadcast receiver to determine what to do if it gets a policy violation report
    private boolean isBackground;

    private ANRWatchDog anrWatchDog;

    private int lastNetworkType;

    private ConfigUpdater configUpdater = new ConfigUpdater();

    private Picasso picasso = null;

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive( Context context, Intent intent ) {
            switch ( intent.getAction() ) {
                case Const.ACTION_UPDATE_CONFIGURATION:
                    RemoteLogger.log(context, Const.LOG_DEBUG, "Update configuration by MainActivity");
                    updateConfig(false);
                    break;
                case Const.ACTION_HIDE_SCREEN:
                    ServerConfig serverConfig = SettingsHelper.getInstance(MainActivity.this).getConfig();
                    if (serverConfig.getLock() != null && serverConfig.getLock()) {
                        // Device is locked by the server administrator!
                        showLockScreen();
                    } else if ( applicationNotAllowed != null &&
                            (!ProUtils.kioskModeRequired(MainActivity.this) || !ProUtils.isKioskAppInstalled(MainActivity.this)) ) {
                        TextView textView = ( TextView ) applicationNotAllowed.findViewById( R.id.package_id );
                        textView.setText(intent.getStringExtra(Const.PACKAGE_NAME));

                        applicationNotAllowed.setVisibility( View.VISIBLE );
                        handler.postDelayed( new Runnable() {
                            @Override
                            public void run() {
                                applicationNotAllowed.setVisibility( View.GONE );
                            }
                        }, 20000 );
                    }
                    break;

                case Const.ACTION_DISABLE_BLOCK_WINDOW:
                    if ( applicationNotAllowed != null) {
                        applicationNotAllowed.setVisibility(View.GONE);
                    }
                    break;

                case Const.ACTION_EXIT:
                    finish();
                    break;

                case Const.ACTION_POLICY_VIOLATION:
                    if (isBackground) {
                        // If we're in the background, let's bring Headwind MDM to top and the notification will be raised in onResume
                        Intent restoreLauncherIntent = new Intent(context, MainActivity.class);
                        restoreLauncherIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(restoreLauncherIntent);
                    } else {
                        // Calling startActivity always calls onPause / onResume which is not what we want
                        // So just show dialog if it isn't already shown
                        if (systemSettingsDialog == null || !systemSettingsDialog.isShowing()) {
                            notifyPolicyViolation(intent.getIntExtra(Const.POLICY_VIOLATION_CAUSE, 0));
                        }
                    }
                    break;

                case Const.ACTION_EXIT_KIOSK:
                    ServerConfig config = settingsHelper.getConfig();
                    if (config != null) {
                        config.setKioskMode(false);
                        RemoteLogger.log(MainActivity.this, Const.LOG_INFO, "Exit kiosk by admin command");
                        showContent(config);
                    }
            }

        }
    };

    private final BroadcastReceiver stateChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Log new connection type
            if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                if (null != activeNetwork) {
                    if (lastNetworkType != activeNetwork.getType()) {
                        lastNetworkType = activeNetwork.getType();
                        RemoteLogger.log(MainActivity.this, Const.LOG_DEBUG, "Network type changed: " + activeNetwork.getTypeName());
                    }
                } else {
                    if (lastNetworkType != -1) {
                        lastNetworkType = -1;
                        RemoteLogger.log(MainActivity.this, Const.LOG_DEBUG, "Network connection lost");
                    }
                }
            }

            try {
                applyEarlyPolicies(settingsHelper.getConfig());
            } catch (Exception e) {
            }
        }
    };

    private ImageView exitView;
    private ImageView infoView;
    private ImageView updateView;

    private View statusBarView;
    private View rightToolbarView;

    private boolean firstStartAfterProvisioning = false;

    @Override
    protected void onCreate( Bundle savedInstanceState ) {
        super.onCreate( savedInstanceState );

        Intent intent = getIntent();
        Log.d(Const.LOG_TAG, "MainActivity started" + (intent != null && intent.getAction() != null ?
                ", action: " + intent.getAction() : ""));
        if (intent != null && "android.app.action.PROVISIONING_SUCCESSFUL".equalsIgnoreCase(intent.getAction())) {
            firstStartAfterProvisioning = true;
        }

        if (CrashLoopProtection.isCrashLoopDetected(this)) {
            Toast.makeText(MainActivity.this, R.string.fault_loop_detected, Toast.LENGTH_LONG).show();
            return;
        }

        // Disable crashes to avoid "select a launcher" popup
        // Crashlytics will show an exception anyway!
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                e.printStackTrace();

                ProUtils.sendExceptionToCrashlytics(e);

                CrashLoopProtection.registerFault(MainActivity.this);
                // Restart launcher if there's a launcher restarter (and we're not in a crash loop)
                if (!CrashLoopProtection.isCrashLoopDetected(MainActivity.this)) {
                    Intent intent = getPackageManager().getLaunchIntentForPackage(Const.LAUNCHER_RESTARTER_PACKAGE_ID);
                    if (intent != null) {
                        startActivity(intent);
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    finishAffinity();
                }
                System.exit(0);
            }
        });


        if (BuildConfig.ANR_WATCHDOG) {
            anrWatchDog = new ANRWatchDog();
            anrWatchDog.start();
        }
        Initializer.init(this);

        // Prevent showing the lock screen during the app download/installation
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        binding = DataBindingUtil.setContentView( this, R.layout.activity_main );
        binding.setMessage( getString( R.string.main_start_preparations ) );
        binding.setLoading( true );

        settingsHelper = SettingsHelper.getInstance( this );
        preferences = getSharedPreferences( Const.PREFERENCES, MODE_PRIVATE );

        settingsHelper.setAppStartTime(System.currentTimeMillis());

        // Try to start services in onCreate(), this may fail, we will try again on each onResume.
        startServicesWithRetry();

        initReceiver();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        intentFilter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(stateChangeReceiver, intentFilter);

        if (!getIntent().getBooleanExtra(Const.RESTORED_ACTIVITY, false)) {
            startAppsAtBoot();
        }

        settingsHelper.setMainActivityRunning(true);
    }

    // On some Android firmwares, onResume is called before onCreate, so the fields are not initialized
    // Here we initialize all required fields to avoid crash at startup
    private void reinitApp() {
        if (binding == null) {
            binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
            binding.setMessage(getString(R.string.main_start_preparations));
            binding.setLoading(true);
        }

        if (settingsHelper == null) {
            settingsHelper = SettingsHelper.getInstance(this);
        }
        if (preferences == null) {
            preferences = getSharedPreferences(Const.PREFERENCES, MODE_PRIVATE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_GPS_STATE_CHANGE) {
            // User changed GPS state, let's update location service
            startLocationServiceWithRetry();
        }
    }

    private void initReceiver() {
        IntentFilter intentFilter = new IntentFilter(Const.ACTION_UPDATE_CONFIGURATION);
        intentFilter.addAction(Const.ACTION_HIDE_SCREEN);
        intentFilter.addAction(Const.ACTION_EXIT);
        intentFilter.addAction(Const.ACTION_POLICY_VIOLATION);
        intentFilter.addAction(Const.ACTION_EXIT_KIOSK);
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, intentFilter);
    }

    @Override
    protected void onResume() {
        super.onResume();

        isBackground = false;

        // On some Android firmwares, onResume is called before onCreate, so the fields are not initialized
        // Here we initialize all required fields to avoid crash at startup
        reinitApp();

        startServicesWithRetry();

        if (interruptResumeFlow) {
            interruptResumeFlow = false;
            return;
        }

        if (!BuildConfig.SYSTEM_PRIVILEGES) {
            if (firstStartAfterProvisioning) {
                firstStartAfterProvisioning = false;
                waitForProvisioning(10);
            } else {
                setDefaultLauncherEarly();
            }
        } else {
            setSelfAsDeviceOwner();
        }
    }

    private void lockOrientation() {
        int orientation = getResources().getConfiguration().orientation;
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        Log.d(Const.LOG_TAG, "Lock orientation: orientation=" + orientation + ", rotation=" + rotation);
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            setRequestedOrientation(rotation < Surface.ROTATION_180 ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
        } else {
            setRequestedOrientation(rotation < Surface.ROTATION_180 ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (mainAppListAdapter != null && event.getAction() == KeyEvent.ACTION_UP) {
            if (!mainAppListAdapter.onKey(keyCode)) {
                if (bottomAppListAdapter != null) {
                    return bottomAppListAdapter.onKey(keyCode);
                }
            };
        }
        return super.onKeyUp(keyCode, event);
    }

    // Workaround against crash "App is in background" on Android 9: this is an Android OS bug
    // https://stackoverflow.com/questions/52013545/android-9-0-not-allowed-to-start-service-app-is-in-background-after-onresume
    private void startServicesWithRetry() {
        try {
            startServices();
        } catch (Exception e) {
            // Android OS bug!!!
            e.printStackTrace();

            // Repeat an attempt to start services after one second
            handler.postDelayed(new Runnable() {
                public void run() {
                    try {
                        startServices();
                    } catch (Exception e) {
                        // Still failed, now give up!
                        // startService may fail after resuming, but the service may be already running (there's a WorkManager)
                        // So if we get an exception here, just ignore it and hope the app will work further
                        e.printStackTrace();
                    }
                }
            }, 1000);
        }
    }

    private void startAppsAtBoot() {
        // Let's assume that we start within two minutes after boot
        // This should work even for slow devices
        long uptimeMillis = SystemClock.uptimeMillis();
        if (uptimeMillis > BOOT_DURATION_SEC * 1000) {
            return;
        }
        final ServerConfig config = settingsHelper.getConfig();
        if (config == null || config.getApplications() == null) {
            // First start
            return;
        }

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                boolean appStarted = false;
                for (Application application : config.getApplications()) {
                    if (application.isRunAtBoot()) {
                        // Delay start of each application to 5 sec
                        try {
                            Thread.sleep(PAUSE_BETWEEN_AUTORUNS_SEC * 1000);
                        } catch (InterruptedException e) {
                        }
                        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(application.getPkg());
                        if (launchIntent != null) {
                            startActivity(launchIntent);
                            appStarted = true;
                        }
                    }
                }
                // Hide apps after start to avoid users confusion
                if (appStarted && !config.isAutostartForeground()) {
                    try {
                        Thread.sleep(PAUSE_BETWEEN_AUTORUNS_SEC * 1000);
                    } catch (InterruptedException e) {
                    }
                    // Notice: if MainActivity will be destroyed after running multiple apps at startup,
                    // we can get the looping here, because startActivity will create a new instance!
                    // That's why we put a boolean extra preventing apps from start
                    Intent intent = new Intent(MainActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    intent.putExtra(Const.RESTORED_ACTIVITY, true);
                    startActivity(intent);
                }

                return null;
            }
        }.execute();

    }

    // Does not seem to work, though. See the comment to SystemUtils.becomeDeviceOwner()
    private void setSelfAsDeviceOwner() {
        // We set self as device owner each time so we could trace errors if device owner setup fails
        if (Utils.isDeviceOwner(this)) {
            checkAndStartLauncher();
            return;
        }

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                if (!SystemUtils.becomeDeviceOwnerByCommand(MainActivity.this)) {
                    SystemUtils.becomeDeviceOwnerByXmlFile(MainActivity.this);
                };
                return null;
            }

            @Override
            protected void onPostExecute(Void v) {
                setDefaultLauncherEarly();
            }
        }.execute();
    }

    private void startServices() {
        // Foreground apps checks are not available in a free version: services are the stubs
        if (preferences.getInt(Const.PREFERENCES_USAGE_STATISTICS, Const.PREFERENCES_OFF) == Const.PREFERENCES_ON) {
            startService(new Intent(MainActivity.this, CheckForegroundApplicationService.class));
        }
        if (preferences.getInt(Const.PREFERENCES_ACCESSIBILITY_SERVICE, Const.PREFERENCES_OFF) == Const.PREFERENCES_ON) {
            startService(new Intent(MainActivity.this, CheckForegroundAppAccessibilityService.class));
        }
        startService(new Intent(MainActivity.this, StatusControlService.class));

        // Moved to onResume!
        // https://stackoverflow.com/questions/51863600/java-lang-illegalstateexception-not-allowed-to-start-service-intent-from-activ
        startService(new Intent(MainActivity.this, PluginApiService.class));

        // Send pending logs to server
        RemoteLogger.sendLogsToServer(MainActivity.this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST) {
            if (Utils.isDeviceOwner(this)) {
                // Even in device owner mode, if "Ask for location" is requested by the admin,
                // let's ask permissions (so do nothing here, fall through)
                if (settingsHelper.getConfig() == null || !ServerConfig.APP_PERMISSIONS_ASK_ALL.equals(settingsHelper.getConfig().getAppPermissions()) &&
                        !ServerConfig.APP_PERMISSIONS_ASK_LOCATION.equals(settingsHelper.getConfig().getAppPermissions())) {
                    // This may be called on Android 10, not sure why; just continue the flow
                    Log.i(Const.LOG_TAG, "Called onRequestPermissionsResult: permissions=" + Arrays.toString(permissions) +
                            ", grantResults=" + Arrays.toString(grantResults));
                    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
                    return;
                }
            }

            boolean locationDisabled = false;
            for (int n = 0; n < permissions.length; n++) {
                if (permissions[n].equals(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    if (grantResults[n] != PackageManager.PERMISSION_GRANTED) {
                        // The user didn't allow to determine location, this is not critical, just ignore it
                        preferences.edit().putInt(Const.PREFERENCES_DISABLE_LOCATION, Const.PREFERENCES_ON).commit();
                        locationDisabled = true;
                    }
                }
            }

            boolean requestPermissions = false;
            for (int n = 0; n < permissions.length; n++) {
                if (grantResults[n] != PackageManager.PERMISSION_GRANTED) {
                    if (permissions[n].equals(Manifest.permission.ACCESS_BACKGROUND_LOCATION) &&
                            (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || locationDisabled)) {
                        // Background location is not available on Android 9 and below
                        // Also we don't need to grant background location permission if we don't grant location at all
                        continue;
                    }

                    if (permissions[n].equals(Manifest.permission.ACCESS_FINE_LOCATION) &&
                            locationDisabled) {
                        // Skip fine location permission if user intentionally disabled it
                        continue;
                    }

                    // Let user know that he need to grant permissions
                     requestPermissions = true;
                }
            }

            if (requestPermissions) {
                createAndShowPermissionsDialog();
            }
        }
    }

    // AdminReceiver may be called later than onCreate() and onResume()
    // so the launcher setup and other methods requiring device owner permissions may fail
    // Here we wait up to 10 seconds until the app gets the device owner permissions
    private void waitForProvisioning(int attempts) {
        if (Utils.isDeviceOwner(this) || attempts <= 0) {
            setDefaultLauncherEarly();
        } else {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    waitForProvisioning(attempts - 1);
                }
            }, 1000);
        }
    }

    private void setDefaultLauncherEarly() {
        ServerConfig config = SettingsHelper.getInstance(this).getConfig();
        if (BuildConfig.SET_DEFAULT_LAUNCHER_EARLY && config == null && Utils.isDeviceOwner(this)) {
            // At first start, temporarily set Headwind MDM as a default launcher
            // to prevent the user from clicking Home to stop running Headwind MDM
            String defaultLauncher = Utils.getDefaultLauncher(this);

            // As per the documentation, setting the default preferred activity should not be done on the main thread
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... voids) {
                    if (!getPackageName().equalsIgnoreCase(defaultLauncher)) {
                        Utils.setDefaultLauncher(MainActivity.this);
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(Void v) {
                    checkAndStartLauncher();
                }
            }.execute();
            return;
        }
        checkAndStartLauncher();
    }

    private void checkAndStartLauncher() {

        boolean deviceOwner = Utils.isDeviceOwner(this);
        preferences.edit().putInt(Const.PREFERENCES_DEVICE_OWNER, deviceOwner ?
            Const.PREFERENCES_ON : Const.PREFERENCES_OFF).commit();

        int miuiPermissionMode = preferences.getInt(Const.PREFERENCES_MIUI_PERMISSIONS, -1);
        if (miuiPermissionMode == -1) {
            preferences.
                    edit().
                    putInt( Const.PREFERENCES_MIUI_PERMISSIONS, Const.PREFERENCES_ON ).
                    commit();
            if (checkMiuiPermissions(Const.MIUI_PERMISSIONS)) {
                // Permissions dialog opened, break the flow!
                return;
            }
        }

        int miuiDeveloperMode = preferences.getInt(Const.PREFERENCES_MIUI_DEVELOPER, -1);
        if (miuiDeveloperMode == -1) {
            preferences.
                    edit().
                    putInt( Const.PREFERENCES_MIUI_DEVELOPER, Const.PREFERENCES_ON ).
                    commit();
            if (checkMiuiPermissions(Const.MIUI_DEVELOPER)) {
                // Permissions dialog opened, break the flow!
                return;
            }
        }

        int miuiOptimizationMode = preferences.getInt(Const.PREFERENCES_MIUI_OPTIMIZATION, -1);
        if (miuiOptimizationMode == -1) {
            preferences.
                    edit().
                    putInt( Const.PREFERENCES_MIUI_OPTIMIZATION, Const.PREFERENCES_ON ).
                    commit();
            if (checkMiuiPermissions(Const.MIUI_OPTIMIZATION)) {
                // Permissions dialog opened, break the flow!
                return;
            }
        }

        int unknownSourceMode = preferences.getInt(Const.PREFERENCES_UNKNOWN_SOURCES, -1);
        if (!deviceOwner && unknownSourceMode == -1) {
            if (checkUnknownSources()) {
                preferences.
                        edit().
                        putInt( Const.PREFERENCES_UNKNOWN_SOURCES, Const.PREFERENCES_ON ).
                        commit();
            } else {
                return;
            }
        }

        int administratorMode = preferences.getInt( Const.PREFERENCES_ADMINISTRATOR, - 1 );
//        RemoteLogger.log(this, Const.LOG_DEBUG, "Saved device admin state: " + administratorMode);
        if ( administratorMode == -1 ) {
            if (checkAdminMode()) {
                RemoteLogger.log(this, Const.LOG_DEBUG, "Saving device admin state as 1 (TRUE)");
                preferences.
                        edit().
                        putInt( Const.PREFERENCES_ADMINISTRATOR, Const.PREFERENCES_ON ).
                        commit();
            } else {
                return;
            }
        }

        int overlayMode = preferences.getInt( Const.PREFERENCES_OVERLAY, - 1 );
        if (ProUtils.isPro() && overlayMode == -1 && needRequestOverlay()) {
            if ( checkAlarmWindow() ) {
                preferences.
                        edit().
                        putInt( Const.PREFERENCES_OVERLAY, Const.PREFERENCES_ON ).
                        commit();
            } else {
                return;
            }
        }

        int usageStatisticsMode = preferences.getInt( Const.PREFERENCES_USAGE_STATISTICS, - 1 );
        if (ProUtils.isPro() && usageStatisticsMode == -1 && needRequestUsageStats()) {
            if ( checkUsageStatistics() ) {
                preferences.
                        edit().
                        putInt( Const.PREFERENCES_USAGE_STATISTICS, Const.PREFERENCES_ON ).
                        commit();

                // If usage statistics is on, there's no need to turn on accessibility services
                preferences.
                        edit().
                        putInt( Const.PREFERENCES_ACCESSIBILITY_SERVICE, Const.PREFERENCES_OFF ).
                        commit();
            } else {
                return;
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            int manageStorageMode = preferences.getInt(Const.PREFERENCES_MANAGE_STORAGE, -1);
            if (manageStorageMode == -1) {
                if (checkManageStorage()) {
                    preferences.
                            edit().
                            putInt(Const.PREFERENCES_MANAGE_STORAGE, Const.PREFERENCES_ON).
                            commit();
                } else {
                    return;
                }
            }
        }

        int accessibilityService = preferences.getInt( Const.PREFERENCES_ACCESSIBILITY_SERVICE, - 1 );
        // Check the same condition as for usage stats here
        // because accessibility is used as a secondary condition when usage stats is not available
        if (ProUtils.isPro() && accessibilityService == -1 && needRequestUsageStats()) {
            if ( checkAccessibilityService() ) {
                preferences.
                        edit().
                        putInt( Const.PREFERENCES_ACCESSIBILITY_SERVICE, Const.PREFERENCES_ON ).
                        commit();
            } else {
                createAndShowAccessibilityServiceDialog();
                return;
            }
        }

        if (settingsHelper != null && settingsHelper.getConfig() != null && settingsHelper.getConfig().getLockStatusBar() != null && settingsHelper.getConfig().getLockStatusBar()) {
            // If the admin requested status bar lock (may be required for some early Samsung devices), block the status bar and right bar (App list) expansion
            statusBarView = ProUtils.preventStatusBarExpansion(this);
            rightToolbarView = ProUtils.preventApplicationsList(this);
        }

        createApplicationNotAllowedScreen();
        createLockScreen();
        startLauncher();
    }

    private void createAndShowPermissionsDialog() {
        dismissDialog(permissionsDialog);
        permissionsDialog = new Dialog( this );
        dialogPermissionsBinding = DataBindingUtil.inflate(
                LayoutInflater.from( this ),
                R.layout.dialog_permissions,
                null,
                false );
        permissionsDialog.setCancelable( false );
        permissionsDialog.requestWindowFeature( Window.FEATURE_NO_TITLE );

        permissionsDialog.setContentView( dialogPermissionsBinding.getRoot() );
        permissionsDialog.show();
    }

    public void permissionsRetryClicked(View view) {
        dismissDialog(permissionsDialog);
        startLauncher();
    }

    public void permissionsExitClicked(View view) {
        dismissDialog(permissionsDialog);
        finish();
    }

    private void createAndShowAccessibilityServiceDialog() {
        dismissDialog(accessibilityServiceDialog);
        accessibilityServiceDialog = new Dialog( this );
        dialogAccessibilityServiceBinding = DataBindingUtil.inflate(
                LayoutInflater.from( this ),
                R.layout.dialog_accessibility_service,
                null,
                false );
        dialogAccessibilityServiceBinding.hint.setText(
                getString(R.string.dialog_accessibility_service_message, getString(R.string.white_app_name)));
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
                putInt( Const.PREFERENCES_ACCESSIBILITY_SERVICE, Const.PREFERENCES_OFF ).
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
        if (ProUtils.kioskModeRequired(this) && !getPackageName().equals(settingsHelper.getConfig().getMainApp())) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    !Settings.canDrawOverlays( this ) &&
                    !BuildConfig.ENABLE_KIOSK_WITHOUT_OVERLAYS) {
                RemoteLogger.log(this, Const.LOG_WARN, "Kiosk mode disabled: no permission to draw over other windows.");
                Toast.makeText(this, getString(R.string.kiosk_mode_requires_overlays,
                        getString(R.string.white_app_name)), Toast.LENGTH_LONG).show();
                config.setKioskMode(false);
                settingsHelper.updateConfig(config);
                createLauncherButtons();
                return;
            }
            View kioskUnlockButton = null;
            if (config.isKioskExit()) {     // Should be true by default, but false on older web panel versions
                kioskUnlockButton = ProUtils.createKioskUnlockButton(this);
            }
            if (kioskUnlockButton != null) {
                kioskUnlockButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        kioskUnlockCounter++;
                        if (kioskUnlockCounter >= Const.KIOSK_UNLOCK_CLICK_COUNT) {
                            // We are in the main app: let's open launcher activity
                            interruptResumeFlow = true;
                            Intent restoreLauncherIntent = new Intent(MainActivity.this, MainActivity.class);
                            restoreLauncherIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                            startActivity(restoreLauncherIntent);
                            createAndShowEnterPasswordDialog();
                            kioskUnlockCounter = 0;
                        }
                    }
                });
            }
        } else {
            createLauncherButtons();
        }
    }

    private void startLauncher() {
        createButtons();

        if (configUpdater.isPendingAppInstall()) {
            // Here we go after completing the user confirmed app installation
            configUpdater.repeatDownloadApps();
        } else if ( !checkPermissions(true)) {
            // Permissions are requested inside checkPermissions, so do nothing here
            Log.i(Const.LOG_TAG, "startLauncher: requesting permissions");
        } else if (!settingsHelper.isBaseUrlSet() && BuildConfig.REQUEST_SERVER_URL) {
            // For common public version, here's an option to change the server
            createAndShowServerDialog(false, settingsHelper.getBaseUrl(), settingsHelper.getServerProject());
        } else if ( settingsHelper.getDeviceId().length() == 0 ) {
            Log.d(Const.LOG_TAG, "Device ID is empty");
            Utils.autoGrantPhonePermission(this);
            if (!SystemUtils.autoSetDeviceId(this)) {
                createAndShowEnterDeviceIdDialog(false, null);
            } else {
                // Retry after automatical setting of device ID
                // We shouldn't get looping here because autoSetDeviceId cannot return true if deviceId.length == 0
                startLauncher();
            }
        } else if ( ! configInitialized ) {
            Log.i(Const.LOG_TAG, "Updating configuration in startLauncher()");
            boolean userInteraction = true;
            boolean integratedProvisioningFlow = settingsHelper.isIntegratedProvisioningFlow();
            if (integratedProvisioningFlow) {
                // InitialSetupActivity just started and this is the first start after
                // the admin integrated provisioning flow, we need to show the process of loading apps
                // Notice the config is not null because it's preloaded in InitialSetupActivity
                settingsHelper.setIntegratedProvisioningFlow(false);
            }
            if (settingsHelper.getConfig() != null && !integratedProvisioningFlow) {
                // If it's not the first start, let's update in the background, show the content first!
                showContent(settingsHelper.getConfig());
                userInteraction = false;
            }
            updateConfig(userInteraction);
        } else {
            showContent(settingsHelper.getConfig());
        }
    }

    private boolean checkAdminMode() {
        if (!Utils.checkAdminMode(this)) {
            createAndShowAdministratorDialog();
            return false;
        }
        return true;
    }

    private boolean needRequestUsageStats() {
        ServerConfig config = SettingsHelper.getInstance(this).getConfig();
        if (config == null) {
            // The app hasn't been properly provisioned because
            // config should be initialized in a setup activity.
            // So we request permissions anyway.
            return true;
        }
        // Usage stats is only required to detect unwanted apps
        // when permissive mode is off and kiosk mode is also off
        return !config.isPermissive() && !config.isKioskMode();
    }

    // Access to usage statistics is required in the Pro-version only
    private boolean checkUsageStatistics() {
        if (!ProUtils.checkUsageStatistics(this)) {
            if (SystemUtils.autoSetUsageStatsPermission(this, getPackageName())) {
                // Permission auto granted
                return true;
            }
            createAndShowHistorySettingsDialog();
            return false;
        }
        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private boolean checkManageStorage() {
        if (!Environment.isExternalStorageManager()) {
            if (SystemUtils.autoSetStoragePermission(this, getPackageName())) {
                // Permission auto granted
                return true;
            }
            createAndShowManageStorageDialog();
            return false;
        }
        return true;
    }

    private boolean needRequestOverlay() {
        ServerConfig config = SettingsHelper.getInstance(this).getConfig();
        if (config == null) {
            // The app hasn't been properly provisioned because
            // config should be initialized in a setup activity.
            // So we request permissions anyway.
            return true;
        }
        if (config.isKioskMode() && config.isKioskExit()) {
            // We need to draw the kiosk exit button
            return true;
        }
        if (!config.isKioskMode() && !config.isPermissive()) {
            // Overlay window is required to block unwanted apps
            return true;
        }
        return false;
    }

    private boolean checkAlarmWindow() {
        if (ProUtils.isPro() && !Utils.canDrawOverlays(this)) {
            if (SystemUtils.autoSetOverlayPermission(this, getPackageName())) {
                // Permission auto granted
                return true;
            }
            createAndShowOverlaySettingsDialog();
            return false;
        } else {
            return true;
        }
    }

    private boolean checkMiuiPermissions(int screen) {
        // Permissions to open popup from background first appears in MIUI 11 (Android 9)
        // Also a workaround against https://qa.h-mdm.com/3119/
        if (Utils.isMiui(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(Const.ACTION_ENABLE_SETTINGS));
            createAndShowMiuiPermissionsDialog(screen);
            // It is not known how to check this setting programmatically, so return true
            return true;
        }
        return false;
    }

    private boolean checkUnknownSources() {
        if ( !Utils.canInstallPackages(this) ) {
            createAndShowUnknownSourcesDialog();
            return false;
        } else {
            return true;
        }
    }

    private WindowManager.LayoutParams overlayLockScreenParams() {
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
        layoutParams.type = Utils.OverlayWindowType();
        layoutParams.gravity = Gravity.RIGHT;
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

        layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        layoutParams.format = PixelFormat.TRANSPARENT;

        return layoutParams;
    }

    private void createApplicationNotAllowedScreen() {
        if ( applicationNotAllowed != null ) {
            return;
        }
        WindowManager manager = ((WindowManager)getApplicationContext().getSystemService(Context.WINDOW_SERVICE));

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
        final TextView tvPackageId = applicationNotAllowed.findViewById(R.id.package_id);
        tvPackageId.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("Package ID", tvPackageId.getText().toString());
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(MainActivity.this, R.string.package_id_copied, Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        applicationNotAllowed.setVisibility( View.GONE );

        try {
            manager.addView( applicationNotAllowed, overlayLockScreenParams() );
        } catch ( Exception e ) {
            // No permission to show overlays; let's try to add view to main view
            try {
                RelativeLayout root = findViewById(R.id.activity_main);
                root.addView(applicationNotAllowed);
            } catch ( Exception e1 ) {
                e1.printStackTrace();
            }
        }
    }

    private void createLockScreen() {
        if ( lockScreen != null ) {
            return;
        }

        WindowManager manager = ((WindowManager)getApplicationContext().getSystemService(Context.WINDOW_SERVICE));

        // Reuse existing "Application not allowed" screen but hide buttons
        lockScreen = LayoutInflater.from( this ).inflate( R.layout.layout_application_not_allowed, null );
        lockScreen.findViewById( R.id.layout_application_not_allowed_continue ).setVisibility(View.GONE);
        lockScreen.findViewById( R.id.layout_application_not_allowed_admin ).setVisibility(View.GONE);
        lockScreen.findViewById( R.id.package_id ).setVisibility(View.GONE);
        lockScreen.findViewById( R.id.message2 ).setVisibility(View.GONE);
        TextView textView = lockScreen.findViewById( R.id.message );
        textView.setText(getString(R.string.device_locked, SettingsHelper.getInstance(this).getDeviceId()));

        lockScreen.setVisibility( View.GONE );

        try {
            manager.addView( lockScreen, overlayLockScreenParams() );
        } catch ( Exception e ) {
            // No permission to show overlays; let's try to add view to main view
            try {
                RelativeLayout root = findViewById(R.id.activity_main);
                root.addView(lockScreen);
            } catch ( Exception e1 ) {
                e1.printStackTrace();
            }
        }
    }

    private ImageView createManageButton(int imageResource, int imageResourceBlack, int offset) {
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.addRule(RelativeLayout.CENTER_VERTICAL);
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);

        boolean dark = true;
        try {
            ServerConfig config = settingsHelper.getConfig();
            if (config.getBackgroundColor() != null) {
                int color = Color.parseColor(config.getBackgroundColor());
                dark = !Utils.isLightColor(color);
            }
        } catch (Exception e) {
        }

        int offsetRight = 0;
        if (settingsHelper != null && settingsHelper.getConfig() != null && settingsHelper.getConfig().getLockStatusBar() != null && settingsHelper.getConfig().getLockStatusBar()) {
            // If we lock the right bar, let's shift buttons to avoid overlapping
            offsetRight = getResources().getDimensionPixelOffset(R.dimen.prevent_applications_list_width);
        }

        RelativeLayout view = new RelativeLayout(this);
        // Offset is multiplied by 2 because the view is centered. Yeah I know its an Induism)
        view.setPadding(0, offset * 2, offsetRight, 0);
        view.setLayoutParams(layoutParams);

        ImageView manageButton = new ImageView( this );
        manageButton.setImageResource(dark ? imageResource : imageResourceBlack);
        view.addView(manageButton);

        try {
            RelativeLayout root = findViewById(R.id.activity_main);
            root.addView(view);
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

    // The userInteraction flag denotes whether the config has been updated from the UI or in the background
    // If this flag is set to true, network error dialog is displayed, and app update schedule is ignored
    private void updateConfig( final boolean userInteraction ) {
        needSendDeviceInfoAfterReconfigure = true;
        needRedrawContentAfterReconfigure = true;
        if (!orientationLocked && !BuildConfig.DISABLE_ORIENTATION_LOCK) {
            lockOrientation();
            orientationLocked = true;
        }
        configUpdater.updateConfig(this, this, userInteraction);
    }

    // Workaround against crash "App is in background" on Android 9: this is an Android OS bug
    // https://stackoverflow.com/questions/52013545/android-9-0-not-allowed-to-start-service-app-is-in-background-after-onresume
    private void startLocationServiceWithRetry() {
        try {
            startLocationService();
        } catch (Exception e) {
            // Android OS bug!!!
            e.printStackTrace();

            // Repeat an attempt to start service after one second
            handler.postDelayed(new Runnable() {
                public void run() {
                    try {
                        startLocationService();
                    } catch (Exception e) {
                        // Still failed, now give up!
                        e.printStackTrace();
                    }
                }
            }, 1000);
        }
    }

    private void startLocationService() {
        ServerConfig config = settingsHelper.getConfig();
        Intent intent = new Intent(this, LocationService.class);
        intent.setAction(config.getRequestUpdates() != null ? config.getRequestUpdates() : LocationService.ACTION_STOP);
        startService(intent);
    }

    @Override
    public void onConfigUpdateStart() {
        binding.setMessage( getString( R.string.main_activity_update_config ) );
    }

    @Override
    public void onConfigUpdateServerError(String errorText) {
        if ( enterDeviceIdDialog != null ) {
            enterDeviceIdDialogBinding.setError( true );
            enterDeviceIdDialog.show();
        } else {
            networkErrorDetails = errorText;
            createAndShowEnterDeviceIdDialog( true, settingsHelper.getDeviceId() );
        }
    }

    @Override
    public void onConfigUpdateNetworkError(String errorText) {
        if (ProUtils.isKioskModeRunning(this) && settingsHelper.getConfig() != null &&
                !getPackageName().equals(settingsHelper.getConfig().getMainApp())) {
            interruptResumeFlow = true;
            Intent restoreLauncherIntent = new Intent(MainActivity.this, MainActivity.class);
            restoreLauncherIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(restoreLauncherIntent);
        }
        // Do not show the reset button if the launcher is installed by scanning a QR code
        // Only show the reset button on manual setup at first start (when config is not yet loaded)
        createAndShowNetworkErrorDialog(settingsHelper.getBaseUrl(), settingsHelper.getServerProject(), errorText,
                settingsHelper.getConfig() == null && !settingsHelper.isQrProvisioning(),
                settingsHelper.getConfig() == null || (settingsHelper.getConfig() != null && settingsHelper.getConfig().isShowWifi()));
    }

    @Override
    public void onConfigLoaded() {
        applyEarlyPolicies(settingsHelper.getConfig());
    }

    @Override
    public void onPoliciesUpdated() {
        startLocationServiceWithRetry();
    }

    @Override
    public void onFileDownloading(RemoteFile remoteFile) {
        handler.post( new Runnable() {
            @Override
            public void run() {
                binding.setMessage(getString(R.string.main_file_downloading) + " " + remoteFile.getPath());
                binding.setDownloading( true );
            }
        } );
    }

    @Override
    public void onDownloadProgress(final int progress, final long total, final long current) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                binding.progress.setMax(100);
                binding.progress.setProgress(progress);

                binding.setFileLength(total);
                binding.setDownloadedLength(current);
            }
        });
    }

    @Override
    public void onFileDownloadError(RemoteFile remoteFile) {
        if (!ProUtils.kioskModeRequired(this) && !isContentShown()) {
            // Notify the error dialog that we're downloading a file, not an app
            downloadingFile = true;
            createAndShowFileNotDownloadedDialog(remoteFile.getUrl());
            binding.setDownloading( false );
        } else {
            // Avoid user interaction in kiosk mode, just ignore download error and keep the old version
            // Also, avoid unexpected messages when the user is seeing the desktop
            configUpdater.skipDownloadFiles();
        }
    }

    @Override
    public void onAppUpdateStart() {
        binding.setMessage( getString( R.string.main_activity_applications_update ) );
        configInitialized = true;
    }

    @Override
    public void onAppInstalling(final Application application) {
        handler.post( new Runnable() {
            @Override
            public void run() {
                binding.setMessage(getString(R.string.main_app_installing) + " " + application.getName());
                binding.setDownloading( false );
            }
        } );
    }

    @Override
    public void onAppDownloadError(Application application) {
        if (!ProUtils.kioskModeRequired(MainActivity.this) && !isContentShown()) {
            // Notify the error dialog that we're downloading an app
            downloadingFile = false;
            createAndShowFileNotDownloadedDialog(application.getName());
            binding.setDownloading( false );
        } else {
            // Avoid user interaction in kiosk mode, just ignore download error and keep the old version
            // Also, avoid unexpected messages when the user is seeing the desktop
            configUpdater.skipDownloadApps();
        }
    }

    @Override
    public void onAppInstallError(String packageName) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (!ProUtils.kioskModeRequired(MainActivity.this) && !isContentShown()) {

                    try {
                        new AlertDialog.Builder(MainActivity.this)
                                .setMessage(getString(R.string.install_error) + " " + packageName)
                                .setPositiveButton(R.string.dialog_administrator_mode_continue, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        configUpdater.repeatDownloadApps();
                                    }
                                })
                                .create()
                                .show();
                    } catch (Exception e) {
                        // Activity closed before showing a dialog, just ignore this exception
                        e.printStackTrace();
                    }
                } else {
                    // Avoid unexpected messages when the config is updated "silently"
                    // (in kiosk mode or when user is seeing the desktop
                    configUpdater.repeatDownloadApps();
                }
            }
        });
    }

    @Override
    public void onAppInstallComplete(String packageName) {

    }

    @Override
    public void onConfigUpdateComplete() {
        SharedPreferences preferences = getApplicationContext().getSharedPreferences(Const.PREFERENCES, MODE_PRIVATE);
        String deviceAdminLog = PreferenceLogger.getLogString(preferences);
        if (deviceAdminLog != null && !deviceAdminLog.equals("")) {
            RemoteLogger.log(this, Const.LOG_DEBUG, deviceAdminLog);
            PreferenceLogger.clearLogString(preferences);
        }
        Log.i(Const.LOG_TAG, "Showing content from setActions()");
        settingsHelper.refreshConfig(this);         // Avoid NPE in showContent()
        showContent(settingsHelper.getConfig());
    }

    @Override
    public void onAllAppInstallComplete() {
        Log.i(Const.LOG_TAG, "Refreshing content - new apps installed");
        settingsHelper.refreshConfig(this);         // Avoid NPE in showContent()
        handler.post(new Runnable() {
            @Override
            public void run() {
                showContent(settingsHelper.getConfig());
            }
        });
    }

    @Override
    public void onAppDownloading(final Application application) {
        handler.post( new Runnable() {
            @Override
            public void run() {
                binding.setMessage(getString(R.string.main_app_downloading) + " " + application.getName());
                binding.setDownloading(true);
            }
        } );
    }

    @Override
    public void onAppRemoving(final Application application) {
        handler.post( new Runnable() {
            @Override
            public void run() {
                binding.setMessage(getString(R.string.main_app_removing) + " " + application.getName());
                binding.setDownloading(false);
            }
        } );
    }

    private boolean applyEarlyPolicies(ServerConfig config) {
        Initializer.applyEarlyNonInteractivePolicies(this, config);
        return true;
    }

    // Network policies are applied after getting all applications
    // These are interactive policies so can't be used when in background mode
    private boolean applyLatePolicies(ServerConfig config) {
        // To delay opening the settings activity
        boolean dialogWillShow = false;

        if (config.getGps() != null) {
            LocationManager lm = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
            if (lm != null) {
                boolean enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
                if (config.getGps() && !enabled) {
                    dialogWillShow = true;
                    // System settings dialog should return result so we could re-initialize location service
                    postDelayedSystemSettingDialog(getString(R.string.message_turn_on_gps),
                            new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), REQUEST_CODE_GPS_STATE_CHANGE);

                } else if (!config.getGps() && enabled) {
                    dialogWillShow = true;
                    postDelayedSystemSettingDialog(getString(R.string.message_turn_off_gps),
                            new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), REQUEST_CODE_GPS_STATE_CHANGE);
                }
            }
        }

        if (config.getMobileData() != null) {
            ConnectivityManager cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null && !dialogWillShow) {
                try {
                    boolean enabled = Utils.isMobileDataEnabled(this);
                    //final Intent mobileDataSettingsIntent = new Intent();
                    // One more hack: open the data transport activity
                    // https://stackoverflow.com/questions/31700842/which-intent-should-open-data-usage-screen-from-settings
                    //mobileDataSettingsIntent.setComponent(new ComponentName("com.android.settings",
                    //        "com.android.settings.Settings$DataUsageSummaryActivity"));
                    //Intent mobileDataSettingsIntent = new Intent(Intent.ACTION_MAIN);
                    //mobileDataSettingsIntent.setClassName("com.android.phone", "com.android.phone.NetworkSetting");
                    Intent mobileDataSettingsIntent = new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS);
                    // Mobile data are turned on/off in the status bar! No settings (as the user can go back in settings and do something nasty)
                    if (config.getMobileData() && !enabled) {
                        postDelayedSystemSettingDialog(getString(R.string.message_turn_on_mobile_data), /*mobileDataSettingsIntent*/null);
                    } else if (!config.getMobileData() && enabled) {
                        postDelayedSystemSettingDialog(getString(R.string.message_turn_off_mobile_data), /*mobileDataSettingsIntent*/null);
                    }
                } catch (Exception e) {
                    // Some problem accessible private API
                }
            }
        }

        if (!Utils.setPasswordMode(config.getPasswordMode(), this)) {
            Intent updatePasswordIntent = new Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD);
            // Different Android versions/builds use different activities to setup password
            // So we have to enable temporary access to settings here (and only here!)
            postDelayedSystemSettingDialog(getString(R.string.message_set_password), updatePasswordIntent, null, true);
        }
        return true;
    }

    private boolean isContentShown() {
        if (binding != null) {
            return binding.getShowContent() != null && binding.getShowContent();
        }
        return false;
    }

    private void showContent(ServerConfig config ) {
        if (!applyEarlyPolicies(config)) {
            // Here we go when the settings window is opened;
            // Next time we're here after we returned from the Android settings through onResume()
            return;
        }

        applyLatePolicies(config);

        sendDeviceInfoAfterReconfigure();
        scheduleDeviceInfoSending();
        scheduleInstalledAppsRun();

        if (config.getLock() != null && config.getLock()) {
            showLockScreen();
            return;
        } else {
            hideLockScreen();
        }

        // Run default launcher option
        if (config.getRunDefaultLauncher() != null && config.getRunDefaultLauncher() &&
            !getPackageName().equals(Utils.getDefaultLauncher(this)) && !Utils.isLauncherIntent(getIntent())) {
            openDefaultLauncher();
            return;
        }

        if (orientationLocked && !BuildConfig.DISABLE_ORIENTATION_LOCK) {
            Utils.setOrientation(this, config);
            orientationLocked = false;
        }

        if (ProUtils.kioskModeRequired(this)) {
            String kioskApp = settingsHelper.getConfig().getMainApp();
            if (kioskApp != null && kioskApp.trim().length() > 0 &&
                    // If Headwind MDM itself is set as kiosk app, the kiosk mode is already turned on;
                    // So here we just proceed to drawing the content
                    (!kioskApp.equals(getPackageName()) || !ProUtils.isKioskModeRunning(this))) {
                if (ProUtils.getKioskAppIntent(kioskApp, this) != null && startKiosk(kioskApp)) {
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    return;
                } else {
                    Log.e(Const.LOG_TAG, "Kiosk mode failed, proceed with the default flow");
                }
            } else {
                if (kioskApp != null && kioskApp.equals(getPackageName()) && ProUtils.isKioskModeRunning(this)) {
                    // Here we go if the configuration is changed when launcher is in the kiosk mode
                    ProUtils.updateKioskAllowedApps(kioskApp, this, false);
                } else {
                    Log.e(Const.LOG_TAG, "Kiosk mode disabled: please setup the main app!");
                }
            }
        } else {
            if (ProUtils.isKioskModeRunning(this)) {
                // Turn off kiosk and show desktop if it is turned off in the configuration
                ProUtils.unlockKiosk(this);
                openDefaultLauncher();
            }
        }

        // TODO: Somehow binding is null here which causes a crash. Not sure why this could happen.
        if ( config.getBackgroundColor() != null ) {
            try {
                binding.activityMainContentWrapper.setBackgroundColor(Color.parseColor(config.getBackgroundColor()));
            } catch (Exception e) {
                // Invalid color
                e.printStackTrace();
                binding.activityMainContentWrapper.setBackgroundColor( getResources().getColor(R.color.defaultBackground));
            }
        } else {
            binding.activityMainContentWrapper.setBackgroundColor( getResources().getColor(R.color.defaultBackground));
        }
        updateTitle(config);

        if (mainAppListAdapter == null || needRedrawContentAfterReconfigure) {
            needRedrawContentAfterReconfigure = false;

            if ( config.getBackgroundImageUrl() != null && config.getBackgroundImageUrl().length() > 0 ) {
                if (picasso == null) {
                    // Initialize it once because otherwise it doesn't work offline
                    Picasso.Builder builder = new Picasso.Builder(this);
                    if (BuildConfig.TRUST_ANY_CERTIFICATE) {
                        builder.downloader(new OkHttp3Downloader(UnsafeOkHttpClient.getUnsafeOkHttpClient()));
                    } else {
                        // Add signature to all requests to protect against unauthorized API calls
                        // For TRUST_ANY_CERTIFICATE, we won't add signatures because it's unsafe anyway
                        // and is just a workaround to use Headwind MDM on the LAN
                        OkHttpClient clientWithSignature = new OkHttpClient.Builder()
                                .cache(new Cache(new File(getApplication().getCacheDir(), "image_cache"), 1000000L))
                                .addInterceptor(chain -> {
                                    okhttp3.Request.Builder requestBuilder = chain.request().newBuilder();
                                    String signature = InstallUtils.getRequestSignature(chain.request().url().toString());
                                    if (signature != null) {
                                        requestBuilder.addHeader("X-Request-Signature", signature);
                                    }
                                    return chain.proceed(requestBuilder.build());

                                })
                                .build();
                        builder.downloader(new OkHttp3Downloader(clientWithSignature));
                    }
                    builder.listener(new Picasso.Listener()
                    {
                        @Override
                        public void onImageLoadFailed(Picasso picasso, Uri uri, Exception exception)
                        {
                            // On fault, get the background image from the cache
                            // This is a workaround against a bug in Picasso: it doesn't display cached images by default!
                            picasso.load(config.getBackgroundImageUrl())
                                    .networkPolicy(NetworkPolicy.OFFLINE)
                                    .fit()
                                    .centerCrop()
                                    .into(binding.activityMainBackground);
                        }
                    });
                    picasso = builder.build();
                }

                picasso.load(config.getBackgroundImageUrl())
                    // fit and centerCrop is a workaround against a crash on too large images on some devices
                    .fit()
                    .centerCrop()
                    .into(binding.activityMainBackground);

            } else {
                binding.activityMainBackground.setImageDrawable(null);
            }

            Display display = getWindowManager().getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);

            int width = size.x;
            int itemWidth = getResources().getDimensionPixelSize(R.dimen.app_list_item_size);

            spanCount = (int) (width * 1.0f / itemWidth);
            mainAppListAdapter = new MainAppListAdapter(this, this, this);
            mainAppListAdapter.setSpanCount(spanCount);

            binding.activityMainContent.setLayoutManager(new GridLayoutManager(this, spanCount));
            binding.activityMainContent.setAdapter(mainAppListAdapter);
            mainAppListAdapter.notifyDataSetChanged();

            int bottomAppCount = AppShortcutManager.getInstance().getInstalledAppCount(this, true);
            if (bottomAppCount > 0) {
                bottomAppListAdapter = new BottomAppListAdapter(this, this, this);
                bottomAppListAdapter.setSpanCount(spanCount);

                binding.activityBottomLayout.setVisibility(View.VISIBLE);
                binding.activityBottomLine.setLayoutManager(new GridLayoutManager(this, bottomAppCount < spanCount ? bottomAppCount : spanCount));
                binding.activityBottomLine.setAdapter(bottomAppListAdapter);
                bottomAppListAdapter.notifyDataSetChanged();
            } else {
                bottomAppListAdapter = null;
                binding.activityBottomLayout.setVisibility(View.GONE);
            }
        }
        binding.setShowContent(true);
        // We can now sleep, uh
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    // Added an option to delay restarting the kiosk app
    // Because some apps need time to finish their work
    private boolean startKiosk(String kioskApp) {
        String kioskDelayStr = settingsHelper.getAppPreference(getPackageName(), "kiosk_restart_delay_ms");
        int kioskDelay = 0;
        try {
            if (kioskDelayStr != null) {
                kioskDelay = Integer.parseInt(kioskDelayStr);
            }
        } catch (/*NumberFormat*/Exception e) {
        }
        if (kioskDelay == 0) {
            // Standard flow: no delay as earlier
            return ProUtils.startCosuKioskMode(kioskApp, MainActivity.this, false);
        } else {
            // Delayed kiosk start
            handler.postDelayed(() -> ProUtils.startCosuKioskMode(kioskApp, MainActivity.this, false), kioskDelay);
            return true;
        }
    }

    private void showLockScreen() {
        if (lockScreen == null) {
            createLockScreen();
            if (lockScreen == null) {
                // Why cannot we create the lock screen? Give up and return
                // The locked device will show the launcher, but still cannot run any application
                return;
            }
        }
        String lockAdminMessage = settingsHelper.getConfig().getLockMessage();
        String lockMessage = getString(R.string.device_locked, SettingsHelper.getInstance(this).getDeviceId());
        if (lockAdminMessage != null) {
            lockMessage += " " + lockAdminMessage;
        }
        TextView textView = lockScreen.findViewById( R.id.message );
        textView.setText(lockMessage);
        lockScreen.setVisibility(View.VISIBLE);
    }

    private void hideLockScreen() {
        if (lockScreen != null && lockScreen.getVisibility() == View.VISIBLE) {
            lockScreen.setVisibility(View.GONE);
        }
    }

    private void notifyPolicyViolation(int cause) {
        switch (cause) {
            case Const.GPS_ON_REQUIRED:
                postDelayedSystemSettingDialog(getString(R.string.message_turn_on_gps),
                        new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), REQUEST_CODE_GPS_STATE_CHANGE);
                break;
            case Const.GPS_OFF_REQUIRED:
                postDelayedSystemSettingDialog(getString(R.string.message_turn_off_gps),
                        new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), REQUEST_CODE_GPS_STATE_CHANGE);
                break;
            case Const.MOBILE_DATA_ON_REQUIRED:
                createAndShowSystemSettingDialog(getString(R.string.message_turn_on_mobile_data), null, 0);
                break;
            case Const.MOBILE_DATA_OFF_REQUIRED:
                createAndShowSystemSettingDialog(getString(R.string.message_turn_off_mobile_data), null, 0);
                break;
        }
    }

    // Run default launcher (Headwind MDM) as if the user clicked Home button
    private void openDefaultLauncher() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    // If we updated the configuration, let's send the final state to the server
    private void sendDeviceInfoAfterReconfigure() {
        if (needSendDeviceInfoAfterReconfigure) {
            needSendDeviceInfoAfterReconfigure = false;
            SendDeviceInfoTask sendDeviceInfoTask = new SendDeviceInfoTask(this);
            DeviceInfo deviceInfo = DeviceInfoProvider.getDeviceInfo(this, true, true);
            sendDeviceInfoTask.execute(deviceInfo);
        }
    }

    private void scheduleDeviceInfoSending() {
        if (sendDeviceInfoScheduled) {
            return;
        }
        sendDeviceInfoScheduled = true;
        SendDeviceInfoWorker.scheduleDeviceInfoSending(this);
    }

    private void scheduleInstalledAppsRun() {
        List<Application> applicationsForRun = configUpdater.getApplicationsForRun();

        if (applicationsForRun.size() == 0) {
            return;
        }
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
        if (titleType != null) {
            if (titleType.equals(ServerConfig.TITLE_NONE)) {
                binding.activityMainTitle.setVisibility(View.GONE);
                return;
            }
            if (config.getTextColor() != null) {
                try {
                    binding.activityMainTitle.setTextColor(Color.parseColor(settingsHelper.getConfig().getTextColor()));
                } catch (Exception e) {
                    // Invalid color
                    e.printStackTrace();
                }
            }
            binding.activityMainTitle.setVisibility(View.VISIBLE);
            String imei = DeviceInfoProvider.getImei(this);
            if (imei == null) {
                imei = "";
            }
            String serial = DeviceInfoProvider.getSerialNumber();
            if (serial == null) {
                serial = "";
            }
            String ip = SettingsHelper.getInstance(this).getExternalIp();
            if (ip == null) {
                ip = "";
            }
            String titleText = titleType
                    .replace(ServerConfig.TITLE_DEVICE_ID, SettingsHelper.getInstance(this).getDeviceId())
                    .replace(ServerConfig.TITLE_DESCRIPTION, config.getDescription() != null ? config.getDescription() : "")
                    .replace(ServerConfig.TITLE_CUSTOM1, config.getCustom1() != null ? config.getCustom1() : "")
                    .replace(ServerConfig.TITLE_CUSTOM2, config.getCustom2() != null ? config.getCustom2() : "")
                    .replace(ServerConfig.TITLE_CUSTOM3, config.getCustom3() != null ? config.getCustom3() : "")
                    .replace(ServerConfig.TITLE_IMEI, imei)
                    .replace(ServerConfig.TITLE_SERIAL, serial)
                    .replace(ServerConfig.TITLE_EXTERNAL_IP, ip)
                    .replace("\\n", "\n");
            binding.activityMainTitle.setText(titleText);
        } else {
            binding.activityMainTitle.setVisibility(View.GONE);
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        settingsHelper.setMainActivityRunning(false);

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

        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
            unregisterReceiver(stateChangeReceiver);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        isBackground = true;

        dismissDialog(fileNotDownloadedDialog);
        dismissDialog(enterServerDialog);
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
        dismissDialog(permissionsDialog);

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
        dialogAdministratorModeBinding.hint.setText(
                getString(R.string.dialog_administrator_mode_message, getString(R.string.white_app_name)));
        administratorModeDialog.setCancelable( false );
        administratorModeDialog.requestWindowFeature( Window.FEATURE_NO_TITLE );

        administratorModeDialog.setContentView( dialogAdministratorModeBinding.getRoot() );
        administratorModeDialog.show();
    }

    public void skipAdminMode( View view ) {
        dismissDialog(administratorModeDialog);

        RemoteLogger.log(this, Const.LOG_INFO, "Manually skipped the device admin permissions setup");
        preferences.
                edit().
                putInt( Const.PREFERENCES_ADMINISTRATOR, Const.PREFERENCES_OFF ).
                commit();

        checkAndStartLauncher();
    }

    public void setAdminMode( View view ) {
        dismissDialog(administratorModeDialog);
        // Use a proxy activity because of an Android bug (see comment to AdminModeRequestActivity!)
        startActivity( new Intent( MainActivity.this, AdminModeRequestActivity.class ) );
    }

    private void createAndShowFileNotDownloadedDialog(String fileName) {
        dismissDialog(fileNotDownloadedDialog);
        fileNotDownloadedDialog = new Dialog( this );
        dialogFileDownloadingFailedBinding = DataBindingUtil.inflate(
                LayoutInflater.from( this ),
                R.layout.dialog_file_downloading_failed,
                null,
                false );
        int errorTextResource = this.downloadingFile ? R.string.main_file_downloading_error : R.string.main_app_downloading_error;
        dialogFileDownloadingFailedBinding.title.setText( getString(errorTextResource) + " " + fileName );
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
        if (downloadingFile) {
            configUpdater.repeatDownloadFiles();
        } else {
            configUpdater.repeatDownloadApps();
        }
    }

    public void confirmDownloadFailureClicked( View view ) {
        dismissDialog(fileNotDownloadedDialog);

        if (downloadingFile) {
            configUpdater.skipDownloadFiles();
        } else {
            configUpdater.skipDownloadApps();
        }
    }

    private void createAndShowHistorySettingsDialog() {
        dismissDialog(historySettingsDialog);
        historySettingsDialog = new Dialog( this );
        dialogHistorySettingsBinding = DataBindingUtil.inflate(
                LayoutInflater.from( this ),
                R.layout.dialog_history_settings,
                null,
                false );
        dialogHistorySettingsBinding.hint.setText(
                getString(R.string.dialog_history_settings_title, getString(R.string.white_app_name)));
        historySettingsDialog.setCancelable( false );
        historySettingsDialog.requestWindowFeature( Window.FEATURE_NO_TITLE );

        historySettingsDialog.setContentView( dialogHistorySettingsBinding.getRoot() );
        historySettingsDialog.show();
    }

    public void historyWithoutPermission( View view ) {
        dismissDialog(historySettingsDialog);

        preferences.
                edit().
                putInt( Const.PREFERENCES_USAGE_STATISTICS, Const.PREFERENCES_OFF ).
                commit();
        checkAndStartLauncher();
    }

    public void continueHistory( View view ) {
        dismissDialog(historySettingsDialog);

        startActivity( new Intent( Settings.ACTION_USAGE_ACCESS_SETTINGS ) );
    }

    private void createAndShowManageStorageDialog() {
        dismissDialog(manageStorageDialog);
        manageStorageDialog = new Dialog( this );
        dialogManageStorageBinding = DataBindingUtil.inflate(
                LayoutInflater.from( this ),
                R.layout.dialog_manage_storage,
                null,
                false );
        manageStorageDialog.setCancelable( false );
        manageStorageDialog.requestWindowFeature( Window.FEATURE_NO_TITLE );

        manageStorageDialog.setContentView( dialogManageStorageBinding.getRoot() );
        manageStorageDialog.show();
    }

    public void storageWithoutPermission(View view) {
        dismissDialog(manageStorageDialog);

        preferences.
                edit().
                putInt( Const.PREFERENCES_MANAGE_STORAGE, Const.PREFERENCES_OFF ).
                commit();
        checkAndStartLauncher();
    }

    public void continueStorage(View view) {
        dismissDialog(manageStorageDialog);
        try {
            Intent intent = new Intent();
            intent.setAction(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            Uri uri = Uri.fromParts("package", this.getPackageName(), null);
            intent.setData(uri);
            startActivity(intent);
        }catch (Exception e){
            Intent intent = new Intent();
            intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            startActivity(intent);
        }
    }

    private void createAndShowOverlaySettingsDialog() {
        dismissDialog(overlaySettingsDialog);
        overlaySettingsDialog = new Dialog( this );
        dialogOverlaySettingsBinding = DataBindingUtil.inflate(
                LayoutInflater.from( this ),
                R.layout.dialog_overlay_settings,
                null,
                false );
        dialogOverlaySettingsBinding.hint.setText(
                getString(R.string.dialog_overlay_settings_title, getString(R.string.white_app_name)));
        overlaySettingsDialog.setCancelable( false );
        overlaySettingsDialog.requestWindowFeature( Window.FEATURE_NO_TITLE );

        overlaySettingsDialog.setContentView( dialogOverlaySettingsBinding.getRoot() );
        overlaySettingsDialog.show();
    }

    public void overlayWithoutPermission( View view ) {
        dismissDialog(overlaySettingsDialog);

        preferences.
                edit().
                putInt( Const.PREFERENCES_OVERLAY, Const.PREFERENCES_OFF ).
                commit();
        checkAndStartLauncher();
    }

    public void continueOverlay( View view ) {
        dismissDialog(overlaySettingsDialog);

        Intent intent = new Intent( Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse( "package:" + getPackageName() ) );
        try {
            startActivityForResult(intent, 1001);
        } catch (/* ActivityNotFound*/Exception e) {
            Toast.makeText(this, R.string.overlays_not_supported, Toast.LENGTH_LONG).show();
            overlayWithoutPermission(view);
        }
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
                Log.i(Const.LOG_TAG, "saveDeviceId(): calling updateConfig()");
                updateConfig( true );
            }
        }
    }


    public void saveServerUrl( View view ) {
        if (saveServerUrlBase()) {
            ServerServiceKeeper.resetServices();
            checkAndStartLauncher();
        }
    }


    public void networkErrorRepeatClicked( View view ) {
        dismissDialog(networkErrorDialog);

        Log.i(Const.LOG_TAG, "networkErrorRepeatClicked(): calling updateConfig()");
        updateConfig( true );
    }

    public void networkErrorResetClicked( View view ) {
        dismissDialog(networkErrorDialog);

        Log.i(Const.LOG_TAG, "networkErrorResetClicked(): calling updateConfig()");
        settingsHelper.setDeviceId("");
        settingsHelper.setBaseUrl("");
        settingsHelper.setSecondaryBaseUrl("");
        settingsHelper.setServerProject("");
        createAndShowServerDialog(false, settingsHelper.getBaseUrl(), settingsHelper.getServerProject());
    }

    public void networkErrorWifiClicked( View view ) {
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(Const.ACTION_ENABLE_SETTINGS));
        if (ProUtils.kioskModeRequired(this) && ProUtils.isKioskModeRunning(this)) {
            String kioskApp = settingsHelper.getConfig().getMainApp();
            ProUtils.startCosuKioskMode(kioskApp, this, true);
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
            }
        }, 500);
    }

    public void networkErrorCancelClicked(View view) {
        dismissDialog(networkErrorDialog);

        if (configFault) {
            Log.i(Const.LOG_TAG, "networkErrorCancelClicked(): no configuration available, quit");
            Toast.makeText(this, getString(R.string.critical_server_failure,
                    getString(R.string.white_app_name)), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        Log.i(Const.LOG_TAG, "networkErrorCancelClicked()");
        if ( settingsHelper.getConfig() != null ) {
            showContent( settingsHelper.getConfig() );
            configUpdater.skipConfigLoad();
        } else {
            Log.i(Const.LOG_TAG, "networkErrorCancelClicked(): no configuration available, retrying");
            Toast.makeText(this, R.string.empty_configuration, Toast.LENGTH_LONG).show();
            configFault = true;
            updateConfig( false );
        }
    }

    public void networkErrorDetailsClicked(View view) {
        ErrorDetailsActivity.display(this, networkErrorDetails, false);
    }

    private boolean checkPermissions( boolean startSettings ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }

        // If the user didn't grant permissions, let him know and do not request until he confirms he want to retry
        if (permissionsDialog != null && permissionsDialog.isShowing()) {
            return false;
        }

        if (Utils.isDeviceOwner(this)) {
            if (settingsHelper.getConfig() != null && (ServerConfig.APP_PERMISSIONS_ASK_ALL.equals(settingsHelper.getConfig().getAppPermissions()) ||
                    ServerConfig.APP_PERMISSIONS_ASK_LOCATION.equals(settingsHelper.getConfig().getAppPermissions()))) {
                // Even in device owner mode, if "Ask for location" is requested by the admin,
                // let's ask permissions (so do nothing here, fall through)
            } else {
                // Do not request permissions if we're the device owner
                // They are added automatically
                return true;
            }
        }

        if (preferences.getInt(Const.PREFERENCES_DISABLE_LOCATION, Const.PREFERENCES_OFF) == Const.PREFERENCES_ON) {
            if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.R && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) ||
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) ||
                    checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {

                if (startSettings) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                        requestPermissions(new String[]{
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.READ_PHONE_STATE
                        }, PERMISSIONS_REQUEST);
                    } else {
                        requestPermissions(new String[]{
                                Manifest.permission.READ_PHONE_STATE
                        }, PERMISSIONS_REQUEST);
                    }
                }
                return false;
            } else {
                return true;
            }
        } else {
            return checkLocationPermissions(startSettings);
        }
    }

    // Location permissions request on Android 10 and above is rather tricky (shame on Google for their stupid logic!!!)
    // So it's implemented in a separate method
    @RequiresApi(api = Build.VERSION_CODES.M)
    private boolean checkLocationPermissions(boolean startSettings) {
        if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.R && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) ||
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) ||
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) ||
                checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {

            if (startSettings) {
                boolean activeModeLocation = false;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        activeModeLocation = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                                checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED /* &&
                                !ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)*/;
                    } catch (Exception e) {
                        // On some older models:
                        // java.lang.IllegalArgumentException
                        // Unknown permission: android.permission.ACCESS_BACKGROUND_LOCATION
                        // Update: since there's the Android version check, we should never be here!
                        e.printStackTrace();
                    }
                }

                if (activeModeLocation) {
                    // The following flow happened
                    // The user has enabled locations, but when the app prompted for the background location,
                    // the user clicked "Locations only in active mode".
                    // In this case, requestPermissions won't show dialog any more!
                    // So we need to open the general permissions dialog
                    // Let's confirm with the user once again, then display the settings sheet
                    try {
                        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(Const.ACTION_ENABLE_SETTINGS));
                        new AlertDialog.Builder(MainActivity.this)
                                .setMessage(getString(R.string.background_location, getString(R.string.white_app_name)))
                                .setPositiveButton(R.string.background_location_continue, (dialog, which) -> {
                                    startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", getPackageName(), null)));
                                })
                                .setNegativeButton(R.string.location_disable, (dialog, which) -> {
                                    preferences.edit().putInt(Const.PREFERENCES_DISABLE_LOCATION, Const.PREFERENCES_ON).commit();
                                    // Continue the main flow!
                                    startLauncher();
                                })
                                .create()
                                .show();
                    } catch (Exception e) {
                        // Activity closed before showing a dialog, just ignore this exception
                        e.printStackTrace();
                    }
                } else {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                        requestPermissions(new String[]{
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                                Manifest.permission.READ_PHONE_STATE
                        }, PERMISSIONS_REQUEST);
                    } else {
                        requestPermissions(new String[]{
                                Manifest.permission.ACCESS_FINE_LOCATION,
// This location can't be requested here: the dialog fails to show when we use SDK 30+
// https://developer.android.com/develop/sensors-and-location/location/permissions#request-location-access-runtime
//                                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                                Manifest.permission.READ_PHONE_STATE
                        }, PERMISSIONS_REQUEST);
                    }
                }
            }
            return false;
        } else {
            return true;
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
            updateConfig(false);
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
                    RemoteLogger.log(MainActivity.this, Const.LOG_INFO, "Administrator panel opened");
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            startActivity(new Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS));
        } else {
            // In Android Oreo and above, permission to install packages are set per each app
            startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
        }
    }

    private void createAndShowMiuiPermissionsDialog(int screen) {
        dismissDialog(miuiPermissionsDialog);
        miuiPermissionsDialog = new Dialog( this );
        dialogMiuiPermissionsBinding = DataBindingUtil.inflate(
                LayoutInflater.from( this ),
                R.layout.dialog_miui_permissions,
                null,
                false );
        miuiPermissionsDialog.setCancelable( false );
        miuiPermissionsDialog.requestWindowFeature( Window.FEATURE_NO_TITLE );

        switch (screen) {
            case Const.MIUI_PERMISSIONS:
                dialogMiuiPermissionsBinding.title.setText(R.string.dialog_miui_permissions_title);
                break;
            case Const.MIUI_DEVELOPER:
                dialogMiuiPermissionsBinding.title.setText(R.string.dialog_miui_developer_title);
                break;
            case Const.MIUI_OPTIMIZATION:
                dialogMiuiPermissionsBinding.title.setText(R.string.dialog_miui_optimization_title);
                break;
        }

        miuiPermissionsDialog.setContentView( dialogMiuiPermissionsBinding.getRoot() );
        miuiPermissionsDialog.show();
    }

    public void continueMiuiPermissions( View view ) {
        String titleText = dialogMiuiPermissionsBinding.title.getText().toString();
        dismissDialog(miuiPermissionsDialog);

        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(Const.ACTION_ENABLE_SETTINGS));
        Intent intent;
        if (titleText.equals(getString(R.string.dialog_miui_permissions_title))) {
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", getPackageName(), null);
            intent.setData(uri);
        } else if (titleText.equals(getString(R.string.dialog_miui_developer_title))) {
            intent = new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS);
        } else {
            // if (titleText.equals(getString(R.string.dialog_miui_optimization_title))
            intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
        }
        try {
            startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onBackPressed() {}

    @Override
    public void onAppChoose( @NonNull AppInfo resolveInfo ) {

    }

    @Override
    public boolean switchAppListAdapter(BaseAppListAdapter adapter, int direction) {
        if (adapter == mainAppListAdapter && bottomAppListAdapter != null &&
                (direction == Const.DIRECTION_RIGHT || direction == Const.DIRECTION_DOWN)) {
            bottomAppListAdapter.setFocused(true);
            return true;
        } else if (adapter == bottomAppListAdapter &&
                (direction == Const.DIRECTION_LEFT || direction == Const.DIRECTION_UP)) {
            mainAppListAdapter.setFocused(true);
            return true;
        }
        return false;
    }

    @Override
    public boolean onLongClick( View v ) {
        createAndShowEnterPasswordDialog();
        return true;

    }

    @Override
    public void onClick( View v ) {
        if (v.equals(infoView)) {
            createAndShowInfoDialog();
        } else if (v.equals(updateView)) {
            if (enterDeviceIdDialog != null && enterDeviceIdDialog.isShowing()) {
                Log.i(Const.LOG_TAG, "Occasional update request when device info is entered, ignoring!");
                return;
            }
            Log.i(Const.LOG_TAG, "updating config on request");
            binding.setShowContent(false);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            updateConfig( true );
        }
    }

    private void postDelayedSystemSettingDialog(final String message, final Intent settingsIntent) {
        postDelayedSystemSettingDialog(message, settingsIntent, null);
    }

    private void postDelayedSystemSettingDialog(final String message, final Intent settingsIntent, final Integer requestCode) {
        postDelayedSystemSettingDialog(message, settingsIntent, requestCode, false);
    }

    private void postDelayedSystemSettingDialog(final String message, final Intent settingsIntent, final Integer requestCode, final boolean forceEnableSettings) {
        if (settingsIntent != null) {
            // If settings are controlled by usage stats, safe settings are allowed, so we need to enable settings in accessibility mode only
            // Accessibility mode is only enabled when usage stats is off
            if (preferences.getInt(Const.PREFERENCES_ACCESSIBILITY_SERVICE, Const.PREFERENCES_OFF) == Const.PREFERENCES_ON || forceEnableSettings) {
                LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(Const.ACTION_ENABLE_SETTINGS));
            }
            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(Const.ACTION_STOP_CONTROL));
        }
        // Delayed start prevents the race of ENABLE_SETTINGS handle and tapping "Next" button
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                createAndShowSystemSettingDialog(message, settingsIntent, requestCode);
            }
        }, 5000);
    }

    private void createAndShowSystemSettingDialog(final String message, final Intent settingsIntent, final Integer requestCode) {
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
                if (settingsIntent == null) {
                    return;
                }
                // Enable settings once again, because the dialog may be shown more than 3 minutes
                // This is not necessary: the problem is resolved by clicking "Continue" in a popup window
                /*LocalBroadcastManager.getInstance( MainActivity.this ).sendBroadcast( new Intent( Const.ACTION_ENABLE_SETTINGS ) );
                // Open settings with a slight delay so Broadcast would certainly be handled
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        startActivity(settingsIntent);
                    }
                }, 300);*/
                try {
                    startActivityOptionalResult(settingsIntent, requestCode);
                } catch (/*ActivityNotFound*/Exception e) {
                    // Open settings by default
                    startActivityOptionalResult(new Intent(android.provider.Settings.ACTION_SETTINGS), requestCode);
                }
            }
        });

        try {
            systemSettingsDialog.show();
        } catch (Exception e) {
            // BadTokenException: activity closed before dialog is shown
            RemoteLogger.log(this, Const.LOG_WARN, "Failed to open a popup system dialog! " + e.getMessage());
            e.printStackTrace();
            systemSettingsDialog = null;
        }
    }

    private void startActivityOptionalResult(Intent intent, Integer requestCode) {
        if (requestCode != null) {
            startActivityForResult(intent, requestCode);
        } else {
            startActivity(intent);
        }
    }

    // The following algorithm of launcher restart works in EMUI:
    // Run EMUI_LAUNCHER_RESTARTER activity once and send the old version number to it.
    // The restarter application will check the launcher version each second, and restart it
    // when it is changed.
    private void startLauncherRestarter() {
        // Sending an intent before updating, otherwise the launcher may be terminated at any time
        Intent intent = getPackageManager().getLaunchIntentForPackage(Const.LAUNCHER_RESTARTER_PACKAGE_ID);
        if (intent == null) {
            Log.i("LauncherRestarter", "No restarter app, please add it in the config!");
            return;
        }
        intent.putExtra(Const.LAUNCHER_RESTARTER_OLD_VERSION, BuildConfig.VERSION_NAME);
        startActivity(intent);
        Log.i("LauncherRestarter", "Calling launcher restarter from the launcher");
    }

    // Create a new file from the template file
    // (replace DEVICE_NUMBER, IMEI, CUSTOM* by their values)
    private void createFileFromTemplate(File srcFile, File dstFile, String deviceId, ServerConfig config) throws IOException {
        // We are supposed to process only small text files
        // So here we are reading the whole file, replacing variables, and save the content
        // It is not optimal for large files - it would be better to replace in a stream (how?)
        String content = FileUtils.readFileToString(srcFile);
        content = content.replace("DEVICE_NUMBER", deviceId)
                .replace("CUSTOM1", config.getCustom1() != null ? config.getCustom1() : "")
                .replace("CUSTOM2", config.getCustom2() != null ? config.getCustom2() : "")
                .replace("CUSTOM3", config.getCustom3() != null ? config.getCustom3() : "");
        FileUtils.writeStringToFile(dstFile, content);
    }
}
