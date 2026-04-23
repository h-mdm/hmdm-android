package com.hmdm.launcher.util;

import android.content.Context;
import android.util.Log;

import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.json.ApplicationSetting;
import com.hmdm.launcher.json.ServerConfig;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CallWhitelistManager {

    private static final String TAG = "CallWhitelistManager";

    // Must match the key you set in Headwind admin > Configuration > Application Settings
    private static final String SETTING_KEY = "allowed_numbers";

    private static CallWhitelistManager instance;
    private final Context context;

    private CallWhitelistManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized CallWhitelistManager getInstance(Context context) {
        if (instance == null) {
            instance = new CallWhitelistManager(context);
        }
        return instance;
    }

    /**
     * Reads the whitelist directly from the locally stored MDM config.
     * Works fully offline — no server connection needed.
     */
    public Set<String> getAllowedNumbers() {
        ServerConfig config = SettingsHelper.getInstance(context).getConfig();
        if (config == null) {
            Log.w(TAG, "No MDM config loaded yet");
            return Collections.emptySet();
        }

        List<ApplicationSetting> settings = config.getApplicationSettings();
        if (settings == null) {
            return Collections.emptySet();
        }

        for (ApplicationSetting setting : settings) {
            if (SETTING_KEY.equals(setting.getName())) {
                String csv = setting.getValue();
                if (csv == null || csv.trim().isEmpty()) {
                    return Collections.emptySet();
                }
                Set<String> numbers = new HashSet<>();
                for (String number : csv.split(",")) {
                    String trimmed = number.trim();
                    if (!trimmed.isEmpty()) {
                        numbers.add(normalize(trimmed));
                    }
                }
                Log.d(TAG, "Loaded " + numbers.size() + " allowed numbers");
                return numbers;
            }
        }

        Log.w(TAG, "No '" + SETTING_KEY + "' setting found in MDM config");
        return Collections.emptySet();
    }

    public boolean isAllowed(String incomingNumber) {
        if (incomingNumber == null) return false;
        Set<String> allowed = getAllowedNumbers();
        if (allowed.isEmpty()) {
            // If no whitelist is configured, block everything
            Log.d(TAG, "Whitelist is empty — blocking all calls");
            return false;
        }
        return allowed.contains(normalize(incomingNumber));
    }

    private String normalize(String number) {
        // Strip all non-digit characters for comparison
        return number.replaceAll("[^0-9]", "");
    }
}