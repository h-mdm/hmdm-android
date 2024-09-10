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

import com.hmdm.launcher.json.RemoteFile;

public class RemoteFileTable {
    private static final String CREATE_TABLE =
            "CREATE TABLE files (" +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "lastUpdate INTEGER, " +
                    "url TEXT, " +
                    "checksum TEXT, " +
                    "path TEXT UNIQUE, " +
                    "description TEXT " +
                    ")";
    private static final String INSERT_FILE =
            "INSERT OR REPLACE INTO files(lastUpdate, url, checksum, path, description) VALUES (?, ?, ?, ?, ?)";
    private static final String DELETE_FILE =
            "DELETE FROM files WHERE _id=?";
    private static final String DELETE_FILE_BY_PATH =
            "DELETE FROM files WHERE path=?";
    private static final String SELECT_FILE_BY_PATH =
            "SELECT * FROM files WHERE path=?";

    public static String getCreateTableSql() {
        return CREATE_TABLE;
    }

    public static void insert(SQLiteDatabase db, RemoteFile item) {
        try {
            db.execSQL(INSERT_FILE, new String[]{
                    Long.toString(item.getLastUpdate()),
                    item.getUrl(),
                    item.getChecksum(),
                    item.getPath(),
                    item.getDescription()
            });
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void deleteByPath(SQLiteDatabase db, String path) {
        try {
            db.execSQL(DELETE_FILE_BY_PATH, new String[]{ path });
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("Range")
    public static RemoteFile selectByPath(SQLiteDatabase db, String path) {
        Cursor cursor = db.rawQuery(SELECT_FILE_BY_PATH, new String[] { path });

        RemoteFile item = null;
        if (cursor.moveToFirst()) {
            item = new RemoteFile();
            item.setId(cursor.getLong(cursor.getColumnIndex("_id")));
            item.setLastUpdate(cursor.getLong(cursor.getColumnIndex("lastUpdate")));
            item.setUrl(cursor.getString(cursor.getColumnIndex("url")));
            item.setChecksum(cursor.getString(cursor.getColumnIndex("checksum")));
            item.setPath(cursor.getString(cursor.getColumnIndex("path")));
            item.setDescription(cursor.getString(cursor.getColumnIndex("description")));
        }
        cursor.close();

        return item;
    }
}
