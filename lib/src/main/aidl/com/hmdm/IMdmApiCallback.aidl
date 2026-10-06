// IMdmApiCallback.aidl
package com.hmdm;

// Callback interface used to deliver live configuration update progress events
// from the Headwind MDM launcher to a connected app.
// Added in library version 1.1.9.
//
// Declared "oneway" so that callbacks are dispatched asynchronously and a slow
// (or hung) client can never block the launcher's configuration update.
oneway interface IMdmApiCallback {
    /**
     * The configuration update has started.
     */
    void onConfigUpdateStart();

    /**
     * The configuration update failed before completion.
     * type: 1 = server error, 2 = network error
     */
    void onConfigUpdateError(int type, String errorText);

    /**
     * The server configuration has been loaded and saved.
     */
    void onConfigLoaded();

    /**
     * Device policies/restrictions have been (re-)applied.
     */
    void onPoliciesUpdated();

    /**
     * A remote file is being downloaded.
     */
    void onFileDownloading(String path);

    /**
     * Download progress for the current file/app.
     */
    void onDownloadProgress(int progress, long total, long current);

    /**
     * A remote file failed.
     * type: 1 = download error, 2 = install error
     */
    void onFileError(int type, String path);

    /**
     * The application install/update phase has started.
     */
    void onAppUpdateStart();

    /**
     * An application is being removed.
     */
    void onAppRemoving(String pkg, String name);

    /**
     * An application is being downloaded.
     */
    void onAppDownloading(String pkg, String name);

    /**
     * An application is being installed.
     */
    void onAppInstalling(String pkg, String name);

    /**
     * An application failed.
     * type: 1 = download error, 2 = install error
     */
    void onAppError(int type, String pkg);

    /**
     * A single application install completed.
     */
    void onAppInstallComplete(String pkg);

    /**
     * The configuration update completed (config + policies + files).
     */
    void onConfigUpdateComplete();

    /**
     * All pending application installs completed.
     */
    void onAllAppInstallComplete();
}
