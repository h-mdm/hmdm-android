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

package com.hmdm.launcher.util;

import android.content.ComponentName;
import android.content.Context;

import com.hmdm.launcher.AdminReceiver;

/**
 * For compatibility with old builds
 * Legacy admin receiver is ru.headwind.kiosk.AdminReceiver, it is replaced in legacy build variants
 */
public class LegacyUtils {
    public static ComponentName getAdminComponentName(Context context) {
        return new ComponentName(context.getApplicationContext(), AdminReceiver.class);
    }
}
