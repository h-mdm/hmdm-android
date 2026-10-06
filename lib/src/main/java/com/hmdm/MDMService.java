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

package com.hmdm;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;

public class MDMService {
    // MDM configuration attributes
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

    public static final int INITIAL_VERSION = 112;

    // Minimal launcher API version that supports forceConfigUpdate() with a progress callback.
    // This MUST stay in sync with PluginApiService.getVersion() in the launcher that introduced
    // forceConfigUpdateWithCallback() - both are bumped together. If the launcher's reported
    // version is renumbered upstream, update this value to match.
    public static final int CALLBACK_VERSION = 119;

    private Context context;
    private IMdmApi mdmApi;
    private RemoteServiceConnection serviceConnection;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // The launcher only holds a remote (proxy) reference to the progress callback.
    // We must keep a strong reference on this side, otherwise the local Binder may be
    // garbage-collected mid-update and the launcher's callbacks would silently stop.
    private IMdmApiCallback activeConfigUpdateCallback;

    private static MDMService instance;

    public interface ResultHandler {
        void onMDMConnected();
        void onMDMDisconnected();
    }

    public static MDMService getInstance() {
        if (instance == null) {
            instance = new MDMService();
        }
        return instance;
    }

    /**
     * Connect to the MDM service
     * @param handler
     * @return true on success and false if no Headwind MDM installed
     */
    public boolean connect(Context context, ResultHandler handler) {
        this.context = context;
        serviceConnection = new RemoteServiceConnection(handler);

        // First we try up-to-date package
        Intent i = new Intent(Const.SERVICE_ACTION);
        i.setPackage(Const.PACKAGE);
        boolean ret = context.bindService(i, serviceConnection, Context.BIND_AUTO_CREATE);
        if (!ret) {
            android.util.Log.i("MDMService", "Failed to bind service: intent " + i.getAction() + ", package " + i.getPackage());
            // Try legacy package
            i.setPackage(Const.LEGACY_PACKAGE);
            ret = context.bindService(i, serviceConnection, Context.BIND_AUTO_CREATE);
        }
        if (!ret) {
            android.util.Log.i("MDMService", "Failed to bind legacy service: intent " + i.getAction() + ", package " + i.getPackage());
        }

        return ret;
    }

    class RemoteServiceConnection implements ServiceConnection {

        ResultHandler handler;

        public RemoteServiceConnection(ResultHandler handler) {
            super();
            this.handler = handler;
        }

        public void onServiceConnected(ComponentName name, IBinder boundService) {
            mdmApi = IMdmApi.Stub.asInterface((IBinder) boundService);
            if (handler != null) {
                handler.onMDMConnected();
            }
        }

        public void onServiceDisconnected(ComponentName name) {
            mdmApi = null;
            if (handler != null) {
                handler.onMDMDisconnected();
            }
        }
    }

    /**
     * Get version
     */
    public int getVersion() throws MDMException {
        if (mdmApi == null) {
            throw new MDMException(MDMError.ERROR_DISCONNECTED);
        }

        try {
            return mdmApi.getVersion();
        } catch (RemoteException e) {
            // No getVersion() method prior to 1.1.3, so return 0 by default
            return 0;
        }
    }

    /**
     * Request the configuration update by the app
     * This method forces the application update even if the background update is scheduled
     * Reason: this method may be called manually from a kiosk app
     */
    public void forceConfigUpdate() throws MDMException {
        if (mdmApi == null) {
            throw new MDMException(MDMError.ERROR_DISCONNECTED);
        }

        try {
            mdmApi.forceConfigUpdate();
        } catch (RemoteException e) {
            // No forceConfigUpdate() method prior to 1.1.5
        }
    }

    /**
     * Live progress events of a forced configuration update.
     * All callbacks are delivered on the main (UI) thread.
     * Error codes: SERVER = 1, NETWORK = 2 (config); DOWNLOAD = 1, INSTALL = 2 (file/app).
     */
    public interface ConfigUpdateListener {
        void onConfigUpdateStart();
        void onConfigUpdateError(int type, String errorText);
        void onConfigLoaded();
        void onPoliciesUpdated();
        void onFileDownloading(String path);
        void onDownloadProgress(int progress, long total, long current);
        void onFileError(int type, String path);
        void onAppUpdateStart();
        void onAppRemoving(String pkg, String name);
        void onAppDownloading(String pkg, String name);
        void onAppInstalling(String pkg, String name);
        void onAppError(int type, String pkg);
        void onAppInstallComplete(String pkg);
        void onConfigUpdateComplete();
        void onAllAppInstallComplete();
    }

    /**
     * Force the configuration update and receive live progress events.
     * Requires the installed launcher to be version 1.1.9 (119) or newer;
     * otherwise throws MDMException(ERROR_VERSION).
     *
     * @param listener receives progress events on the main thread (may be null,
     *                 in which case this behaves like {@link #forceConfigUpdate()})
     */
    public void forceConfigUpdate(final ConfigUpdateListener listener) throws MDMException {
        if (mdmApi == null) {
            throw new MDMException(MDMError.ERROR_DISCONNECTED);
        }

        if (getVersion() < CALLBACK_VERSION) {
            throw new MDMException(MDMError.ERROR_VERSION);
        }

        try {
            // Retain a strong reference to the callback Binder for the whole update
            // (see field declaration) so it isn't garbage-collected mid-update.
            activeConfigUpdateCallback = (listener == null) ? null : wrapListener(listener);
            mdmApi.forceConfigUpdateWithCallback(activeConfigUpdateCallback);
        } catch (RemoteException e) {
            // Defensive: if the version check above let us through but the launcher
            // doesn't actually implement this method (e.g. CALLBACK_VERSION drifted out
            // of sync with the launcher's reported version), surface it as a version
            // error so callers can fall back to plain forceConfigUpdate().
            throw new MDMException(MDMError.ERROR_VERSION);
        }
    }

    private IMdmApiCallback wrapListener(final ConfigUpdateListener listener) {
        return new IMdmApiCallback.Stub() {
            @Override
            public void onConfigUpdateStart() {
                mainHandler.post(() -> listener.onConfigUpdateStart());
            }
            @Override
            public void onConfigUpdateError(int type, String errorText) {
                mainHandler.post(() -> listener.onConfigUpdateError(type, errorText));
            }
            @Override
            public void onConfigLoaded() {
                mainHandler.post(() -> listener.onConfigLoaded());
            }
            @Override
            public void onPoliciesUpdated() {
                mainHandler.post(() -> listener.onPoliciesUpdated());
            }
            @Override
            public void onFileDownloading(String path) {
                mainHandler.post(() -> listener.onFileDownloading(path));
            }
            @Override
            public void onDownloadProgress(int progress, long total, long current) {
                mainHandler.post(() -> listener.onDownloadProgress(progress, total, current));
            }
            @Override
            public void onFileError(int type, String path) {
                mainHandler.post(() -> listener.onFileError(type, path));
            }
            @Override
            public void onAppUpdateStart() {
                mainHandler.post(() -> listener.onAppUpdateStart());
            }
            @Override
            public void onAppRemoving(String pkg, String name) {
                mainHandler.post(() -> listener.onAppRemoving(pkg, name));
            }
            @Override
            public void onAppDownloading(String pkg, String name) {
                mainHandler.post(() -> listener.onAppDownloading(pkg, name));
            }
            @Override
            public void onAppInstalling(String pkg, String name) {
                mainHandler.post(() -> listener.onAppInstalling(pkg, name));
            }
            @Override
            public void onAppError(int type, String pkg) {
                mainHandler.post(() -> listener.onAppError(type, pkg));
            }
            @Override
            public void onAppInstallComplete(String pkg) {
                mainHandler.post(() -> listener.onAppInstallComplete(pkg));
            }
            @Override
            public void onConfigUpdateComplete() {
                mainHandler.post(() -> listener.onConfigUpdateComplete());
            }
            @Override
            public void onAllAppInstallComplete() {
                mainHandler.post(() -> listener.onAllAppInstallComplete());
            }
        };
    }

    /**
     * Query configuration
     */
    public Bundle queryConfig() throws MDMException {
        if (mdmApi == null) {
            throw new MDMException(MDMError.ERROR_DISCONNECTED);
        }

        try {
            return mdmApi.queryConfig();
        } catch (RemoteException e) {
            throw new MDMException(MDMError.ERROR_INTERNAL);
        }
    }

    /**
     * Query configuration (including privileged fields)
     */
    public Bundle queryConfig(String apiKey) throws MDMException {
        if (mdmApi == null) {
            throw new MDMException(MDMError.ERROR_DISCONNECTED);
        }

        if (getVersion() <= INITIAL_VERSION) {
            throw new MDMException(MDMError.ERROR_VERSION);
        }

        try {
            Bundle config = mdmApi.queryPrivilegedConfig(apiKey);
            String error = config.getString(KEY_ERROR);
            if (error != null) {
                if (error.equals("KEY_NOT_MATCH")) {
                    throw new MDMException(MDMError.ERROR_KEY_NOT_MATCH);
                } else {
                    throw new MDMException(MDMError.ERROR_INTERNAL);
                }
            }
            return config;
        } catch (RemoteException e) {
            throw new MDMException(MDMError.ERROR_INTERNAL);
        }
    }

    /**
     * Set a custom field to send its value to the server
     */
    public void setCustom(int number, String value) throws MDMException {
        if (mdmApi == null) {
            throw new MDMException(MDMError.ERROR_DISCONNECTED);
        }

        if (getVersion() <= INITIAL_VERSION) {
            throw new MDMException(MDMError.ERROR_VERSION);
        }

        try {
            mdmApi.setCustom(number, value);
        } catch (RemoteException e) {
            throw new MDMException(MDMError.ERROR_INTERNAL);
        }
    }

    /**
     * Send a push command
     */
    public boolean sendPush(String apiKey, String type, String payload) throws MDMException {
        if (mdmApi == null) {
            throw new MDMException(MDMError.ERROR_DISCONNECTED);
        }

        if (getVersion() < 118) {
            throw new MDMException(MDMError.ERROR_VERSION);
        }

        try {
            return mdmApi.sendPush(apiKey, type, payload);
        } catch (RemoteException e) {
            throw new MDMException(MDMError.ERROR_INTERNAL);
        }
    }

    /**
     * Usage:
     * Log.v (etc)
     */
    public static class Log {
        public static final int ERROR = 1;
        public static final int WARN = 2;
        public static final int INFO = 3;
        public static final int DEBUG = 4;
        public static final int VERBOSE = 5;

        public static void log(int level, String tag, String message) {
            if (instance == null || instance.mdmApi == null || instance.context == null) {
                // Not initialized, just return
                android.util.Log.w(Const.LOG_TAG, "Remote logger not initialized!");
                return;
            }
            try {
                String finalMessage = tag != null ? tag + " " + message : message;
                instance.mdmApi.log(System.currentTimeMillis(), level, instance.context.getPackageName(), finalMessage);
            } catch (Exception e) {
                android.util.Log.w(Const.LOG_TAG, "Remote exception while trying to send a log to Headwind MDM!");
                e.printStackTrace();
            }
        }

        public static void v(String tag, String message) {
            android.util.Log.v(tag, message);
            log(VERBOSE, tag, message);
        }

        public static void d(String tag, String message) {
            android.util.Log.d(tag, message);
            log(DEBUG, tag, message);
        }

        public static void i(String tag, String message) {
            android.util.Log.i(tag, message);
            log(INFO, tag, message);
        }

        public static void w(String tag, String message) {
            android.util.Log.w(tag, message);
            log(WARN, tag, message);
        }

        public static void e(String tag, String message) {
            android.util.Log.e(tag, message);
            log(ERROR, tag, message);
        }
    }

    public static class Preferences {

        public static String get(String attr, String defValue) {
            if (instance == null || instance.mdmApi == null || instance.context == null) {
                // Not initialized, just return
                android.util.Log.w(Const.LOG_TAG, "Connection to Headwind MDM not initialized!");
                return defValue;
            }
            try {
                String result = instance.mdmApi.queryAppPreference(instance.context.getPackageName(), attr);
                if (result == null) {
                    return defValue;
                }
                return result;
            } catch (Exception e) {
                android.util.Log.w(Const.LOG_TAG, "Remote exception while trying to get Headwind MDM app preference " + attr);
                e.printStackTrace();
            }
            return defValue;
        }

        public static boolean set(String attr, String value) {
            if (instance == null || instance.mdmApi == null || instance.context == null) {
                // Not initialized, just return
                android.util.Log.w(Const.LOG_TAG, "Connection to Headwind MDM not initialized!");
            }
            try {
                return instance.mdmApi.setAppPreference(instance.context.getPackageName(), attr, value);
            } catch (Exception e) {
                android.util.Log.w(Const.LOG_TAG, "Remote exception while trying to set Headwind MDM app preference " + attr + "=" + value);
                e.printStackTrace();
            }
            return false;
        }

        public static void apply() {
            if (instance == null || instance.mdmApi == null || instance.context == null) {
                // Not initialized, just return
                android.util.Log.w(Const.LOG_TAG, "Connection to Headwind MDM not initialized!");
                return;
            }
            try {
                instance.mdmApi.commitAppPreferences(instance.context.getPackageName());
            } catch (Exception e) {
                android.util.Log.w(Const.LOG_TAG, "Remote exception while trying to apply Headwind MDM app preferences!");
                e.printStackTrace();
            }
        }
    }

}
