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
import com.hmdm.launcher.util.DeviceInfoProvider;
import com.hmdm.launcher.util.Utils;

import org.json.JSONObject;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class BaseActivity extends AppCompatActivity {

    protected ProgressDialog progressDialog;

    protected Dialog enterServerDialog;
    protected DialogEnterServerBinding dialogEnterServerBinding;

    protected Dialog enterDeviceIdDialog;
    protected DialogEnterDeviceIdBinding enterDeviceIdDialogBinding;

    protected Dialog networkErrorDialog;
    protected DialogNetworkErrorBinding dialogNetworkErrorBinding;

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
        if (BuildConfig.SUGGEST_IMEI_AS_ID) {
            String imei = DeviceInfoProvider.getImei(this);
            if (imei != null) {
                variantsList.add(imei);
            }
            String serial = DeviceInfoProvider.getSerialNumber();
            if (serial != null) {
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

        enterDeviceIdDialogBinding.showDeviceIdQrCode.setVisibility(Utils.isDeviceOwner(this) ? View.VISIBLE : View.GONE);

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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        try {
            IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
            if (result != null) {
                if (result.getContents() != null) {
                    updateSettingsFromQr(result.getContents());
                }
            } else {
                super.onActivityResult(requestCode, resultCode, data);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateSettingsFromQr(String qrcode) {
        try {
            SettingsHelper settingsHelper = SettingsHelper.getInstance(getApplicationContext());
            JSONObject qr = new JSONObject(qrcode);
            JSONObject extras = qr.getJSONObject(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE);

            String deviceId = extras.optString(Const.QR_DEVICE_ID_ATTR, null);
            if (deviceId == null) {
                // Also let's try legacy attribute
                deviceId = extras.optString(Const.QR_LEGACY_DEVICE_ID_ATTR, null);
            }
            if (deviceId != null) {
                settingsHelper.setDeviceId(deviceId);
            }

            String baseUrl = extras.optString(Const.QR_BASE_URL_ATTR, null);
            if (baseUrl != null) {
                settingsHelper.setBaseUrl(baseUrl);
            }

            String secondaryBaseUrl = extras.optString(Const.QR_SECONDARY_BASE_URL_ATTR, null);
            if (secondaryBaseUrl != null) {
                settingsHelper.setSecondaryBaseUrl(secondaryBaseUrl);
            }

            String serverProject = extras.optString(Const.QR_SERVER_PROJECT_ATTR, null);
            if (serverProject != null) {
                settingsHelper.setServerProject(serverProject);
            }

        } catch (Exception e) {
            Toast.makeText(this, R.string.qrcode_contents_error, Toast.LENGTH_LONG).show();
        }
    }

    public void exitDeviceId(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            finishAffinity();
        }
        System.exit(0);
    }

    protected void createAndShowNetworkErrorDialog(String serverName, String serverPath) {
        dismissDialog(networkErrorDialog);
        networkErrorDialog = new Dialog( this );
        dialogNetworkErrorBinding = DataBindingUtil.inflate(
                LayoutInflater.from( this ),
                R.layout.dialog_network_error,
                null,
                false );
        networkErrorDialog.setCancelable( false );
        networkErrorDialog.requestWindowFeature( Window.FEATURE_NO_TITLE );

        String serverUrl = serverName;
        if (serverPath != null && serverPath.length() > 0) {
            serverUrl += "/";
            serverUrl += serverPath;
        }
        dialogNetworkErrorBinding.title.setText(getString(R.string.dialog_network_error_title, serverUrl));

        networkErrorDialog.setContentView( dialogNetworkErrorBinding.getRoot() );
        networkErrorDialog.show();
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
            URL url;
            try {
                url = new URL(serverUrl);
            } catch (Exception e) {
                // Malformed URL
                dialogEnterServerBinding.setError(true);
                return false;
            }

            String baseUrl = url.getProtocol() + "://" + url.getHost();
            if (url.getPort() != -1) {
                baseUrl += ":" + url.getPort();
            }
            String serverProject = url.getPath();
            if (serverProject.endsWith("/")) {
                serverProject = serverProject.substring(0, serverProject.length() - 1);
            }
            if (serverProject.startsWith("/")) {
                serverProject = serverProject.substring(1);
            }
            SettingsHelper settingsHelper = SettingsHelper.getInstance( this );
            settingsHelper.setBaseUrl(baseUrl);
            settingsHelper.setSecondaryBaseUrl(baseUrl);
            settingsHelper.setServerProject(serverProject);
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
        dialogDeviceInfoBinding.setVersion( BuildConfig.FLAVOR.length() > 0 ?
                BuildConfig.VERSION_NAME + "-" + BuildConfig.FLAVOR : BuildConfig.VERSION_NAME );

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

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (progressDialog != null) {
                    progressDialog.dismiss();
                    progressDialog = null;
                }

                Intent intent = new Intent( Intent.ACTION_MAIN );
                intent.addCategory( Intent.CATEGORY_HOME );
                intent.addCategory( Intent.CATEGORY_DEFAULT );
                intent.setFlags( Intent.FLAG_ACTIVITY_NEW_TASK );

                startActivity( Intent.createChooser( intent, getString( R.string.select_system_launcher ) ) );
            }
        }, 1000);
    }

}
