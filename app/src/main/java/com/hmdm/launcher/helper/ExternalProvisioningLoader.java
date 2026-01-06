package com.hmdm.launcher.helper;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.Manifest;
import android.text.TextUtils;
import android.util.Log;

import com.hmdm.launcher.Const;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.LinkedHashSet;

/**
 * Loads launcher provisioning data (server URL, device id) from an external JSON file.
 * Intended for one-time provisioning on first launch. The file is read only when
 * the device id is not yet configured; if a device id is already set (by any
 * other flow), the external file is ignored even if present.
 * The file is searched in public external locations (Downloads and external
 * storage root) when read access is allowed. After applying, the file is deleted.
 *
 * Expected structure:
 * {
 *   "baseUrl": "https://mdm.example.com",
 *   "secondaryBaseUrl": "https://mdm.example.com",
 *   "deviceId": "1234-android-1"
 * }
 */
public class ExternalProvisioningLoader {

    private static final String FILE_NAME = "HeadwindProvisioning.json";

    private final Context context;

    public ExternalProvisioningLoader(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean applyIfPresent(SettingsHelper settingsHelper) {
        boolean hasDeviceId = !TextUtils.isEmpty(settingsHelper.getDeviceId());
        if (hasDeviceId) {
            return false;
        }
        File configFile = locateConfigFile();
        if (configFile == null) {
            return false;
        }
        try {
            String payload = readFile(configFile);
            if (TextUtils.isEmpty(payload)) {
                Log.w(Const.LOG_TAG, "External provisioning file is empty: " + configFile);
                return false;
            }
            JSONObject jsonObject = new JSONObject(payload);
            if (!hasKnownKeys(jsonObject)) {
                Log.w(Const.LOG_TAG, "External provisioning file missing expected keys: " + configFile);
                return false;
            }
            applyPayload(jsonObject, settingsHelper);
            if (!configFile.delete()) {
                Log.w(Const.LOG_TAG, "Failed to delete external provisioning file: " + configFile);
            }
            Log.i(Const.LOG_TAG, "External provisioning settings applied from " + configFile.getAbsolutePath());
            return true;
        } catch (JSONException e) {
            Log.w(Const.LOG_TAG, "Invalid JSON in external provisioning file: " + configFile, e);
            return false;
        } catch (IOException e) {
            Log.w(Const.LOG_TAG, "Failed to read external provisioning file: " + configFile, e);
            return false;
        }
    }

    private File locateConfigFile() {
        LinkedHashSet<File> candidates = new LinkedHashSet<>();
        // Public locations only when we are allowed to read them.
        if (canAccessPublicExternal()) {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (downloadsDir != null) {
                candidates.add(new File(downloadsDir, FILE_NAME));
            }
            File externalRoot = Environment.getExternalStorageDirectory();
            if (externalRoot != null) {
                candidates.add(new File(externalRoot, FILE_NAME));
            }
        }
        for (File candidate : candidates) {
            if (candidate != null && candidate.exists()) {
                return candidate;
            }
        }
        return null;
    }

    private boolean canAccessPublicExternal() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private String readFile(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            return baos.toString("UTF-8");
        } finally {
            fis.close();
        }
    }

    private void applyPayload(JSONObject payload, SettingsHelper settingsHelper) throws JSONException {
        if (payload.has("baseUrl")) {
            settingsHelper.setBaseUrl(payload.getString("baseUrl"));
        }
        if (payload.has("secondaryBaseUrl")) {
            settingsHelper.setSecondaryBaseUrl(payload.getString("secondaryBaseUrl"));
        }
        if (payload.has("deviceId")) {
            settingsHelper.setDeviceId(payload.getString("deviceId"));
        }
        if (payload.has("deviceIdUse")) {
            settingsHelper.setDeviceIdUse(payload.optString("deviceIdUse", null));
        }
    }

    private boolean hasKnownKeys(JSONObject payload) {
        return payload.has("baseUrl") ||
                payload.has("secondaryBaseUrl") ||
                payload.has("deviceId") ||
                payload.has("deviceIdUse");
    }
}
