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
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.hmdm.launcher.Const;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.json.PushMessage;
import com.hmdm.launcher.json.PushResponse;
import com.hmdm.launcher.server.ServerService;
import com.hmdm.launcher.server.ServerServiceKeeper;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import retrofit2.Response;

public class PushNotificationPollingService extends Service {

    // Minimal interval is 15 minutes as per docs
    public static final int FIRE_PERIOD_MINS = 15;

    private static final String WORK_TAG_ONETIME = "com.hmdm.launcher.WORK_TAG_PUSH_ONETIME";
    private static final String WORK_TAG_PERIODIC = "com.hmdm.launcher.WORK_TAG_PUSH_PERIODIC";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        // This one is just for test purposes!
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(QueryPushWorker.class)
                .addTag(Const.WORK_TAG_COMMON)
                .build();
        WorkManager.getInstance(this).enqueueUniqueWork(WORK_TAG_ONETIME, ExistingWorkPolicy.REPLACE, request);

        PeriodicWorkRequest queryRequest =
                new PeriodicWorkRequest.Builder(QueryPushWorker.class, FIRE_PERIOD_MINS, TimeUnit.MINUTES)
                        .addTag(Const.WORK_TAG_COMMON)
                        .build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(WORK_TAG_PERIODIC, ExistingPeriodicWorkPolicy.REPLACE, queryRequest);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    public static class QueryPushWorker extends Worker {

        private Context context;
        private SettingsHelper settingsHelper;

        public QueryPushWorker(
                @NonNull final Context context,
                @NonNull WorkerParameters params) {
            super(context, params);
            this.context = context;
            settingsHelper = SettingsHelper.getInstance(context);
        }

        @Override
        // This is running in a background thread by WorkManager
        public Result doWork() {
            if (settingsHelper == null || settingsHelper.getConfig() == null) {
                return Result.failure();
            }

            ServerService serverService = ServerServiceKeeper.getServerServiceInstance(context);
            ServerService secondaryServerService = ServerServiceKeeper.getSecondaryServerServiceInstance(context);
            Response<PushResponse> response = null;

            try {
                response = serverService.
                        queryPushNotifications(settingsHelper.getServerProject(), settingsHelper.getDeviceId()).execute();
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                if (response == null) {
                    response = secondaryServerService.
                            queryPushNotifications(settingsHelper.getServerProject(), settingsHelper.getDeviceId()).execute();
                }

                if ( response.isSuccessful() ) {
                    if ( Const.STATUS_OK.equals( response.body().getStatus() ) && response.body().getData() != null ) {
                        Map<String, PushMessage> filteredMessages = new HashMap<String, PushMessage>();
                        for (PushMessage message : response.body().getData()) {
                            // Filter out multiple configuration update requests
                            if (!message.getMessageType().equals(PushMessage.TYPE_CONFIG_UPDATED) ||
                                    !filteredMessages.containsKey(PushMessage.TYPE_CONFIG_UPDATED)) {
                                filteredMessages.put(message.getMessageType(), message);
                            }
                        }
                        for (Map.Entry<String, PushMessage> entry : filteredMessages.entrySet()) {
                            PushNotificationProcessor.process(entry.getValue(), context);
                        }
                        return Result.success();
                    } else {
                        return Result.failure();
                    }
                }
            } catch ( Exception e ) {
                e.printStackTrace();
            }

            return Result.failure();
        }
    }
}
