package com.hmdm.launcher.ui;

import android.app.AlertDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

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
import com.hmdm.launcher.util.Utils;

public class InitialSetupActivity extends BaseActivity implements ConfigUpdater.UINotifier {
    private ActivityInitialSetupBinding binding;
    private ConfigUpdater configUpdater;
    private SettingsHelper settingsHelper;

    @Override
    protected void onCreate( Bundle savedInstanceState ) {
        super.onCreate(savedInstanceState);

        Log.d(Const.LOG_TAG, "Launching the initial setup activity");

        binding = DataBindingUtil.setContentView(this, R.layout.activity_initial_setup);
        binding.setMessage(getString(R.string.initializing_mdm));
        binding.setLoading(true);

        settingsHelper = SettingsHelper.getInstance(this);

        configUpdater = new ConfigUpdater();
        configUpdater.setLoadOnly(true);
        updateConfig();
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
                ServiceHelper.startServices(InitialSetupActivity.this);
            }
        }
        completeConfig(settingsHelper);
    }

    private void completeConfig(final SettingsHelper settingsHelper) {
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
                .setNegativeButton(R.string.main_activity_reset, (dialogInterface, i) -> abort())
                .setPositiveButton(R.string.main_activity_repeat, (dialogInterface, i) -> updateConfig())
                .create()
                .show();
    }

    private void abort() {
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
