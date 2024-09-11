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

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.hmdm.launcher.BuildConfig;
import com.hmdm.launcher.Const;
import com.hmdm.launcher.R;
import com.hmdm.launcher.databinding.DialogDeviceInfoBinding;
import com.hmdm.launcher.databinding.DialogEnterDeviceIdBinding;
import com.hmdm.launcher.databinding.DialogEnterServerBinding;
import com.hmdm.launcher.databinding.DialogNetworkErrorBinding;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.json.DeviceCreateOptions;
import com.hmdm.launcher.json.ServerConfig;
import com.hmdm.launcher.server.ServerUrl;
import com.hmdm.launcher.util.DeviceInfoProvider;
import com.hmdm.launcher.util.Utils;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import okhttp3.HttpUrl;

public class BaseActivity extends AppCompatActivity {

    protected ProgressDialog progressDialog;

    protected Dialog enterServerDialog;
    protected DialogEnterServerBinding dialogEnterServerBinding;

    protected Dialog enterDeviceIdDialog;
    protected DialogEnterDeviceIdBinding enterDeviceIdDialogBinding;

    protected Dialog networkErrorDialog;
    protected DialogNetworkErrorBinding dialogNetworkErrorBinding;
    protected String networkErrorDetails;

    protected Dialog deviceInfoDialog;
    protected DialogDeviceInfoBinding dialogDeviceInfoBinding;

    protected void dismissDialog(Dialog dialog) {
        if (dialog != null) {
            try {
                dialog.dismiss();
            } catch (Exception ignored) {
            }
        }
    }

    protected void createAndShowEnterDeviceIdDialog( boolean error, String deviceId ) {
        dismissDialog(enterDeviceIdDialog);
        enterDeviceIdDialog = new Dialog( this );
        enterDeviceIdDialogBinding = DataBindingUtil.inflate(
                LayoutInflater.from( this ),
                R.layout.dialog_enter_device_id,
                null,
                false );
        SettingsHelper settingsHelper = SettingsHelper.getInstance(this);
        String serverUrl = settingsHelper.getBaseUrl();
        String serverPath = settingsHelper.getServerProject();
        if (serverPath.length() > 0) {
            serverUrl += "/" + serverPath;
        }
        enterDeviceIdDialogBinding.deviceIdPrompt.setText(getString(R.string.dialog_enter_device_id_title, serverUrl));
        enterDeviceIdDialogBinding.deviceIdError.setText(getString(R.string.dialog_enter_device_id_error, serverUrl));
        enterDeviceIdDialogBinding.setError( error );
        enterDeviceIdDialog.setCancelable( false );
        enterDeviceIdDialog.requestWindowFeature( Window.FEATURE_NO_TITLE );
        if (deviceId != null) {
            enterDeviceIdDialogBinding.deviceId.setText(deviceId);
        }

        // Suggest IMEI as ID is an option which could be turned on in the build settings
        // Don't use this by default because the device ID must not be bound to IMEI:
        // if it's bound to IMEI, it becomes difficult to replace the device
        List<String> variantsList = new ArrayList<>();
        if (!BuildConfig.DEVICE_ID_CHOICE.equals("user")) {
            Utils.autoGrantPhonePermission(this);
            String imei = DeviceInfoProvider.getImei(this);
            if (imei != null) {
                variantsList.add(imei);
            }
            String serial = DeviceInfoProvider.getSerialNumber();
            if (serial != null && !serial.equals(Build.UNKNOWN)) {
                variantsList.add(serial);
            }
        }
        if (variantsList.size() > 0) {
            String[] variantsArray = variantsList.toArray(new String[variantsList.size()]);
            enterDeviceIdDialogBinding.deviceId.setThreshold(0);
            enterDeviceIdDialogBinding.deviceId.setAdapter(new ArrayAdapter<String>(this,
                    android.R.layout.select_dialog_item, variantsArray));
        } else {
            enterDeviceIdDialogBinding.showDeviceIdVariants.setVisibility(View.GONE);
        }

        enterDeviceIdDialogBinding.showDeviceIdQrCode.setVisibility(View.VISIBLE);

        enterDeviceIdDialog.setContentView( enterDeviceIdDialogBinding.getRoot() );
        enterDeviceIdDialog.show();
    }

    public void showDeviceIdVariants(View view) {
        enterDeviceIdDialogBinding.deviceId.showDropDown();
    }

    public void showDeviceIdQrCode(View view) {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setOrientationLocked(false);
        integrator.initiateScan();
    }

    public void showErrorDetails(View view) {
        ErrorDetailsActivity.display(this, networkErrorDetails, false);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        try {
            IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
            if (result != null) {
                if (result.getContents() != null) {
                    updateSettingsFromQr(result.getContents());
                }
            } else {
                Log.d(Const.LOG_TAG, "Failed to parse QR code!");
                super.onActivityResult(requestCode, resultCode, data);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void updateSettingsFromQr(String qrcode) {
        try {
            Log.d(Const.LOG_TAG, "Get initial settings from the QR code");
            SettingsHelper settingsHelper = SettingsHelper.getInstance(getApplicationContext());
            JSONObject qr = new JSONObject(qrcode);
            JSONObject extras = qr.getJSONObject(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE);

            String deviceId = extras.optString(Const.QR_DEVICE_ID_ATTR, null);
            if (deviceId == null) {
                // Also let's try legacy attribute
                deviceId = extras.optString(Const.QR_LEGACY_DEVICE_ID_ATTR, null);
            }
            if (deviceId != null) {
                Log.d(Const.LOG_TAG, "Device ID: " + deviceId);
                settingsHelper.setDeviceId(deviceId);
            } else {
                Log.d(Const.LOG_TAG, "Device ID is null");
                String deviceIdUse = extras.optString(Const.QR_DEVICE_ID_USE_ATTR, null);
                if (deviceIdUse != null) {
                    Log.d(Const.LOG_TAG, "Device ID use: " + deviceIdUse);
                    // Save for further automatic choice of the device ID
                    settingsHelper.setDeviceIdUse(deviceIdUse);
                }
            }

            String baseUrl = extras.optString(Const.QR_BASE_URL_ATTR, null);
            String secondaryBaseUrl = extras.optString(Const.QR_SECONDARY_BASE_URL_ATTR, null);
            if (baseUrl != null) {
                Log.d(Const.LOG_TAG, "Base URL: " + baseUrl);
                settingsHelper.setBaseUrl(baseUrl);
                // If we don't set the secondary base URL, it will point to app.h-mdm.com by default which is wrong
                if (secondaryBaseUrl == null) {
                    secondaryBaseUrl = baseUrl;
                }
            }
            if (secondaryBaseUrl != null) {
                Log.d(Const.LOG_TAG, "Secondary base URL: " + baseUrl);
                settingsHelper.setSecondaryBaseUrl(secondaryBaseUrl);
            }

            String serverProject = extras.optString(Const.QR_SERVER_PROJECT_ATTR, null);
            if (serverProject != null) {
                Log.d(Const.LOG_TAG, "Project path: " + serverProject);
                settingsHelper.setServerProject(serverProject);
            }

            DeviceCreateOptions createOptions = new DeviceCreateOptions();
            createOptions.setCustomer(extras.optString(Const.QR_CUSTOMER_ATTR, null));
            createOptions.setConfiguration(extras.optString(Const.QR_CONFIG_ATTR, null));
            createOptions.setGroups(extras.optString(Const.QR_GROUP_ATTR, null));
            if (createOptions.getCustomer() != createOptions.getCustomer()) {
                Log.d(Const.LOG_TAG, "Customer: " + serverProject);
                settingsHelper.setCreateOptionCustomer(createOptions.getCustomer());
            }
            if (createOptions.getConfiguration() != null) {
                Log.d(Const.LOG_TAG, "Configuration: " + createOptions.getConfiguration());
                settingsHelper.setCreateOptionConfigName(createOptions.getConfiguration());
            }
            if (createOptions.getGroups() != null) {
                settingsHelper.setCreateOptionGroup(createOptions.getGroupSet());
            }

        } catch (Exception e) {
            Log.w(Const.LOG_TAG, "Invalid QR code contents, got an exception!");
            e.printStackTrace();
            Toast.makeText(this, getString(R.string.qrcode_contents_error,
                    getString(R.string.white_app_name)), Toast.LENGTH_LONG).show();
        }
    }

    public void exitDeviceId(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            finishAffinity();
        }
        System.exit(0);
    }

    protected String concatenateServerUrl(String serverName, String serverPath) {
        String serverUrl = serverName;
        if (serverPath != null && serverPath.length() > 0) {
            serverUrl += "/";
            serverUrl += serverPath;
        }
        return serverUrl;
    }

    protected void createAndShowNetworkErrorDialog(String serverName,
                                                   String serverPath,
                                                   String errorDetails,
                                                   boolean showResetButton,
                                                   boolean showWifiButton) {
        dismissDialog(networkErrorDialog);
        networkErrorDialog = new Dialog( this );
        dialogNetworkErrorBinding = DataBindingUtil.inflate(
                LayoutInflater.from( this ),
                R.layout.dialog_network_error,
                null,
                false );
        networkErrorDialog.setCancelable( false );
        networkErrorDialog.requestWindowFeature( Window.FEATURE_NO_TITLE );
        networkErrorDetails = errorDetails;

        String serverUrl = serverName;
        if (serverPath != null && serverPath.length() > 0) {
            serverUrl += "/";
            serverUrl += serverPath;
        }
        dialogNetworkErrorBinding.title.setText(getString(R.string.dialog_network_error_title, serverUrl));

        dialogNetworkErrorBinding.resetButton.setVisibility(showResetButton ? View.VISIBLE : View.GONE);
        dialogNetworkErrorBinding.wifiButton.setVisibility(showWifiButton ? View.VISIBLE : View.GONE);

        networkErrorDialog.setContentView( dialogNetworkErrorBinding.getRoot() );
        try {
            networkErrorDialog.show();
        } catch (Exception e) {
            // Unable to add window -- token is not valid; is your activity running?
            e.printStackTrace();
        }
    }


    protected void createAndShowServerDialog(boolean error, String serverName, String serverPath) {
        dismissDialog(enterServerDialog);
        enterServerDialog = new Dialog( this );
        dialogEnterServerBinding = DataBindingUtil.inflate(
                LayoutInflater.from( this ),
                R.layout.dialog_enter_server,
                null,
                false );
        dialogEnterServerBinding.setError(error);
        enterServerDialog.setCancelable(false);
        enterServerDialog.requestWindowFeature( Window.FEATURE_NO_TITLE );

        String serverUrl = serverName;
        if (serverPath.length() > 0) {
            serverUrl += "/";
            serverUrl += serverPath;
        }
        dialogEnterServerBinding.setServer(serverUrl);

        enterServerDialog.setContentView( dialogEnterServerBinding.getRoot() );
        enterServerDialog.show();
    }

    public boolean saveServerUrlBase() {
        String serverUrl = dialogEnterServerBinding.serverUrl.getText().toString();
        if ( "".equals( serverUrl ) ) {
            dialogEnterServerBinding.setError(true);
            return false;
        } else {
            ServerUrl url = null;
            try {
                url = new ServerUrl(serverUrl);

                // Retrofit uses HttpUrl!
                HttpUrl httpUrl = HttpUrl.parse(serverUrl);
                if (httpUrl == null) {
                    // Malformed URL
                    dialogEnterServerBinding.setError(true);
                    return false;
                }
            } catch (Exception e) {
                // Malformed URL
                dialogEnterServerBinding.setError(true);
                return false;
            }

            SettingsHelper settingsHelper = SettingsHelper.getInstance( this );
            settingsHelper.setBaseUrl(url.baseUrl);
            settingsHelper.setSecondaryBaseUrl(url.baseUrl);
            settingsHelper.setServerProject(url.serverProject);
            dialogEnterServerBinding.setError( false );

            dismissDialog(enterServerDialog);

            Log.i(Const.LOG_TAG, "saveServerUrl(): calling updateConfig()");
            return true;
        }
    }

    @SuppressLint( { "MissingPermission" } )
    protected void createAndShowInfoDialog() {
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

        SettingsHelper settingsHelper = SettingsHelper.getInstance(this);

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
        dialogDeviceInfoBinding.setVersion(BuildConfig.VERSION_NAME + "-" + Utils.getLauncherVariant());

        String serverPath = SettingsHelper.getInstance(this).getServerProject();
        if (serverPath.length() > 0) {
            serverPath = "/" + serverPath;
        }
        dialogDeviceInfoBinding.setServerUrl(SettingsHelper.getInstance(this).getBaseUrl() + serverPath);

        deviceInfoDialog.show();
    }

    public void closeDeviceInfoDialog( View view ) {
        dismissDialog(deviceInfoDialog);
    }


    public void exitToSystemLauncher( View view ) {
        LocalBroadcastManager.getInstance( this ).sendBroadcast( new Intent( Const.ACTION_SERVICE_STOP ) );
        LocalBroadcastManager.getInstance( this ).sendBroadcast( new Intent( Const.ACTION_EXIT ) );

        // One second delay is required to avoid race between opening a forbidden activity and stopping the locked mode
        progressDialog = new ProgressDialog(this);
        progressDialog.setCancelable(false);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progressDialog.setMessage(getString(R.string.switch_off_blockings));
        progressDialog.show();

        SettingsHelper settingsHelper = SettingsHelper.getInstance(this);
        if (settingsHelper != null && settingsHelper.getConfig() != null) {
            ServerConfig config = settingsHelper.getConfig();
            if (config.getRestrictions() != null && !config.getRestrictions().trim().equals("")) {
                Utils.releaseUserRestrictions(this, config.getRestrictions());
            }
        }

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (progressDialog != null) {
                    progressDialog.dismiss();
                    progressDialog = null;
                }

                openLauncherChoiceDialog();
            }
        }, 1000);
    }

    protected void openLauncherChoiceDialog() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        startActivity(Intent.createChooser(intent, getString(R.string.select_system_launcher, getString(R.string.white_app_name))));
    }

}
