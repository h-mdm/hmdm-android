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

import android.os.Bundle;

import org.json.JSONException;
import org.json.JSONObject;

public class MDMPushMessage {
    private String type;
    private JSONObject data;

    public static final String MessageConfigUpdated = "configUpdated";

    public MDMPushMessage(String action, Bundle bundle) throws MDMException {
        if (!action.startsWith(Const.INTENT_PUSH_NOTIFICATION_PREFIX)) {
            throw new MDMException(MDMError.ERROR_INVALID_PARAMETER);
        }
        type = action.substring(Const.INTENT_PUSH_NOTIFICATION_PREFIX.length());
        if (bundle != null) {
            String packedPayload = bundle.getString(Const.INTENT_PUSH_NOTIFICATION_EXTRA);
            if (packedPayload != null) {
                try {
                    data = new JSONObject(packedPayload);
                } catch (JSONException e) {
                    throw new MDMException(MDMError.ERROR_INVALID_PARAMETER);
                }
            }
        }
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public JSONObject getData() {
        return data;
    }

    public void setData(JSONObject data) {
        this.data = data;
    }
}
