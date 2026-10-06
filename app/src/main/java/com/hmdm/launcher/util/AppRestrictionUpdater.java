package com.hmdm.launcher.util;

import android.annotation.TargetApi;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;

import com.hmdm.launcher.json.ApplicationSetting;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class AppRestrictionUpdater {

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static void updateAppRestrictions(Context context, Map<String, ApplicationSetting> appSettings) {
        Map<String, Bundle> map = new HashMap<>();

        for (ApplicationSetting setting : appSettings.values()) {
            Bundle bundle = map.get(setting.getPackageId());
            if (bundle == null) {
                bundle = new Bundle();
                map.put(setting.getPackageId(), bundle);
            }
            if (setting.getName().equals("managedConfig")) {
                parseManagedConfig(bundle, setting.getValue());
                continue;
            }
            String val = setting.getValue();
            Integer intVal = getIntValue(val);
            if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
                bundle.putString(setting.getName(), val.substring(1, val.length() - 1));
            } else if (val.startsWith("[") && val.endsWith("]") && val.length() >= 2) {
                bundle.putStringArray(setting.getName(), parseArray(val));
            } else if (setting.getValue().equalsIgnoreCase("true")) {
                bundle.putBoolean(setting.getName(), true);
            } else if (setting.getValue().equalsIgnoreCase("false")) {
                bundle.putBoolean(setting.getName(), false);
            } else if (intVal != null) {
                bundle.putInt(setting.getName(), intVal);
            } else {
                bundle.putString(setting.getName(), setting.getValue());
            }
        }

        for (Map.Entry<String, Bundle> entry : map.entrySet()) {
            try {
                DevicePolicyManager devicePolicyManager = (DevicePolicyManager) context.getSystemService(
                        Context.DEVICE_POLICY_SERVICE);
                ComponentName adminComponentName = LegacyUtils.getAdminComponentName(context);
                devicePolicyManager.setApplicationRestrictions(adminComponentName,
                        entry.getKey(), entry.getValue());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void parseManagedConfig(Bundle bundle, String str) {
        try {
            JSONObject json = new JSONObject(str);
            parseManagedConfig(bundle, json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void parseManagedConfig(Bundle bundle, JSONObject json) throws JSONException {
        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = json.get(key);
            if (value instanceof String) {
                bundle.putString(key, (String)value);
            } else if (value instanceof Integer) {
                bundle.putInt(key, (int)value);
            } else if (value instanceof Long) {
                bundle.putLong(key, (int)value);
            } else if (value instanceof Double) {
                bundle.putFloat(key, (float)value);
            } else if (value instanceof Boolean) {
                bundle.putBoolean(key, (boolean)value);
            } else if (value instanceof JSONObject) {
                Bundle nested = new Bundle();
                parseManagedConfig(nested, (JSONObject) value);
                bundle.putBundle(key, nested);
            } else if (value instanceof JSONArray) {
                JSONArray array = (JSONArray)value;
                if (array.length() > 0) {
                    Object first = array.opt(0);
                    if (first instanceof String) {
                        String[] stringArray = new String[array.length()];
                        for (int i = 0; i < array.length(); i++) {
                            stringArray[i] = array.optString(i);
                        }
                        bundle.putStringArray(key, stringArray);
                    } else if (first instanceof JSONObject) {
                        Bundle[] bundleArray = new Bundle[array.length()];
                        for (int i = 0; i < array.length(); i++) {
                            Bundle nested = new Bundle();
                            parseManagedConfig(nested, array.optJSONObject(i));
                            bundleArray[i] = nested;
                        }
                        bundle.putParcelableArray(key, bundleArray);
                    }
                    // arrays of integers and booleans are not supported
                }
            }
        }

    }

    private static String[] parseArray(String str) {
        String str1 = str.substring(1, str.length() - 1);
        String[] parts = str1.split(",");
        List<String> res = new LinkedList<>();
        for (String part : parts) {
            part = part.trim();
            if (part.startsWith("\"") && part.endsWith("\"") && part.length() >= 2) {
                part = part.substring(1, part.length() - 1);
            }
            res.add(part);
        }
        return res.toArray(new String[0]);
    }

    private static Integer getIntValue(String val) {
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
