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

package com.hmdm.launcher.service;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.hmdm.IMdmApi;
import com.hmdm.launcher.BuildConfig;
import com.hmdm.launcher.Const;
import com.hmdm.launcher.helper.ConfigUpdater;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.json.PushMessage;
import com.hmdm.launcher.json.RemoteLogItem;
import com.hmdm.launcher.pro.ProUtils;
import com.hmdm.launcher.util.DeviceInfoProvider;
import com.hmdm.launcher.util.RemoteLogger;
import com.hmdm.launcher.util.Utils;
import com.hmdm.launcher.worker.PushNotificationProcessor;

public class PluginApiService extends Service {
    // Data keys
    public static final String KEY_SERVER_HOST = "SERVER_HOST";
    public static final String KEY_SECONDARY_SERVER_HOST = "SECONDARY_SERVER_HOST";
    public static final String KEY_SERVER_PATH = "SERVER_PATH";
    public static final String KEY_DEVICE_ID = "DEVICE_ID";
    public static final String KEY_CUSTOM_1 = "CUSTOM_1";
    public static final String KEY_CUSTOM_2 = "CUSTOM_2";
    public static final String KEY_CUSTOM_3 = "CUSTOM_3";
    public static final String KEY_IMEI = "IMEI";
    public static final String KEY_SERIAL = "SERIAL";
    public static final String KEY_IS_MANAGED = "IS_MANAGED";
    public static final String KEY_IS_KIOSK = "IS_KIOSK";
    public static final String KEY_ERROR = "ERROR";

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private final IMdmApi.Stub mBinder = new IMdmApi.Stub() {

        @Override
        public int getVersion() {
            // 1.1.8
            return 118;
        }

        @Override
        public Bundle queryConfig() {
            return queryPrivilegedConfig(null);
        }

        @Override
        public Bundle queryPrivilegedConfig(String apiKey) {
            SettingsHelper settingsHelper = SettingsHelper.getInstance(PluginApiService.this);
            if ( settingsHelper.getConfig() == null ) {
                // This shouldn't happen!
                return null;
            } else {
                Bundle bundle = new Bundle();
                bundle.putString(KEY_SERVER_HOST, settingsHelper.getBaseUrl());
                bundle.putString(KEY_SECONDARY_SERVER_HOST, settingsHelper.getSecondaryBaseUrl());
                bundle.putString(KEY_SERVER_PATH, settingsHelper.getServerProject());
                bundle.putString(KEY_DEVICE_ID, settingsHelper.getDeviceId());
                bundle.putBoolean(KEY_IS_MANAGED, Utils.isDeviceOwner(PluginApiService.this));
                bundle.putBoolean(KEY_IS_KIOSK, ProUtils.isKioskModeRunning(PluginApiService.this));
                if (settingsHelper.getConfig().getCustom1() != null) {
                    bundle.putString(KEY_CUSTOM_1, settingsHelper.getConfig().getCustom1());
                }
                if (settingsHelper.getConfig().getCustom2() != null) {
                    bundle.putString(KEY_CUSTOM_2, settingsHelper.getConfig().getCustom2());
                }
                if (settingsHelper.getConfig().getCustom3() != null) {
                    bundle.putString(KEY_CUSTOM_3, settingsHelper.getConfig().getCustom3());
                }
                if (apiKey != null) {
                    if (apiKey.equals(BuildConfig.LIBRARY_API_KEY)) {
                        // IMEI and serial are set only to authorized requests
                        bundle.putString(KEY_IMEI, DeviceInfoProvider.getImei(PluginApiService.this));
                        bundle.putString(KEY_SERIAL, DeviceInfoProvider.getSerialNumber());
                    } else {
                        bundle.putString(KEY_ERROR, "KEY_NOT_MATCH");
                    }
                }

                return bundle;
            }
        }

        @Override
        public void log(long timestamp, int level, String packageId, String message) {
            Log.i(Const.LOG_TAG, "Got a log item from " + packageId);
            RemoteLogItem item = new RemoteLogItem();
            item.setTimestamp(timestamp);
            item.setLogLevel(level);
            item.setPackageId(packageId);
            item.setMessage(message);
            RemoteLogger.postLog(PluginApiService.this, item);
        }

        @Override
        public String queryAppPreference(String packageId, String attr) {
            SettingsHelper settingsHelper = SettingsHelper.getInstance(PluginApiService.this);
            if ( settingsHelper.getConfig() == null ) {
                // This shouldn't happen!
                return null;
            }
            return settingsHelper.getAppPreference(packageId, attr);
        }

        @Override
        public boolean setAppPreference(String packageId, String attr, String value) {
            SettingsHelper settingsHelper = SettingsHelper.getInstance(PluginApiService.this);
            if ( settingsHelper.getConfig() == null ) {
                // This shouldn't happen!
                return false;
            }
            return settingsHelper.setAppPreference(packageId, attr, value);
        }

        @Override
        public void commitAppPreferences(String packageId) {
            SettingsHelper settingsHelper = SettingsHelper.getInstance(PluginApiService.this);
            if ( settingsHelper.getConfig() == null ) {
                // This shouldn't happen!
                return;
            }
            settingsHelper.commitAppPreferences(packageId);
        }

        @Override
        public void setCustom(int number, String value) {
            SettingsHelper settingsHelper = SettingsHelper.getInstance(PluginApiService.this);
            if ( settingsHelper.getConfig() == null ) {
                // This shouldn't happen!
                return;
            }
            switch (number) {
                case 1:
                    settingsHelper.getConfig().setCustom1(value);
                    settingsHelper.setUserCustom1(value);
                    break;
                case 2:
                    settingsHelper.getConfig().setCustom2(value);
                    settingsHelper.setUserCustom2(value);
                    break;
                case 3:
                    settingsHelper.getConfig().setCustom3(value);
                    settingsHelper.setUserCustom3(value);
                    break;
            }
        }

        @Override
        public void forceConfigUpdate() {
            // userInteraction is set to true so the applications are also updated unrelated from the app update schedule
            ConfigUpdater.forceConfigUpdate(PluginApiService.this, null, true);
        }

        @Override
        public boolean sendPush(String apiKey, String type, String payload) {
            if (!apiKey.equals(BuildConfig.LIBRARY_API_KEY)) {
                return false;
            }
            PushMessage message = new PushMessage();
            message.setMessageType(type);
            message.setPayload(payload);
            PushNotificationProcessor.process(message, PluginApiService.this);
            return true;
        }
    };
}
