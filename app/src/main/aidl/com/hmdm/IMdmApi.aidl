// IMdmApi.aidl
package com.hmdm;

// Declare any non-default types here with import statements

interface IMdmApi {
    /**
     * Get the MDM configuration (non-privileged, no sensitive info)
     */
    Bundle queryConfig();

    /**
     * Send a log message
     */
    void log(long timestamp, int level, String packageId, String message);

    /**
     * Get app preference
     */
    String queryAppPreference(String packageId, String attr);

    /**
     * Set app preference
     */
    boolean setAppPreference(String packageId, String attr, String value);

    /**
     * Send app preferences to server
     */
    void commitAppPreferences(String packageId);

    // Added in library version 1.1.3
    // All new methods should be added at the end of the AIDL file!!!

    /**
     * Get the API version supported by the launcher (1.1.3 = 113)
     */
    int getVersion();

    /**
     * Get the MDM configuration (privileged, including IMEI and serial number)
     */
    Bundle queryPrivilegedConfig(String apiKey);

    /**
     * Set a custom field to send it to the server
     */
    void setCustom(int number, String value);

    // Added in library version 1.1.5
    /**
     * Force the configuration update
     */
    void forceConfigUpdate();

    // Added in library version 1.1.8
    /**
     * Send a Push notification to initiate an action from the app
     * Returns true on success and false if the api key is invalid
     */
    boolean sendPush(String apiKey, String type, String payload);
}
