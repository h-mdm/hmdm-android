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

import android.content.Context;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.util.Log;

import com.hmdm.launcher.Const;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class XapkUtils {
    public static List<File> extract(Context context, File xapk) {
        // Here we presume that xapk file name ends with .xapk
        try {
            String extractDir = xapk.getName().substring(0, xapk.getName().length() - 5);
            File extractDirFile = new File(context.getExternalFilesDir(null), extractDir);
            if (extractDirFile.isDirectory()) {
                FileUtils.deleteDirectory(extractDirFile);
            } else if (extractDirFile.exists()) {
                extractDirFile.delete();
            }
            extractDirFile.mkdirs();

            List<File> result = new LinkedList<File>();

            ZipFile zipFile = new ZipFile(xapk);
            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while(entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".apk")) {
                    InputStream inputStream = zipFile.getInputStream(entry);
                    File resultFile = new File(extractDirFile, entry.getName());
                    FileOutputStream outputStream = new FileOutputStream(resultFile);
                    IOUtils.copy(inputStream, outputStream);
                    inputStream.close();
                    outputStream.close();
                    result.add(resultFile);
                }

            }
            zipFile.close();
            return result;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void install(Context context, List<File> files, String packageName, InstallUtils.InstallErrorHandler errorHandler) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        if (files == null) {
            RemoteLogger.log(context, Const.LOG_WARN, "Failed to unpack XAPK for " + packageName + " - ignoring installation");
            if (errorHandler != null) {
                errorHandler.onInstallError(null);
            }
            return;
        }
        long totalSize = 0;
        for (File file : files) {
            totalSize += file.length();
        }

        try {
            Log.i(Const.LOG_TAG, "Installing XAPK " + packageName);
            PackageInstaller packageInstaller = context.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            if (packageName != null) {
                params.setAppPackageName(packageName);
            }
            params.setSize(totalSize);
            int sessionId = packageInstaller.createSession(params);

            for (File file : files) {
                addFileToSession(sessionId, file, packageInstaller);
            }

            PackageInstaller.Session session = packageInstaller.openSession(sessionId);
            session.commit(InstallUtils.createIntentSender(context, sessionId, packageName));
            session.close();
            Log.i(Const.LOG_TAG, "Installation session committed");

        } catch (Exception e) {
            if (errorHandler != null) {
                errorHandler.onInstallError(e.getMessage());
            }
        }
    }

    private static void addFileToSession(int sessionId, File file, PackageInstaller packageInstaller) throws IOException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }

        FileInputStream in = new FileInputStream(file);
        // set params
        PackageInstaller.Session session = packageInstaller.openSession(sessionId);
        OutputStream out = session.openWrite(file.getName(), 0, file.length());
        byte[] buffer = new byte[65536];
        int c;
        while ((c = in.read(buffer)) != -1) {
            out.write(buffer, 0, c);
        }
        session.fsync(out);
        in.close();
        out.close();
        session.close();
    }

}
