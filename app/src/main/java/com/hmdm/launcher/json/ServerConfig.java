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

    private String newNumber;

    private String backgroundColor;
    private String textColor;
    private String backgroundImageUrl;
    private String password;
    private String phone;
    private String imei;
    private Integer iconSize;
    private String title;
    private boolean displayStatus;

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
    private String appUpdateFrom;
    private String appUpdateTo;
    private String downloadUpdates;

    private Boolean factoryReset;
    private Boolean reboot;
    private Boolean lock;
    private String lockMessage;
    private String passwordReset;

    private String pushOptions;
    private Integer keepaliveTime;
    private String requestUpdates;
    private Boolean disableLocation;
    private String appPermissions;

    private Boolean usbStorage;
    private Boolean autoBrightness;
    private Integer brightness;
    private Boolean manageTimeout;
    private Integer timeout;
    private Boolean lockVolume;
    private Boolean manageVolume;
    private Integer volume;
    private String passwordMode;
    private String timeZone;
    private String allowedClasses;

    private Integer orientation;
    private Boolean kioskHome;
    private Boolean kioskRecents;
    private Boolean kioskNotifications;
    private Boolean kioskSystemInfo;
    private Boolean kioskKeyguard;
    private Boolean kioskLockButtons;
    private Boolean kioskScreenOn;
    private String restrictions;

    private String description;
    private String custom1;
    private String custom2;
    private String custom3;

    private Boolean runDefaultLauncher;

    private String newServerUrl;

    private boolean lockSafeSettings;
    private boolean permissive;
    private boolean kioskExit;
    private boolean disableScreenshots;
    private boolean autostartForeground;

    private boolean showWifi;

    private String appName;
    private String vendor;

    private List<Application> applications = new LinkedList();

    private List<ApplicationSetting> applicationSettings = new LinkedList();

    private List<RemoteFile> files = new LinkedList();

    private List<Action> actions = new LinkedList();

    public static final String TITLE_NONE = "none";
    public static final String TITLE_DEVICE_ID = "deviceId";
    public static final String TITLE_DESCRIPTION = "description";
    public static final String TITLE_CUSTOM1 = "custom1";
    public static final String TITLE_CUSTOM2 = "custom2";
    public static final String TITLE_CUSTOM3 = "custom3";
    public static final String TITLE_IMEI = "imei";
    public static final String TITLE_SERIAL = "serialNumber";
    public static final String TITLE_EXTERNAL_IP = "externalIp";
    public static final int DEFAULT_ICON_SIZE = 100;

    public static final int SYSTEM_UPDATE_DEFAULT = 0;
    public static final int SYSTEM_UPDATE_INSTANT = 1;
    public static final int SYSTEM_UPDATE_SCHEDULE = 2;
    public static final int SYSTEM_UPDATE_MANUAL = 3;

    public static final String PUSH_OPTIONS_MQTT_WORKER = "mqttWorker";
    public static final String PUSH_OPTIONS_MQTT_ALARM = "mqttAlarm";
    public static final String PUSH_OPTIONS_POLLING = "polling";

    public static final String APP_PERMISSIONS_ASK_LOCATION = "asklocation";
    public static final String APP_PERMISSIONS_DENY_LOCATION = "denylocation";
    public static final String APP_PERMISSIONS_ASK_ALL = "askall";

    public ServerConfig() {}

    public String getNewNumber() {
        return newNumber;
    }

    public void setNewNumber(String newNumber) {
        this.newNumber = newNumber;
    }

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

    public boolean isDisplayStatus() {
        return displayStatus;
    }

    public void setDisplayStatus(boolean displayStatus) {
        this.displayStatus = displayStatus;
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

    public boolean isKioskMode() {
        return kioskMode != null && kioskMode;
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

    public String getAppUpdateFrom() {
        return appUpdateFrom;
    }

    public void setAppUpdateFrom(String appUpdateFrom) {
        this.appUpdateFrom = appUpdateFrom;
    }

    public String getAppUpdateTo() {
        return appUpdateTo;
    }

    public void setAppUpdateTo(String appUpdateTo) {
        this.appUpdateTo = appUpdateTo;
    }

    public String getDownloadUpdates() {
        return downloadUpdates;
    }

    public void setDownloadUpdates(String downloadUpdates) {
        this.downloadUpdates = downloadUpdates;
    }

    public Boolean getFactoryReset() {
        return factoryReset;
    }

    public void setFactoryReset(Boolean factoryReset) {
        this.factoryReset = factoryReset;
    }

    public Boolean getReboot() {
        return reboot;
    }

    public void setReboot(Boolean reboot) {
        this.reboot = reboot;
    }

    public Boolean getLock() {
        return lock;
    }

    public void setLock(Boolean lock) {
        this.lock = lock;
    }

    public String getLockMessage() {
        return lockMessage;
    }

    public void setLockMessage(String lockMessage) {
        this.lockMessage = lockMessage;
    }

    public String getPasswordReset() {
        return passwordReset;
    }

    public void setPasswordReset(String passwordReset) {
        this.passwordReset = passwordReset;
    }

    public String getPushOptions() {
        return pushOptions;
    }

    public void setPushOptions(String pushOptions) {
        this.pushOptions = pushOptions;
    }

    public Integer getKeepaliveTime() {
        return keepaliveTime;
    }

    public void setKeepaliveTime(Integer keepaliveTime) {
        this.keepaliveTime = keepaliveTime;
    }

    public String getRequestUpdates() {
        return requestUpdates;
    }

    public void setRequestUpdates(String requestUpdates) {
        this.requestUpdates = requestUpdates;
    }

    public Boolean getDisableLocation() {
        return disableLocation;
    }

    public void setDisableLocation(Boolean disableLocation) {
        this.disableLocation = disableLocation;
    }

    public String getAppPermissions() {
        return appPermissions;
    }

    public void setAppPermissions(String appPermissions) {
        this.appPermissions = appPermissions;
    }

    public Boolean getUsbStorage() {
        return usbStorage;
    }

    public void setUsbStorage(Boolean usbStorage) {
        this.usbStorage = usbStorage;
    }

    public Boolean getAutoBrightness() {
        return autoBrightness;
    }

    public void setAutoBrightness(Boolean autoBrightness) {
        this.autoBrightness = autoBrightness;
    }

    public Integer getBrightness() {
        return brightness;
    }

    public void setBrightness(Integer brightness) {
        this.brightness = brightness;
    }

    public Boolean getManageTimeout() {
        return manageTimeout;
    }

    public void setManageTimeout(Boolean manageTimeout) {
        this.manageTimeout = manageTimeout;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public Boolean getLockVolume() {
        return lockVolume;
    }

    public void setLockVolume(Boolean lockVolume) {
        this.lockVolume = lockVolume;
    }

    public Boolean getManageVolume() {
        return manageVolume;
    }

    public void setManageVolume(Boolean manageVolume) {
        this.manageVolume = manageVolume;
    }

    public Integer getVolume() {
        return volume;
    }

    public void setVolume(Integer volume) {
        this.volume = volume;
    }

    public String getPasswordMode() {
        return passwordMode;
    }

    public void setPasswordMode(String passwordMode) {
        this.passwordMode = passwordMode;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public String getAllowedClasses() {
        return allowedClasses;
    }

    public void setAllowedClasses(String allowedClasses) {
        this.allowedClasses = allowedClasses;
    }

    public Integer getOrientation() {
        return orientation;
    }

    public void setOrientation(Integer orientation) {
        this.orientation = orientation;
    }

    public Boolean getKioskHome() {
        return kioskHome;
    }

    public void setKioskHome(Boolean kioskHome) {
        this.kioskHome = kioskHome;
    }

    public Boolean getKioskRecents() {
        return kioskRecents;
    }

    public void setKioskRecents(Boolean kioskRecents) {
        this.kioskRecents = kioskRecents;
    }

    public Boolean getKioskNotifications() {
        return kioskNotifications;
    }

    public void setKioskNotifications(Boolean kioskNotifications) {
        this.kioskNotifications = kioskNotifications;
    }

    public Boolean getKioskSystemInfo() {
        return kioskSystemInfo;
    }

    public void setKioskSystemInfo(Boolean kioskSystemInfo) {
        this.kioskSystemInfo = kioskSystemInfo;
    }

    public Boolean getKioskKeyguard() {
        return kioskKeyguard;
    }

    public void setKioskKeyguard(Boolean kioskKeyguard) {
        this.kioskKeyguard = kioskKeyguard;
    }

    public Boolean getKioskLockButtons() {
        return kioskLockButtons;
    }

    public void setKioskLockButtons(Boolean kioskLockButtons) {
        this.kioskLockButtons = kioskLockButtons;
    }

    public Boolean getKioskScreenOn() {
        return kioskScreenOn;
    }

    public void setKioskScreenOn(Boolean kioskScreenOn) {
        this.kioskScreenOn = kioskScreenOn;
    }

    public Boolean getRunDefaultLauncher() {
        return runDefaultLauncher;
    }

    public void setRunDefaultLauncher(Boolean runDefaultLauncher) {
        this.runDefaultLauncher = runDefaultLauncher;
    }

    public String getNewServerUrl() {
        return newServerUrl;
    }

    public void setNewServerUrl(String newServerUrl) {
        this.newServerUrl = newServerUrl;
    }

    public boolean isLockSafeSettings() {
        return lockSafeSettings;
    }

    public void setLockSafeSettings(boolean lockSafeSettings) {
        this.lockSafeSettings = lockSafeSettings;
    }

    public boolean isPermissive() {
        return permissive;
    }

    public void setPermissive(boolean permissive) {
        this.permissive = permissive;
    }

    public boolean isKioskExit() {
        return kioskExit;
    }

    public void setKioskExit(boolean kioskExit) {
        this.kioskExit = kioskExit;
    }

    public boolean isDisableScreenshots() {
        return disableScreenshots;
    }

    public void setDisableScreenshots(boolean disableScreenshots) {
        this.disableScreenshots = disableScreenshots;
    }

    public boolean isAutostartForeground() {
        return autostartForeground;
    }

    public void setAutostartForeground(boolean autostartForeground) {
        this.autostartForeground = autostartForeground;
    }

    public boolean isShowWifi() {
        return showWifi;
    }

    public void setShowWifi(boolean showWifi) {
        this.showWifi = showWifi;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public String getRestrictions() {
        return restrictions;
    }

    public void setRestrictions(String restrictions) {
        this.restrictions = restrictions;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public List<RemoteFile> getFiles() {
        return files;
    }

    public void setFiles(List<RemoteFile> files) {
        this.files = files;
    }

    public List<Action> getActions() {
        return actions;
    }

    public void setActions(List<Action> actions) {
        this.actions = actions;
    }
}
