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

package com.hmdm.launcher.worker;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.hmdm.launcher.Const;
import com.hmdm.launcher.helper.ConfigUpdater;
import com.hmdm.launcher.json.PushMessage;
import com.hmdm.launcher.util.InstallUtils;
import com.hmdm.launcher.util.RemoteLogger;
import com.hmdm.launcher.util.SystemUtils;
import com.hmdm.launcher.util.Utils;

import org.json.JSONObject;

import java.io.File;
import java.util.Iterator;

public class PushNotificationProcessor {
    public static void process(PushMessage message, Context context) {
        RemoteLogger.log(context, Const.LOG_INFO, "Got Push Message, type " + message.getMessageType());
        if (message.getMessageType().equals(PushMessage.TYPE_CONFIG_UPDATED)) {
            // Update local configuration
            ConfigUpdater.notifyConfigUpdate(context);
            // The configUpdated should be broadcasted after the configuration update is completed
            return;
        } else if (message.getMessageType().equals(PushMessage.TYPE_RUN_APP)) {
            // Run application
            runApplication(context, message.getPayloadJSON());
            // Do not broadcast this message to other apps
            return;
        } else if (message.getMessageType().equals(PushMessage.TYPE_UNINSTALL_APP)) {
            // Uninstall application
            AsyncTask.execute(() -> uninstallApplication(context, message.getPayloadJSON()));
            return;
        } else if (message.getMessageType().equals(PushMessage.TYPE_DELETE_FILE)) {
            // Delete file
            AsyncTask.execute(() -> deleteFile(context, message.getPayloadJSON()));
            return;
        } else if (message.getMessageType().equals(PushMessage.TYPE_DELETE_DIR)) {
            // Delete directory recursively
            AsyncTask.execute(() -> deleteDir(context, message.getPayloadJSON()));
            return;
        } else if (message.getMessageType().equals(PushMessage.TYPE_PURGE_DIR)) {
            // Purge directory (delete all files recursively)
            AsyncTask.execute(() -> purgeDir(context, message.getPayloadJSON()));
            return;
        } else if (message.getMessageType().equals(PushMessage.TYPE_PERMISSIVE_MODE)) {
            // Turn on permissive mode
            LocalBroadcastManager.getInstance(context).
                    sendBroadcast(new Intent(Const.ACTION_PERMISSIVE_MODE));
            return;
        } else if (message.getMessageType().equals(PushMessage.TYPE_RUN_COMMAND)) {
            // Run a command-line script
            AsyncTask.execute(() -> runCommand(context, message.getPayloadJSON()));
            return;
        } else if (message.getMessageType().equals(PushMessage.TYPE_REBOOT)) {
            // Reboot a device
            AsyncTask.execute(() -> reboot(context));
            return;
        } else if (message.getMessageType().equals(PushMessage.TYPE_EXIT_KIOSK)) {
            // Temporarily exit kiosk mode
            LocalBroadcastManager.getInstance(context).
                sendBroadcast(new Intent(Const.ACTION_EXIT_KIOSK));
        return;
    }

        // Send broadcast to all plugins
        Intent intent = new Intent(Const.INTENT_PUSH_NOTIFICATION_PREFIX + message.getMessageType());
        JSONObject jsonObject = message.getPayloadJSON();
        if (jsonObject != null) {
            intent.putExtra(Const.INTENT_PUSH_NOTIFICATION_EXTRA, jsonObject.toString());
        }
        context.sendBroadcast(intent);
    }

    private static void runApplication(Context context, JSONObject payload) {
        if (payload == null) {
            return;
        }
        try {
            String pkg = payload.getString("pkg");
            String action = payload.optString("action", null);
            JSONObject extras = payload.optJSONObject("extra");
            Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(pkg);
            if (launchIntent != null) {
                if (action != null) {
                    launchIntent.setAction(action);
                }
                if (extras != null) {
                    Iterator<String> keys = extras.keys();
                    String key;
                    while (keys.hasNext()) {
                        key = keys.next();
                        Object value = extras.get(key);
                        if (value instanceof String) {
                            launchIntent.putExtra(key, (String) value);
                        } else if (value instanceof Integer) {
                            launchIntent.putExtra(key, ((Integer) value).intValue());
                        } else if (value instanceof Float) {
                            launchIntent.putExtra(key, ((Float) value).floatValue());
                        } else if (value instanceof Boolean) {
                            launchIntent.putExtra(key, ((Boolean) value).booleanValue());
                        }
                    }
                }

                // These magic flags are found in the source code of the default Android launcher
                // These flags preserve the app activity stack (otherwise a launch activity appears at the top which is not correct)
                launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                context.startActivity(launchIntent);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void uninstallApplication(Context context, JSONObject payload) {
        if (payload == null) {
            RemoteLogger.log(context, Const.LOG_WARN, "Uninstall request failed: no package specified");
            return;
        }
        if (!Utils.isDeviceOwner(context)) {
            // Require device owner for non-interactive uninstallation
            RemoteLogger.log(context, Const.LOG_WARN, "Uninstall request failed: no device owner");
            return;
        }

        try {
            String pkg = payload.getString("pkg");
            InstallUtils.silentUninstallApplication(context, pkg);
            RemoteLogger.log(context, Const.LOG_INFO, "Uninstalled application: " + pkg);
        } catch (Exception e) {
            RemoteLogger.log(context, Const.LOG_WARN, "Uninstall request failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void deleteFile(Context context, JSONObject payload) {
        if (payload == null) {
            RemoteLogger.log(context, Const.LOG_WARN, "File delete failed: no path specified");
            return;
        }

        try {
            String path = payload.getString("path");
            File file = new File(Environment.getExternalStorageDirectory(), path);
            file.delete();
            RemoteLogger.log(context, Const.LOG_INFO, "Deleted file: " + path);
        } catch (Exception e) {
            RemoteLogger.log(context, Const.LOG_WARN, "File delete failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] childFiles = fileOrDirectory.listFiles();
            for (File child : childFiles) {
                deleteRecursive(child);
            }
        }
        fileOrDirectory.delete();
    }

    private static void deleteDir(Context context, JSONObject payload) {
        if (payload == null) {
            RemoteLogger.log(context, Const.LOG_WARN, "Directory delete failed: no path specified");
            return;
        }

        try {
            String path = payload.getString("path");
            File file = new File(Environment.getExternalStorageDirectory(), path);
            deleteRecursive(file);
            RemoteLogger.log(context, Const.LOG_INFO, "Deleted directory: " + path);
        } catch (Exception e) {
            RemoteLogger.log(context, Const.LOG_WARN, "Directory delete failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void purgeDir(Context context, JSONObject payload) {
        if (payload == null) {
            RemoteLogger.log(context, Const.LOG_WARN, "Directory purge failed: no path specified");
            return;
        }

        try {
            String path = payload.getString("path");
            File file = new File(Environment.getExternalStorageDirectory(), path);
            if (!file.isDirectory()) {
                RemoteLogger.log(context, Const.LOG_WARN, "Directory purge failed: not a directory: " + path);
                return;
            }
            String recursive = payload.optString("recursive");
            File[] childFiles = file.listFiles();
            for (File child : childFiles) {
                if (recursive == null || !recursive.equals("1")) {
                    if (!child.isDirectory()) {
                        child.delete();
                    }
                } else {
                    deleteRecursive(child);
                }
            }
            RemoteLogger.log(context, Const.LOG_INFO, "Purged directory: " + path);
        } catch (Exception e) {
            RemoteLogger.log(context, Const.LOG_WARN, "Directory purge failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void runCommand(Context context, JSONObject payload) {
        if (payload == null) {
            RemoteLogger.log(context, Const.LOG_WARN, "Command failed: no command specified");
            return;
        }

        try {
            String command = payload.getString("command");
            Log.d(Const.LOG_TAG, "Executing a command: " + command);
            String result = SystemUtils.executeShellCommand(command, true);
            String msg = "Executed a command: " + command;
            if (!result.equals("")) {
                if (result.length() > 200) {
                    result = result.substring(0, 200) + "...";
                }
                msg += " Result: " + result;
            }
            RemoteLogger.log(context, Const.LOG_DEBUG, msg);

        } catch (Exception e) {
            RemoteLogger.log(context, Const.LOG_WARN, "Command failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void reboot(Context context) {
        RemoteLogger.log(context, Const.LOG_WARN, "Rebooting by a Push message");
        if (Utils.checkAdminMode(context)) {
            if (!Utils.reboot(context)) {
                RemoteLogger.log(context, Const.LOG_WARN, "Reboot failed");
            }
        } else {
            RemoteLogger.log(context, Const.LOG_WARN, "Reboot failed: no permissions");
        }
    }
}
