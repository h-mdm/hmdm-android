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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.telephony.TelephonyManager;

import com.hmdm.launcher.BuildConfig;
import com.hmdm.launcher.Const;
import com.hmdm.launcher.db.DatabaseHelper;
import com.hmdm.launcher.db.RemoteFileTable;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.json.Application;
import com.hmdm.launcher.json.DeviceInfo;
import com.hmdm.launcher.json.RemoteFile;
import com.hmdm.launcher.pro.ProUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.lang.reflect.Method;
import java.util.List;

public class DeviceInfoProvider {
    public static DeviceInfo getDeviceInfo(Context context, boolean queryPermissions, boolean queryApps) {
        DeviceInfo deviceInfo = new DeviceInfo();
        List<Integer> permissions = deviceInfo.getPermissions();
        List<Application> applications = deviceInfo.getApplications();
        List<RemoteFile> files = deviceInfo.getFiles();

        deviceInfo.setModel(Build.MODEL);

        if (queryPermissions) {
            permissions.add(Utils.checkAdminMode(context) ? 1 : 0);
            permissions.add(Utils.canDrawOverlays(context) ? 1 : 0);
            permissions.add(ProUtils.checkUsageStatistics(context) ? 1 : 0);
        }

        SettingsHelper config = SettingsHelper.getInstance(context);
        if (queryApps) {
            PackageManager packageManager = context.getPackageManager();
            if (config.getConfig() != null) {
                List<Application> requiredApps = SettingsHelper.getInstance(context).getConfig().getApplications();
                for (Application application : requiredApps) {
                    if (application.isRemove()) {
                        continue;
                    }
                    try {
                        PackageInfo packageInfo = packageManager.getPackageInfo(application.getPkg(), 0);

                        Application installedApp = new Application();
                        installedApp.setName(application.getName());
                        installedApp.setPkg(packageInfo.packageName);
                        installedApp.setVersion(packageInfo.versionName);

                        // Verify there's no duplicates (due to different versions in config), otherwise it causes an error on the server
                        boolean appPresents = false;
                        for (Application a : applications) {
                            if (a.getPkg().equalsIgnoreCase(installedApp.getPkg())) {
                                appPresents = true;
                                break;
                            }
                        }
                        if (!appPresents) {
                            applications.add(installedApp);
                        }
                    } catch (PackageManager.NameNotFoundException e) {
                        // Application not installed
                    }
                }

                List<RemoteFile> requiredFiles = SettingsHelper.getInstance(context).getConfig().getFiles();
                for (RemoteFile remoteFile : requiredFiles) {
                    File file = new File(Environment.getExternalStorageDirectory(), remoteFile.getPath());
                    if (file.exists()) {
                        RemoteFile remoteFileDb = RemoteFileTable.selectByPath(DatabaseHelper.instance(context).getReadableDatabase(),
                                remoteFile.getPath());
                        if (remoteFileDb != null) {
                            files.add(remoteFileDb);
                        } else {
                            // How could that happen? The database entry should exist for each file
                            // Let's recalculate the checksum to check if the file matches
                            try {
                                RemoteFile copy = new RemoteFile(remoteFile);
                                copy.setChecksum(CryptoUtils.calculateChecksum(new FileInputStream(file)));
                                files.add(copy);
                            } catch (FileNotFoundException e) {
                            }
                        }
                    }
                }
            }
        }

        deviceInfo.setDeviceId( SettingsHelper.getInstance( context ).getDeviceId() );

        String phone = DeviceInfoProvider.getPhoneNumber(context);
        if (phone == null || phone.equals("")) {
            phone = config.getConfig().getPhone();
        }
        deviceInfo.setPhone(phone);

        String imei = DeviceInfoProvider.getImei(context);
        if (imei == null || imei.equals("")) {
            imei = config.getConfig().getImei();
        }
        deviceInfo.setImei(imei);

        // Battery
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);

        Intent batteryStatus = context.registerReceiver(null, ifilter);
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        if (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL) {
            int chargePlug = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
            switch (chargePlug) {
                case BatteryManager.BATTERY_PLUGGED_USB:
                    deviceInfo.setBatteryCharging(Const.DEVICE_CHARGING_USB);
                    break;
                case BatteryManager.BATTERY_PLUGGED_AC:
                    deviceInfo.setBatteryCharging(Const.DEVICE_CHARGING_AC);
                    break;
            }
        } else {
            deviceInfo.setBatteryCharging("");
        }

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        deviceInfo.setBatteryLevel(level * 100 / scale);

        deviceInfo.setAndroidVersion(Build.VERSION.RELEASE);
        deviceInfo.setLocation(getLocation(context));
        deviceInfo.setMdmMode(Utils.isDeviceOwner(context));
        deviceInfo.setLauncherType(BuildConfig.FLAVOR);

        String launcherPackage = Utils.getDefaultLauncher(context);
        deviceInfo.setLauncherPackage(launcherPackage != null ? launcherPackage : "");
        deviceInfo.setDefaultLauncher(context.getPackageName().equals(launcherPackage));

        return deviceInfo;
    }

    @SuppressWarnings({"MissingPermission"})
    public static DeviceInfo.Location getLocation(Context context) {
        try {
            LocationManager locationManager = (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
            Location lastLocationGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location lastLocationNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

            if (lastLocationGps != null || lastLocationNetwork != null) {

                DeviceInfo.Location location = new DeviceInfo.Location();

                Location lastLocation;
                if (lastLocationGps == null) {
                    lastLocation = lastLocationNetwork;
                } else if (lastLocationNetwork == null) {
                    lastLocation = lastLocationGps;
                } else {
                    // Get the latest location as the best one
                    if (lastLocationGps.getTime() >= lastLocationNetwork.getTime()) {
                        lastLocation = lastLocationGps;
                    } else {
                        lastLocation = lastLocationNetwork;
                    }
                }

                location.setLat(lastLocation.getLatitude());
                location.setLon(lastLocation.getLongitude());
                location.setTs(lastLocation.getTime());
                return location;
            }
        } catch (Exception e) {
        }
        return null;
    }

    @SuppressLint("MissingPermission")
    public static String getSerialNumber() {
        String serialNumber = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                return Build.getSerial();
            } catch (SecurityException e) {
            }
        }
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method get = c.getMethod("get", String.class);
            serialNumber = (String) get.invoke(c, "ril.serialnumber");
        } catch (Exception ignored) { /*noop*/ }
        if (serialNumber != null && !serialNumber.equals("")) {
            return serialNumber;
        }
        return Build.SERIAL;
    }

    @SuppressLint( { "MissingPermission" } )
    public static String getPhoneNumber(Context context) {
        try {
            TelephonyManager tMgr = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tMgr == null) {
                return null;
            }
            return tMgr.getLine1Number();
        } catch (Exception e) {
            return null;
        }
    }


    @SuppressLint( { "MissingPermission" } )
    public static String getImei(Context context) {
        try {
            TelephonyManager tMgr = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tMgr == null) {
                return null;
            }
            return tMgr.getDeviceId();
        } catch (Exception e) {
            return null;
        }
    }
}
