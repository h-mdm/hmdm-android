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

package com.hmdm.launcher.receiver;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.hmdm.launcher.Const;
import com.hmdm.launcher.util.AlarmUtils;
import com.hmdm.launcher.util.PushNotificationMqttWrapper;

// Retries an MQTT connection after a connect failure. Runs on an AlarmManager one-shot alarm
// instead of a WorkManager OneTimeWorkRequest (see WakefulAlarmReceiver for why).
public class MqttReconnectAlarmReceiver extends WakefulAlarmReceiver {

    private static final String ACTION_FIRE = "com.hmdm.launcher.action.MQTT_RECONNECT_ALARM";

    private static final String EXTRA_HOST = "host";
    private static final String EXTRA_PORT = "port";
    private static final String EXTRA_PUSH_TYPE = "pushType";
    private static final String EXTRA_KEEPALIVE = "keepalive";
    private static final String EXTRA_DEVICE_ID = "deviceId";

    public static void schedule(Context context, String host, int port, String pushType, int keepaliveTime, String deviceId, long delayMillis) {
        Intent intent = new Intent(context, MqttReconnectAlarmReceiver.class);
        intent.setAction(ACTION_FIRE);
        intent.putExtra(EXTRA_HOST, host);
        intent.putExtra(EXTRA_PORT, port);
        intent.putExtra(EXTRA_PUSH_TYPE, pushType);
        intent.putExtra(EXTRA_KEEPALIVE, keepaliveTime);
        intent.putExtra(EXTRA_DEVICE_ID, deviceId);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmUtils.scheduleAlarm(context, pendingIntent, delayMillis);
    }

    public static void cancel(Context context) {
        // The extras don't take part in PendingIntent identity, only action/component do,
        // so an empty intent is enough to cancel a previously scheduled reconnect alarm.
        Intent intent = new Intent(context, MqttReconnectAlarmReceiver.class);
        intent.setAction(ACTION_FIRE);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmUtils.cancelAlarm(context, pendingIntent);
    }

    @Override
    protected String getWakeLockTag() {
        return "hmdm:MqttReconnectAlarmReceiver";
    }

    @Override
    protected void doWork(Context context, Intent intent) {
        PushNotificationMqttWrapper.getInstance().connect(context, intent.getStringExtra(EXTRA_HOST),
                intent.getIntExtra(EXTRA_PORT, 0), intent.getStringExtra(EXTRA_PUSH_TYPE),
                intent.getIntExtra(EXTRA_KEEPALIVE, Const.DEFAULT_PUSH_ALARM_KEEPALIVE_TIME_SEC),
                intent.getStringExtra(EXTRA_DEVICE_ID), null, null);
    }
}
