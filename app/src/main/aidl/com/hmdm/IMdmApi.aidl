// IMdmApi.aidl
package com.hmdm;

// Declare any non-default types here with import statements

interface IMdmApi {
    /**
     * Get the MDM configuration
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
}
