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

package com.hmdm.launcher.db;

import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import com.hmdm.launcher.json.DetailedInfo;

import java.util.LinkedList;
import java.util.List;

public class InfoHistoryTable {
    private static final String CREATE_TABLE =
            "CREATE TABLE info_history (" +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "ts INTEGER, " +
                    "deviceBatteryLevel INTEGER, " +
                    "deviceBatteryCharging TEXT, " +
                    "deviceWifi INTEGER, " +
                    "deviceGps INTEGER, " +
                    "deviceIp TEXT, " +
                    "deviceKeyguard INTEGER, " +
                    "deviceRingVolume INTEGER, " +
                    "deviceMobileData INTEGER, " +
                    "deviceBluetooth INTEGER, " +
                    "deviceUsbStorage INTEGER, " +
                    "wifiRssi INTEGER, " +
                    "wifiSsid TEXT, " +
                    "wifiSecurity TEXT, " +
                    "wifiState TEXT, " +
                    "wifiIp TEXT, " +
                    "wifiTx INTEGER, " +
                    "wifiRx INTEGER, " +
                    "gpsState TEXT, " +
                    "gpsProvider TEXT, " +
                    "gpsLat REAL, " +
                    "gpsLon REAL, " +
                    "gpsAlt REAL, " +
                    "gpsSpeed REAL, " +
                    "gpsCourse REAL, " +
                    "mobileRssi INTEGER, " +
                    "mobileCarrier TEXT, " +
                    "mobileNumber TEXT, " +
                    "mobileImsi TEXT, " +
                    "mobileData INTEGER, " +
                    "mobileIp TEXT, " +
                    "mobileState TEXT, " +
                    "mobileSimState TEXT, " +
                    "mobileTx INTEGER, " +
                    "mobileRx INTEGER, " +
                    "mobile2Rssi INTEGER, " +
                    "mobile2Carrier TEXT, " +
                    "mobile2Number TEXT, " +
                    "mobile2Imsi TEXT, " +
                    "mobile2Data INTEGER, " +
                    "mobile2Ip TEXT, " +
                    "mobile2State TEXT, " +
                    "mobile2SimState TEXT, " +
                    "mobile2Tx INTEGER, " +
                    "mobile2Rx INTEGER, " +
                    "deviceMemoryTotal INTEGER, " +
                    "deviceMemoryAvailable INTEGER " +
                    ")";
    private static final String ALTER_TABLE_ADD_MEMORY_TOTAL = "ALTER TABLE info_history ADD deviceMemoryTotal INT";
    private static final String ALTER_TABLE_ADD_MEMORY_AVAILABLE = "ALTER TABLE info_history ADD deviceMemoryAvailable INT";
    private static final String SELECT_LAST_INFO =
            "SELECT * FROM info_history ORDER BY ts LIMIT ?";
    private static final String INSERT_INFO =
            "INSERT OR IGNORE INTO info_history(ts, deviceBatteryLevel, deviceBatteryCharging, deviceWifi, " +
            "deviceGps, deviceIp, deviceKeyguard, deviceRingVolume, deviceMobileData, deviceBluetooth, deviceUsbStorage, " +
            "wifiRssi, wifiSsid, wifiSecurity, wifiState, wifiIp, wifiTx, wifiRx, " +
            "gpsState, gpsProvider, gpsLat, gpsLon, gpsAlt, gpsSpeed, gpsCourse, " +
            "mobileRssi, mobileCarrier, mobileNumber, mobileImsi, mobileData, mobileIp, mobileState, mobileSimState, mobileTx, mobileRx, " +
            "mobile2Rssi, mobile2Carrier, mobile2Number, mobile2Imsi, mobile2Data, mobile2Ip, mobile2State, mobile2SimState, mobile2Tx, mobile2Rx," +
            "deviceMemoryTotal, deviceMemoryAvailable" +
            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, " +
            "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String DELETE_FROM_INFO =
            "DELETE FROM info_history WHERE _id=?";
    private static final String DELETE_OLD_ITEMS =
            "DELETE FROM info_history WHERE ts < ?";

    public static String getCreateTableSql() {
        return CREATE_TABLE;
    }

    public static String getAlterTableAddMemoryTotalSql() {
        return ALTER_TABLE_ADD_MEMORY_TOTAL;
    }

    public static String getAlterTableAddMemoryAvailableSql() {
        return ALTER_TABLE_ADD_MEMORY_AVAILABLE;
    }

    public static void insert(SQLiteDatabase db, DetailedInfo item) {
        try {
            DetailedInfo.Device device = item.getDevice();
            DetailedInfo.Wifi wifi = item.getWifi();
            DetailedInfo.Gps gps = item.getGps();
            DetailedInfo.Mobile mobile = item.getMobile();
            DetailedInfo.Mobile mobile2 = item.getMobile2();

            db.execSQL(INSERT_INFO, new String[]{
                    Long.toString(item.getTs()),

                    device != null && device.getBatteryLevel() != null ? device.getBatteryLevel().toString() : null,
                    device != null ? device.getBatteryCharging() : null,
                    device != null && device.getWifi() != null ? (device.getWifi() ? "1" : "0") : null,
                    device != null && device.getGps() != null ? (device.getGps() ? "1" : "0") : null,
                    device != null ? device.getIp() : null,
                    device != null && device.getKeyguard() != null ? (device.getKeyguard() ? "1" : "0") : null,
                    device != null && device.getRingVolume() != null ? device.getRingVolume().toString() : null,
                    device != null && device.getMobileData() != null ? (device.getMobileData() ? "1" : "0") : null,
                    device != null && device.getBluetooth() != null ? (device.getBluetooth() ? "1" : "0") : null,
                    device != null && device.getUsbStorage() != null ? (device.getUsbStorage() ? "1" : "0") : null,

                    wifi != null && wifi.getRssi() != null ? wifi.getRssi().toString() : null,
                    wifi != null ? wifi.getSsid() : null,
                    wifi != null ? wifi.getSecurity() : null,
                    wifi != null ? wifi.getState() : null,
                    wifi != null ? wifi.getIp() : null,
                    wifi != null && wifi.getTx() != null ? wifi.getTx().toString() : null,
                    wifi != null && wifi.getRx() != null ? wifi.getRx().toString() : null,

                    gps != null ? gps.getState() : null,
                    gps != null ? gps.getProvider() : null,
                    gps != null && gps.getLat() != null ? gps.getLat().toString() : null,
                    gps != null && gps.getLon() != null ? gps.getLon().toString() : null,
                    gps != null && gps.getAlt() != null ? gps.getAlt().toString() : null,
                    gps != null && gps.getSpeed() != null ? gps.getSpeed().toString() : null,
                    gps != null && gps.getCourse() != null ? gps.getCourse().toString() : null,

                    mobile != null && mobile.getRssi() != null ? mobile.getRssi().toString() : null,
                    mobile != null ? mobile.getCarrier() : null,
                    mobile != null ? mobile.getNumber() : null,
                    mobile != null ? mobile.getImsi() : null,
                    mobile != null && mobile.getData() != null ? (mobile.getData() ? "1" : "0") : null,
                    mobile != null ? mobile.getIp() : null,
                    mobile != null ? mobile.getState() : null,
                    mobile != null ? mobile.getSimState() : null,
                    mobile != null && mobile.getTx() != null ? mobile.getTx().toString() : null,
                    mobile != null && mobile.getRx() != null ? mobile.getRx().toString() : null,

                    mobile2 != null && mobile2.getRssi() != null ? mobile2.getRssi().toString() : null,
                    mobile2 != null ? mobile2.getCarrier() : null,
                    mobile2 != null ? mobile2.getNumber() : null,
                    mobile2 != null ? mobile2.getImsi() : null,
                    mobile2 != null && mobile2.getData() != null ? (mobile2.getData() ? "1" : "0") : null,
                    mobile2 != null ? mobile2.getIp() : null,
                    mobile2 != null ? mobile2.getState() : null,
                    mobile2 != null ? mobile2.getSimState() : null,
                    mobile2 != null && mobile2.getTx() != null ? mobile2.getTx().toString() : null,
                    mobile2 != null && mobile2.getRx() != null ? mobile2.getRx().toString() : null,

                    device != null && device.getMemoryTotal() != null ? device.getMemoryTotal().toString() : null,
                    device != null && device.getMemoryAvailable() != null ? device.getMemoryAvailable().toString() : null,
            });
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void deleteOldItems(SQLiteDatabase db) {
        long oldTs = System.currentTimeMillis() - 24 * 60 * 60 * 1000L;
        try {
            db.execSQL(DELETE_OLD_ITEMS, new String[]{
                    Long.toString(oldTs)
            });
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void delete(SQLiteDatabase db, List<DetailedInfo> items) {
        db.beginTransaction();
        try {
            for (DetailedInfo item : items) {
                db.execSQL(DELETE_FROM_INFO, new String[]{
                        Long.toString(item.getId())
                });
            }
            db.setTransactionSuccessful();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            db.endTransaction();
        }
    }

    public static List<DetailedInfo> select(SQLiteDatabase db, int limit) {
        Cursor cursor = db.rawQuery( SELECT_LAST_INFO, new String[] {
                Integer.toString(limit)
        });
        List<DetailedInfo> result = new LinkedList<>();

        boolean isDataNotEmpty = cursor.moveToFirst();
        while (isDataNotEmpty) {
            DetailedInfo item = new DetailedInfo(cursor);
            result.add(item);
            isDataNotEmpty = cursor.moveToNext();
        }
        cursor.close();

        return result;
    }
}
