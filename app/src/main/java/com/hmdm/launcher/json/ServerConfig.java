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

import java.util.LinkedList;
import java.util.List;

@JsonIgnoreProperties( ignoreUnknown = true )
public class ServerConfig {

    private String backgroundColor;
    private String textColor;
    private String backgroundImageUrl;
    private String password;
    private String phone;
    private String imei;
    private Integer iconSize;
    private String title;

    private Boolean gps;
    private Boolean bluetooth;
    private Boolean wifi;
    private Boolean mobileData;

    private Boolean kioskMode;
    private String mainApp;

    private Boolean lockStatusBar;
    private Integer systemUpdateType;
    private String systemUpdateFrom;
    private String systemUpdateTo;

    private List< Application > applications = new LinkedList();

    private List< ApplicationSetting > applicationSettings = new LinkedList();

    public static final String TITLE_NONE = "none";
    public static final String TITLE_DEVICE_ID = "deviceId";
    public static final int DEFAULT_ICON_SIZE = 100;

    public static final int SYSTEM_UPDATE_DEFAULT = 0;
    public static final int SYSTEM_UPDATE_INSTANT = 1;
    public static final int SYSTEM_UPDATE_SCHEDULE = 2;
    public static final int SYSTEM_UPDATE_MANUAL = 3;

    public ServerConfig() {}

    public String getBackgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor( String backgroundColor ) {
        this.backgroundColor = backgroundColor;
    }

    public String getTextColor() {
        return textColor;
    }

    public void setTextColor( String textColor ) {
        this.textColor = textColor;
    }

    public String getBackgroundImageUrl() {
        return backgroundImageUrl;
    }

    public void setBackgroundImageUrl( String backgroundImageUrl ) {
        this.backgroundImageUrl = backgroundImageUrl;
    }

    public List< Application > getApplications() {
        return applications;
    }

    public void setApplications( List< Application > applications ) {
        this.applications = applications;
    }

    public List< ApplicationSetting > getApplicationSettings() {
        return applicationSettings;
    }

    public void setApplicationSettings( List< ApplicationSetting > applicationSettings ) {
        this.applicationSettings = applicationSettings;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword( String password ) {
        this.password = password;
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

    public Integer getIconSize() {
        return iconSize;
    }

    public void setIconSize(Integer iconSize) {
        this.iconSize = iconSize;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Boolean getGps() {
        return gps;
    }

    public void setGps(Boolean gps) {
        this.gps = gps;
    }

    public Boolean getBluetooth() {
        return bluetooth;
    }

    public void setBluetooth(Boolean bluetooth) {
        this.bluetooth = bluetooth;
    }

    public Boolean getWifi() {
        return wifi;
    }

    public void setWifi(Boolean wifi) {
        this.wifi = wifi;
    }
    public Boolean getMobileData() {
        return mobileData;
    }

    public void setMobileData(Boolean mobileData) {
        this.mobileData = mobileData;
    }

    public Boolean getKioskMode() {
        return kioskMode;
    }

    public void setKioskMode(Boolean kioskMode) {
        this.kioskMode = kioskMode;
    }

    public String getMainApp() {
        return mainApp;
    }

    public void setMainApp(String mainApp) {
        this.mainApp = mainApp;
    }

    public Boolean getLockStatusBar() {
        return lockStatusBar;
    }

    public void setLockStatusBar(Boolean lockStatusBar) {
        this.lockStatusBar = lockStatusBar;
    }

    public Integer getSystemUpdateType() {
        return systemUpdateType;
    }

    public void setSystemUpdateType(Integer systemUpdateType) {
        this.systemUpdateType = systemUpdateType;
    }

    public String getSystemUpdateFrom() {
        return systemUpdateFrom;
    }

    public void setSystemUpdateFrom(String systemUpdateFrom) {
        this.systemUpdateFrom = systemUpdateFrom;
    }

    public String getSystemUpdateTo() {
        return systemUpdateTo;
    }

    public void setSystemUpdateTo(String systemUpdateTo) {
        this.systemUpdateTo = systemUpdateTo;
    }
}
