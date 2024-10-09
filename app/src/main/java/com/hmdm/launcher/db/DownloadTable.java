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

import android.annotation.SuppressLint;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import com.hmdm.launcher.json.Download;

import java.util.LinkedList;
import java.util.List;

public class DownloadTable {
    private static final String CREATE_TABLE =
            "CREATE TABLE downloads (" +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "url TEXT, " +
                    "path TEXT UNIQUE, " +
                    "attempts INTEGER, " +
                    "lastAttemptTime INTEGER, " +
                    "downloaded INTEGER, " +
                    "installed INTEGER " +
                    ")";
    private static final String INSERT_DOWNLOAD =
            "INSERT OR REPLACE INTO downloads(url, path, attempts, lastAttemptTime, downloaded, installed) VALUES (?, ?, ?, ?, ?, ?)";
    private static final String DELETE_DOWNLOAD =
            "DELETE FROM downloads WHERE _id=?";
    private static final String DELETE_DOWNLOAD_BY_PATH =
            "DELETE FROM downloads WHERE path=?";
    private static final String SELECT_ALL_DOWNLOADS =
            "SELECT * FROM downloads";
    private static final String SELECT_DOWNLOAD_BY_PATH =
            "SELECT * FROM downloads WHERE path=?";
    private static final String DELETE_ALL_DOWNLOADS =
            "DELETE FROM downloads";

    public static String getCreateTableSql() {
        return CREATE_TABLE;
    }

    public static void insert(SQLiteDatabase db, Download item) {
        try {
            db.execSQL(INSERT_DOWNLOAD, new String[]{
                    item.getUrl(),
                    item.getPath(),
                    Long.toString(item.getAttempts()),
                    Long.toString(item.getLastAttemptTime()),
                    item.isDownloaded() ? "1" : "0",
                    item.isInstalled() ? "1" : "0"
            });
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void deleteByPath(SQLiteDatabase db, String path) {
        try {
            db.execSQL(DELETE_DOWNLOAD_BY_PATH, new String[]{ path });
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void deleteAll(SQLiteDatabase db) {
        try {
            db.execSQL(DELETE_ALL_DOWNLOADS, new String[]{ });
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("Range")
    public static List<Download> selectAll(SQLiteDatabase db) {
        Cursor cursor = db.rawQuery(SELECT_ALL_DOWNLOADS, new String[] {});
        List<Download> result = new LinkedList<>();

        boolean isDataNotEmpty = cursor.moveToFirst();
        while (isDataNotEmpty) {
            Download item = new Download(cursor);
            result.add(item);

            isDataNotEmpty = cursor.moveToNext();
        }
        cursor.close();

        return result;
    }

    @SuppressLint("Range")
    public static Download selectByPath(SQLiteDatabase db, String path) {
        Cursor cursor = db.rawQuery(SELECT_DOWNLOAD_BY_PATH, new String[] { path });

        Download item = null;
        if (cursor.moveToFirst()) {
            item = new Download(cursor);
        }
        cursor.close();

        return item;
    }
}
