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

import android.app.Application;
import android.content.Context;
import android.os.Build;

import com.hmdm.launcher.helper.SettingsHelper;
import com.jakewharton.picasso.OkHttp3Downloader;
import com.squareup.picasso.Picasso;

public class App extends Application {

    @Override
    protected void attachBaseContext(Context base) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Context deviceContext = base.createDeviceProtectedStorageContext();
            deviceContext.moveSharedPreferencesFrom(base, base.getPackageName() + ".helpers.PREFERENCES");
            deviceContext.moveSharedPreferencesFrom(base, Const.PREFERENCES);
            deviceContext.moveDatabaseFrom(base, "hmdm.launcher.sqlite");
            deviceContext.moveDatabaseFrom(base, "mqttAndroidService.db");
            base = deviceContext;
        }
        super.attachBaseContext(base);
        // base is now whichever context (device-protected, or the original on pre-N) components
        // should actually use; initialize the singleton here so no caller ever has to pass one in.
        SettingsHelper.init(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Picasso.Builder builder = new Picasso.Builder(this);
        builder.downloader(new OkHttp3Downloader(this,Integer.MAX_VALUE));
        Picasso built = builder.build();
        //built.setIndicatorsEnabled(true);
        //built.setLoggingEnabled(true);
        Picasso.setSingletonInstance(built);
    }

}
