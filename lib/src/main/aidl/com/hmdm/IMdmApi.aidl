// IMdmApi.aidl
package com.hmdm;

interface IMdmApi {
    /**
     * Get the MDM configuration
     */
    Bundle queryConfig();

    /**
     * Send a log message
     */
    void log(long timestamp, int level, String packageId, String message);
}
