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

import com.hmdm.launcher.json.RemoteLogConfig;
import com.hmdm.launcher.json.RemoteLogItem;

import java.util.List;

public class LogConfigTable {
    private static final String CREATE_TABLE =
            "CREATE TABLE log_rules (" +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "packageId TEXT, " +
                    "level INTEGER, " +
                    "filter TEXT " +
                    ")";
    private static final String DELETE_ALL =
            "DELETE FROM log_rules";
    private static final String INSERT_RULE =
            "INSERT OR IGNORE INTO log_rules(packageId, level, filter) VALUES (?, ?, ?)";
    private static final String FIND_MATCHING =
            "SELECT * FROM log_rules WHERE packageId = ? AND level >= ? AND (filter IS NULL OR filter = '' OR ? LIKE ('%' || filter || '%')) LIMIT 1";

    public static String getCreateTableSql() {
        return CREATE_TABLE;
    }

    public static void replaceAll(SQLiteDatabase db, List<RemoteLogConfig> items) {
        db.beginTransaction();
        try {
            db.execSQL(DELETE_ALL);
            for (RemoteLogConfig item : items) {
                db.execSQL(INSERT_RULE, new String[]{
                        item.getPackageId(),
                        Integer.toString(item.getLogLevel()),
                        item.getFilter()
                });
            }
            db.setTransactionSuccessful();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            db.endTransaction();
        }
    }

    public static boolean match(SQLiteDatabase db, RemoteLogItem item) {
        Cursor cursor = db.rawQuery(FIND_MATCHING, new String[] {
                item.getPackageId(),
                Integer.toString(item.getLogLevel()),
                item.getMessage()
        });
        boolean ret = cursor.moveToFirst();
        cursor.close();
        return ret;
    }
}
