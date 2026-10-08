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
import com.hmdm.launcher.db.DatabaseHelper;
import com.hmdm.launcher.db.LogTable;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.json.RemoteLogItem;
import com.hmdm.launcher.receiver.WakefulAlarmReceiver;
import com.hmdm.launcher.server.ServerService;
import com.hmdm.launcher.server.ServerServiceKeeper;
import com.hmdm.launcher.util.AlarmUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.ResponseBody;
import retrofit2.Response;

// Uploads pending remote log entries to the server. Runs on an AlarmManager one-shot alarm
// instead of a WorkManager OneTimeWorkRequest (see WakefulAlarmReceiver for why).
public class RemoteLogWorker extends WakefulAlarmReceiver {

    // Amount of log messages sent to server at once
    public static final int MAX_UPLOADED_MESSAGES = 10;

    // Logs are sent once per minute to reduce the server load
    public static final int FIRE_PERIOD_MINS = 1;

    // If there's no Internet, retry in 15 minutes
    public static final int FIRE_PERIOD_RETRY_MINS = 15;

    private static final String ACTION_FIRE = "com.hmdm.launcher.action.REMOTE_LOG_ALARM";

    private static boolean uploadScheduled = false;

    public static void resetState() {
        uploadScheduled = false;
    }

    public static void scheduleUpload(Context context) {
        scheduleUpload(context, 0);
    }

    public static synchronized void scheduleUpload(Context context, int delayMins) {
        Log.i(Const.LOG_TAG, "RemoteLogWorker scheduled");
        if (!uploadScheduled) {
            uploadScheduled = true;
            AlarmUtils.scheduleAlarm(context, createPendingIntent(context), TimeUnit.MINUTES.toMillis(delayMins));
        }
    }

    private static PendingIntent createPendingIntent(Context context) {
        Intent intent = new Intent(context, RemoteLogWorker.class);
        intent.setAction(ACTION_FIRE);
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override
    protected String getWakeLockTag() {
        return "hmdm:RemoteLogWorker";
    }

    @Override
    protected void doWork(Context context, Intent intent) {
        SettingsHelper settingsHelper = SettingsHelper.getInstance();
        try {
            DatabaseHelper dbHelper = DatabaseHelper.instance(context);

            while (true) {
                List<RemoteLogItem> unsentItems = LogTable.select(dbHelper.getReadableDatabase(), MAX_UPLOADED_MESSAGES);
                Log.i(Const.LOG_TAG, "Remote logger: unsent items: " + unsentItems.size());
                if (unsentItems.size() == 0) {
                    uploadScheduled = false;
                    return;
                }
                if (!upload(context, settingsHelper, unsentItems)) {
                    // There was an error: retry!
                    Log.i(Const.LOG_TAG, "Failed to upload logs: retry in " + FIRE_PERIOD_RETRY_MINS + " mins");
                    uploadScheduled = false;
                    scheduleUpload(context, FIRE_PERIOD_RETRY_MINS);
                    return;
                } else {
                    Log.i(Const.LOG_TAG, "Logs are uploaded");
                    // Mark items as sent and query next items
                    LogTable.delete(DatabaseHelper.instance(context).getWritableDatabase(), unsentItems);
                }
            }
        } catch (Exception e) {
            // Oops... WTF? We need to retry!
            e.printStackTrace();
            uploadScheduled = false;
            scheduleUpload(context, FIRE_PERIOD_MINS);
        }
    }

    // Returns true on success and false on failure
    public boolean upload(Context context, SettingsHelper settingsHelper, List<RemoteLogItem> logItems) {
        ServerService serverService = ServerServiceKeeper.getServerServiceInstance(context);
        ServerService secondaryServerService = ServerServiceKeeper.getSecondaryServerServiceInstance(context);
        Response<ResponseBody> response = null;

        try {
            response = serverService.sendLogs(settingsHelper.getServerProject(), settingsHelper.getDeviceId(), logItems).execute();
            return response.isSuccessful();
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            if (response == null) {
                response = secondaryServerService.
                        sendLogs(settingsHelper.getServerProject(), settingsHelper.getDeviceId(), logItems).execute();
                return response.isSuccessful();
            }
        } catch ( Exception e ) {
            e.printStackTrace();
        }
        return false;
    }
}
