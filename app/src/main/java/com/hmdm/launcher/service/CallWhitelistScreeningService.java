package com.hmdm.launcher.service;

import android.net.Uri;
import android.os.Build;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.hmdm.launcher.util.CallWhitelistManager;

@RequiresApi(api = Build.VERSION_CODES.N)
public class CallWhitelistScreeningService extends CallScreeningService {

    private static final String TAG = "CallScreening";

    @Override
    public void onScreenCall(@NonNull Call.Details callDetails) {
        Uri handle = callDetails.getHandle();
        String number = handle != null ? handle.getSchemeSpecificPart() : null;
        Log.d(TAG, "Screening call from: " + number);

        boolean allowed = CallWhitelistManager.getInstance(this).isAllowed(number);

        CallResponse response = new CallResponse.Builder()
                .setDisallowCall(!allowed)
                .setRejectCall(!allowed)
                .setSkipCallLog(false)       // always log for audit
                .setSkipNotification(!allowed)
                .build();

        respondToCall(callDetails, response);
        Log.d(TAG, "Call " + (allowed ? "ALLOWED" : "BLOCKED") + ": " + number);
    }
}

