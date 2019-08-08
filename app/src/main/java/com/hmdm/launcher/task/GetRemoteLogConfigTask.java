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

import com.hmdm.launcher.Const;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.json.RemoteLogConfigResponse;
import com.hmdm.launcher.server.ServerService;
import com.hmdm.launcher.server.ServerServiceKeeper;
import com.hmdm.launcher.util.RemoteLogger;

import retrofit2.Response;

public class GetRemoteLogConfigTask extends AsyncTask< Void, Integer, Integer > {
    private Context context;
    private SettingsHelper settingsHelper;

    public GetRemoteLogConfigTask( Context context ) {
        this.context = context;
        this.settingsHelper = SettingsHelper.getInstance( context );
    }

    @Override
    protected Integer doInBackground( Void... voids ) {
        ServerService serverService = ServerServiceKeeper.getServerServiceInstance(context);
        ServerService secondaryServerService = ServerServiceKeeper.getSecondaryServerServiceInstance(context);
        Response<RemoteLogConfigResponse> response = null;

        try {
            response = serverService.
                    getRemoteLogConfig(settingsHelper.getServerProject(), settingsHelper.getDeviceId()).execute();
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            if (response == null) {
                response = secondaryServerService.
                        getRemoteLogConfig(settingsHelper.getServerProject(), settingsHelper.getDeviceId()).execute();
            }

            if ( response.isSuccessful() ) {
                if ( Const.STATUS_OK.equals( response.body().getStatus() ) && response.body().getData() != null ) {
                    RemoteLogger.updateConfig(context, response.body().getData());

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
