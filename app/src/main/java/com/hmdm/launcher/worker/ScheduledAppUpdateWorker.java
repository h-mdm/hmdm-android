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

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.hmdm.launcher.Const;
import com.hmdm.launcher.helper.ConfigUpdater;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.util.RemoteLogger;

import java.util.concurrent.TimeUnit;

public class ScheduledAppUpdateWorker extends Worker {

    // Minimal interval is 15 minutes as per docs
    public static final int FIRE_PERIOD_MINS = 15;

    private static final String WORK_TAG_SCHEDULED_UPDATES = "com.hmdm.launcher.WORK_TAG_SCHEDULED_UPDATES";

    public static void schedule(Context context) {
        SettingsHelper settingsHelper = SettingsHelper.getInstance(context);
        if (settingsHelper.getConfig() != null) {
            settingsHelper.setLastAppUpdateState(ConfigUpdater.checkAppUpdateTimeRestriction(settingsHelper.getConfig()));
        }
        Log.d(Const.LOG_TAG, "Scheduled app updates worker runs each " + FIRE_PERIOD_MINS + " mins");
        PeriodicWorkRequest queryRequest =
                new PeriodicWorkRequest.Builder(ScheduledAppUpdateWorker.class, FIRE_PERIOD_MINS, TimeUnit.MINUTES)
                        .addTag(Const.WORK_TAG_COMMON)
                        .build();
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniquePeriodicWork(WORK_TAG_SCHEDULED_UPDATES,
                ExistingPeriodicWorkPolicy.REPLACE, queryRequest);
    }

    private Context context;
    private SettingsHelper settingsHelper;

    public ScheduledAppUpdateWorker(
            @NonNull final Context context,
            @NonNull WorkerParameters params) {
        super(context, params);
        this.context = context;
        settingsHelper = SettingsHelper.getInstance(context);
    }

    @Override
    // This is running in a background thread by WorkManager
    public Result doWork() {
        if (settingsHelper.getConfig() == null) {
            Log.d(Const.LOG_TAG, "ScheduledAppUpdateWorker: config=null");
            return Result.failure();
        }
        if (settingsHelper.getConfig().getAppUpdateFrom() == null || settingsHelper.getConfig().getAppUpdateTo() == null) {
            // No need to do anything
            Log.d(Const.LOG_TAG, "ScheduledAppUpdateWorker: scheduled app update not set");
            return Result.success();
        }

        boolean lastAppUpdateState = settingsHelper.getLastAppUpdateState();
        boolean canUpdateAppsNow = ConfigUpdater.checkAppUpdateTimeRestriction(settingsHelper.getConfig());
        Log.d(Const.LOG_TAG, "ScheduledAppUpdateWorker: lastAppUpdateState=" + lastAppUpdateState + ", canUpdateAppsNow=" + canUpdateAppsNow);
        if (lastAppUpdateState == canUpdateAppsNow) {
            // App update state not changed
            return Result.success();
        }

        if (!lastAppUpdateState && canUpdateAppsNow) {
            // Need to update apps now
            RemoteLogger.log(context, Const.LOG_DEBUG, "Running scheduled app update");
            settingsHelper.setConfigUpdateTimestamp(System.currentTimeMillis());
            ConfigUpdater.forceConfigUpdate(context);
        }
        settingsHelper.setLastAppUpdateState(canUpdateAppsNow);
        return Result.success();
    }
}
