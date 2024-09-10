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

import android.annotation.SuppressLint;
import android.database.Cursor;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties( ignoreUnknown = true )
public class DetailedInfo {

    @JsonIgnore
    private long _id;

    private long ts;
    private Device device;
    private Wifi wifi;
    private Gps gps;
    private Mobile mobile;
    private Mobile mobile2;

    public DetailedInfo() {
    }

    @SuppressLint("Range")
    public DetailedInfo(Cursor cursor) {
        _id = cursor.getLong(cursor.getColumnIndex("_id"));
        ts = cursor.getLong(cursor.getColumnIndex("ts"));

        device = new Device();
        device.setBatteryLevel(cursor.getInt(cursor.getColumnIndex("deviceBatteryLevel")));
        device.setBatteryCharging(cursor.getString(cursor.getColumnIndex("deviceBatteryCharging")));
        device.setWifi(cursor.getInt(cursor.getColumnIndex("deviceWifi")) != 0);
        device.setGps(cursor.getInt(cursor.getColumnIndex("deviceGps")) != 0);
        device.setIp(cursor.getString(cursor.getColumnIndex("deviceIp")));
        device.setKeyguard(cursor.getInt(cursor.getColumnIndex("deviceKeyguard")) != 0);
        device.setRingVolume(cursor.getInt(cursor.getColumnIndex("deviceRingVolume")));
        device.setMobileData(cursor.getInt(cursor.getColumnIndex("deviceMobileData")) != 0);
        device.setBluetooth(cursor.getInt(cursor.getColumnIndex("deviceBluetooth")) != 0);
        device.setUsbStorage(cursor.getInt(cursor.getColumnIndex("deviceUsbStorage")) != 0);
        device.setMemoryTotal(cursor.getInt(cursor.getColumnIndex("deviceMemoryTotal")));
        device.setMemoryAvailable(cursor.getInt(cursor.getColumnIndex("deviceMemoryAvailable")));

        wifi = new Wifi();
        wifi.setRssi(cursor.getInt(cursor.getColumnIndex("wifiRssi")));
        wifi.setSsid(cursor.getString(cursor.getColumnIndex("wifiSsid")));
        wifi.setSecurity(cursor.getString(cursor.getColumnIndex("wifiSecurity")));
        wifi.setState(cursor.getString(cursor.getColumnIndex("wifiState")));
        wifi.setIp(cursor.getString(cursor.getColumnIndex("wifiIp")));
        wifi.setTx(cursor.getLong(cursor.getColumnIndex("wifiTx")));
        wifi.setRx(cursor.getLong(cursor.getColumnIndex("wifiRx")));

        gps = new Gps();
        gps.setState(cursor.getString(cursor.getColumnIndex("gpsState")));
        gps.setLat(cursor.getDouble(cursor.getColumnIndex("gpsLat")));
        gps.setLon(cursor.getDouble(cursor.getColumnIndex("gpsLon")));
        gps.setAlt(cursor.getDouble(cursor.getColumnIndex("gpsAlt")));
        gps.setSpeed(cursor.getDouble(cursor.getColumnIndex("gpsSpeed")));
        gps.setCourse(cursor.getDouble(cursor.getColumnIndex("gpsCourse")));

        mobile = new Mobile();
        mobile.setRssi(cursor.getInt(cursor.getColumnIndex("mobileRssi")));
        mobile.setCarrier(cursor.getString(cursor.getColumnIndex("mobileCarrier")));
        mobile.setNumber(cursor.getString(cursor.getColumnIndex("mobileNumber")));
        mobile.setImsi(cursor.getString(cursor.getColumnIndex("mobileImsi")));
        mobile.setData(cursor.getInt(cursor.getColumnIndex("mobileData")) != 0);
        mobile.setIp(cursor.getString(cursor.getColumnIndex("mobileIp")));
        mobile.setState(cursor.getString(cursor.getColumnIndex("mobileState")));
        mobile.setSimState(cursor.getString(cursor.getColumnIndex("mobileSimState")));
        mobile.setTx(cursor.getLong(cursor.getColumnIndex("mobileTx")));
        mobile.setRx(cursor.getLong(cursor.getColumnIndex("mobileRx")));

        mobile2 = new Mobile();
        mobile2.setRssi(cursor.getInt(cursor.getColumnIndex("mobile2Rssi")));
        mobile2.setCarrier(cursor.getString(cursor.getColumnIndex("mobile2Carrier")));
        mobile2.setNumber(cursor.getString(cursor.getColumnIndex("mobile2Number")));
        mobile2.setImsi(cursor.getString(cursor.getColumnIndex("mobile2Imsi")));
        mobile2.setData(cursor.getInt(cursor.getColumnIndex("mobile2Data")) != 0);
        mobile2.setIp(cursor.getString(cursor.getColumnIndex("mobile2Ip")));
        mobile2.setState(cursor.getString(cursor.getColumnIndex("mobile2State")));
        mobile2.setSimState(cursor.getString(cursor.getColumnIndex("mobile2SimState")));
        mobile2.setTx(cursor.getLong(cursor.getColumnIndex("mobile2Tx")));
        mobile2.setRx(cursor.getLong(cursor.getColumnIndex("mobile2Rx")));
    }

    public long getId() {
        return _id;
    }

    public void setId(long _id) {
        this._id = _id;
    }

    public long getTs() {
        return ts;
    }

    public void setTs(long ts) {
        this.ts = ts;
    }

    public Device getDevice() {
        return device;
    }

    public void setDevice(Device device) {
        this.device = device;
    }

    public Wifi getWifi() {
        return wifi;
    }

    public void setWifi(Wifi wifi) {
        this.wifi = wifi;
    }

    public Gps getGps() {
        return gps;
    }

    public void setGps(Gps gps) {
        this.gps = gps;
    }

    public Mobile getMobile() {
        return mobile;
    }

    public void setMobile(Mobile mobile) {
        this.mobile = mobile;
    }

    public Mobile getMobile2() {
        return mobile2;
    }

    public void setMobile2(Mobile mobile2) {
        this.mobile2 = mobile2;
    }

    @JsonIgnoreProperties( ignoreUnknown = true )
    public static class Device {
        private Integer batteryLevel;
        private String batteryCharging;
        private Boolean wifi;
        private Boolean gps;
        private String ip;
        private Boolean keyguard;
        private Integer ringVolume;
        private Boolean mobileData;
        private Boolean bluetooth;
        private Boolean usbStorage;
        private Integer memoryTotal;
        private Integer memoryAvailable;

        public Integer getBatteryLevel() {
            return batteryLevel;
        }

        public void setBatteryLevel(Integer batteryLevel) {
            this.batteryLevel = batteryLevel;
        }

        public String getBatteryCharging() {
            return batteryCharging;
        }

        public void setBatteryCharging(String batteryCharging) {
            this.batteryCharging = batteryCharging;
        }

        public Boolean getWifi() {
            return wifi;
        }

        public void setWifi(Boolean wifi) {
            this.wifi = wifi;
        }

        public Boolean getGps() {
            return gps;
        }

        public void setGps(Boolean gps) {
            this.gps = gps;
        }

        public String getIp() {
            return ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }

        public Boolean getKeyguard() {
            return keyguard;
        }

        public void setKeyguard(Boolean keyguard) {
            this.keyguard = keyguard;
        }

        public Integer getRingVolume() {
            return ringVolume;
        }

        public void setRingVolume(Integer ringVolume) {
            this.ringVolume = ringVolume;
        }

        public Boolean getMobileData() {
            return mobileData;
        }

        public void setMobileData(Boolean mobileData) {
            this.mobileData = mobileData;
        }

        public Boolean getBluetooth() {
            return bluetooth;
        }

        public void setBluetooth(Boolean bluetooth) {
            this.bluetooth = bluetooth;
        }

        public Boolean getUsbStorage() {
            return usbStorage;
        }

        public void setUsbStorage(Boolean usbStorage) {
            this.usbStorage = usbStorage;
        }

        public Integer getMemoryTotal() {
            return memoryTotal;
        }

        public void setMemoryTotal(Integer memoryTotal) {
            this.memoryTotal = memoryTotal;
        }

        public Integer getMemoryAvailable() {
            return memoryAvailable;
        }

        public void setMemoryAvailable(Integer memoryAvailable) {
            this.memoryAvailable = memoryAvailable;
        }
    }

    @JsonIgnoreProperties( ignoreUnknown = true )
    public static class Wifi {
        private Integer rssi;
        private String ssid;
        private String security;
        private String state;
        private String ip;
        private Long tx;
        private Long rx;

        public Integer getRssi() {
            return rssi;
        }

        public void setRssi(Integer rssi) {
            this.rssi = rssi;
        }

        public String getSsid() {
            return ssid;
        }

        public void setSsid(String ssid) {
            this.ssid = ssid;
        }

        public String getSecurity() {
            return security;
        }

        public void setSecurity(String security) {
            this.security = security;
        }

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }

        public String getIp() {
            return ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }

        public Long getTx() {
            return tx;
        }

        public void setTx(Long tx) {
            this.tx = tx;
        }

        public Long getRx() {
            return rx;
        }

        public void setRx(Long rx) {
            this.rx = rx;
        }
    }

    @JsonIgnoreProperties( ignoreUnknown = true )
    public static class Gps {
        private String state;
        private String provider;
        private Double lat;
        private Double lon;
        private Double alt;
        private Double speed;
        private Double course;

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public Double getLat() {
            return lat;
        }

        public void setLat(Double lat) {
            this.lat = lat;
        }

        public Double getLon() {
            return lon;
        }

        public void setLon(Double lon) {
            this.lon = lon;
        }

        public Double getAlt() {
            return alt;
        }

        public void setAlt(Double alt) {
            this.alt = alt;
        }

        public Double getSpeed() {
            return speed;
        }

        public void setSpeed(Double speed) {
            this.speed = speed;
        }

        public Double getCourse() {
            return course;
        }

        public void setCourse(Double course) {
            this.course = course;
        }
    }

    @JsonIgnoreProperties( ignoreUnknown = true )
    public static class Mobile {
        private Integer rssi;
        private String carrier;
        private String number;
        private String imsi;
        private Boolean data;
        private String ip;
        private String state;
        private String simState;
        private Long tx;
        private Long rx;

        public Integer getRssi() {
            return rssi;
        }

        public void setRssi(Integer rssi) {
            this.rssi = rssi;
        }

        public String getCarrier() {
            return carrier;
        }

        public void setCarrier(String carrier) {
            this.carrier = carrier;
        }

        public String getNumber() {
            return number;
        }

        public void setNumber(String number) {
            this.number = number;
        }

        public String getImsi() {
            return imsi;
        }

        public void setImsi(String imsi) {
            this.imsi = imsi;
        }

        public Boolean getData() {
            return data;
        }

        public void setData(Boolean data) {
            this.data = data;
        }

        public String getIp() {
            return ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }

        public String getSimState() {
            return simState;
        }

        public void setSimState(String simState) {
            this.simState = simState;
        }

        public Long getTx() {
            return tx;
        }

        public void setTx(Long tx) {
            this.tx = tx;
        }

        public Long getRx() {
            return rx;
        }

        public void setRx(Long rx) {
            this.rx = rx;
        }
    }
}
