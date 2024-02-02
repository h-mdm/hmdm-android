package com.hmdm.launcher.worker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.hmdm.launcher.Const;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.json.DeviceInfo;
import com.hmdm.launcher.server.ServerService;
import com.hmdm.launcher.server.ServerServiceKeeper;
import com.hmdm.launcher.util.DeviceInfoProvider;

import java.util.concurrent.TimeUnit;

import okhttp3.ResponseBody;
import retrofit2.Response;

public class SendDeviceInfoWorker extends Worker {

    private static final int SEND_DEVICE_INFO_PERIOD_MINS = 15;

    private static final String WORK_TAG_DEVICEINFO = "com.hmdm.launcher.WORK_TAG_DEVICEINFO";

    private Context context;
    private SettingsHelper settingsHelper;

    public SendDeviceInfoWorker(
            @NonNull final Context context,
            @NonNull WorkerParameters params) {
        super(context, params);
        this.context = context;
        settingsHelper = SettingsHelper.getInstance(context);
    }

    @Override
    // This is running in a background thread by WorkManager
    public Result doWork() {
        if (settingsHelper == null || settingsHelper.getConfig() == null) {
            return Result.failure();
        }

        DeviceInfo deviceInfo = DeviceInfoProvider.getDeviceInfo(context, true, true);

        ServerService serverService = ServerServiceKeeper.getServerServiceInstance(context);
        ServerService secondaryServerService = ServerServiceKeeper.getSecondaryServerServiceInstance(context);
        Response<ResponseBody> response = null;

        try {
            response = serverService.sendDevice(settingsHelper.getServerProject(), deviceInfo).execute();
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            if (response == null) {
                response = secondaryServerService.sendDevice(settingsHelper.getServerProject(), deviceInfo).execute();
            }
            if ( response.isSuccessful() ) {
                SettingsHelper.getInstance(context).setExternalIp(response.headers().get(Const.HEADER_IP_ADDRESS));
                return Result.success();
            }
        }
        catch ( Exception e ) { e.printStackTrace(); }

        return Result.failure();
    }

    public static void scheduleDeviceInfoSending(Context context) {
        PeriodicWorkRequest request =
                new PeriodicWorkRequest.Builder(SendDeviceInfoWorker.class, SEND_DEVICE_INFO_PERIOD_MINS, TimeUnit.MINUTES)
                        .addTag(Const.WORK_TAG_COMMON)
                        .setInitialDelay(SEND_DEVICE_INFO_PERIOD_MINS, TimeUnit.MINUTES)
                        .build();
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniquePeriodicWork(WORK_TAG_DEVICEINFO, ExistingPeriodicWorkPolicy.REPLACE, request);
    }
}
