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
import android.content.Intent;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.hmdm.launcher.Const;
import com.hmdm.launcher.json.PushMessage;
import com.hmdm.launcher.util.RemoteLogger;

import org.json.JSONObject;

public class PushNotificationProcessor {
    public static void process(PushMessage message, Context context) {
        RemoteLogger.log(context, Const.LOG_DEBUG, "Got Push Message, type " + message.getMessageType());
        if (message.getMessageType().equals(PushMessage.TYPE_CONFIG_UPDATED)) {
            // Update local configuration
            LocalBroadcastManager.getInstance(context).
                    sendBroadcast(new Intent(Const.ACTION_UPDATE_CONFIGURATION));
        }
        // Send broadcast to all plugins
        Intent intent = new Intent(Const.INTENT_PUSH_NOTIFICATION_PREFIX + message.getMessageType());
        JSONObject jsonObject = message.getPayloadJSON();
        if (jsonObject != null) {
            intent.putExtra(Const.INTENT_PUSH_NOTIFICATION_EXTRA, jsonObject.toString());
        }
        context.sendBroadcast(intent);
    }
}
