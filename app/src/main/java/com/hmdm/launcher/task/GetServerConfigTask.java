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

package com.hmdm.launcher.task;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.Settings;

import com.hmdm.launcher.Const;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.json.ServerConfigResponse;
import com.hmdm.launcher.pro.ProUtils;
import com.hmdm.launcher.server.ServerService;
import com.hmdm.launcher.server.ServerServiceKeeper;

import retrofit2.Response;

public class GetServerConfigTask extends AsyncTask< Void, Integer, Integer > {

    private Context context;
    private SettingsHelper settingsHelper;

    public GetServerConfigTask( Context context ) {
        this.context = context;
        this.settingsHelper = SettingsHelper.getInstance( context );
    }

    @Override
    protected Integer doInBackground( Void... voids ) {
        ServerService serverService;
        ServerService secondaryServerService;
        try {
            serverService = ServerServiceKeeper.getServerServiceInstance(context);
            secondaryServerService = ServerServiceKeeper.getSecondaryServerServiceInstance(context);
        } catch (Exception e) {
            return Const.TASK_NETWORK_ERROR;
        }
        Response< ServerConfigResponse > response = null;

        try {
            response = serverService.
                    getServerConfig(settingsHelper.getServerProject(), settingsHelper.getDeviceId()).execute();
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            if (response == null) {
                response = secondaryServerService.
                        getServerConfig(settingsHelper.getServerProject(), settingsHelper.getDeviceId()).execute();
            }

            Thread.sleep( 1500 );

            if ( response.isSuccessful() ) {
                if ( Const.STATUS_OK.equals( response.body().getStatus() ) && response.body().getData() != null ) {
                    SettingsHelper.getInstance(context).setExternalIp(response.headers().get(Const.HEADER_IP_ADDRESS));
                    settingsHelper.updateConfig( response.body().getData() );

                    // Prevent from occasional launch in the kiosk mode without any possibility to exit!
                    if (ProUtils.kioskModeRequired(context) &&
                            !settingsHelper.getConfig().getMainApp().equals(context.getPackageName()) &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                            !Settings.canDrawOverlays(context)) {
                            settingsHelper.getConfig().setKioskMode(false);
                            settingsHelper.updateConfig(settingsHelper.getConfig());
                    }

                    return Const.TASK_SUCCESS;
                } else {
                    return Const.TASK_ERROR;
                }
            }
        } catch ( Exception e ) {
            e.printStackTrace();
        }

        return Const.TASK_NETWORK_ERROR;
    }
}
