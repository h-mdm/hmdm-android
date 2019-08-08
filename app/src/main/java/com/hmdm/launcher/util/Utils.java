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
import android.app.admin.DevicePolicyManager;
import android.app.admin.SystemUpdatePolicy;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.view.WindowManager;

import com.hmdm.launcher.Const;
import com.hmdm.launcher.json.ServerConfig;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static android.app.admin.DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED;

public class Utils {
    private static final String LOG_TAG = "HeadwindMDM";

    public static boolean isDeviceOwner(Context context) {
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        return dpm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && dpm.isDeviceOwnerApp(context.getPackageName());
    }

    public interface DownloadApplicationProgress {
        void onDownloadProgress(final int progress, final long total, final long current);
    }

    public static File downloadApplication(String strUrl, DownloadApplicationProgress progressHandler ) throws Exception {
        File tempFile = new File( Environment.getExternalStorageDirectory(), getFileName( strUrl ) );
        if ( tempFile.exists() ) {
            tempFile.delete();
        }

        try {
            tempFile.createNewFile();
        } catch ( Exception e ) {
            e.printStackTrace();

            tempFile = File.createTempFile( getFileName( strUrl ), "temp" );
        }

        URL url = new URL( strUrl );

        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept-Encoding", "identity");
        connection.setConnectTimeout((int)Const.CONNECTION_TIMEOUT);
        connection.setReadTimeout((int)Const.CONNECTION_TIMEOUT);
        connection.connect();

        if (connection.getResponseCode() != 200) {
            return null;
        }

        int lengthOfFile = connection.getContentLength();

        progressHandler.onDownloadProgress( 0, lengthOfFile, 0 );

        InputStream is = url.openStream();
        DataInputStream dis = new DataInputStream(is);

        byte[] buffer = new byte[1024];
        int length;
        long total = 0;

        FileOutputStream fos = new FileOutputStream( tempFile );
        while ( ( length = dis.read( buffer ) ) > 0 ) {
            total += length;
            progressHandler.onDownloadProgress(
                    ( int ) ( ( total * 100.0f ) / lengthOfFile ),
                    lengthOfFile,
                    total );
            fos.write( buffer, 0, length );
        }
        fos.flush();
        fos.close();

        dis.close();

        return tempFile;
    }

    private static String getFileName( String strUrl ) {
        return strUrl.substring( strUrl.lastIndexOf( "/" ) );
    }

    public interface SilentInstallErrorHandler {
        public void onInstallError();
    }

    public static void silentInstallApplication(Context context, File file, String packageName, SilentInstallErrorHandler errorHandler) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        try {
            Log.i(LOG_TAG, "Installing " + packageName);
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
            Log.i(LOG_TAG, "Installation session committed");

        } catch (Exception e) {
            errorHandler.onInstallError();
        }
    }

    private static IntentSender createIntentSender(Context context, int sessionId, String packageName) {
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

    // Automatically get dangerous permissions
    @TargetApi(Build.VERSION_CODES.M)
    public static boolean autoGrantRequestedPermissions(Context context, String packageName) {
        DevicePolicyManager devicePolicyManager = (DevicePolicyManager) context.getSystemService(
                Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponentName = LegacyUtils.getAdminComponentName(context);

        List<String> permissions = getRuntimePermissions(context.getPackageManager(), packageName);
        for (String permission : permissions) {
            boolean success = devicePolicyManager.setPermissionGrantState(adminComponentName,
                    packageName, permission, PERMISSION_GRANT_STATE_GRANTED);
            if (!success) {
                return false;
            }
        }
        return true;
    }

    private static List<String> getRuntimePermissions(PackageManager packageManager, String packageName) {
        List<String> permissions = new ArrayList<>();
        PackageInfo packageInfo;
        try {
            packageInfo =
                    packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
        } catch (PackageManager.NameNotFoundException e) {
            return permissions;
        }

        if (packageInfo != null && packageInfo.requestedPermissions != null) {
            for (String requestedPerm : packageInfo.requestedPermissions) {
                if (isRuntimePermission(packageManager, requestedPerm)) {
                    permissions.add(requestedPerm);
                }
            }
        }
        return permissions;
    }

    private static boolean isRuntimePermission(PackageManager packageManager, String permission) {
        try {
            PermissionInfo pInfo = packageManager.getPermissionInfo(permission, 0);
            if (pInfo != null) {
                if ((pInfo.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE)
                        == PermissionInfo.PROTECTION_DANGEROUS) {
                    return true;
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
        }
        return false;
    }

    public static int OverlayWindowType() {
        // https://stackoverflow.com/questions/45867533/system-alert-window-permission-on-api-26-not-working-as-expected-permission-den
        if (  Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            return WindowManager.LayoutParams.TYPE_SYSTEM_ERROR;
        }
    }

    public static boolean isLightColor(int color) {
        final int THRESHOLD = 0xA0;
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        return red >= THRESHOLD && green >= THRESHOLD && blue >= THRESHOLD;
    }

    @TargetApi(Build.VERSION_CODES.M)
    public static void setSystemUpdatePolicy(Context context, int systemUpdateType, String scheduledFrom, String scheduledTo) {
        DevicePolicyManager devicePolicyManager = (DevicePolicyManager)context.getSystemService(
                Context.DEVICE_POLICY_SERVICE);
        ComponentName deviceAdmin = LegacyUtils.getAdminComponentName(context);

        SystemUpdatePolicy currentPolicy = devicePolicyManager.getSystemUpdatePolicy();
        if (currentPolicy != null) {
            // Check if policy type shouldn't be changed
            if (systemUpdateType == ServerConfig.SYSTEM_UPDATE_INSTANT && currentPolicy.getPolicyType() == SystemUpdatePolicy.TYPE_INSTALL_AUTOMATIC ||
                systemUpdateType == ServerConfig.SYSTEM_UPDATE_MANUAL && currentPolicy.getPolicyType() == SystemUpdatePolicy.TYPE_POSTPONE) {
                return;
            }
        }
        SystemUpdatePolicy newPolicy = null;
        switch (systemUpdateType) {
            case ServerConfig.SYSTEM_UPDATE_INSTANT:
                newPolicy = SystemUpdatePolicy.createAutomaticInstallPolicy();
                break;
            case ServerConfig.SYSTEM_UPDATE_SCHEDULE:
                // Here we use update window times
                if (scheduledFrom != null && scheduledTo != null) {
                    int windowStart = getMinutesFromString(scheduledFrom);
                    int windowEnd = getMinutesFromString(scheduledTo);
                    if (windowStart == -1) {
                        Log.e(Const.LOG_TAG, "Ignoring scheduled system update policy: wrong start time: " + scheduledFrom);
                        return;
                    }
                    if (windowEnd == -1) {
                        Log.e(Const.LOG_TAG, "Ignoring scheduled system update policy: wrong end time: " + scheduledFrom);
                        return;
                    }
                    newPolicy = SystemUpdatePolicy.createWindowedInstallPolicy(windowStart, windowEnd);
                } else {
                    Log.e(Const.LOG_TAG, "Ignoring scheduled system update policy: update window is not set on server");
                    return;
                }
                break;
            case ServerConfig.SYSTEM_UPDATE_MANUAL:
                newPolicy = SystemUpdatePolicy.createPostponeInstallPolicy();
                break;
        }
        try {
            devicePolicyManager.setSystemUpdatePolicy(deviceAdmin, newPolicy);
        } catch (Exception e) {
            Log.e(Const.LOG_TAG, "Failed to set system update policy: " + e.getMessage());
        }
    }

    private static int getMinutesFromString(String s) {
        try {
            // s has a fixed format: hh:mm with heading zeroes
            String hours = s.substring(0, 2);
            String minutes = s.substring(3, 5);
            int h = Integer.parseInt(hours);
            int m = Integer.parseInt(minutes);
            return h * 60 + m;
        } catch (Exception e) {
            return -1;
        }
    }
}
