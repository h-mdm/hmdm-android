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

class Const {
    static final String SERVICE_ACTION = "com.hmdm.action.Connect";
    static final String PACKAGE = "com.hmdm.launcher";
    static final String LEGACY_PACKAGE = "ru.headwind.kiosk";
    static final String ADMIN_RECEIVER_CLASS = "com.hmdm.launcher.AdminReceiver";

    public static final String INTENT_PUSH_NOTIFICATION_PREFIX = "com.hmdm.push.";
    public static final String INTENT_PUSH_NOTIFICATION_EXTRA = "com.hmdm.PUSH_DATA";

    public static final String LOG_TAG ="HeadwindMDMAPI";

    public static final String NOTIFICATION_CONFIG_UPDATED = "com.hmdm.push.configUpdated";

    public static final int HMDM_RECONNECT_DELAY_FIRST = 5000;
    public static final int HMDM_RECONNECT_DELAY_NEXT = 60000;
}
