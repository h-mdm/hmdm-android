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

package com.hmdm.launcher.server;

import android.content.Context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdm.launcher.Const;
import com.hmdm.launcher.helper.SettingsHelper;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public class ServerServiceKeeper {

    private static ServerService serverServiceInstance;
    private static ServerService secondaryServerServiceInstance;

    // This is called after changing the server URL
    public static void resetServices() {
        serverServiceInstance = null;
        secondaryServerServiceInstance = null;
    }

    public static ServerService getServerServiceInstance(Context context) {
        if ( serverServiceInstance == null ) {
            serverServiceInstance = createServerService( SettingsHelper.getInstance(context).getBaseUrl() );
        }

        return serverServiceInstance;
    }

    public static ServerService getSecondaryServerServiceInstance(Context context) {
        if ( secondaryServerServiceInstance == null ) {
            try {
                secondaryServerServiceInstance = createServerService(SettingsHelper.getInstance(context).getSecondaryBaseUrl());
            } catch (Exception e) {
                // Here we can go if the secondary base URL is invalid
                // In this case, just return a copy of the primary instance
                secondaryServerServiceInstance = getServerServiceInstance(context);
            }
        }

        return secondaryServerServiceInstance;
    }

    private static ServerService createServerService( String baseUrl ) {
        return createBuilder( baseUrl ).build().create( ServerService.class );
    }

    private static Retrofit.Builder createBuilder( String baseUrl ) {
        Retrofit.Builder builder = new Retrofit.Builder();
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder().
                connectTimeout( Const.CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS ).
                readTimeout( Const.CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS ).
                writeTimeout( Const.CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS );

        builder.client( clientBuilder.build() );

        builder.baseUrl( baseUrl )
                .addConverterFactory( JacksonConverterFactory.create( new ObjectMapper()) );

        return builder;
    }
}
