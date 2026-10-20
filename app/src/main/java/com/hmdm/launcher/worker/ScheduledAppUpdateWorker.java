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

package com.hmdm.launcher.worker;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.hmdm.launcher.Const;
import com.hmdm.launcher.helper.ConfigUpdater;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.receiver.WakefulAlarmReceiver;
import com.hmdm.launcher.util.AlarmUtils;
import com.hmdm.launcher.util.RemoteLogger;

import java.util.concurrent.TimeUnit;

// Watches the scheduled app-update time window and forces a config update whenever the
// device enters or leaves it. Runs on a self-rescheduling AlarmManager alarm instead of a
// WorkManager PeriodicWorkRequest (see WakefulAlarmReceiver for why).
public class ScheduledAppUpdateWorker extends WakefulAlarmReceiver {

    // Minimal interval is 15 minutes, kept the same cadence as before
    public static final int FIRE_PERIOD_MINS = 15;

    private static final String ACTION_FIRE = "com.hmdm.launcher.action.SCHEDULED_APP_UPDATE_ALARM";

    public static void schedule(Context context) {
        SettingsHelper settingsHelper = SettingsHelper.getInstance();
        if (settingsHelper.getConfig() != null) {
            settingsHelper.setLastAppUpdateState(ConfigUpdater.checkAppUpdateTimeRestriction(settingsHelper.getConfig()));
        }
        Log.d(Const.LOG_TAG, "Scheduled app updates alarm runs each " + FIRE_PERIOD_MINS + " mins");
        AlarmUtils.scheduleAlarm(context, createPendingIntent(context), TimeUnit.MINUTES.toMillis(FIRE_PERIOD_MINS));
    }

    private static PendingIntent createPendingIntent(Context context) {
        Intent intent = new Intent(context, ScheduledAppUpdateWorker.class);
        intent.setAction(ACTION_FIRE);
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override
    protected String getWakeLockTag() {
        return "hmdm:ScheduledAppUpdateWorker";
    }

    @Override
    protected void doWork(Context context, Intent intent) {
        try {
            SettingsHelper settingsHelper = SettingsHelper.getInstance();
            if (settingsHelper.getConfig() == null) {
                Log.d(Const.LOG_TAG, "ScheduledAppUpdateWorker: config=null");
                return;
            }
            if (settingsHelper.getConfig().getAppUpdateFrom() == null || settingsHelper.getConfig().getAppUpdateTo() == null) {
                // No need to do anything
                Log.d(Const.LOG_TAG, "ScheduledAppUpdateWorker: scheduled app update not set");
                return;
            }

            boolean lastAppUpdateState = settingsHelper.getLastAppUpdateState();
            boolean canUpdateAppsNow = ConfigUpdater.checkAppUpdateTimeRestriction(settingsHelper.getConfig());
            Log.d(Const.LOG_TAG, "ScheduledAppUpdateWorker: lastAppUpdateState=" + lastAppUpdateState + ", canUpdateAppsNow=" + canUpdateAppsNow);
            if (lastAppUpdateState == canUpdateAppsNow) {
                // App update state not changed
                return;
            }

            if (!lastAppUpdateState && canUpdateAppsNow) {
                // Need to update apps now
                RemoteLogger.log(context, Const.LOG_DEBUG, "Running scheduled app update");
                settingsHelper.setConfigUpdateTimestamp(System.currentTimeMillis());
                ConfigUpdater.forceConfigUpdate(context);
            }
            settingsHelper.setLastAppUpdateState(canUpdateAppsNow);
        } finally {
            // Reschedule the next firing regardless of the outcome of this one
            schedule(context);
        }
    }
}
