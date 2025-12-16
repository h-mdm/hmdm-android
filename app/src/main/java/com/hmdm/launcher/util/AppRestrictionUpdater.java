package com.hmdm.launcher.util;

import android.annotation.TargetApi;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;

import com.hmdm.launcher.json.ApplicationSetting;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class AppRestrictionUpdater {

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static void updateAppRestrictions(Context context, List<ApplicationSetting> appSettings) {
        Map<String, Bundle> map = new HashMap<>();

        for (ApplicationSetting setting : appSettings) {
            Bundle bundle = map.get(setting.getPackageId());
            if (bundle == null) {
                bundle = new Bundle();
                map.put(setting.getPackageId(), bundle);
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

    private static String[] parseArray(String str) {
        String str1 = str.substring(1, str.length() - 2);
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
