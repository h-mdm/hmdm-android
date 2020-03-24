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

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.hmdm.launcher.Const;
import com.hmdm.launcher.db.DatabaseHelper;
import com.hmdm.launcher.db.RemoteFileTable;
import com.hmdm.launcher.json.Application;
import com.hmdm.launcher.json.RemoteFile;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import java.util.List;

public class InstallUtils {

    public static void generateApplicationsForInstallList(Context context, List<Application> applications,
                                                          List<Application> applicationsForInstall) {
        PackageManager packageManager = context.getPackageManager();

        // First handle apps to be removed, then apps to be installed
        // We process only applications of type "app" (default) and skip web links and others
        for (Application a : applications) {
            if ((a.getType() == null || a.getType().equals(Application.TYPE_APP)) && a.isRemove()) {
                Log.d(Const.LOG_TAG, "checkAndUpdateApplications(): marking app " + a.getPkg() + " to remove");
                applicationsForInstall.add(a);
            }
        }
        for (Application a : applications) {
            if ((a.getType() == null || a.getType().equals(Application.TYPE_APP)) && !a.isRemove()) {
                Log.d(Const.LOG_TAG, "checkAndUpdateApplications(): marking app " + a.getPkg() + " to install");
                applicationsForInstall.add(a);
            }
        }
        Iterator< Application > it = applicationsForInstall.iterator();

        while ( it.hasNext() ) {
            Application application = it.next();
            if ( (application.getUrl() == null || application.getUrl().trim().equals("")) && !application.isRemove() ) {
                // An app without URL is a system app which doesn't require installation
                Log.d(Const.LOG_TAG, "checkAndUpdateApplications(): app " + application.getPkg() + " is system, skipping");
                it.remove();
                continue;
            }

            try {
                PackageInfo packageInfo = packageManager.getPackageInfo( application.getPkg(), 0 );

                if (application.isRemove() && !application.getVersion().equals("0") &&
                        !areVersionsEqual(packageInfo.versionName, application.getVersion())) {
                    // If a removal is required, but the app version doesn't match, do not remove
                    Log.d(Const.LOG_TAG, "checkAndUpdateApplications(): app " + application.getPkg() + " version not match: "
                            + application.getVersion() + " " + packageInfo.versionName + ", skipping");
                    it.remove();
                    continue;
                }

                if (!application.isRemove() &&
                        (application.isSkipVersion() || application.getVersion().equals("0") || areVersionsEqual(packageInfo.versionName, application.getVersion()))) {
                    // If installation is required, but the app of the same version already installed, do not install
                    Log.d(Const.LOG_TAG, "checkAndUpdateApplications(): app " + application.getPkg() + " versions match: "
                            + application.getVersion() + " " + packageInfo.versionName + ", skipping");
                    it.remove();
                    continue;
                }
            } catch ( PackageManager.NameNotFoundException e ) {
                // The app isn't installed, let's keep it in the "To be installed" list
                if (application.isRemove()) {
                    // The app requires removal but already removed, remove from the list so do nothing with the app
                    Log.d(Const.LOG_TAG, "checkAndUpdateApplications(): app " + application.getPkg() + " not found, nothing to remove");
                    it.remove();
                    continue;
                }
            }
        }
    }

    private static boolean areVersionsEqual(String v1, String v2) {
        // Compare only digits (in Android 9 EMUI on Huawei Honor 8A, getPackageInfo doesn't get letters!)
        String v1d = v1.replaceAll("[^\\d.]", "");
        String v2d = v2.replaceAll("[^\\d.]", "");
        return v1d.equals(v2d);
    }

    public static void generateFilesForInstallList(Context context, List<RemoteFile> files,
                                                          List<RemoteFile> filesForInstall) {
        final long TIME_TOLERANCE_MS = 60000;
        for (RemoteFile remoteFile : files) {
            File file = new File(Environment.getExternalStorageDirectory(), remoteFile.getPath());
            if (remoteFile.isRemove()) {
                if (file.exists()) {
                    filesForInstall.add(remoteFile);
                }
            } else {
                if (!file.exists()) {
                    filesForInstall.add(remoteFile);
                } else {
                    RemoteFile remoteFileDb = RemoteFileTable.selectByPath(DatabaseHelper.instance(context).getReadableDatabase(),
                            remoteFile.getPath());
                    if (remoteFileDb != null) {
                        if (!remoteFileDb.getChecksum().equalsIgnoreCase(remoteFile.getChecksum())) {
                            filesForInstall.add(remoteFile);
                        }
                    } else {
                        // Entry not found in the database, let's check the checksum
                        try {
                            String checksum = CryptoUtils.calculateChecksum(new FileInputStream(file));
                            if (checksum.equalsIgnoreCase(remoteFile.getChecksum())) {
                                // File is correct, just save the entry in the database
                                RemoteFileTable.insert(DatabaseHelper.instance(context).getWritableDatabase(), remoteFile);
                            } else {
                                filesForInstall.add(remoteFile);
                            }
                        } catch (FileNotFoundException e) {
                            // We should never be here!
                            filesForInstall.add(remoteFile);
                        }
                    }
                }
            }
        }
    }


        public interface DownloadProgress {
        void onDownloadProgress(final int progress, final long total, final long current);
    }

    public static File downloadFile(Context context, String strUrl, DownloadProgress progressHandler ) throws Exception {
        File tempFile = new File(context.getExternalFilesDir(null), getFileName(strUrl));
        if (tempFile.exists()) {
            tempFile.delete();
        }

        try {
            tempFile.createNewFile();
        } catch (Exception e) {
            e.printStackTrace();

            tempFile = File.createTempFile(getFileName(strUrl), "temp");
        }

        URL url = new URL(strUrl);

        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept-Encoding", "identity");
        connection.setConnectTimeout((int) Const.CONNECTION_TIMEOUT);
        connection.setReadTimeout((int)Const.CONNECTION_TIMEOUT);
        connection.connect();

        if (connection.getResponseCode() != 200) {
            return null;
        }

        int lengthOfFile = connection.getContentLength();

        progressHandler.onDownloadProgress(0, lengthOfFile, 0);

        InputStream is = url.openStream();
        DataInputStream dis = new DataInputStream(is);

        byte[] buffer = new byte[1024];
        int length;
        long total = 0;

        FileOutputStream fos = new FileOutputStream(tempFile);
        while ((length = dis.read(buffer)) > 0) {
            total += length;
            progressHandler.onDownloadProgress(
                    (int)((total * 100.0f) / lengthOfFile),
                    lengthOfFile,
                    total);
            fos.write(buffer, 0, length);
        }
        fos.flush();
        fos.close();

        dis.close();

        return tempFile;
    }

    private static String getFileName(String strUrl) {
        return strUrl.substring(strUrl.lastIndexOf("/"));
    }

    public interface InstallErrorHandler {
        public void onInstallError();
    }

    public static void silentInstallApplication(Context context, File file, String packageName, InstallErrorHandler errorHandler) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }

        if (file.getName().endsWith(".xapk")) {
            XapkUtils.install(context, XapkUtils.extract(context, file), packageName, errorHandler);
            return;
        }

        try {
            Log.i(Const.LOG_TAG, "Installing " + packageName);
            FileInputStream in = new FileInputStream(file);
            PackageInstaller packageInstaller = context.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(packageName);
            // set params
            int sessionId = packageInstaller.createSession(params);
            PackageInstaller.Session session = packageInstaller.openSession(sessionId);
            OutputStream out = session.openWrite("COSU", 0, -1);
            byte[] buffer = new byte[65536];
            int c;
            while ((c = in.read(buffer)) != -1) {
                out.write(buffer, 0, c);
            }
            session.fsync(out);
            in.close();
            out.close();

            session.commit(createIntentSender(context, sessionId, packageName));
            Log.i(Const.LOG_TAG, "Installation session committed");

        } catch (Exception e) {
            errorHandler.onInstallError();
        }
    }

    public static IntentSender createIntentSender(Context context, int sessionId, String packageName) {
        Intent intent = new Intent(Const.ACTION_INSTALL_COMPLETE);
        if (packageName != null) {
            intent.putExtra(Const.PACKAGE_NAME, packageName);
        }
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                0);
        return pendingIntent.getIntentSender();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static void silentUninstallApplication(Context context, String packageName) {
        PackageInstaller packageInstaller = context.getPackageManager().getPackageInstaller();
        try {
            packageInstaller.uninstall(packageName, createIntentSender(context, 0, null));
        } catch (Exception e) {
            // If we're trying to remove an unexistent app, it causes an exception so just ignore it
        }
    }

    public static void requestInstallApplication(Context context, File file, InstallErrorHandler errorHandler) {
        if (file.getName().endsWith(".xapk")) {
            XapkUtils.install(context, XapkUtils.extract(context, file), null, errorHandler);
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = FileProvider.getUriForFile( context,
                    context.getApplicationContext().getPackageName() + ".provider",
                    file );
            intent.setDataAndType( uri, "application/vnd.android.package-archive" );
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(intent);
        } else {
            Uri apkUri = Uri.fromFile( file );
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    public static void requestUninstallApplication(Context context, String packageName) {
        Uri packageUri = Uri.parse("package:" + packageName);
        context.startActivity(new Intent(Intent.ACTION_UNINSTALL_PACKAGE, packageUri));
    }

    public static String getPackageInstallerStatusMessage(int status) {
        switch (status) {
            case PackageInstaller.STATUS_PENDING_USER_ACTION:
                return "PENDING_USER_ACTION";
            case PackageInstaller.STATUS_SUCCESS:
                return "SUCCESS";
            case PackageInstaller.STATUS_FAILURE:
                return "FAILURE_UNKNOWN";
            case PackageInstaller.STATUS_FAILURE_BLOCKED:
                return "BLOCKED";
            case PackageInstaller.STATUS_FAILURE_ABORTED:
                return "ABORTED";
            case PackageInstaller.STATUS_FAILURE_INVALID:
                return "INVALID";
            case PackageInstaller.STATUS_FAILURE_CONFLICT:
                return "CONFLICT";
            case PackageInstaller.STATUS_FAILURE_STORAGE:
                return "STORAGE";
            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
                return "INCOMPATIBLE";
        }
        return "UNKNOWN";
    }

}
