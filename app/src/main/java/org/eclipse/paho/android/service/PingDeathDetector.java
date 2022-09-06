package org.eclipse.paho.android.service;

import android.util.Log;

import com.hmdm.launcher.Const;

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

    public boolean detectPingDeath() {
        Log.d(Const.LOG_TAG, "checkPingDeath(): last connect " + lastPingTimestamp + ", current time " + System.currentTimeMillis());
        return lastPingTimestamp < System.currentTimeMillis() - 1800000;
    }
}
