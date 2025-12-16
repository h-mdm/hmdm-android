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

package com.hmdm.launcher.task;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdm.launcher.BuildConfig;
import com.hmdm.launcher.Const;
import com.hmdm.launcher.helper.CryptoHelper;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.json.DeviceEnrollOptions;
import com.hmdm.launcher.json.ServerConfig;
import com.hmdm.launcher.json.ServerConfigResponse;
import com.hmdm.launcher.pro.ProUtils;
import com.hmdm.launcher.server.ServerService;
import com.hmdm.launcher.server.ServerServiceKeeper;
import com.hmdm.launcher.util.AppRestrictionUpdater;
import com.hmdm.launcher.util.PushNotificationMqttWrapper;
import com.hmdm.launcher.util.RemoteLogger;
import com.hmdm.launcher.util.Utils;

import okhttp3.ResponseBody;
import retrofit2.Response;

public class GetServerConfigTask extends AsyncTask< Void, Integer, Integer > {

    private Context context;
    private SettingsHelper settingsHelper;

    private ServerService serverService;
    private ServerService secondaryServerService;

    private String serverHost;
    private String urlTemplate = "{project}/rest/public/sync/configuration/{number}";
    private String errorText;
    // There are actually three types of errors: network error, HTTP error and application error
    // First two should be treated similarly and reported only in the foreground mode
    // This flag is introduced to distinguish between HTTP and application errors
    private boolean isDeviceNotFound;
    // This is the only application error which requires reporting in the background
    private String notFoundError = "error.notfound.device";

    public GetServerConfigTask( Context context ) {
        this.context = context;
        this.settingsHelper = SettingsHelper.getInstance( context );
    }

    public String getErrorText() {
        return errorText;
    }

    @Override
    protected Integer doInBackground( Void... voids ) {
        DeviceEnrollOptions enrollOptions = null;
        if (settingsHelper.getConfig() == null) {
            // This is a first start, we need to set up additional options to create a device on demand
            // Even if there's no additional options, we call POST method (enroll) rather than GET (getConfig)
            enrollOptions = new DeviceEnrollOptions();
            enrollOptions.setCustomer(settingsHelper.getEnrollOptionCustomer());
            enrollOptions.setConfiguration(settingsHelper.getEnrollOptionConfigName());
            enrollOptions.setGroups(settingsHelper.getEnrollOptionGroup());
        }

        try {
            serverService = ServerServiceKeeper.getServerServiceInstance(context);
            secondaryServerService = ServerServiceKeeper.getSecondaryServerServiceInstance(context);
        } catch (Exception e) {
            errorText = "Exception: " + e.getMessage();
            return Const.TASK_NETWORK_ERROR;
        }

        String deviceId = settingsHelper.getDeviceId();
        String signature = "";
        try {
            signature = CryptoHelper.getSHA1String(BuildConfig.REQUEST_SIGNATURE + deviceId);
        } catch (Exception e) {
        }

        isDeviceNotFound = false;
        try {
            ServerConfig serverConfig = null;
            if (enrollOptions == null) {
                serverConfig = BuildConfig.CHECK_SIGNATURE ?
                        getServerConfigSecure(deviceId, signature) :
                        getServerConfigPlain(deviceId, signature);
            } else {
                serverConfig = BuildConfig.CHECK_SIGNATURE ?
                        enrollSecure(deviceId, enrollOptions, signature) :
                        enrollPlain(deviceId, enrollOptions, signature);
            }

            if (serverConfig != null) {
                if (serverConfig.getNewNumber() != null) {
                    RemoteLogger.log(context, Const.LOG_INFO, "Device number changed from " + settingsHelper.getDeviceId() + " to " + serverConfig.getNewNumber());
                    settingsHelper.setDeviceId(serverConfig.getNewNumber());
                    serverConfig.setNewNumber(null);
                    try {
                        PushNotificationMqttWrapper.getInstance().disconnect(context);
                    } catch (Exception e) {
                    }
                }

                settingsHelper.updateConfig(serverConfig);
                if (Utils.isDeviceOwner(context)) {
                    AppRestrictionUpdater.updateAppRestrictions(context, serverConfig.getApplicationSettings());
                }

                // Device already created, erase the device creation options
                settingsHelper.setDeviceIdUse(null);
                settingsHelper.setEnrollOptionCustomer(null);
                settingsHelper.setEnrollOptionConfigName(null);
                settingsHelper.setEnrollOptionGroup(null);

                // User-friendly error report if a content app in kiosk mode is not set
                if (ProUtils.kioskModeRequired(context) &&
                        (settingsHelper.getConfig().getMainApp() == null || settingsHelper.getConfig().getMainApp().trim().equals(""))) {
                    throw new Exception("Content app in kiosk mode is not set");
                }

                // Prevent from occasional launch in the kiosk mode without any possibility to exit!
                if (ProUtils.kioskModeRequired(context) &&
                        !context.getPackageName().equals(settingsHelper.getConfig().getMainApp()) &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                        !Settings.canDrawOverlays(context) && !BuildConfig.ENABLE_KIOSK_WITHOUT_OVERLAYS) {
                        RemoteLogger.log(context, Const.LOG_WARN, "Kiosk mode disabled: no permission to draw over other windows.");
                        settingsHelper.getConfig().setKioskMode(false);
                        settingsHelper.updateConfig(settingsHelper.getConfig());
                }

                ProUtils.processConfig(context, serverConfig);

                return Const.TASK_SUCCESS;
            } else {
                return isDeviceNotFound ? Const.TASK_ERROR : Const.TASK_NETWORK_ERROR;
            }
        } catch ( Exception e ) {
            e.printStackTrace();
            buildNetworkErrorText(e.getMessage());
        }

        return Const.TASK_NETWORK_ERROR;
    }

    private ServerConfig getServerConfigPlain(String deviceId, String signature) throws Exception {
        Response<ServerConfigResponse> response = null;
        try {
            serverHost = settingsHelper.getBaseUrl();
            response = serverService.getServerConfig(settingsHelper.getServerProject(),
                    deviceId, signature, Build.CPU_ABI).execute();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (response == null) {
            serverHost = settingsHelper.getSecondaryBaseUrl();
            response = secondaryServerService.getServerConfig(settingsHelper.getServerProject(),
                    deviceId, signature, Build.CPU_ABI).execute();
        }

        if (response.isSuccessful() && Const.STATUS_OK.equals(response.body().getStatus()) && response.body().getData() != null) {
            SettingsHelper.getInstance(context).setExternalIp(response.headers().get(Const.HEADER_IP_ADDRESS));
            return response.body().getData();
        } else {
            isDeviceNotFound = response.body() != null && notFoundError.equals(response.body().getMessage());
            buildTaskErrorText(response);
        }
        return null;
    }

    // Check server signature before accepting server response
    // This is an additional protection against Man-In-The-Middle attacks
    private ServerConfig getServerConfigSecure(String deviceId, String signature) throws Exception {
        Response<ResponseBody> response = null;

        try {
            serverHost = settingsHelper.getBaseUrl();
            response = serverService.getServerConfigRaw(settingsHelper.getServerProject(),
                    deviceId, signature, Build.CPU_ABI).execute();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (response == null) {
            serverHost = settingsHelper.getSecondaryBaseUrl();
            response = secondaryServerService.getServerConfigRaw(settingsHelper.getServerProject(),
                    deviceId, signature, Build.CPU_ABI).execute();
        }

        if (response.isSuccessful()) {
            String serverResponse = response.body().string();

            ServerConfigResponse serverConfigResponse;
            try {
                serverConfigResponse = new ObjectMapper().readValue(serverResponse, ServerConfigResponse.class);
            } catch (Exception e) {
                errorText = "Failed to parse JSON";
                Log.e(Const.LOG_TAG, errorText);
                buildTaskErrorTextSecure(response, serverResponse);
                return null;
            }

            // Check for application errors before checking the signature
            // Because the errors are not signed
            if (!Const.STATUS_OK.equals(serverConfigResponse.getStatus())) {
                isDeviceNotFound = notFoundError.equals(serverConfigResponse.getMessage());
                buildTaskErrorTextSecure(response, serverResponse);
                return null;
            }

            // Check response signature
            String serverSignature = response.headers().get(Const.HEADER_RESPONSE_SIGNATURE);
            if (serverSignature == null) {
                errorText = "Missing " + Const.HEADER_RESPONSE_SIGNATURE + " flag, dropping response";
                Log.e(Const.LOG_TAG, errorText);
                buildTaskErrorTextSecure(response, serverResponse);
                return null;
            }

            // We need to extract data from the response body
            // Here we assume the specific form of response body: {"status":"OK","message":null,"data":{...}}
            final String dataMarker = "\"data\":";
            int pos = serverResponse.indexOf(dataMarker);
            if (pos == -1) {
                errorText = "Wrong server response, missing data";
                Log.e(Const.LOG_TAG, errorText + ": " + serverResponse);
                buildTaskErrorTextSecure(response, serverResponse);
                return null;
            }
            String serverData = serverResponse.substring(pos + dataMarker.length(), serverResponse.length() - 1);
            String calculatedSignature = CryptoHelper.getSHA1String(BuildConfig.REQUEST_SIGNATURE + serverData.replaceAll("\\s", ""));
            if (!calculatedSignature.equalsIgnoreCase(serverSignature)) {
                errorText = "Server signature " + serverSignature + " doesn't match calculated signature " + calculatedSignature + ", dropping response";
                Log.e(Const.LOG_TAG, errorText);
                buildTaskErrorTextSecure(response, serverResponse);
                return null;
            }
            return new ObjectMapper().readValue(serverData, ServerConfig.class);
        } else {
            buildTaskErrorTextSecure(response, null);
        }
        return null;
    }

    // Apply extra device creation options (need to be used only at first start when config=null!)
    private ServerConfig enrollPlain(String deviceId, DeviceEnrollOptions createOptions,
                                     String signature) throws Exception {
        Response<ServerConfigResponse> response = null;
        try {
            serverHost = settingsHelper.getBaseUrl();
            response = serverService.enrollAndGetServerConfig(settingsHelper.getServerProject(),
                    deviceId, signature, Build.CPU_ABI, createOptions).execute();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (response == null) {
            serverHost = settingsHelper.getSecondaryBaseUrl();
            response = secondaryServerService.enrollAndGetServerConfig(settingsHelper.getServerProject(),
                    deviceId, signature, Build.CPU_ABI, createOptions).execute();
        }

        if (response.isSuccessful() && Const.STATUS_OK.equals(response.body().getStatus()) && response.body().getData() != null) {
            SettingsHelper.getInstance(context).setExternalIp(response.headers().get(Const.HEADER_IP_ADDRESS));
            return response.body().getData();
        } else {
            isDeviceNotFound = response.body() != null && notFoundError.equals(response.body().getMessage());
            buildTaskErrorText(response);
        }
        return null;
    }

    // Check server signature before accepting server response
    // This is an additional protection against Man-In-The-Middle attacks
    // Apply extra device creation options (need to be used only at first start when config=null!)
    private ServerConfig enrollSecure(String deviceId,
                                      DeviceEnrollOptions createOptions,
                                      String signature) throws Exception {
        Response<ResponseBody> response = null;

        try {
            serverHost = settingsHelper.getBaseUrl();
            response = serverService.
                    enrollAndGetServerConfigRaw(settingsHelper.getServerProject(),
                            deviceId, signature, Build.CPU_ABI, createOptions).execute();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (response == null) {
            serverHost = settingsHelper.getSecondaryBaseUrl();
            response = secondaryServerService.
                    enrollAndGetServerConfigRaw(settingsHelper.getServerProject(),
                            deviceId, signature, Build.CPU_ABI, createOptions).execute();
        }

        if (response.isSuccessful()) {
            String serverResponse = response.body().string();

            ServerConfigResponse serverConfigResponse;
            try {
                serverConfigResponse = new ObjectMapper().readValue(serverResponse, ServerConfigResponse.class);
            } catch (Exception e) {
                errorText = "Failed to parse JSON";
                Log.e(Const.LOG_TAG, errorText);
                buildTaskErrorTextSecure(response, serverResponse);
                return null;
            }

            // Check for application errors before checking the signature
            // Because the errors are not signed
            if (!Const.STATUS_OK.equals(serverConfigResponse.getStatus())) {
                isDeviceNotFound = notFoundError.equals(serverConfigResponse.getMessage());
                buildTaskErrorTextSecure(response, serverResponse);
                return null;
            }

            // Check response signature
            String serverSignature = response.headers().get(Const.HEADER_RESPONSE_SIGNATURE);
            if (serverSignature == null) {
                errorText = "Missing " + Const.HEADER_RESPONSE_SIGNATURE + " flag, dropping response";
                Log.e(Const.LOG_TAG, errorText);
                buildTaskErrorTextSecure(response, serverResponse);
            }

            // We need to extract data from the response body
            // Here we assume the specific form of response body: {"status":"OK","message":null,"data":{...}}
            final String dataMarker = "\"data\":";
            int pos = serverResponse.indexOf(dataMarker);
            if (pos == -1) {
                errorText = "Wrong server response, missing data";
                Log.e(Const.LOG_TAG, errorText + ": " + serverResponse);
                buildTaskErrorTextSecure(response, serverResponse);
                return null;
            }
            String serverData = serverResponse.substring(pos + dataMarker.length(), serverResponse.length() - 1);
            String calculatedSignature = CryptoHelper.getSHA1String(BuildConfig.REQUEST_SIGNATURE + serverData.replaceAll("\\s", ""));
            if (!calculatedSignature.equalsIgnoreCase(serverSignature)) {
                errorText = "Server signature " + serverSignature + " doesn't match calculated signature " + calculatedSignature + ", dropping response";
                Log.e(Const.LOG_TAG, errorText);
                buildTaskErrorTextSecure(response, serverResponse);
                return null;
            }
            return new ObjectMapper().readValue(serverData, ServerConfig.class);
        } else {
            buildTaskErrorTextSecure(response, null);
        }
        return null;
    }

    private void buildTaskErrorText(Response<ServerConfigResponse> response) {
        String message = "HTTP status: " + response.code();
        if (response.isSuccessful()) {
            message += "\n" +
                    "JSON status: " + response.body().getStatus() + "\n" +
                    "JSON message: " + response.body().getMessage();
        }
        buildNetworkErrorText(message);
    }

    private void buildTaskErrorTextSecure(Response<ResponseBody> response, String body) {
        String reason = errorText;
        String message = "HTTP status: " + response.code();
        if (response.isSuccessful()) {
            message += "\n" +
                    "Body: " + body;
        }
        buildNetworkErrorText(message);
        if (reason != null && !reason.equals("")) {
            errorText = reason + "\n\n" + errorText;
        }
    }

    private void buildNetworkErrorText(String message) {
        String serverProject = settingsHelper.getServerProject();
        if (serverProject.length() > 0) {
            serverProject = "/" + serverProject;
        }
        String url = serverHost + urlTemplate
                .replace("{project}", serverProject)
                .replace("{number}", settingsHelper.getDeviceId());

        errorText = url + "\n\n" +
                message;

        String tag = queryTag(message);
        if (tag != null) {
            errorText += "\n\nError tag: " +
                    tag;
        }
    }

    private String queryTag(String message) {
        if (message != null && message.contains("Trust anchor")) {
            return "trust_anchor";
        }
        return null;
    }
}
