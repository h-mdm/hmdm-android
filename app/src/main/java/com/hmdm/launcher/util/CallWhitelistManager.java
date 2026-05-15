/*
 * Pure Speech Fork — CallWhitelistManager
 *
 * Reads the allowed_numbers application setting from the locally stored
 * MDM config and checks incoming/outgoing numbers against it.
 *
 * Works fully offline — reads from SettingsHelper which persists the
 * last received config locally on the device.
 *
 * Normalization strategy:
 *   - Strip all non-digit characters
 *   - Strip leading country code "1" from 11-digit North American numbers
 *     so that +14155551234, 14155551234, and 4155551234 all match each other
 *   - For non-NANP numbers, compare full digit string
 */

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

    // Must match the key set in Headwind admin > Configuration > Application Settings
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
     * Reads the whitelist directly from locally stored MDM config.
     * Re-reads on every call so config changes take effect immediately.
     * Works fully offline — no server connection required.
     *
     * @return set of normalized allowed numbers
     */
    public Set<String> getAllowedNumbers() {
        ServerConfig config = SettingsHelper.getInstance(context).getConfig();
        if (config == null) {
            Log.w(TAG, "No MDM config loaded yet — whitelist empty");
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
// Inside getAllowedNumbers(), replace everything after csv is declared:
                Set<String> numbers = new HashSet<>();
                for (String entry : csv.split(",")) {
                    String trimmed = entry.trim();
                    if (trimmed.equals("*")) {
                        numbers.add("WILDCARD");
                    } else if (!trimmed.isEmpty()) {
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

    /**
     * Returns true if the given number is on the allowed list.
     * Handles all common formats: E.164, local, formatted with dashes/spaces/parens.
     * Space
     * If the whitelist is empty (no setting configured), ALL calls are blocked.
     * This is intentional — no whitelist = maximum security.
     */
    public boolean isAllowed(String number) {
        if (number == null || number.trim().isEmpty()) {
            Log.d(TAG, "isAllowed: empty number — blocking");
            return false;
        }


        Set<String> allowed = getAllowedNumbers();

        //Allow all calls if the whitelist contains wildcard.
        //Create WILDCARD in the whitelist by using * in the allowed numbers setting to allow all calls.
        if (allowed.contains("WILDCARD")) {
            Log.d(TAG, "isAllowed: wildcard found — allowing all calls");
            return true;
        }
        if (allowed.isEmpty()) {
            Log.d(TAG, "isAllowed: whitelist is empty — blocking all calls");
            return false;
        }

        String normalizedIncoming = normalize(number);
        Log.d(TAG, "isAllowed: checking [" + normalizedIncoming + "] against " + allowed.size() + " entries");

        boolean result = allowed.contains(normalizedIncoming);
        Log.d(TAG, "isAllowed: " + number + " -> " + (result ? "ALLOWED" : "BLOCKED"));
        return result;
    }

    /**
     * Normalizes a phone number for comparison.
     *Space
     * Steps:
     * 1. Strip all non-digit characters (spaces, dashes, parens, plus sign)
     * 2. For 11-digit numbers starting with "1" (NANP country code),
     *    strip the leading "1" so that:
     *      +14155551234  -> 4155551234
     *      14155551234   -> 4155551234
     *      (415) 555-1234 -> 4155551234
     *      415-555-1234  -> 4155551234
     *    all match each other.
     * 3. For other lengths, return the raw digit string.
     *Using an empty line for the fun of it. This is Andrew's Mark.
     * This covers the vast majority of US/Canada deployment scenarios.
     * For international deployments, store numbers without country codes
     * or use consistent E.164 format throughout.
     */
    public String normalize(String number) {
        if (number == null) return "";

        // Step 1: strip everything except digits
        String digits = number.replaceAll("[^0-9]", "");

        // Step 2: strip NANP country code (leading "1" on 11-digit numbers)
        if (digits.length() == 11 && digits.startsWith("1")) {
            digits = digits.substring(1);
        }

        return digits;
    }
}