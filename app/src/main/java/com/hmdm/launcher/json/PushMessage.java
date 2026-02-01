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

package com.hmdm.launcher.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.json.JSONObject;

@JsonIgnoreProperties( ignoreUnknown = true )
public class PushMessage {
    private String messageType;
    private String payload;

    public static final String TYPE_CONFIG_UPDATING = "configUpdating";
    public static final String TYPE_CONFIG_UPDATED = "configUpdated";
    public static final String TYPE_RUN_APP = "runApp";
    public static final String TYPE_BROADCAST = "broadcast";
    public static final String TYPE_UNINSTALL_APP = "uninstallApp";
    public static final String TYPE_DELETE_FILE = "deleteFile";
    public static final String TYPE_PURGE_DIR = "purgeDir";
    public static final String TYPE_DELETE_DIR = "deleteDir";
    public static final String TYPE_PERMISSIVE_MODE = "permissiveMode";
    public static final String TYPE_RUN_COMMAND = "runCommand";
    public static final String TYPE_REBOOT = "reboot";
    public static final String TYPE_EXIT_KIOSK = "exitKiosk";
    public static final String TYPE_CLEAR_DOWNLOADS = "clearDownloadHistory";
    public static final String TYPE_INTENT = "intent";
    public static final String TYPE_GRANT_PERMISSIONS = "grantPermissions";
    public static final String TYPE_ADMIN_PANEL = "adminPanel";
    public static final String TYPE_CLEAR_APP_DATA = "clearAppData";

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType( String messageType ) {
        this.messageType = messageType;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public JSONObject getPayloadJSON() {
        if (payload != null) {
            try {
                return new JSONObject(payload);
            } catch (Exception e) {
                // Bad payload
            }
        }
        return null;
    }
}
