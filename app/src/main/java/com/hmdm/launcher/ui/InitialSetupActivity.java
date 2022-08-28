package com.hmdm.launcher.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.databinding.DataBindingUtil;

import com.hmdm.launcher.Const;
import com.hmdm.launcher.R;
import com.hmdm.launcher.databinding.ActivityInitialSetupBinding;
import com.hmdm.launcher.helper.ConfigUpdater;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.json.Application;
import com.hmdm.launcher.json.RemoteFile;
import com.hmdm.launcher.json.ServerConfig;
import com.hmdm.launcher.service.ServiceHelper;
import com.hmdm.launcher.util.RemoteLogger;
import com.hmdm.launcher.util.Utils;

public class InitialSetupActivity extends BaseActivity implements ConfigUpdater.UINotifier {
    private ActivityInitialSetupBinding binding;
    private ConfigUpdater configUpdater;
    private SettingsHelper settingsHelper;
    private boolean configuring = false;

    @Override
    protected void onCreate( Bundle savedInstanceState ) {
        super.onCreate(savedInstanceState);

        Log.d(Const.LOG_TAG, "Launching the initial setup activity");

        binding = DataBindingUtil.setContentView(this, R.layout.activity_initial_setup);
        binding.setMessage(getString(R.string.initializing_mdm));
        binding.setLoading(true);
    }

    @Override
    protected void onResume() {
        super.onResume();

        settingsHelper = SettingsHelper.getInstance(this);
        settingsHelper.setAppStartTime(System.currentTimeMillis());

        if (!configuring) {
            configuring = true;
            configUpdater = new ConfigUpdater();
            configUpdater.setLoadOnly(true);
            updateConfig();
        }
    }

    private void updateConfig() {
        configUpdater.updateConfig(this, this, true);
    }

    @Override
    public void onConfigUpdateStart() {
        Log.d(Const.LOG_TAG, "Initial setup activity: onConfigUpdateStart");
    }

    @Override
    public void onConfigUpdateServerError() {
        Log.d(Const.LOG_TAG, "Initial setup activity: onConfigUpdateServerError");
        displayError(getString(R.string.dialog_server_error_title, concatenateServerUrl(settingsHelper.getBaseUrl(), settingsHelper.getServerProject())));
    }

    @Override
    public void onConfigUpdateNetworkError() {
        Log.d(Const.LOG_TAG, "Initial setup activity: onConfigUpdateNetworkError");
        displayError(getString(R.string.dialog_network_error_title, concatenateServerUrl(settingsHelper.getBaseUrl(), settingsHelper.getServerProject())));
    }

    @Override
    public void onConfigLoaded() {
        // Set Headwind MDM as the default launcher if required
        final ServerConfig config = settingsHelper.getConfig();
        if (config != null) {
            // Device owner should be already granted, so we grant requested permissions early
            boolean deviceOwner = Utils.isDeviceOwner(this);
            Log.d(Const.LOG_TAG, "Device Owner: " + deviceOwner);
            getSharedPreferences( Const.PREFERENCES, MODE_PRIVATE ).edit().putInt(Const.PREFERENCES_DEVICE_OWNER, deviceOwner ?
                    Const.PREFERENCES_ON : Const.PREFERENCES_OFF).commit();
            if (deviceOwner) {
                Utils.autoGrantRequestedPermissions(this, getPackageName(), config.getAppPermissions(), true);
            }

            if (Utils.isDeviceOwner(this) &&
                    (config.getRunDefaultLauncher() == null || !config.getRunDefaultLauncher())) {
                // As per the documentation, setting the default preferred activity should not be done on the main thread
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... voids) {
                        Utils.setDefaultLauncher(InitialSetupActivity.this);
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Void v) {
                        completeConfig(settingsHelper);
                    }
                }.execute();
                return;
            } else {
                // Headwind MDM works with default system launcher
                // Run services here
                Log.d(Const.LOG_TAG, "Working in background, starting services and installing apps");
                ServiceHelper.startServices(InitialSetupActivity.this);
            }
        }
        completeConfig(settingsHelper);
    }

    private void completeConfig(final SettingsHelper settingsHelper) {
        configuring = false;
        if (settingsHelper.getConfig() != null) {
            try {
                Utils.applyEarlyNonInteractivePolicies(this, settingsHelper.getConfig());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        settingsHelper.setIntegratedProvisioningFlow(true);

        Log.d(Const.LOG_TAG, "Initial setup activity: setup completed");
        setResult(RESULT_OK);
        finish();
    }

    private void displayError(String message) {
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setNeutralButton(R.string.main_activity_reset, (dialogInterface, i) -> abort())
                .setNegativeButton(R.string.main_activity_wifi, (dialogInterface, i) -> openWiFiSettings())
                .setPositiveButton(R.string.main_activity_repeat, (dialogInterface, i) -> updateConfig())
                .create()
                .show();
    }

    private void openWiFiSettings() {
        configuring = false;
        try {
            startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void abort() {
        // Factory reset!!!
        if (!Utils.factoryReset(this)) {
            RemoteLogger.log(this, Const.LOG_WARN, "Device reset failed");
        }
        setResult(RESULT_CANCELED);
        finish();
    }

    @Override
    public void onPoliciesUpdated() {
        // Not used in early setup
    }

    @Override
    public void onFileDownloading(RemoteFile remoteFile) {
        // Not used in early setup
    }

    @Override
    public void onDownloadProgress(int progress, long total, long current) {
        // Not used in early setup
    }

    @Override
    public void onFileDownloadError(RemoteFile remoteFile) {
        // Not used in early setup
    }

    @Override
    public void onAppUpdateStart() {
        // Not used in early setup
    }

    @Override
    public void onAppRemoving(Application application) {
        // Not used in early setup
    }

    @Override
    public void onAppDownloading(Application application) {
        // Not used in early setup
    }

    @Override
    public void onAppInstalling(Application application) {
        // Not used in early setup
    }

    @Override
    public void onAppDownloadError(Application application) {
        // Not used in early setup
    }

    @Override
    public void onAppInstallError(String packageName) {
        // Not used in early setup
    }

    @Override
    public void onAppInstallComplete(String packageName) {
        // Not used in early setup
    }

    @Override
    public void onConfigUpdateComplete() {
        // Not used in early setup
    }

    @Override
    public void onAllAppInstallComplete() {
        // Not used in early setup
    }
}
