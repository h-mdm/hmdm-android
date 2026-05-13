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
import android.graphics.drawable.GradientDrawable;
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
import android.widget.Button;
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
import com.hmdm.launcher.AdminReceiver;
import com.hmdm.launcher.BuildConfig;
import com.hmdm.launcher.Const;
import com.hmdm.launcher.R;
import com.hmdm.launcher.dialer.DialerActivity;
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
import com.hmdm.launcher.receiver.ScreenOffReceiver;
import com.hmdm.launcher.server.ServerServiceKeeper;
import com.hmdm.launcher.server.UnsafeOkHttpClient;
import com.hmdm.launcher.service.LocationService;
import com.hmdm.launcher.service.PluginApiService;
import com.hmdm.launcher.service.StatusControlService;
import com.hmdm.launcher.task.GetServerConfigTask;
import com.hmdm.launcher.task.SendDeviceInfoTask;
import com.hmdm.launcher.ui.custom.StatusBarUpdater;
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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
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
    private StatusBarUpdater statusBarUpdater = new StatusBarUpdater();

    private static boolean configInitialized = false;
    private static boolean interruptResumeFlow = false;
    private static final int BOOT_DURATION_SEC = 120;
    private static final int PAUSE_BETWEEN_AUTORUNS_SEC = 5;
    private boolean sendDeviceInfoScheduled = false;
    private boolean downloadingFile = false;
    private int kioskUnlockCounter = 0;
    private boolean configFault = false;
    private boolean needSendDeviceInfoAfterReconfigure = false;
    private boolean needRedrawContentAfterReconfigure = false;
    private boolean orientationLocked = false;
    private int REQUEST_CODE_GPS_STATE_CHANGE = 1;
    private boolean isBackground;
    private ANRWatchDog anrWatchDog;
    private int lastNetworkType;
    private ConfigUpdater configUpdater = null;
    private Picasso picasso = null;

    // -------------------------------------------------------------------------
    // Lock screen flag — set true when lockScreen() is called via end call key.
    // Tells screenOnLockReceiver to show LockScreenActivity when screen wakes.
    // -------------------------------------------------------------------------
    private boolean screenWasLocked = false;

    // =========================================================================
    // Broadcast receivers
    // =========================================================================

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case Const.ACTION_UPDATE_CONFIGURATION:
                    RemoteLogger.log(context, Const.LOG_DEBUG, "Update configuration by MainActivity");
                    updateConfig(false);
                    break;
                case Const.ACTION_HIDE_SCREEN:
                    RemoteLogger.log(MainActivity.this, Const.LOG_DEBUG, "Received ACTION_HIDE_SCREEN for package: " + intent.getStringExtra(Const.PACKAGE_NAME));
                    ServerConfig serverConfig = SettingsHelper.getInstance(MainActivity.this).getConfig();
                    if (serverConfig.getLock() != null && serverConfig.getLock()) {
                        RemoteLogger.log(MainActivity.this, Const.LOG_DEBUG, "Showing lock screen due to server lock");
                        showLockScreen();
                    } else if (applicationNotAllowed != null &&
                            (!ProUtils.kioskModeRequired(MainActivity.this) || !ProUtils.isKioskAppInstalled(MainActivity.this))) {
                        RemoteLogger.log(MainActivity.this, Const.LOG_INFO, "Showing 'package not allowed' overlay for " + intent.getStringExtra(Const.PACKAGE_NAME));
                        TextView textView = (TextView) applicationNotAllowed.findViewById(R.id.package_id);
                        textView.setText(intent.getStringExtra(Const.PACKAGE_NAME));
                        applicationNotAllowed.setVisibility(View.VISIBLE);
                        applicationNotAllowed.post(() -> {
                            View button = applicationNotAllowed.findViewById(R.id.layout_application_not_allowed_continue);
                            button.requestFocus();
                        });
                        handler.postDelayed(() -> applicationNotAllowed.setVisibility(View.GONE), 20000);
                    }
                    break;
                case Const.ACTION_DISABLE_BLOCK_WINDOW:
                    if (applicationNotAllowed != null) applicationNotAllowed.setVisibility(View.GONE);
                    break;
                case Const.ACTION_EXIT:
                    finish();
                    break;
                case Const.ACTION_POLICY_VIOLATION:
                    if (isBackground) {
                        Intent restoreLauncherIntent = new Intent(context, MainActivity.class);
                        restoreLauncherIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(restoreLauncherIntent);
                    } else {
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
                    break;
                case Const.ACTION_ADMIN_PANEL:
                    openAdminPanel();
                    break;
            }
        }
    };

    private final BroadcastReceiver screenOffReceiver = new ScreenOffReceiver();

    private final BroadcastReceiver stateChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
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
            try { applyEarlyPolicies(settingsHelper.getConfig()); } catch (Exception e) {}
        }
    };

    // -------------------------------------------------------------------------
    // Screen-on receiver — launches LockScreenActivity after lockNow()
    // Only fires if screenWasLocked=true so normal screen wakes are unaffected
    // -------------------------------------------------------------------------
    private final BroadcastReceiver screenOnLockReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_ON.equals(intent.getAction()) && screenWasLocked) {
                screenWasLocked = false;
                Intent lockIntent = new Intent(MainActivity.this, LockScreenActivity.class);
                lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(lockIntent);
            }
        }
    };

    private GradientDrawable selectedManageButtonBorder = new GradientDrawable();
    private ImageView exitView;
    private long exitFirstTapTime = 0;
    private int exitTapCount = 0;
    private ImageView infoView;
    private ImageView updateView;
    private View statusBarView;
    private View rightToolbarView;
    private boolean firstStartAfterProvisioning = false;

    // =========================================================================
    // onCreate
    // =========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        Log.d(Const.LOG_TAG, "MainActivity started" + (intent != null && intent.getAction() != null ?
                ", action: " + intent.getAction() : ""));
        if (intent != null && "android.app.action.PROVISIONING_SUCCESSFUL".equalsIgnoreCase(intent.getAction())) {
            firstStartAfterProvisioning = true;
        }

        if (CrashLoopProtection.isCrashLoopDetected(this)) {
            Toast.makeText(MainActivity.this, R.string.fault_loop_detected, Toast.LENGTH_LONG).show();
            openLauncherChoiceDialog();
            return;
        }

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                e.printStackTrace();
                try { logCrash(e); } catch (Exception e1) { e1.printStackTrace(); }
                ProUtils.sendExceptionToCrashlytics(e);
                CrashLoopProtection.registerFault(MainActivity.this);
                if (!CrashLoopProtection.isCrashLoopDetected(MainActivity.this)) {
                    Intent i = getPackageManager().getLaunchIntentForPackage(Const.LAUNCHER_RESTARTER_PACKAGE_ID);
                    if (i != null) startActivity(i);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) finishAffinity();
                System.exit(0);
            }
        });

        if (BuildConfig.ANR_WATCHDOG) {
            anrWatchDog = new ANRWatchDog();
            anrWatchDog.start();
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        binding.setMessage(getString(R.string.main_start_preparations));
        binding.loading.setVisibility(View.VISIBLE);

        settingsHelper = SettingsHelper.getInstance(this);
        preferences = getSharedPreferences(Const.PREFERENCES, MODE_PRIVATE);
        configUpdater = new ConfigUpdater(this);

        if ("".equals(settingsHelper.getDeviceId()) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AdminReceiver.updateSettingsFromFile(this);
        }

        settingsHelper.setAppStartTime(System.currentTimeMillis());

        if (Utils.isDeviceOwner(this) && "".equals(settingsHelper.getImei())) {
            settingsHelper.setImei(DeviceInfoProvider.getImei(this, 0));
        }

        Initializer.init(this, () -> {

            startServicesWithRetry();
            initReceiver();

            // Connectivity / Bluetooth state changes
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
            intentFilter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
            intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                registerReceiver(stateChangeReceiver, intentFilter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(stateChangeReceiver, intentFilter);
            }

            // Screen off receiver
            IntentFilter screenOffFilter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                registerReceiver(screenOffReceiver, screenOffFilter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(screenOffReceiver, screenOffFilter);
            }

            // Screen on receiver — shows LockScreenActivity after lockNow()
            IntentFilter screenOnFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                registerReceiver(screenOnLockReceiver, screenOnFilter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(screenOnLockReceiver, screenOnFilter);
            }

            if (!getIntent().getBooleanExtra(Const.RESTORED_ACTIVITY, false)) {
                startAppsAtBoot();
            }

            // Phone button
            Button phoneButton = findViewById(R.id.launcher_phone_button);
            if (phoneButton != null) {
                phoneButton.setOnClickListener(v -> openDialer(null));
                phoneButton.setOnKeyListener((v, keyCode, event) -> {
                    if (event.getAction() == KeyEvent.ACTION_DOWN &&
                            (keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                    keyCode == KeyEvent.KEYCODE_ENTER)) {
                        openDialer(null);
                        return true;
                    }
                    return false;
                });
            }

            // Set as default dialer (once, persists)
            if (Utils.isDeviceOwner(this)) {
                setDefaultDialerApp();
            }

            settingsHelper.setMainActivityRunning(true);
        });
    }

    private void logCrash(Throwable e) throws FileNotFoundException {
        File file = new File("/storage/emulated/0/Download/hmdm_stack_trace.txt");
        if (file.exists()) file.delete();
        FileOutputStream fos = new FileOutputStream(file, false);
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(fos));
        e.printStackTrace(writer);
        writer.flush();
        writer.close();
    }

    private void reinitApp() {
        if (binding == null) {
            binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
            binding.setMessage(getString(R.string.main_start_preparations));
            binding.loading.setVisibility(View.VISIBLE);
        }
        if (settingsHelper == null) settingsHelper = SettingsHelper.getInstance(this);
        if (preferences == null) preferences = getSharedPreferences(Const.PREFERENCES, MODE_PRIVATE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_GPS_STATE_CHANGE) startLocationServiceWithRetry();
    }

    private void initReceiver() {
        IntentFilter intentFilter = new IntentFilter(Const.ACTION_UPDATE_CONFIGURATION);
        intentFilter.addAction(Const.ACTION_HIDE_SCREEN);
        intentFilter.addAction(Const.ACTION_EXIT);
        intentFilter.addAction(Const.ACTION_POLICY_VIOLATION);
        intentFilter.addAction(Const.ACTION_EXIT_KIOSK);
        intentFilter.addAction(Const.ACTION_ADMIN_PANEL);
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, intentFilter);
    }

    // =========================================================================
    // onResume
    // =========================================================================

    @Override
    protected void onResume() {
        super.onResume();
        isBackground = false;
        reinitApp();
        statusBarUpdater.startUpdating(this, binding.clock, binding.batteryState);
        startServicesWithRetry();
        if (interruptResumeFlow) { interruptResumeFlow = false; return; }
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
            setRequestedOrientation(rotation < Surface.ROTATION_180 ?
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT :
                    ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
        } else {
            setRequestedOrientation(rotation < Surface.ROTATION_180 ?
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE :
                    ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
        }
    }

    // =========================================================================
    // Key handling
    // =========================================================================

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (mainAppListAdapter != null && event.getAction() == KeyEvent.ACTION_UP) {
            if (!mainAppListAdapter.onKey(keyCode)) {
                if (bottomAppListAdapter != null) return bottomAppListAdapter.onKey(keyCode);
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENDCALL:
                lockScreen();
                return true;
            case KeyEvent.KEYCODE_CALL:
                openDialer(null);
                return true;
            case KeyEvent.KEYCODE_0: case KeyEvent.KEYCODE_1: case KeyEvent.KEYCODE_2:
            case KeyEvent.KEYCODE_3: case KeyEvent.KEYCODE_4: case KeyEvent.KEYCODE_5:
            case KeyEvent.KEYCODE_6: case KeyEvent.KEYCODE_7: case KeyEvent.KEYCODE_8:
            case KeyEvent.KEYCODE_9:
                openDialer(String.valueOf(keyCode - KeyEvent.KEYCODE_0));
                return true;
            case KeyEvent.KEYCODE_STAR:
            case KeyEvent.KEYCODE_POUND:
                openDialer(null);
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // =========================================================================
    // Dialer
    // =========================================================================

    private void openDialer(String prefillDigit) {
        Intent intent = new Intent(this, DialerActivity.class);
        if (prefillDigit != null && !prefillDigit.isEmpty()) {
            intent.putExtra("prefill_digit", prefillDigit);
        }
        startActivity(intent);
    }

    // =========================================================================
    // Lock screen — single definition
    // Sets screenWasLocked=true so screenOnLockReceiver shows LockScreenActivity
    // =========================================================================

    private void lockScreen() {
        if (Utils.isDeviceOwner(this)) {
            // Device Owner: lock instantly and silently.
            // screenOnLockReceiver will show LockScreenActivity when screen wakes.
            screenWasLocked = true;
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
            if (dpm != null) dpm.lockNow();
        } else {
            // Fallback when not Device Owner: show lock screen directly
            startActivity(new Intent(this, LockScreenActivity.class));
        }
    }

    // =========================================================================
    // Default dialer
    // =========================================================================

    private void setDefaultDialerApp() {
        try {
            android.telecom.TelecomManager tm =
                    (android.telecom.TelecomManager) getSystemService(TELECOM_SERVICE);
            if (tm == null) {
                Log.w(Const.LOG_TAG, "setDefaultDialerApp: TelecomManager unavailable");
                return;
            }
            if (getPackageName().equals(tm.getDefaultDialerPackage())) {
                Log.d(Const.LOG_TAG, "setDefaultDialerApp: already default dialer");
                return;
            }
            Log.d(Const.LOG_TAG, "setDefaultDialerApp: requesting, current=" + tm.getDefaultDialerPackage());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.app.role.RoleManager rm = getSystemService(android.app.role.RoleManager.class);
                if (rm != null &&
                        rm.isRoleAvailable(android.app.role.RoleManager.ROLE_DIALER) &&
                        !rm.isRoleHeld(android.app.role.RoleManager.ROLE_DIALER)) {
                    startActivityForResult(
                            rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_DIALER),
                            1002);
                }
            } else {
                Intent i = new Intent(android.telecom.TelecomManager.ACTION_CHANGE_DEFAULT_DIALER);
                i.putExtra(android.telecom.TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME,
                        getPackageName());
                startActivity(i);
            }
        } catch (Exception e) {
            Log.e(Const.LOG_TAG, "setDefaultDialerApp failed: " + e.getMessage());
        }
    }

    // =========================================================================
    // Service management
    // =========================================================================

    private void startServicesWithRetry() {
        try {
            startServices();
        } catch (Exception e) {
            e.printStackTrace();
            handler.postDelayed(() -> {
                try { startServices(); } catch (Exception e2) { e2.printStackTrace(); }
            }, 1000);
        }
    }

    private void startAppsAtBoot() {
        if (SystemClock.uptimeMillis() > BOOT_DURATION_SEC * 1000) return;
        final ServerConfig config = settingsHelper.getConfig();
        if (config == null || config.getApplications() == null) return;
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                boolean appStarted = false;
                for (Application application : config.getApplications()) {
                    if (application.isRunAtBoot()) {
                        try { Thread.sleep(PAUSE_BETWEEN_AUTORUNS_SEC * 1000); } catch (InterruptedException e) {}
                        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(application.getPkg());
                        if (launchIntent != null) { startActivity(launchIntent); appStarted = true; }
                    }
                }
                if (appStarted && !config.isAutostartForeground()) {
                    try { Thread.sleep(PAUSE_BETWEEN_AUTORUNS_SEC * 1000); } catch (InterruptedException e) {}
                    Intent intent = new Intent(MainActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    intent.putExtra(Const.RESTORED_ACTIVITY, true);
                    startActivity(intent);
                }
                return null;
            }
        }.execute();
    }

    private void setSelfAsDeviceOwner() {
        if (Utils.isDeviceOwner(this)) { checkAndStartLauncher(); return; }
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                if (!SystemUtils.becomeDeviceOwnerByCommand(MainActivity.this))
                    SystemUtils.becomeDeviceOwnerByXmlFile(MainActivity.this);
                return null;
            }
            @Override
            protected void onPostExecute(Void v) { setDefaultLauncherEarly(); }
        }.execute();
    }

    private void startServices() {
        if (preferences.getInt(Const.PREFERENCES_USAGE_STATISTICS, Const.PREFERENCES_OFF) == Const.PREFERENCES_ON)
            startService(new Intent(MainActivity.this, CheckForegroundApplicationService.class));
        if (BuildConfig.USE_ACCESSIBILITY &&
                preferences.getInt(Const.PREFERENCES_ACCESSIBILITY_SERVICE, Const.PREFERENCES_OFF) == Const.PREFERENCES_ON)
            startService(new Intent(MainActivity.this, CheckForegroundAppAccessibilityService.class));
        startService(new Intent(MainActivity.this, StatusControlService.class));
        startService(new Intent(MainActivity.this, PluginApiService.class));
        RemoteLogger.resetState();
        RemoteLogger.sendLogsToServer(MainActivity.this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST) {
            if (Utils.isDeviceOwner(this)) {
                if (settingsHelper.getConfig() == null ||
                        !ServerConfig.APP_PERMISSIONS_ASK_ALL.equals(settingsHelper.getConfig().getAppPermissions()) &&
                                !ServerConfig.APP_PERMISSIONS_ASK_LOCATION.equals(settingsHelper.getConfig().getAppPermissions())) {
                    Log.i(Const.LOG_TAG, "Called onRequestPermissionsResult: permissions=" + Arrays.toString(permissions) +
                            ", grantResults=" + Arrays.toString(grantResults));
                    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
                    return;
                }
            }
            boolean locationDisabled = false;
            for (int n = 0; n < permissions.length; n++) {
                if (permissions[n].equals(Manifest.permission.ACCESS_FINE_LOCATION) &&
                        grantResults[n] != PackageManager.PERMISSION_GRANTED) {
                    preferences.edit().putInt(Const.PREFERENCES_DISABLE_LOCATION, Const.PREFERENCES_ON).commit();
                    locationDisabled = true;
                }
            }
            boolean requestPermissions = false;
            for (int n = 0; n < permissions.length; n++) {
                if (grantResults[n] != PackageManager.PERMISSION_GRANTED) {
                    if (permissions[n].equals(Manifest.permission.ACCESS_BACKGROUND_LOCATION) &&
                            (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || locationDisabled)) continue;
                    if (permissions[n].equals(Manifest.permission.ACCESS_FINE_LOCATION) && locationDisabled) continue;
                    requestPermissions = true;
                }
            }
            if (requestPermissions) createAndShowPermissionsDialog();
        }
    }

    private void waitForProvisioning(int attempts) {
        if (Utils.isDeviceOwner(this) || attempts <= 0) setDefaultLauncherEarly();
        else handler.postDelayed(() -> waitForProvisioning(attempts - 1), 1000);
    }

    private void setDefaultLauncherEarly() {
        ServerConfig config = SettingsHelper.getInstance(this).getConfig();
        if (BuildConfig.SET_DEFAULT_LAUNCHER_EARLY && config == null && Utils.isDeviceOwner(this)) {
            String defaultLauncher = Utils.getDefaultLauncher(this);
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... voids) {
                    if (!getPackageName().equalsIgnoreCase(defaultLauncher)) Utils.setDefaultLauncher(MainActivity.this);
                    return null;
                }
                @Override
                protected void onPostExecute(Void v) { checkAndStartLauncher(); }
            }.execute();
            return;
        }
        checkAndStartLauncher();
    }

    private void checkAndStartLauncher() {
        boolean deviceOwner = Utils.isDeviceOwner(this);
        preferences.edit().putInt(Const.PREFERENCES_DEVICE_OWNER, deviceOwner ? Const.PREFERENCES_ON : Const.PREFERENCES_OFF).commit();

        int miuiPermissionMode = preferences.getInt(Const.PREFERENCES_MIUI_PERMISSIONS, -1);
        if (miuiPermissionMode == -1) { preferences.edit().putInt(Const.PREFERENCES_MIUI_PERMISSIONS, Const.PREFERENCES_ON).commit(); if (checkMiuiPermissions(Const.MIUI_PERMISSIONS)) return; }
        int miuiDeveloperMode = preferences.getInt(Const.PREFERENCES_MIUI_DEVELOPER, -1);
        if (miuiDeveloperMode == -1) { preferences.edit().putInt(Const.PREFERENCES_MIUI_DEVELOPER, Const.PREFERENCES_ON).commit(); if (checkMiuiPermissions(Const.MIUI_DEVELOPER)) return; }
        int miuiOptimizationMode = preferences.getInt(Const.PREFERENCES_MIUI_OPTIMIZATION, -1);
        if (miuiOptimizationMode == -1) { preferences.edit().putInt(Const.PREFERENCES_MIUI_OPTIMIZATION, Const.PREFERENCES_ON).commit(); if (checkMiuiPermissions(Const.MIUI_OPTIMIZATION)) return; }
        int unknownSourceMode = preferences.getInt(Const.PREFERENCES_UNKNOWN_SOURCES, -1);
        if (!deviceOwner && unknownSourceMode == -1) { if (checkUnknownSources()) preferences.edit().putInt(Const.PREFERENCES_UNKNOWN_SOURCES, Const.PREFERENCES_ON).commit(); else return; }
        int administratorMode = preferences.getInt(Const.PREFERENCES_ADMINISTRATOR, -1);
        if (administratorMode == -1) { if (checkAdminMode()) { RemoteLogger.log(this, Const.LOG_DEBUG, "Saving device admin state as 1 (TRUE)"); preferences.edit().putInt(Const.PREFERENCES_ADMINISTRATOR, Const.PREFERENCES_ON).commit(); } else return; }
        int overlayMode = preferences.getInt(Const.PREFERENCES_OVERLAY, -1);
        if (ProUtils.isPro() && overlayMode == -1 && needRequestOverlay()) { if (checkAlarmWindow()) preferences.edit().putInt(Const.PREFERENCES_OVERLAY, Const.PREFERENCES_ON).commit(); else return; }
        int usageStatisticsMode = preferences.getInt(Const.PREFERENCES_USAGE_STATISTICS, -1);
        if (ProUtils.isPro() && usageStatisticsMode == -1 && needRequestUsageStats()) {
            if (checkUsageStatistics()) { preferences.edit().putInt(Const.PREFERENCES_USAGE_STATISTICS, Const.PREFERENCES_ON).commit(); preferences.edit().putInt(Const.PREFERENCES_ACCESSIBILITY_SERVICE, Const.PREFERENCES_OFF).commit(); } else return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            int manageStorageMode = preferences.getInt(Const.PREFERENCES_MANAGE_STORAGE, -1);
            if (manageStorageMode == -1) { if (checkManageStorage()) preferences.edit().putInt(Const.PREFERENCES_MANAGE_STORAGE, Const.PREFERENCES_ON).commit(); else return; }
        }
        int accessibilityService = preferences.getInt(Const.PREFERENCES_ACCESSIBILITY_SERVICE, -1);
        if (ProUtils.isPro() && BuildConfig.USE_ACCESSIBILITY && accessibilityService == -1 && needRequestUsageStats()) {
            if (checkAccessibilityService()) preferences.edit().putInt(Const.PREFERENCES_ACCESSIBILITY_SERVICE, Const.PREFERENCES_ON).commit();
            else { createAndShowAccessibilityServiceDialog(); return; }
        }
        if (settingsHelper != null && settingsHelper.getConfig() != null &&
                settingsHelper.getConfig().getLockStatusBar() != null && settingsHelper.getConfig().getLockStatusBar()) {
            statusBarView = ProUtils.preventStatusBarExpansion(this);
            rightToolbarView = ProUtils.preventApplicationsList(this);
        }
        createApplicationNotAllowedScreen();
        createLockScreen();
        startLauncher();
    }

    private void createAndShowPermissionsDialog() {
        dismissDialog(permissionsDialog);
        permissionsDialog = new Dialog(this);
        dialogPermissionsBinding = DataBindingUtil.inflate(LayoutInflater.from(this), R.layout.dialog_permissions, null, false);
        permissionsDialog.setCancelable(false); permissionsDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        permissionsDialog.setContentView(dialogPermissionsBinding.getRoot()); permissionsDialog.show();
    }

    public void permissionsRetryClicked(View view) { dismissDialog(permissionsDialog); startLauncher(); }
    public void permissionsExitClicked(View view) { dismissDialog(permissionsDialog); finish(); }

    private void createAndShowAccessibilityServiceDialog() {
        dismissDialog(accessibilityServiceDialog);
        accessibilityServiceDialog = new Dialog(this);
        dialogAccessibilityServiceBinding = DataBindingUtil.inflate(LayoutInflater.from(this), R.layout.dialog_accessibility_service, null, false);
        dialogAccessibilityServiceBinding.hint.setText(getString(R.string.dialog_accessibility_service_message, getString(R.string.white_app_name)));
        accessibilityServiceDialog.setCancelable(false); accessibilityServiceDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        accessibilityServiceDialog.setContentView(dialogAccessibilityServiceBinding.getRoot()); accessibilityServiceDialog.show();
    }

    public void skipAccessibilityService(View view) {
        try { accessibilityServiceDialog.dismiss(); } catch (Exception e) { e.printStackTrace(); }
        accessibilityServiceDialog = null;
        preferences.edit().putInt(Const.PREFERENCES_ACCESSIBILITY_SERVICE, Const.PREFERENCES_OFF).commit();
        checkAndStartLauncher();
    }

    public void setAccessibilityService(View view) {
        try { accessibilityServiceDialog.dismiss(); } catch (Exception e) { e.printStackTrace(); }
        accessibilityServiceDialog = null;
        startActivityForResult(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS), 0);
    }

    private boolean checkAccessibilityService() { return ProUtils.checkAccessibilityService(this); }

    private void createLauncherButtons() { createExitButton(); createInfoButton(); createUpdateButton(); }

    private void createButtons() {
        ServerConfig config = settingsHelper.getConfig();
        if (ProUtils.kioskModeRequired(this) && !getPackageName().equals(settingsHelper.getConfig().getMainApp())) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this) && !BuildConfig.ENABLE_KIOSK_WITHOUT_OVERLAYS) {
                RemoteLogger.log(this, Const.LOG_WARN, "Kiosk mode disabled: no permission to draw over other windows.");
                Toast.makeText(this, getString(R.string.kiosk_mode_requires_overlays, getString(R.string.white_app_name)), Toast.LENGTH_LONG).show();
                config.setKioskMode(false); settingsHelper.updateConfig(config); createLauncherButtons(); return;
            }
            View kioskUnlockButton = null;
            if (config.isKioskExit()) kioskUnlockButton = ProUtils.createKioskUnlockButton(this);
            if (kioskUnlockButton != null) {
                kioskUnlockButton.setOnClickListener(v -> {
                    kioskUnlockCounter++;
                    if (kioskUnlockCounter >= Const.KIOSK_UNLOCK_CLICK_COUNT) {
                        interruptResumeFlow = true;
                        Intent restoreLauncherIntent = new Intent(MainActivity.this, MainActivity.class);
                        restoreLauncherIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(restoreLauncherIntent);
                        createAndShowEnterPasswordDialog();
                        kioskUnlockCounter = 0;
                    }
                });
            }
        } else { createLauncherButtons(); }
    }

    private void startLauncher() {
        createButtons();
        if (configUpdater.isPendingAppInstall()) { configUpdater.repeatDownloadApps(); }
        else if (!checkPermissions(true)) { Log.i(Const.LOG_TAG, "startLauncher: requesting permissions"); }
        else if (!settingsHelper.isBaseUrlSet() && BuildConfig.REQUEST_SERVER_URL) { createAndShowServerDialog(false, settingsHelper.getBaseUrl(), settingsHelper.getServerProject()); }
        else if (settingsHelper.getDeviceId().length() == 0) {
            Log.d(Const.LOG_TAG, "Device ID is empty");
            Utils.autoGrantPhonePermission(this);
            if (!SystemUtils.autoSetDeviceId(this)) createAndShowEnterDeviceIdDialog(false, null);
            else startLauncher();
        } else if (!configInitialized) {
            Log.i(Const.LOG_TAG, "Updating configuration in startLauncher()");
            boolean userInteraction = true;
            boolean integratedProvisioningFlow = settingsHelper.isIntegratedProvisioningFlow();
            if (integratedProvisioningFlow) settingsHelper.setIntegratedProvisioningFlow(false);
            if (settingsHelper.getConfig() != null && !integratedProvisioningFlow) { showContent(settingsHelper.getConfig()); userInteraction = false; }
            updateConfig(userInteraction);
        } else { showContent(settingsHelper.getConfig()); }
    }

    private boolean checkAdminMode() { if (!Utils.checkAdminMode(this)) { createAndShowAdministratorDialog(); return false; } return true; }

    private boolean needRequestUsageStats() {
        ServerConfig config = SettingsHelper.getInstance(this).getConfig();
        if (config == null) return true;
        return !config.isPermissive() && !config.isKioskMode();
    }

    private boolean checkUsageStatistics() {
        if (!ProUtils.checkUsageStatistics(this)) {
            if (SystemUtils.autoSetUsageStatsPermission(this, getPackageName()) && ProUtils.checkUsageStatistics(this)) return true;
            createAndShowHistorySettingsDialog(); return false;
        }
        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private boolean checkManageStorage() {
        if (!Environment.isExternalStorageManager()) {
            if (SystemUtils.autoSetStoragePermission(this, getPackageName()) && Environment.isExternalStorageManager()) return true;
            createAndShowManageStorageDialog(); return false;
        }
        return true;
    }

    private boolean needRequestOverlay() {
        ServerConfig config = SettingsHelper.getInstance(this).getConfig();
        if (config == null) return true;
        if (config.isKioskMode() && config.isKioskExit()) return true;
        if (!config.isKioskMode() && !config.isPermissive()) return true;
        return false;
    }

    private boolean checkAlarmWindow() {
        if (ProUtils.isPro() && !Utils.canDrawOverlays(this)) {
            if (SystemUtils.autoSetOverlayPermission(this, getPackageName()) && Utils.canDrawOverlays(this)) return true;
            createAndShowOverlaySettingsDialog(); return false;
        }
        return true;
    }

    private boolean checkMiuiPermissions(int screen) {
        if (Utils.isMiui(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(Const.ACTION_ENABLE_SETTINGS));
            createAndShowMiuiPermissionsDialog(screen); return true;
        }
        return false;
    }

    private boolean checkUnknownSources() { if (!Utils.canInstallPackages(this)) { createAndShowUnknownSourcesDialog(); return false; } return true; }

    private WindowManager.LayoutParams overlayLockScreenParams() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.type = Utils.OverlayWindowType(); lp.gravity = Gravity.RIGHT;
        lp.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        lp.height = WindowManager.LayoutParams.MATCH_PARENT; lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        lp.format = PixelFormat.TRANSPARENT;
        return lp;
    }

    private void createApplicationNotAllowedScreen() {
        if (applicationNotAllowed != null) return;
        WindowManager manager = ((WindowManager) getApplicationContext().getSystemService(Context.WINDOW_SERVICE));
        applicationNotAllowed = LayoutInflater.from(this).inflate(R.layout.layout_application_not_allowed, null);
        applicationNotAllowed.findViewById(R.id.layout_application_not_allowed_continue).setOnClickListener(v -> applicationNotAllowed.setVisibility(View.GONE));
        applicationNotAllowed.findViewById(R.id.layout_application_not_allowed_admin).setOnClickListener(v -> { applicationNotAllowed.setVisibility(View.GONE); createAndShowEnterPasswordDialog(); });
        final TextView tvPackageId = applicationNotAllowed.findViewById(R.id.package_id);
        tvPackageId.setOnClickListener(v -> {
            try {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("Package ID", tvPackageId.getText().toString()));
                Toast.makeText(MainActivity.this, R.string.package_id_copied, Toast.LENGTH_LONG).show();
            } catch (Exception e) { e.printStackTrace(); }
        });
        applicationNotAllowed.setVisibility(View.GONE);
        try { manager.addView(applicationNotAllowed, overlayLockScreenParams()); }
        catch (Exception e) { try { ((RelativeLayout) findViewById(R.id.activity_main)).addView(applicationNotAllowed); } catch (Exception e1) { e1.printStackTrace(); } }
    }

    private void createLockScreen() {
        if (lockScreen != null) return;
        WindowManager manager = ((WindowManager) getApplicationContext().getSystemService(Context.WINDOW_SERVICE));
        lockScreen = LayoutInflater.from(this).inflate(R.layout.layout_application_not_allowed, null);
        lockScreen.findViewById(R.id.layout_application_not_allowed_continue).setVisibility(View.GONE);
        lockScreen.findViewById(R.id.layout_application_not_allowed_admin).setVisibility(View.GONE);
        lockScreen.findViewById(R.id.package_id).setVisibility(View.GONE);
        lockScreen.findViewById(R.id.message2).setVisibility(View.GONE);
        ((TextView) lockScreen.findViewById(R.id.message)).setText(getString(R.string.device_locked, SettingsHelper.getInstance(this).getDeviceId()));
        lockScreen.setVisibility(View.GONE);
        try { manager.addView(lockScreen, overlayLockScreenParams()); }
        catch (Exception e) { try { ((RelativeLayout) findViewById(R.id.activity_main)).addView(lockScreen); } catch (Exception e1) { e1.printStackTrace(); } }
    }

    private boolean isDarkBackground() {
        try { ServerConfig config = settingsHelper.getConfig(); if (config.getBackgroundColor() != null) return !Utils.isLightColor(Color.parseColor(config.getBackgroundColor())); } catch (Exception e) {}
        return true;
    }

    private ImageView createManageButton(int imageResource, int imageResourceBlack, int offset) {
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.addRule(RelativeLayout.CENTER_VERTICAL); lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        int offsetRight = 0;
        if (settingsHelper != null && settingsHelper.getConfig() != null && settingsHelper.getConfig().getLockStatusBar() != null && settingsHelper.getConfig().getLockStatusBar())
            offsetRight = getResources().getDimensionPixelOffset(R.dimen.prevent_applications_list_width);
        RelativeLayout view = new RelativeLayout(this);
        view.setPadding(0, offset * 2, offsetRight, 0); view.setLayoutParams(lp);
        ImageView manageButton = new ImageView(this);
        manageButton.setImageResource(isDarkBackground() ? imageResource : imageResourceBlack);
        view.addView(manageButton);
        selectedManageButtonBorder.setColor(0);
        selectedManageButtonBorder.setStroke(2, isDarkBackground() ? 0xa0ffffff : 0xa0000000);
        manageButton.setOnFocusChangeListener((v, hasFocus) -> v.setBackground(hasFocus ? selectedManageButtonBorder : null));
        try { ((RelativeLayout) findViewById(R.id.activity_main)).addView(view); } catch (Exception e) { e.printStackTrace(); }
        return manageButton;
    }

    private void createExitButton() {
        if (exitView != null) return;
        exitView = createManageButton(R.drawable.ic_vpn_key_opaque_24dp, R.drawable.ic_vpn_key_black_24dp, 0);
        exitView.setOnClickListener(view -> {
            if (view.hasFocus()) {
                long now = System.currentTimeMillis();
                if (exitFirstTapTime < now - 3000) { exitFirstTapTime = now; exitTapCount = 1; }
                else { exitTapCount++; if (exitTapCount >= 6) { exitFirstTapTime = 0; exitTapCount = 0; createAndShowEnterPasswordDialog(); } }
            }
        });
        exitView.setOnLongClickListener(this);
    }

    private void createInfoButton() {
        if (infoView != null) return;
        infoView = createManageButton(R.drawable.ic_info_opaque_24dp, R.drawable.ic_info_black_24dp, getResources().getDimensionPixelOffset(R.dimen.info_icon_margin));
        infoView.setOnClickListener(this);
    }

    private void createUpdateButton() {
        if (updateView != null) return;
        updateView = createManageButton(R.drawable.ic_system_update_opaque_24dp, R.drawable.ic_system_update_black_24dp, (int)(2.05f * getResources().getDimensionPixelOffset(R.dimen.info_icon_margin)));
        updateView.setOnClickListener(this);
    }

    private void updateConfig(final boolean userInteraction) {
        needSendDeviceInfoAfterReconfigure = true; needRedrawContentAfterReconfigure = true;
        if (!orientationLocked && !BuildConfig.DISABLE_ORIENTATION_LOCK) { lockOrientation(); orientationLocked = true; }
        configUpdater.updateConfig(this, this, userInteraction);
    }

    private void startLocationServiceWithRetry() {
        try { startLocationService(); }
        catch (Exception e) { e.printStackTrace(); handler.postDelayed(() -> { try { startLocationService(); } catch (Exception e2) { e2.printStackTrace(); } }, 1000); }
    }

    private void startLocationService() {
        ServerConfig config = settingsHelper.getConfig();
        Intent intent = new Intent(this, LocationService.class);
        intent.setAction(config.getRequestUpdates() != null ? config.getRequestUpdates() : LocationService.ACTION_STOP);
        startService(intent);
    }

    @Override public void onConfigUpdateStart() { binding.setMessage(getString(R.string.main_activity_update_config)); }

    @Override
    public void onConfigUpdateServerError(String errorText) {
        if (enterDeviceIdDialog != null) { enterDeviceIdDialogBinding.setError(true); enterDeviceIdDialog.show(); }
        else { networkErrorDetails = errorText; createAndShowEnterDeviceIdDialog(true, settingsHelper.getDeviceId()); }
    }

    @Override
    public void onConfigUpdateNetworkError(String errorText) {
        if (ProUtils.isKioskModeRunning(this) && settingsHelper.getConfig() != null && !getPackageName().equals(settingsHelper.getConfig().getMainApp())) {
            interruptResumeFlow = true;
            Intent rl = new Intent(MainActivity.this, MainActivity.class);
            rl.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(rl);
        }
        createAndShowNetworkErrorDialog(settingsHelper.getBaseUrl(), settingsHelper.getServerProject(), errorText,
                settingsHelper.getConfig() == null && !settingsHelper.isQrProvisioning(),
                settingsHelper.getConfig() == null || settingsHelper.getConfig().isShowWifi());
    }

    @Override public void onConfigLoaded() { applyEarlyPolicies(settingsHelper.getConfig()); }
    @Override public void onPoliciesUpdated() { startLocationServiceWithRetry(); }
    @Override public void onFileDownloading(RemoteFile remoteFile) { handler.post(() -> { binding.setMessage(getString(R.string.main_file_downloading) + " " + remoteFile.getPath()); binding.setDownloading(true); }); }
    @Override public void onDownloadProgress(final int progress, final long total, final long current) { handler.post(() -> { binding.progress.setMax(100); binding.progress.setProgress(progress); binding.setFileLength(total); binding.setDownloadedLength(current); }); }

    @Override
    public void onFileDownloadError(RemoteFile remoteFile) {
        if (!ProUtils.kioskModeRequired(this) && !isContentShown()) { downloadingFile = true; createAndShowFileNotDownloadedDialog(remoteFile.getUrl()); binding.setDownloading(false); }
        else configUpdater.skipDownloadFiles();
    }

    @Override
    public void onFileInstallError(RemoteFile remoteFile) {
        if (!ProUtils.kioskModeRequired(MainActivity.this) && !isContentShown()) {
            try { new AlertDialog.Builder(MainActivity.this).setMessage(getString(R.string.file_create_error) + " " + remoteFile.getPath()).setPositiveButton(R.string.dialog_administrator_mode_continue, (d, w) -> configUpdater.skipDownloadFiles()).create().show(); }
            catch (Exception e) { e.printStackTrace(); }
        } else configUpdater.skipDownloadFiles();
    }

    @Override public void onAppUpdateStart() { binding.setMessage(getString(R.string.main_activity_applications_update)); configInitialized = true; }
    @Override public void onAppInstalling(final Application application) { handler.post(() -> { binding.setMessage(getString(R.string.main_app_installing) + " " + application.getName()); binding.setDownloading(false); }); }

    @Override
    public void onAppDownloadError(Application application) {
        if (!ProUtils.kioskModeRequired(MainActivity.this) && !isContentShown()) { downloadingFile = false; createAndShowFileNotDownloadedDialog(application.getName()); binding.setDownloading(false); }
        else configUpdater.skipDownloadApps();
    }

    @Override
    public void onAppInstallError(String packageName) {
        handler.post(() -> {
            if (!ProUtils.kioskModeRequired(MainActivity.this) && !isContentShown()) {
                try { new AlertDialog.Builder(MainActivity.this).setMessage(getString(R.string.install_error) + " " + packageName).setPositiveButton(R.string.dialog_administrator_mode_continue, (d, w) -> configUpdater.repeatDownloadApps()).create().show(); }
                catch (Exception e) { e.printStackTrace(); }
            } else configUpdater.repeatDownloadApps();
        });
    }

    @Override public void onAppInstallComplete(String packageName) {}

    @Override
    public void onConfigUpdateComplete() {
        SharedPreferences prefs = getApplicationContext().getSharedPreferences(Const.PREFERENCES, MODE_PRIVATE);
        String deviceAdminLog = PreferenceLogger.getLogString(prefs);
        if (deviceAdminLog != null && !deviceAdminLog.equals("")) { RemoteLogger.log(this, Const.LOG_DEBUG, deviceAdminLog); PreferenceLogger.clearLogString(prefs); }
        Log.i(Const.LOG_TAG, "Showing content from setActions()");
        settingsHelper.refreshConfig(this); showContent(settingsHelper.getConfig());
    }

    @Override
    public void onAllAppInstallComplete() {
        Log.i(Const.LOG_TAG, "Refreshing content - new apps installed");
        settingsHelper.refreshConfig(this); handler.post(() -> showContent(settingsHelper.getConfig()));
    }

    @Override public void onAppDownloading(final Application application) { handler.post(() -> { binding.setMessage(getString(R.string.main_app_downloading) + " " + application.getName()); binding.setDownloading(true); }); }
    @Override public void onAppRemoving(final Application application) { handler.post(() -> { binding.setMessage(getString(R.string.main_app_removing) + " " + application.getName()); binding.setDownloading(false); }); }

    private boolean applyEarlyPolicies(ServerConfig config) { Initializer.applyEarlyNonInteractivePolicies(this, config); return true; }

    private boolean applyLatePolicies(ServerConfig config) {
        boolean dialogWillShow = false;
        if (config.getGps() != null) {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (lm != null) {
                boolean enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
                if (config.getGps() && !enabled) { dialogWillShow = true; postDelayedSystemSettingDialog(getString(R.string.message_turn_on_gps), new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), REQUEST_CODE_GPS_STATE_CHANGE); }
                else if (!config.getGps() && enabled) { dialogWillShow = true; postDelayedSystemSettingDialog(getString(R.string.message_turn_off_gps), new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), REQUEST_CODE_GPS_STATE_CHANGE); }
            }
        }
        if (config.getMobileData() != null) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null && !dialogWillShow) {
                try {
                    boolean enabled = Utils.isMobileDataEnabled(this);
                    if (config.getMobileData() && !enabled) postDelayedSystemSettingDialog(getString(R.string.message_turn_on_mobile_data), null);
                    else if (!config.getMobileData() && enabled) postDelayedSystemSettingDialog(getString(R.string.message_turn_off_mobile_data), null);
                } catch (Exception e) {}
            }
        }
        if (!Utils.setPasswordMode(config.getPasswordMode(), this)) postDelayedSystemSettingDialog(getString(R.string.message_set_password), new Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD), null, true);
        return true;
    }

    private boolean isContentShown() { if (binding != null) return binding.getShowContent() != null && binding.getShowContent(); return false; }

    private void showContent(ServerConfig config) {
        if (!applyEarlyPolicies(config)) return;
        applyLatePolicies(config);
        sendDeviceInfoAfterReconfigure(); scheduleDeviceInfoSending(); scheduleInstalledAppsRun();
        if (config.getLock() != null && config.getLock()) { showLockScreen(); return; } else hideLockScreen();
        if (config.getRunDefaultLauncher() != null && config.getRunDefaultLauncher() &&
                !getPackageName().equals(Utils.getDefaultLauncher(this)) && !Utils.isLauncherIntent(getIntent())) { openDefaultLauncher(); return; }
        if (orientationLocked && !BuildConfig.DISABLE_ORIENTATION_LOCK) { Utils.setOrientation(this, config); orientationLocked = false; }
        if (ProUtils.kioskModeRequired(this)) {
            String kioskApp = settingsHelper.getConfig().getMainApp();
            if (kioskApp != null && kioskApp.trim().length() > 0 && (!kioskApp.equals(getPackageName()) || !ProUtils.isKioskModeRunning(this))) {
                if (ProUtils.getKioskAppIntent(kioskApp, this) != null && startKiosk(kioskApp)) { getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); return; }
                else Log.e(Const.LOG_TAG, "Kiosk mode failed, proceed with the default flow");
            } else {
                if (kioskApp != null && kioskApp.equals(getPackageName()) && ProUtils.isKioskModeRunning(this)) { ProUtils.updateKioskAllowedApps(kioskApp, this, false); ProUtils.updateKioskOptions(this); }
                else Log.e(Const.LOG_TAG, "Kiosk mode disabled: please setup the main app!");
            }
        } else { if (ProUtils.isKioskModeRunning(this)) { ProUtils.unlockKiosk(this); openDefaultLauncher(); } }
        if (config.getBackgroundColor() != null) {
            try { binding.activityMainContentWrapper.setBackgroundColor(Color.parseColor(config.getBackgroundColor())); }
            catch (Exception e) { e.printStackTrace(); binding.activityMainContentWrapper.setBackgroundColor(getResources().getColor(R.color.defaultBackground)); }
        } else binding.activityMainContentWrapper.setBackgroundColor(getResources().getColor(R.color.defaultBackground));
        updateTitle(config);
        statusBarUpdater.updateControlsState(config.isDisplayStatus(), isDarkBackground());
        if (mainAppListAdapter == null || needRedrawContentAfterReconfigure) {
            needRedrawContentAfterReconfigure = false;
            if (config.getBackgroundImageUrl() != null && config.getBackgroundImageUrl().length() > 0) {
                if (picasso == null) {
                    Picasso.Builder builder = new Picasso.Builder(this);
                    if (BuildConfig.TRUST_ANY_CERTIFICATE) { builder.downloader(new OkHttp3Downloader(UnsafeOkHttpClient.getUnsafeOkHttpClient())); }
                    else {
                        OkHttpClient clientWithSignature = new OkHttpClient.Builder()
                                .cache(new Cache(new File(getApplication().getCacheDir(), "image_cache"), 1000000L))
                                .addInterceptor(chain -> { okhttp3.Request.Builder rb = chain.request().newBuilder(); String sig = InstallUtils.getRequestSignature(chain.request().url().toString()); if (sig != null) rb.addHeader("X-Request-Signature", sig); return chain.proceed(rb.build()); }).build();
                        builder.downloader(new OkHttp3Downloader(clientWithSignature));
                    }
                    builder.listener((p, uri, ex) -> p.load(config.getBackgroundImageUrl()).networkPolicy(NetworkPolicy.OFFLINE).fit().centerCrop().into(binding.activityMainBackground));
                    picasso = builder.build();
                }
                picasso.load(config.getBackgroundImageUrl()).fit().centerCrop().into(binding.activityMainBackground);
            } else binding.activityMainBackground.setImageDrawable(null);
            Display display = getWindowManager().getDefaultDisplay();
            Point size = new Point(); display.getSize(size);
            spanCount = (int)(size.x * 1.0f / getResources().getDimensionPixelSize(R.dimen.app_list_item_size));
            mainAppListAdapter = new MainAppListAdapter(this, this, this);
            mainAppListAdapter.setSpanCount(spanCount);
            binding.activityMainContent.setLayoutManager(new GridLayoutManager(this, spanCount));
            binding.activityMainContent.setAdapter(mainAppListAdapter);
            mainAppListAdapter.notifyDataSetChanged();
            // Long press on background → home customization
            binding.activityMainContentWrapper.setOnLongClickListener(v -> {
                startActivityForResult(new Intent(MainActivity.this, HomeCustomizationActivity.class), 3001);
                return true;
            });
            int bottomAppCount = AppShortcutManager.getInstance().getInstalledAppCount(this, true);
            if (bottomAppCount > 0) {
                bottomAppListAdapter = new BottomAppListAdapter(this, this, this);
                bottomAppListAdapter.setSpanCount(spanCount);
                binding.activityBottomLayout.setVisibility(View.VISIBLE);
                binding.activityBottomLine.setLayoutManager(new GridLayoutManager(this, bottomAppCount < spanCount ? bottomAppCount : spanCount));
                binding.activityBottomLine.setAdapter(bottomAppListAdapter);
                bottomAppListAdapter.notifyDataSetChanged();
            } else { bottomAppListAdapter = null; binding.activityBottomLayout.setVisibility(View.GONE); }
        }
        binding.loading.setVisibility(View.GONE); binding.setShowContent(true);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private boolean startKiosk(String kioskApp) {
        String kioskDelayStr = settingsHelper.getAppPreference(getPackageName(), "kiosk_restart_delay_ms");
        int kioskDelay = 0;
        try { if (kioskDelayStr != null) kioskDelay = Integer.parseInt(kioskDelayStr); } catch (Exception e) {}
        if (kioskDelay == 0) return ProUtils.startCosuKioskMode(kioskApp, MainActivity.this, false);
        handler.postDelayed(() -> ProUtils.startCosuKioskMode(kioskApp, MainActivity.this, false), kioskDelay);
        return true;
    }

    private void showLockScreen() {
        if (lockScreen == null) { createLockScreen(); if (lockScreen == null) return; }
        String lockAdminMessage = settingsHelper.getConfig().getLockMessage();
        String lockMessage = getString(R.string.device_locked, SettingsHelper.getInstance(this).getDeviceId());
        if (lockAdminMessage != null) lockMessage += " " + lockAdminMessage;
        ((TextView) lockScreen.findViewById(R.id.message)).setText(lockMessage);
        lockScreen.setVisibility(View.VISIBLE);
    }

    private void hideLockScreen() { if (lockScreen != null && lockScreen.getVisibility() == View.VISIBLE) lockScreen.setVisibility(View.GONE); }

    private void notifyPolicyViolation(int cause) {
        switch (cause) {
            case Const.GPS_ON_REQUIRED: postDelayedSystemSettingDialog(getString(R.string.message_turn_on_gps), new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), REQUEST_CODE_GPS_STATE_CHANGE); break;
            case Const.GPS_OFF_REQUIRED: postDelayedSystemSettingDialog(getString(R.string.message_turn_off_gps), new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), REQUEST_CODE_GPS_STATE_CHANGE); break;
            case Const.MOBILE_DATA_ON_REQUIRED: createAndShowSystemSettingDialog(getString(R.string.message_turn_on_mobile_data), null, 0); break;
            case Const.MOBILE_DATA_OFF_REQUIRED: createAndShowSystemSettingDialog(getString(R.string.message_turn_off_mobile_data), null, 0); break;
        }
    }

    private void openDefaultLauncher() { Intent intent = new Intent(Intent.ACTION_MAIN); intent.addCategory(Intent.CATEGORY_HOME); intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK); startActivity(intent); }

    private void sendDeviceInfoAfterReconfigure() { if (needSendDeviceInfoAfterReconfigure) { needSendDeviceInfoAfterReconfigure = false; new SendDeviceInfoTask(this).execute(DeviceInfoProvider.getDeviceInfo(this, true, true)); } }

    private void scheduleDeviceInfoSending() { if (sendDeviceInfoScheduled) return; sendDeviceInfoScheduled = true; SendDeviceInfoWorker.scheduleDeviceInfoSending(this); }

    private void scheduleInstalledAppsRun() {
        List<Application> applicationsForRun = configUpdater.getApplicationsForRun();
        if (applicationsForRun.size() == 0) return;
        int pause = PAUSE_BETWEEN_AUTORUNS_SEC;
        while (applicationsForRun.size() > 0) {
            final Application application = applicationsForRun.get(0); applicationsForRun.remove(0);
            handler.postDelayed(() -> { Intent launchIntent = getPackageManager().getLaunchIntentForPackage(application.getPkg()); if (launchIntent != null) startActivity(launchIntent); }, pause * 1000);
            pause += PAUSE_BETWEEN_AUTORUNS_SEC;
        }
    }

    private void updateTitle(ServerConfig config) {
        String titleType = config.getTitle();
        if (titleType != null) {
            if (titleType.equals(ServerConfig.TITLE_NONE)) { binding.activityMainTitle.setVisibility(View.GONE); return; }
            if (config.getTextColor() != null && !config.getTextColor().isEmpty()) {
                try { binding.activityMainTitle.setTextColor(
                        Color.parseColor(settingsHelper.getConfig().getTextColor())); }
                catch (Exception e) { e.printStackTrace(); }
            }            binding.activityMainTitle.setVisibility(View.VISIBLE);
            String imei = DeviceInfoProvider.getImei(this); if (imei == null) imei = "";
            String serial = DeviceInfoProvider.getSerialNumber(); if (serial == null) serial = "";
            String ip = SettingsHelper.getInstance(this).getExternalIp(); if (ip == null) ip = "";
            binding.activityMainTitle.setText(titleType
                    .replace(ServerConfig.TITLE_DEVICE_ID, SettingsHelper.getInstance(this).getDeviceId())
                    .replace(ServerConfig.TITLE_DESCRIPTION, config.getDescription() != null ? config.getDescription() : "")
                    .replace(ServerConfig.TITLE_CUSTOM1, config.getCustom1() != null ? config.getCustom1() : "")
                    .replace(ServerConfig.TITLE_CUSTOM2, config.getCustom2() != null ? config.getCustom2() : "")
                    .replace(ServerConfig.TITLE_CUSTOM3, config.getCustom3() != null ? config.getCustom3() : "")
                    .replace(ServerConfig.TITLE_IMEI, imei).replace(ServerConfig.TITLE_SERIAL, serial)
                    .replace(ServerConfig.TITLE_EXTERNAL_IP, ip).replace("\\n", "\n"));
        } else binding.activityMainTitle.setVisibility(View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        settingsHelper.setMainActivityRunning(false);
        WindowManager manager = ((WindowManager) getApplicationContext().getSystemService(Context.WINDOW_SERVICE));
        if (applicationNotAllowed != null) { try { manager.removeView(applicationNotAllowed); } catch (Exception e) { e.printStackTrace(); } }
        if (statusBarView != null) { try { manager.removeView(statusBarView); } catch (Exception e) { e.printStackTrace(); } }
        if (rightToolbarView != null) { try { manager.removeView(rightToolbarView); } catch (Exception e) { e.printStackTrace(); } }
        if (exitView != null) { try { manager.removeView(exitView); } catch (Exception e) { e.printStackTrace(); } }
        if (infoView != null) { try { manager.removeView(infoView); } catch (Exception e) { e.printStackTrace(); } }
        if (updateView != null) { try { manager.removeView(updateView); } catch (Exception e) { e.printStackTrace(); } }
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
            unregisterReceiver(stateChangeReceiver);
            unregisterReceiver(screenOffReceiver);
            unregisterReceiver(screenOnLockReceiver);
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isBackground = true;
        statusBarUpdater.stopUpdating();
        dismissDialog(fileNotDownloadedDialog); dismissDialog(enterServerDialog); dismissDialog(enterDeviceIdDialog);
        dismissDialog(networkErrorDialog); dismissDialog(enterPasswordDialog); dismissDialog(historySettingsDialog);
        dismissDialog(unknownSourcesDialog); dismissDialog(overlaySettingsDialog); dismissDialog(administratorModeDialog);
        dismissDialog(deviceInfoDialog); dismissDialog(accessibilityServiceDialog); dismissDialog(systemSettingsDialog);
        dismissDialog(permissionsDialog);
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(Const.ACTION_SHOW_LAUNCHER));
    }

    private void createAndShowAdministratorDialog() {
        dismissDialog(administratorModeDialog);
        administratorModeDialog = new Dialog(this);
        dialogAdministratorModeBinding = DataBindingUtil.inflate(LayoutInflater.from(this), R.layout.dialog_administrator_mode, null, false);
        dialogAdministratorModeBinding.hint.setText(getString(R.string.dialog_administrator_mode_message, getString(R.string.white_app_name)));
        administratorModeDialog.setCancelable(false); administratorModeDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        administratorModeDialog.setContentView(dialogAdministratorModeBinding.getRoot()); administratorModeDialog.show();
    }

    public void skipAdminMode(View view) { dismissDialog(administratorModeDialog); RemoteLogger.log(this, Const.LOG_INFO, "Manually skipped the device admin permissions setup"); preferences.edit().putInt(Const.PREFERENCES_ADMINISTRATOR, Const.PREFERENCES_OFF).commit(); checkAndStartLauncher(); }
    public void setAdminMode(View view) { dismissDialog(administratorModeDialog); startActivity(new Intent(MainActivity.this, AdminModeRequestActivity.class)); }

    private void createAndShowFileNotDownloadedDialog(String fileName) {
        dismissDialog(fileNotDownloadedDialog);
        fileNotDownloadedDialog = new Dialog(this);
        dialogFileDownloadingFailedBinding = DataBindingUtil.inflate(LayoutInflater.from(this), R.layout.dialog_file_downloading_failed, null, false);
        dialogFileDownloadingFailedBinding.title.setText(getString(this.downloadingFile ? R.string.main_file_downloading_error : R.string.main_app_downloading_error) + " " + fileName);
        fileNotDownloadedDialog.setCancelable(false); fileNotDownloadedDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        fileNotDownloadedDialog.setContentView(dialogFileDownloadingFailedBinding.getRoot());
        try { fileNotDownloadedDialog.show(); } catch (Exception e) {}
    }

    public void repeatDownloadClicked(View view) { dismissDialog(fileNotDownloadedDialog); if (downloadingFile) configUpdater.repeatDownloadFiles(); else configUpdater.repeatDownloadApps(); }
    public void confirmDownloadFailureClicked(View view) { dismissDialog(fileNotDownloadedDialog); if (downloadingFile) configUpdater.skipDownloadFiles(); else configUpdater.skipDownloadApps(); }

    private void createAndShowHistorySettingsDialog() {
        dismissDialog(historySettingsDialog);
        historySettingsDialog = new Dialog(this);
        dialogHistorySettingsBinding = DataBindingUtil.inflate(LayoutInflater.from(this), R.layout.dialog_history_settings, null, false);
        dialogHistorySettingsBinding.hint.setText(getString(R.string.dialog_history_settings_title, getString(R.string.white_app_name)));
        historySettingsDialog.setCancelable(false); historySettingsDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        historySettingsDialog.setContentView(dialogHistorySettingsBinding.getRoot()); historySettingsDialog.show();
    }

    public void historyWithoutPermission(View view) { dismissDialog(historySettingsDialog); preferences.edit().putInt(Const.PREFERENCES_USAGE_STATISTICS, Const.PREFERENCES_OFF).commit(); checkAndStartLauncher(); }
    public void continueHistory(View view) { dismissDialog(historySettingsDialog); startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); }

    private void createAndShowManageStorageDialog() {
        dismissDialog(manageStorageDialog);
        manageStorageDialog = new Dialog(this);
        dialogManageStorageBinding = DataBindingUtil.inflate(LayoutInflater.from(this), R.layout.dialog_manage_storage, null, false);
        manageStorageDialog.setCancelable(false); manageStorageDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        manageStorageDialog.setContentView(dialogManageStorageBinding.getRoot()); manageStorageDialog.show();
    }

    public void storageWithoutPermission(View view) { dismissDialog(manageStorageDialog); preferences.edit().putInt(Const.PREFERENCES_MANAGE_STORAGE, Const.PREFERENCES_OFF).commit(); checkAndStartLauncher(); }

    public void continueStorage(View view) {
        dismissDialog(manageStorageDialog);
        try { startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.fromParts("package", this.getPackageName(), null))); }
        catch (Exception e) {
            try { startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)); }
            catch (Exception e1) { Toast.makeText(this, R.string.manage_storage_not_supported, Toast.LENGTH_LONG).show(); preferences.edit().putInt(Const.PREFERENCES_MANAGE_STORAGE, Const.PREFERENCES_OFF).commit(); checkAndStartLauncher(); }
        }
    }

    private void createAndShowOverlaySettingsDialog() {
        dismissDialog(overlaySettingsDialog);
        overlaySettingsDialog = new Dialog(this);
        dialogOverlaySettingsBinding = DataBindingUtil.inflate(LayoutInflater.from(this), R.layout.dialog_overlay_settings, null, false);
        dialogOverlaySettingsBinding.hint.setText(getString(R.string.dialog_overlay_settings_title, getString(R.string.white_app_name)));
        overlaySettingsDialog.setCancelable(false); overlaySettingsDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        overlaySettingsDialog.setContentView(dialogOverlaySettingsBinding.getRoot()); overlaySettingsDialog.show();
    }

    public void overlayWithoutPermission(View view) { dismissDialog(overlaySettingsDialog); preferences.edit().putInt(Const.PREFERENCES_OVERLAY, Const.PREFERENCES_OFF).commit(); checkAndStartLauncher(); }

    public void continueOverlay(View view) {
        dismissDialog(overlaySettingsDialog);
        try { startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())), 1001); }
        catch (Exception e) { Toast.makeText(this, R.string.overlays_not_supported, Toast.LENGTH_LONG).show(); overlayWithoutPermission(view); }
    }

    public void saveDeviceId(View view) {
        String deviceId = enterDeviceIdDialogBinding.deviceId.getText().toString();
        if ("".equals(deviceId)) return;
        settingsHelper.setDeviceId(deviceId); enterDeviceIdDialogBinding.setError(false); dismissDialog(enterDeviceIdDialog);
        if (checkPermissions(true)) { Log.i(Const.LOG_TAG, "saveDeviceId(): calling updateConfig()"); updateConfig(true); }
    }

    public void saveServerUrl(View view) { if (saveServerUrlBase()) { ServerServiceKeeper.resetServices(); checkAndStartLauncher(); } }
    public void networkErrorRepeatClicked(View view) { dismissDialog(networkErrorDialog); Log.i(Const.LOG_TAG, "networkErrorRepeatClicked(): calling updateConfig()"); updateConfig(true); }

    public void networkErrorResetClicked(View view) {
        dismissDialog(networkErrorDialog); Log.i(Const.LOG_TAG, "networkErrorResetClicked(): calling updateConfig()");
        settingsHelper.setDeviceId(""); settingsHelper.setBaseUrl(""); settingsHelper.setSecondaryBaseUrl(""); settingsHelper.setServerProject("");
        createAndShowServerDialog(false, settingsHelper.getBaseUrl(), settingsHelper.getServerProject());
    }

    public void networkErrorWifiClicked(View view) {
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(Const.ACTION_ENABLE_SETTINGS));
        if (ProUtils.kioskModeRequired(this) && ProUtils.isKioskModeRunning(this)) ProUtils.startCosuKioskMode(settingsHelper.getConfig().getMainApp(), this, true);
        handler.postDelayed(() -> startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)), 500);
    }

    public void networkErrorCancelClicked(View view) {
        dismissDialog(networkErrorDialog);
        if (configFault) { Log.i(Const.LOG_TAG, "networkErrorCancelClicked(): no configuration available, quit"); Toast.makeText(this, getString(R.string.critical_server_failure, getString(R.string.white_app_name)), Toast.LENGTH_LONG).show(); finish(); return; }
        Log.i(Const.LOG_TAG, "networkErrorCancelClicked()");
        if (settingsHelper.getConfig() != null) { showContent(settingsHelper.getConfig()); configUpdater.skipConfigLoad(); }
        else { Log.i(Const.LOG_TAG, "networkErrorCancelClicked(): no configuration available, retrying"); Toast.makeText(this, R.string.empty_configuration, Toast.LENGTH_LONG).show(); configFault = true; updateConfig(false); }
    }

    public void networkErrorDetailsClicked(View view) { ErrorDetailsActivity.display(this, networkErrorDetails, false); }

    private boolean checkPermissions(boolean startSettings) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        if (permissionsDialog != null && permissionsDialog.isShowing()) return false;
        if (Utils.isDeviceOwner(this)) {
            if (settingsHelper.getConfig() != null && (ServerConfig.APP_PERMISSIONS_ASK_ALL.equals(settingsHelper.getConfig().getAppPermissions()) || ServerConfig.APP_PERMISSIONS_ASK_LOCATION.equals(settingsHelper.getConfig().getAppPermissions()))) {}
            else return true;
        }
        if (preferences.getInt(Const.PREFERENCES_DISABLE_LOCATION, Const.PREFERENCES_OFF) == Const.PREFERENCES_ON) {
            if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.R && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) ||
                    (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) ||
                    checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                if (startSettings) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) requestPermissions(new String[]{ Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_PHONE_STATE }, PERMISSIONS_REQUEST);
                    else requestPermissions(new String[]{ Manifest.permission.READ_PHONE_STATE }, PERMISSIONS_REQUEST);
                }
                return false;
            }
            return true;
        }
        return checkLocationPermissions(startSettings);
    }

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
                    try { activeModeLocation = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED; } catch (Exception e) { e.printStackTrace(); }
                }
                if (activeModeLocation) {
                    try {
                        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(Const.ACTION_ENABLE_SETTINGS));
                        new AlertDialog.Builder(MainActivity.this)
                                .setMessage(getString(R.string.background_location, getString(R.string.white_app_name)))
                                .setPositiveButton(R.string.background_location_continue, (d, w) -> startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", getPackageName(), null))))
                                .setNegativeButton(R.string.location_disable, (d, w) -> { preferences.edit().putInt(Const.PREFERENCES_DISABLE_LOCATION, Const.PREFERENCES_ON).commit(); startLauncher(); })
                                .create().show();
                    } catch (Exception e) { e.printStackTrace(); }
                } else {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) requestPermissions(new String[]{ Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION, Manifest.permission.READ_PHONE_STATE }, PERMISSIONS_REQUEST);
                    else requestPermissions(new String[]{ Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_PHONE_STATE }, PERMISSIONS_REQUEST);
                }
            }
            return false;
        }
        return true;
    }

    private void createAndShowEnterPasswordDialog() {
        dismissDialog(enterPasswordDialog);
        enterPasswordDialog = new Dialog(this);
        dialogEnterPasswordBinding = DataBindingUtil.inflate(LayoutInflater.from(this), R.layout.dialog_enter_password, null, false);
        enterPasswordDialog.requestWindowFeature(Window.FEATURE_NO_TITLE); enterPasswordDialog.setCancelable(false);
        enterPasswordDialog.setContentView(dialogEnterPasswordBinding.getRoot()); dialogEnterPasswordBinding.setLoading(false);
        try { enterPasswordDialog.show(); } catch (Exception e) { Toast.makeText(getApplicationContext(), R.string.internal_error, Toast.LENGTH_LONG).show(); }
    }

    public void closeEnterPasswordDialog(View view) { dismissDialog(enterPasswordDialog); if (ProUtils.kioskModeRequired(this)) { checkAndStartLauncher(); updateConfig(false); } }

    public void checkAdministratorPassword(View view) {
        dialogEnterPasswordBinding.setLoading(true);
        GetServerConfigTask task = new GetServerConfigTask(this) {
            @Override
            protected void onPostExecute(Integer result) {
                dialogEnterPasswordBinding.setLoading(false);
                String masterPassword = CryptoHelper.getMD5String("12345678");
                if (settingsHelper.getConfig() != null && settingsHelper.getConfig().getPassword() != null) masterPassword = settingsHelper.getConfig().getPassword();
                if (CryptoHelper.getMD5String(dialogEnterPasswordBinding.password.getText().toString()).equals(masterPassword)) { dismissDialog(enterPasswordDialog); dialogEnterPasswordBinding.setError(false); openAdminPanel(); }
                else dialogEnterPasswordBinding.setError(true);
            }
        };
        task.execute();
    }

    private void openAdminPanel() {
        if (ProUtils.kioskModeRequired(MainActivity.this)) ProUtils.unlockKiosk(MainActivity.this);
        RemoteLogger.log(MainActivity.this, Const.LOG_INFO, "Administrator panel opened");
        startActivity(new Intent(MainActivity.this, AdminActivity.class));
    }

    private void createAndShowUnknownSourcesDialog() {
        dismissDialog(unknownSourcesDialog);
        unknownSourcesDialog = new Dialog(this);
        dialogUnknownSourcesBinding = DataBindingUtil.inflate(LayoutInflater.from(this), R.layout.dialog_unknown_sources, null, false);
        unknownSourcesDialog.setCancelable(false); unknownSourcesDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        unknownSourcesDialog.setContentView(dialogUnknownSourcesBinding.getRoot()); unknownSourcesDialog.show();
    }

    public void continueUnknownSources(View view) {
        dismissDialog(unknownSourcesDialog);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) startActivity(new Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS));
        else startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
    }

    private void createAndShowMiuiPermissionsDialog(int screen) {
        dismissDialog(miuiPermissionsDialog);
        miuiPermissionsDialog = new Dialog(this);
        dialogMiuiPermissionsBinding = DataBindingUtil.inflate(LayoutInflater.from(this), R.layout.dialog_miui_permissions, null, false);
        miuiPermissionsDialog.setCancelable(false); miuiPermissionsDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        switch (screen) {
            case Const.MIUI_PERMISSIONS: dialogMiuiPermissionsBinding.title.setText(R.string.dialog_miui_permissions_title); break;
            case Const.MIUI_DEVELOPER: dialogMiuiPermissionsBinding.title.setText(R.string.dialog_miui_developer_title); break;
            case Const.MIUI_OPTIMIZATION: dialogMiuiPermissionsBinding.title.setText(R.string.dialog_miui_optimization_title); break;
        }
        miuiPermissionsDialog.setContentView(dialogMiuiPermissionsBinding.getRoot()); miuiPermissionsDialog.show();
    }

    public void continueMiuiPermissions(View view) {
        String titleText = dialogMiuiPermissionsBinding.title.getText().toString();
        dismissDialog(miuiPermissionsDialog);
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(Const.ACTION_ENABLE_SETTINGS));
        Intent intent;
        if (titleText.equals(getString(R.string.dialog_miui_permissions_title))) { intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS); intent.setData(Uri.fromParts("package", getPackageName(), null)); }
        else if (titleText.equals(getString(R.string.dialog_miui_developer_title))) intent = new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS);
        else intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
        try { startActivity(intent); } catch (Exception e) { e.printStackTrace(); }
    }

    @Override public void onBackPressed() {}
    @Override public void onAppChoose(@NonNull AppInfo resolveInfo) {}

    @Override
    public boolean switchAppListAdapter(BaseAppListAdapter adapter, int direction) {
        if (adapter == mainAppListAdapter && bottomAppListAdapter != null && (direction == Const.DIRECTION_RIGHT || direction == Const.DIRECTION_DOWN)) { bottomAppListAdapter.setFocused(true); return true; }
        else if (adapter == bottomAppListAdapter && (direction == Const.DIRECTION_LEFT || direction == Const.DIRECTION_UP)) { mainAppListAdapter.setFocused(true); return true; }
        return false;
    }

    @Override public boolean onLongClick(View v) { createAndShowEnterPasswordDialog(); return true; }

    @Override
    public void onClick(View v) {
        if (v.equals(infoView)) createAndShowInfoDialog();
        else if (v.equals(updateView)) {
            if (enterDeviceIdDialog != null && enterDeviceIdDialog.isShowing()) { Log.i(Const.LOG_TAG, "Occasional update request when device info is entered, ignoring!"); return; }
            Log.i(Const.LOG_TAG, "updating config on request");
            binding.loading.setVisibility(View.VISIBLE); binding.setShowContent(false);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); updateConfig(true);
        }
    }

    private void postDelayedSystemSettingDialog(final String message, final Intent settingsIntent) { postDelayedSystemSettingDialog(message, settingsIntent, null); }
    private void postDelayedSystemSettingDialog(final String message, final Intent settingsIntent, final Integer requestCode) { postDelayedSystemSettingDialog(message, settingsIntent, requestCode, false); }

    private void postDelayedSystemSettingDialog(final String message, final Intent settingsIntent, final Integer requestCode, final boolean forceEnableSettings) {
        if (settingsIntent != null) {
            if (preferences.getInt(Const.PREFERENCES_ACCESSIBILITY_SERVICE, Const.PREFERENCES_OFF) == Const.PREFERENCES_ON || forceEnableSettings) LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(Const.ACTION_ENABLE_SETTINGS));
            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(Const.ACTION_STOP_CONTROL));
        }
        handler.postDelayed(() -> createAndShowSystemSettingDialog(message, settingsIntent, requestCode), 5000);
    }

    private void createAndShowSystemSettingDialog(final String message, final Intent settingsIntent, final Integer requestCode) {
        dismissDialog(systemSettingsDialog);
        systemSettingsDialog = new Dialog(this);
        dialogSystemSettingsBinding = DataBindingUtil.inflate(LayoutInflater.from(this), R.layout.dialog_system_settings, null, false);
        systemSettingsDialog.requestWindowFeature(Window.FEATURE_NO_TITLE); systemSettingsDialog.setCancelable(false);
        systemSettingsDialog.setContentView(dialogSystemSettingsBinding.getRoot());
        dialogSystemSettingsBinding.setMessage(message);
        systemSettingsDialog.findViewById(R.id.continueButton).setOnClickListener(v -> {
            dismissDialog(systemSettingsDialog);
            if (settingsIntent == null) return;
            try { startActivityOptionalResult(settingsIntent, requestCode); }
            catch (Exception e) { startActivityOptionalResult(new Intent(android.provider.Settings.ACTION_SETTINGS), requestCode); }
        });
        try { systemSettingsDialog.show(); }
        catch (Exception e) { RemoteLogger.log(this, Const.LOG_WARN, "Failed to open a popup system dialog! " + e.getMessage()); e.printStackTrace(); systemSettingsDialog = null; }
    }

    private void startActivityOptionalResult(Intent intent, Integer requestCode) { if (requestCode != null) startActivityForResult(intent, requestCode); else startActivity(intent); }

    private void startLauncherRestarter() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(Const.LAUNCHER_RESTARTER_PACKAGE_ID);
        if (intent == null) { Log.i("LauncherRestarter", "No restarter app, please add it in the config!"); return; }
        intent.putExtra(Const.LAUNCHER_RESTARTER_OLD_VERSION, BuildConfig.VERSION_NAME);
        startActivity(intent);
        Log.i("LauncherRestarter", "Calling launcher restarter from the launcher");
    }

    private void createFileFromTemplate(File srcFile, File dstFile, String deviceId, ServerConfig config) throws IOException {
        String content = FileUtils.readFileToString(srcFile);
        content = content.replace("DEVICE_NUMBER", deviceId)
                .replace("CUSTOM1", config.getCustom1() != null ? config.getCustom1() : "")
                .replace("CUSTOM2", config.getCustom2() != null ? config.getCustom2() : "")
                .replace("CUSTOM3", config.getCustom3() != null ? config.getCustom3() : "");
        FileUtils.writeStringToFile(dstFile, content);
    }
}