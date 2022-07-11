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
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.LinkedList;
import java.util.List;

@JsonIgnoreProperties( ignoreUnknown = true )
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeviceInfo {

    private String model;
    private List<Integer> permissions = new LinkedList();
    private List<Application> applications = new LinkedList();
    private List<RemoteFile> files = new LinkedList();
    private String deviceId;
    private String phone;
    private String imei;
    private boolean mdmMode;
    private boolean kioskMode;
    private int batteryLevel;
    private String batteryCharging;
    private String androidVersion;
    private Boolean factoryReset;
    private Location location;
    private String launcherType;
    private String launcherPackage;
    private boolean defaultLauncher;
    private String iccid;
    private String imsi;
    private String phone2;
    private String imei2;
    private String iccid2;
    private String imsi2;
    private String cpu;
    private String serial;

    // These fields are reserved for custom builds of Headwind MDM
    private String custom1;
    private String custom2;
    private String custom3;

    public static class Location {
        private long ts;
        private double lat;
        private double lon;

        public long getTs() {
            return ts;
        }

        public void setTs(long ts) {
            this.ts = ts;
        }

        public double getLat() {
            return lat;
        }

        public void setLat(double lat) {
            this.lat = lat;
        }

        public double getLon() {
            return lon;
        }

        public void setLon(double lon) {
            this.lon = lon;
        }
    }

    public DeviceInfo() {}

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<Integer> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<Integer> permissions) {
        this.permissions = permissions;
    }

    public List<Application> getApplications() {
        return applications;
    }

    public void setApplications(List<Application> applications) {
        this.applications = applications;
    }

    public List<RemoteFile> getFiles() {
        return files;
    }

    public void setFiles(List<RemoteFile> files) {
        this.files = files;
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

    public boolean isMdmMode() {
        return mdmMode;
    }

    public void setMdmMode(boolean mdmMode) {
        this.mdmMode = mdmMode;
    }

    public boolean isKioskMode() {
        return kioskMode;
    }

    public void setKioskMode(boolean kioskMode) {
        this.kioskMode = kioskMode;
    }

    public int getBatteryLevel() {
        return batteryLevel;
    }

    public void setBatteryLevel(int batteryLevel) {
        this.batteryLevel = batteryLevel;
    }

    public String isBatteryCharging() {
        return batteryCharging;
    }

    public void setBatteryCharging(String batteryCharging) {
        this.batteryCharging = batteryCharging;
    }

    public String getAndroidVersion() {
        return androidVersion;
    }

    public void setAndroidVersion(String androidVersion) {
        this.androidVersion = androidVersion;
    }

    public Boolean getFactoryReset() {
        return factoryReset;
    }

    public void setFactoryReset(Boolean factoryReset) {
        this.factoryReset = factoryReset;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public String getLauncherType() {
        return launcherType;
    }

    public void setLauncherType(String launcherType) {
        this.launcherType = launcherType;
    }

    public String getLauncherPackage() {
        return launcherPackage;
    }

    public void setLauncherPackage(String launcherPackage) {
        this.launcherPackage = launcherPackage;
    }

    public boolean isDefaultLauncher() {
        return defaultLauncher;
    }

    public void setDefaultLauncher(boolean defaultLauncher) {
        this.defaultLauncher = defaultLauncher;
    }

    public String getIccid() {
        return iccid;
    }

    public void setIccid(String iccid) {
        this.iccid = iccid;
    }

    public String getImsi() {
        return imsi;
    }

    public void setImsi(String imsi) {
        this.imsi = imsi;
    }

    public String getPhone2() {
        return phone2;
    }

    public void setPhone2(String phone2) {
        this.phone2 = phone2;
    }

    public String getImei2() {
        return imei2;
    }

    public void setImei2(String imei2) {
        this.imei2 = imei2;
    }

    public String getIccid2() {
        return iccid2;
    }

    public void setIccid2(String iccid2) {
        this.iccid2 = iccid2;
    }

    public String getImsi2() {
        return imsi2;
    }

    public void setImsi2(String imsi2) {
        this.imsi2 = imsi2;
    }

    public String getCpu() {
        return cpu;
    }

    public void setCpu(String cpu) {
        this.cpu = cpu;
    }

    public String getSerial() {
        return serial;
    }

    public void setSerial(String serial) {
        this.serial = serial;
    }

    public String getCustom1() {
        return custom1;
    }

    public void setCustom1(String custom1) {
        this.custom1 = custom1;
    }

    public String getCustom2() {
        return custom2;
    }

    public void setCustom2(String custom2) {
        this.custom2 = custom2;
    }

    public String getCustom3() {
        return custom3;
    }

    public void setCustom3(String custom3) {
        this.custom3 = custom3;
    }
}
