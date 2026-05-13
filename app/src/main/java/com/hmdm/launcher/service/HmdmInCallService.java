/*
 * Pure Speech Fork — HmdmInCallService (COMPLETE UPDATED VERSION)
 *
 * Changes in this version:
 *   - Outgoing calls now launch InCallActivity immediately on STATE_DIALING
 *     so the user sees "Calling..." straight away instead of a blank dialer
 *   - STATE_ACTIVE transitions InCallActivity from "Calling..." to "Connected"
 *     via a broadcast intent
 *   - Incoming call flow unchanged
 */

package com.hmdm.launcher.service;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.InCallService;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.hmdm.launcher.dialer.InCallActivity;
import com.hmdm.launcher.dialer.IncomingCallActivity;
import com.hmdm.launcher.util.CallWhitelistManager;

@RequiresApi(api = Build.VERSION_CODES.M)
public class HmdmInCallService extends InCallService {

    private static final String TAG = "HmdmInCallService";

    /** Broadcast action sent to InCallActivity when call becomes active */
    public static final String ACTION_CALL_CONNECTED =
            "com.hmdm.launcher.ACTION_CALL_CONNECTED";

    @SuppressLint("StaticFieldLeak")
    private static Call currentCall;

    private static HmdmInCallService instance;

    public static Call getCurrentCall() { return currentCall; }
    public static HmdmInCallService getInstance() { return instance; }

    public void muteCall(boolean muted) { setMuted(muted); }

    public void setSpeakerRoute(boolean speaker) {
        setAudioRoute(speaker
                ? CallAudioState.ROUTE_SPEAKER
                : CallAudioState.ROUTE_EARPIECE);
    }

    // =========================================================================
    // onCallAdded — entry point for every call
    // =========================================================================

    @Override
    public void onCallAdded(Call call) {
        instance   = this;
        currentCall = call;
        super.onCallAdded(call);

        int state = call.getState();
        Log.d(TAG, "onCallAdded — state=" + stateToString(state));

        // Resolve caller info up-front (used in both incoming and outgoing paths)
        final String resolvedNumber = (call.getDetails().getHandle() != null)
                ? call.getDetails().getHandle().getSchemeSpecificPart()
                : "";
        final String resolvedName = resolveCallerName(resolvedNumber);

        if (state == Call.STATE_RINGING) {
            // ---- INCOMING call — enforce whitelist ----
            handleIncomingCall(call, resolvedNumber, resolvedName);

        } else if (state == Call.STATE_DIALING ||
                state == Call.STATE_CONNECTING ||
                state == Call.STATE_NEW) {
            // -------------------------------------------------------
            // Enforce whitelist for ALL outgoing calls regardless of
            // what app placed them — Contacts, browser, 3rd party, etc.
            // ConfirmCallActivity is only a UI hint, not enforcement.
            // -------------------------------------------------------
            if (!CallWhitelistManager.getInstance(this).isAllowed(resolvedNumber)) {
                Log.d(TAG, "BLOCKING outgoing call to: " + resolvedNumber);
                call.disconnect();
                currentCall = null;
                return; // Do not register callback or launch any UI
            }
            // Allowed — show InCallActivity immediately with "Calling..." state
            Log.d(TAG, "Outgoing call allowed — launching InCallActivity (DIALING)");
            launchInCallActivity(resolvedName, resolvedNumber, false);
        }

        // ---- State-change callback ----
        call.registerCallback(new Call.Callback() {

            private boolean inCallLaunched =
                    (state == Call.STATE_DIALING  ||
                            state == Call.STATE_CONNECTING ||
                            state == Call.STATE_NEW);

            @Override
            public void onStateChanged(Call call, int newState) {
                Log.d(TAG, "State -> " + stateToString(newState));

                // For outgoing calls that were already in DIALING when added,
                // we launched InCallActivity above, so just notify it to switch
                // from "Calling..." to "Connected" when STATE_ACTIVE fires.
                if (newState == Call.STATE_ACTIVE) {
                    if (!inCallLaunched) {
                        // Edge case: call went straight to ACTIVE (e.g. emulator)
                        inCallLaunched = true;
                        launchInCallActivity(resolvedName, resolvedNumber, true);
                    } else {
                        // Already showing InCallActivity — broadcast "connected"
                        Intent connected = new Intent(ACTION_CALL_CONNECTED);
                        connected.setPackage(getPackageName());
                        sendBroadcast(connected);
                    }
                }

                // For incoming calls that reach DIALING after being answered
                if ((newState == Call.STATE_DIALING ||
                        newState == Call.STATE_CONNECTING) && !inCallLaunched) {
                    inCallLaunched = true;
                    launchInCallActivity(resolvedName, resolvedNumber, false);
                }

                if (newState == Call.STATE_DISCONNECTED ||
                        newState == Call.STATE_DISCONNECTING) {
                    if (call.equals(currentCall)) {
                        currentCall = null;
                        Log.d(TAG, "currentCall cleared");
                    }
                }
            }
        });
    }

    @Override
    public void onCallRemoved(Call call) {
        super.onCallRemoved(call);
        if (call.equals(currentCall)) currentCall = null;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Launches InCallActivity.
     * @param isConnected true = show "Connected" + start timer immediately;
     *                    false = show "Calling..." and wait for the broadcast.
     */
    private void launchInCallActivity(String name, String number, boolean isConnected) {
        Intent intent = new Intent(this, InCallActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
        intent.putExtra(InCallActivity.EXTRA_CALLER_NAME,   name);
        intent.putExtra(InCallActivity.EXTRA_CALLER_NUMBER, number);
        intent.putExtra(InCallActivity.EXTRA_IS_CONNECTED,  isConnected);
        startActivity(intent);
    }

    private void handleIncomingCall(Call call, String number, String callerName) {
        Log.d(TAG, "Incoming call from: [" + number + "]");

        boolean allowed = CallWhitelistManager.getInstance(this).isAllowed(number);

        if (!allowed) {
            Log.d(TAG, "BLOCKING call from: " + number);
            call.reject(false, null);
            currentCall = null;
            return;
        }

        Log.d(TAG, "ALLOWING call from: " + number);

        Intent intent = new Intent(this, IncomingCallActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT |
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        intent.putExtra(IncomingCallActivity.EXTRA_CALLER_NAME,   callerName);
        intent.putExtra(IncomingCallActivity.EXTRA_CALLER_NUMBER, number);
        startActivity(intent);
    }

    private String resolveCallerName(String number) {
        if (number == null || number.isEmpty()) return "Unknown";
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME},
                    ContactsContract.CommonDataKinds.Phone.NUMBER + " = ?",
                    new String[]{number}, null);
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && !name.isEmpty()) return name;
            }
        } catch (Exception e) {
            Log.w(TAG, "Contact lookup failed: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }
        return number;
    }

    private String stateToString(int state) {
        switch (state) {
            case Call.STATE_RINGING:       return "RINGING";
            case Call.STATE_ACTIVE:        return "ACTIVE";
            case Call.STATE_HOLDING:       return "HOLDING";
            case Call.STATE_DIALING:       return "DIALING";
            case Call.STATE_CONNECTING:    return "CONNECTING";
            case Call.STATE_DISCONNECTED:  return "DISCONNECTED";
            case Call.STATE_DISCONNECTING: return "DISCONNECTING";
            case Call.STATE_NEW:           return "NEW";
            default:                       return "UNKNOWN(" + state + ")";
        }
    }
}