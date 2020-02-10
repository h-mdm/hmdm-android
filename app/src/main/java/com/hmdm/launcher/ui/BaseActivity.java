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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;

import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

import com.hmdm.launcher.BuildConfig;
import com.hmdm.launcher.R;
import com.hmdm.launcher.databinding.DialogDeviceInfoBinding;
import com.hmdm.launcher.databinding.DialogEnterDeviceIdBinding;
import com.hmdm.launcher.databinding.DialogEnterServerBinding;
import com.hmdm.launcher.databinding.DialogNetworkErrorBinding;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.util.DeviceInfoProvider;

import java.net.URL;

public class BaseActivity extends AppCompatActivity {

    protected final static String LOG_TAG = "HeadwindMdm";

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
        enterDeviceIdDialogBinding.setError( error );
        enterDeviceIdDialog.setCancelable( false );
        enterDeviceIdDialog.requestWindowFeature( Window.FEATURE_NO_TITLE );
        if (deviceId != null) {
            enterDeviceIdDialogBinding.deviceId.setText(deviceId);
        }

        // Suggest IMEI as ID is an option which could be turned on in the build settings
        // Don't use this by default because the device ID must not be bound to IMEI:
        // if it's bound to IMEI, it becomes difficult to replace the device
        String imei = BuildConfig.SUGGEST_IMEI_AS_ID ? DeviceInfoProvider.getImei(this) : null;
        if (imei != null) {
            String[] imeiArray = {
                    imei
            };
            enterDeviceIdDialogBinding.deviceId.setThreshold(0);
            enterDeviceIdDialogBinding.deviceId.setAdapter(new ArrayAdapter<String>(this,
                    android.R.layout.select_dialog_item, imeiArray));
        } else {
            enterDeviceIdDialogBinding.showDeviceIdVariants.setVisibility(View.GONE);
        }

        enterDeviceIdDialog.setContentView( enterDeviceIdDialogBinding.getRoot() );
        enterDeviceIdDialog.show();
    }

    protected void createAndShowNetworkErrorDialog() {
        dismissDialog(networkErrorDialog);
        networkErrorDialog = new Dialog( this );
        dialogNetworkErrorBinding = DataBindingUtil.inflate(
                LayoutInflater.from( this ),
                R.layout.dialog_network_error,
                null,
                false );
        networkErrorDialog.setCancelable( false );
        networkErrorDialog.requestWindowFeature( Window.FEATURE_NO_TITLE );

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

            Log.i(LOG_TAG, "saveServerUrl(): calling updateConfig()");
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

}
