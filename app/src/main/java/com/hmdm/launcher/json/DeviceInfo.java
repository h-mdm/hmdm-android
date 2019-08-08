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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.hmdm.launcher.Const;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.util.DeviceInfoProvider;

import java.util.LinkedList;
import java.util.List;

@JsonIgnoreProperties( ignoreUnknown = true )
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeviceInfo {

    private String model;
    private List< Integer > permissions = new LinkedList();
    private List< Application > applications = new LinkedList();
    private String deviceId;
    private String phone;
    private String imei;

    public DeviceInfo() {}

    public DeviceInfo( Context context ) {
        this.model = Build.MODEL;

        SharedPreferences preferences = context.getSharedPreferences( Const.PREFERENCES, Context.MODE_PRIVATE );
        permissions.add( preferences.getInt( Const.PREFERENCES_ADMINISTRATOR, -1 ) );
        permissions.add( preferences.getInt( Const.PREFERENCES_OVERLAY, -1 ) );
        permissions.add( preferences.getInt( Const.PREFERENCES_USAGE_STATISTICS, -1 ) );

        PackageManager packageManager = context.getPackageManager();
        SettingsHelper config = SettingsHelper.getInstance( context );
        if ( config.getConfig() != null ) {
            List< Application > requiredApps = SettingsHelper.getInstance( context ).getConfig().getApplications();
            for ( Application application : requiredApps ) {
                try {
                    PackageInfo packageInfo = packageManager.getPackageInfo( application.getPkg(), 0 );

                    Application installedApp = new Application();
                    installedApp.setName( application.getName() );
                    installedApp.setPkg( packageInfo.packageName );
                    installedApp.setVersion( packageInfo.versionName );

                    applications.add( installedApp );
                } catch ( PackageManager.NameNotFoundException e ) {
                    // Application not installed
                }
            }
        }

        this.setDeviceId( SettingsHelper.getInstance( context ).getDeviceId() );

        String phone = DeviceInfoProvider.getPhoneNumber(context);
        if (phone == null || phone.equals("")) {
            phone = config.getConfig().getPhone();
        }
        this.setPhone(phone);

        String imei = DeviceInfoProvider.getImei(context);
        if (imei == null || imei.equals("")) {
            imei = config.getConfig().getImei();
        }
        this.setImei(imei);
    }

    public String getModel() {
        return model;
    }

    public void setModel( String model ) {
        this.model = model;
    }

    public List< Integer > getPermissions() {
        return permissions;
    }

    public void setPermissions( List< Integer > permissions ) {
        this.permissions = permissions;
    }

    public List< Application > getApplications() {
        return applications;
    }

    public void setApplications( List< Application > applications ) {
        this.applications = applications;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId( String deviceId ) {
        this.deviceId = deviceId;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getImei() {
        return imei;
    }

    public void setImei(String imei) {
        this.imei = imei;
    }
}
