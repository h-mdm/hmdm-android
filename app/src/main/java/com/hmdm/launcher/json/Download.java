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
public class Download {
    @JsonIgnore
    private long _id;

    private String url;
    private String path;
    private long attempts;
    private long lastAttemptTime;
    private boolean downloaded;
    private boolean installed;

    public Download() {}

    public Download(Download download) {
        _id = download._id;
        url = download.url;
        path = download.path;
        attempts = download.attempts;
        lastAttemptTime = download.lastAttemptTime;
        downloaded = download.downloaded;
        installed = download.installed;
    }

    @SuppressLint("Range")
    public Download(Cursor cursor) {
        setId(cursor.getLong(cursor.getColumnIndex("_id")));
        setUrl(cursor.getString(cursor.getColumnIndex("url")));
        setPath(cursor.getString(cursor.getColumnIndex("path")));
        setAttempts(cursor.getLong(cursor.getColumnIndex("attempts")));
        setLastAttemptTime(cursor.getLong(cursor.getColumnIndex("lastAttemptTime")));
        setDownloaded(cursor.getInt(cursor.getColumnIndex("downloaded")) != 0);
        setInstalled(cursor.getInt(cursor.getColumnIndex("installed")) != 0);
    }

    @JsonIgnore
    public long getId() {
        return _id;
    }

    @JsonIgnore
    public void setId(long _id) {
        this._id = _id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public long getAttempts() {
        return attempts;
    }

    public void setAttempts(long attempts) {
        this.attempts = attempts;
    }

    public long getLastAttemptTime() {
        return lastAttemptTime;
    }

    public void setLastAttemptTime(long lastAttemptTime) {
        this.lastAttemptTime = lastAttemptTime;
    }

    public boolean isDownloaded() {
        return downloaded;
    }

    public void setDownloaded(boolean downloaded) {
        this.downloaded = downloaded;
    }

    public boolean isInstalled() {
        return installed;
    }

    public void setInstalled(boolean installed) {
        this.installed = installed;
    }
}
