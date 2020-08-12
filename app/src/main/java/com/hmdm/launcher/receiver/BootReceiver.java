package com.hmdm.launcher.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.hmdm.launcher.Const;
import com.hmdm.launcher.ui.MainActivity;
import com.hmdm.launcher.util.Utils;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(Const.LOG_TAG, "Got the BOOT_RECEIVER broadcast");

/*        SettingsHelper settingsHelper = SettingsHelper.getInstance(context);
        if (settingsHelper != null) {
            ServerConfig config = settingsHelper.getConfig();
            if (config != null) {
                try {
                    if (config.getRunDefaultLauncher() == null || !config.getRunDefaultLauncher() ||
                            context.getPackageName().equals(Utils.getDefaultLauncher(context))) {
                        Log.i(Const.LOG_TAG, "Headwind MDM doesn't need to force running at boot");
                        return;
                    }
                } catch (Exception e) {
                    Log.i(Const.LOG_TAG, "Unexpected error in BootReceiver");
                    e.printStackTrace();
                    return;
                }
            }
        }*/

        try {
            if (context.getPackageName().equals(Utils.getDefaultLauncher(context))) {
                Log.i(Const.LOG_TAG, "Headwind MDM is set as default launcher and doesn't need to force running at boot");
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        Intent newIntent = new Intent(context, MainActivity.class);
        newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(newIntent);
            Log.i(Const.LOG_TAG, "Starting main activity from BootReceiver");
        } catch (Exception e) {
            Log.e(Const.LOG_TAG, "Failed to start main activity!");
            e.printStackTrace();
        }

    }
}
