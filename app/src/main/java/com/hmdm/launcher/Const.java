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

package com.hmdm.launcher;

public class Const {

    public static final int TASK_SUCCESS = 0;
    public static final int TASK_ERROR = 1;
    public static final int TASK_NETWORK_ERROR = 2;

    public static final String ACTION_SERVICE_STOP = "SERVICE_STOP";
    public static final String ACTION_SHOW_LAUNCHER = "SHOW_LAUNCHER";
    public static final String ACTION_ENABLE_SETTINGS = "ENABLE_SETTINGS";
    public static final String ACTION_EXIT = "EXIT";
    public static final String ACTION_HIDE_SCREEN = "HIDE_SCREEN";
    public static final String ACTION_UPDATE_CONFIGURATION = "UPDATE_CONFIGURATION";
    public static final String ACTION_ADMIN = "ADMIN";
    public static final String ACTION_INSTALL_COMPLETE = "INSTALL_COMPLETE";

    public static long CONNECTION_TIMEOUT = 10000;
    public static final String STATUS_OK = "OK";
    public static final String ORIENTATION = "ORIENTATION";
    public static final String PACKAGE_NAME = "PACKAGE_NAME";

    public static final String PREFERENCES = "PREFERENCES";

    public static final int PREFERENCES_ON = 1;
    public static final int PREFERENCES_OFF = 0;

    public static final String PREFERENCES_ADMINISTRATOR = "PREFERENCES_ADMINISTRATOR";
    public static final String PREFERENCES_OVERLAY = "PREFERENCES_OVERLAY";
    public static final String PREFERENCES_USAGE_STATISTICS = "PREFERENCES_USAGE_STATISTICS";
    public static final String PREFERENCES_ACCESSIBILITY_SERVICE = "PREFERENCES_ACCESSIBILITY_SERVICE";
    public static final String PREFERENCES_DEVICE_OWNER = "PREFERENCES_DEVICE_OWNER";
    public static final String PREFERENCES_UNKNOWN_SOURCES = "PREFERENCES_UNKNOWN_SOURCES";
    public static final String PREFERENCES_DISABLE_LOCATION = "PREFERENCES_DISABLE_LOCATION";
    public static final String PREFERENCES_MIUI_PERMISSIONS = "PREFERENCES_MIUI_PERMISSIONS";

    public static final String LOG_TAG = "HeadwindMDM";

    public static final int SETTINGS_UNBLOCK_TIME = 180000;

    public static final String LAUNCHER_RESTARTER_PACKAGE_ID = "com.hmdm.emuilauncherrestarter";
    public static final String LAUNCHER_RESTARTER_OLD_VERSION = "oldVersion";

    public static final String SETTINGS_PACKAGE_NAME = "com.android.settings";
    public static final String GSF_PACKAGE_NAME = "com.google.android.gsf";
    public static final String SYSTEM_UI_PACKAGE_NAME = "com.android.systemui";

    public static final String QR_BASE_URL_ATTR = "com.hmdm.BASE_URL";
    public static final String QR_SECONDARY_BASE_URL_ATTR = "com.hmdm.SECONDARY_BASE_URL";
    public static final String QR_SERVER_PROJECT_ATTR = "com.hmdm.SERVER_PROJECT";
    public static final String QR_DEVICE_ID_ATTR = "com.hmdm.DEVICE_ID";
    public static final String QR_LEGACY_DEVICE_ID_ATTR = "ru.headwind.kiosk.DEVICE_ID";

    public static final int KIOSK_UNLOCK_CLICK_COUNT = 4;

    public static final String INTENT_PUSH_NOTIFICATION_PREFIX = "com.hmdm.push.";
    public static final String INTENT_PUSH_NOTIFICATION_EXTRA = "com.hmdm.PUSH_DATA";

    public static final String WORK_TAG_COMMON = "com.hmdm.launcher";

    public static final String DEVICE_CHARGING_USB = "usb";
    public static final String DEVICE_CHARGING_AC = "ac";

    public static final String WIFI_STATE_FAILED = "failed";
    public static final String WIFI_STATE_INACTIVE = "inactive";
    public static final String WIFI_STATE_SCANNING = "scanning";
    public static final String WIFI_STATE_DISCONNECTED = "disconnected";
    public static final String WIFI_STATE_CONNECTING = "connecting";
    public static final String WIFI_STATE_CONNECTED = "connected";

    public static final String GPS_STATE_INACTIVE = "inactive";
    public static final String GPS_STATE_LOST = "lost";
    public static final String GPS_STATE_ACTIVE = "active";

    public static final String MOBILE_STATE_INACTIVE = "inactive";
    public static final String MOBILE_STATE_DISCONNECTED = "disconnected";
    public static final String MOBILE_STATE_CONNECTED = "connected";

    public static final String MOBILE_SIMSTATE_UNKNOWN = "unknown";
    public static final String MOBILE_SIMSTATE_ABSENT = "absent";
    public static final String MOBILE_SIMSTATE_PIN_REQUIRED = "pinRequired";
    public static final String MOBILE_SIMSTATE_PUK_REQUIRED = "pukRequired";
    public static final String MOBILE_SIMSTATE_LOCKED = "locked";
    public static final String MOBILE_SIMSTATE_READY = "ready";
    public static final String MOBILE_SIMSTATE_NOT_READY = "notReady";
    public static final String MOBILE_SIMSTATE_DISABLED = "disabled";
    public static final String MOBILE_SIMSTATE_ERROR = "error";
    public static final String MOBILE_SIMSTATE_RESTRICTED = "restricted";

    public static final int LOG_ERROR = 1;
    public static final int LOG_WARN = 2;
    public static final int LOG_INFO = 3;
    public static final int LOG_DEBUG = 4;
    public static final int LOG_VERBOSE = 5;
}
