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

import com.hmdm.launcher.Const;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.json.DeviceInfo;
import com.hmdm.launcher.receiver.WakefulAlarmReceiver;
import com.hmdm.launcher.server.ServerService;
import com.hmdm.launcher.server.ServerServiceKeeper;
import com.hmdm.launcher.util.AlarmUtils;
import com.hmdm.launcher.util.DeviceInfoProvider;

import java.util.concurrent.TimeUnit;

import okhttp3.ResponseBody;
import retrofit2.Response;

// Periodically reports device info to the server. Runs on a self-rescheduling AlarmManager
// alarm instead of a WorkManager PeriodicWorkRequest (see WakefulAlarmReceiver for why).
public class SendDeviceInfoWorker extends WakefulAlarmReceiver {

    private static final int SEND_DEVICE_INFO_PERIOD_MINS = 15;

    private static final String ACTION_FIRE = "com.hmdm.launcher.action.SEND_DEVICE_INFO_ALARM";

    public static void scheduleDeviceInfoSending(Context context) {
        AlarmUtils.scheduleAlarm(context, createPendingIntent(context), TimeUnit.MINUTES.toMillis(SEND_DEVICE_INFO_PERIOD_MINS));
    }

    private static PendingIntent createPendingIntent(Context context) {
        Intent intent = new Intent(context, SendDeviceInfoWorker.class);
        intent.setAction(ACTION_FIRE);
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override
    protected String getWakeLockTag() {
        return "hmdm:SendDeviceInfoWorker";
    }

    @Override
    protected void doWork(Context context, Intent intent) {
        try {
            SettingsHelper settingsHelper = SettingsHelper.getInstance();
            if (settingsHelper == null || settingsHelper.getConfig() == null) {
                return;
            }

            DeviceInfo deviceInfo = DeviceInfoProvider.getDeviceInfo(context, true, true);

            ServerService serverService = ServerServiceKeeper.getServerServiceInstance(context);
            ServerService secondaryServerService = ServerServiceKeeper.getSecondaryServerServiceInstance(context);
            Response<ResponseBody> response = null;

            try {
                response = serverService.sendDevice(settingsHelper.getServerProject(), deviceInfo).execute();
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                if (response == null) {
                    response = secondaryServerService.sendDevice(settingsHelper.getServerProject(), deviceInfo).execute();
                }
                if ( response.isSuccessful() ) {
                    SettingsHelper.getInstance().setExternalIp(response.headers().get(Const.HEADER_IP_ADDRESS));
                }
            }
            catch ( Exception e ) { e.printStackTrace(); }
        } finally {
            // Reschedule the next firing regardless of the outcome of this one
            scheduleDeviceInfoSending(context);
        }
    }
}
