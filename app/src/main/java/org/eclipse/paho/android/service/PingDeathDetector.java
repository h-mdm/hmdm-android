package org.eclipse.paho.android.service;

import android.content.Context;
import android.util.Log;

import com.hmdm.launcher.Const;
import com.hmdm.launcher.util.RemoteLogger;

public class PingDeathDetector {
    private static PingDeathDetector instance;

    private long lastPingTimestamp = 0;

    private PingDeathDetector() {
    }

    public static PingDeathDetector getInstance() {
        if (instance == null) {
            instance = new PingDeathDetector();
        }
        return instance;
    }

    public void registerPing() {
        lastPingTimestamp = System.currentTimeMillis();
    }

    public boolean detectPingDeath(Context context) {
        Log.d(Const.LOG_TAG, "checkPingDeath(): last connect " + lastPingTimestamp + ", current time " + System.currentTimeMillis());
        boolean pingDeath = lastPingTimestamp != 0 && lastPingTimestamp < System.currentTimeMillis() - 1800000;
        if (pingDeath) {
            RemoteLogger.log(context, Const.LOG_DEBUG, "PingDeathDetector: lastPingTimestamp=" + lastPingTimestamp +
                    ", now=" + System.currentTimeMillis());
        }
        return pingDeath;
    }
}
