package com.hmdm.launcher.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.hmdm.launcher.Const;

public class ConnectionWaiter {
    private static Handler handler = new Handler(Looper.getMainLooper());

    public static boolean waitForConnect(Context context, Runnable uiCallback) {
        for (int n = 0; n < 10; n++) {
            if (isNetworkAvailable(context)) {
                if (n > 0) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                Log.d(Const.LOG_TAG, "Network is available, resuming flow");
                handler.post(uiCallback);
                return true;
            }
            Log.d(Const.LOG_TAG, "Network is unavailable, waiting, attempts: " + (9 - n));
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        Log.d(Const.LOG_TAG, "Proceed without network!");
        handler.post(uiCallback);
        return false;
    }

    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = cm.getActiveNetwork();
            if (network == null) return false;

            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            return capabilities != null &&
                    (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        } else {
            android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnected();
        }
    }
}
