package com.hmdm.launcher.receiver;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.hmdm.launcher.helper.Initializer;
import com.hmdm.launcher.helper.SettingsHelper;

public class LockedBootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootLocked";
    private static final int RETRY_DELAY = 5 * 1000;
    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onReceive(Context context, Intent intent) {
        tryCheckNetwork(context, 1);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void tryCheckNetwork(Context context, int attempt) {
       Context deviceContext = context.createDeviceProtectedStorageContext();

        Log.d(TAG, "Trying network check - Attempt: " + attempt);

        if (isNetworkAvailable(deviceContext)) {
            Log.d(TAG, "✅ Network AVAILABLE on attempt: " + attempt);

            SettingsHelper settingsHelper = SettingsHelper.getInstance(deviceContext.getApplicationContext());
            if (!settingsHelper.isBaseUrlSet()) {
                // We're here before initializing after the factory reset! Let's ignore this call
                return;
            }

            long lastAppStartTime = settingsHelper.getAppStartTime();
            long bootTime = System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime();
            Log.d(TAG, "appStartTime=" + lastAppStartTime + ", bootTime=" + bootTime);
            if (lastAppStartTime < bootTime) {
                Log.i(TAG, "Headwind MDM wasn't started since boot, start initializing services");
            } else {
                Log.i(TAG, "Headwind MDM is already started, ignoring BootReceiver");
                return;
            }

//            Initializer.init(deviceContext);
            //todo to be set to false in case not locked_boot_completed fired
            SettingsHelper.getInstance(context).setLockedBootReceiverFired(true);
            Initializer.startServicesAndLoadConfig(deviceContext);
            SettingsHelper.getInstance(context).setMainActivityRunning(false);


        } else {
            Log.d(TAG, "❌ Network NOT available on attempt: " + attempt);
            if (attempt < 5) {
                Log.d(TAG, "Scheduling retry after " + (RETRY_DELAY / 1000) + " seconds");
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    tryCheckNetwork(context, attempt + 1);
                }, RETRY_DELAY);

            } else {
                Log.e(TAG, "❗ Network failed after max retries");
            }
        }
    }

    private boolean isNetworkAvailable(Context context) {
        Log.d(TAG, "Checking network availability");

        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (cm == null) {
            Log.e(TAG, "ConnectivityManager is NULL");
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = cm.getActiveNetwork();
            if (network == null) {
                Log.d(TAG, "Active network is NULL");
                return false;
            }

            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            if (capabilities == null) {
                Log.d(TAG, "NetworkCapabilities is NULL");
                return false;
            }

            boolean hasInternet =
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                            && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);

            Log.d(TAG, "Network validated internet = " + hasInternet);
            return hasInternet;

        } else {
            NetworkInfo info = cm.getActiveNetworkInfo();
            boolean connected = info != null && info.isConnected();
            Log.d(TAG, "Legacy network connected = " + connected);
            return connected;
        }
    }
}
