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

package com.hmdm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

public abstract class MDMPushHandler {
    private BroadcastReceiver receiver;

    // This should be overridden
    public abstract void onMessageReceived(MDMPushMessage message);

    public void register(String messageType, Context context) {
        register(new String[] { messageType }, context);
    }

    public void register(String[] messageTypes, Context context) {
        if (messageTypes.length == 0) {
            return;
        }

        IntentFilter intentFilter = new IntentFilter();
        for (int n = 0; n < messageTypes.length; n++) {
            intentFilter.addAction(Const.INTENT_PUSH_NOTIFICATION_PREFIX + messageTypes[n]);
        }
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                try {
                    MDMPushMessage message = new MDMPushMessage(intent.getAction(), intent.getExtras());
                    onMessageReceived(message);
                } catch (MDMException e) {
                    e.printStackTrace();
                }
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.registerReceiver(receiver, intentFilter, Context.RECEIVER_EXPORTED);
        } else {
            context.registerReceiver(receiver, intentFilter);
        }
    }

    public void unregister(Context context) {
        if (receiver != null) {
            context.unregisterReceiver(receiver);
        }
    }
}
