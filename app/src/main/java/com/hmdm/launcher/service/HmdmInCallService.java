/*
 * Headwind MDM: Open Source Android MDM Software
 * https://h-mdm.com
 *
 * Pure Speech Fork — HmdmInCallService (COMPLETE UPDATED VERSION)
 * Intercepts all incoming and outgoing calls.
 * Blocks non-whitelisted incoming calls silently.
 * Launches IncomingCallActivity for allowed calls.
 * Launches InCallActivity when a call becomes active.
 * Reads whitelist from locally stored MDM config — fully offline capable.
 *
 * Requires: Android API 23+ (Android 6.0+)
 * App must be set as default phone/dialer app for this service to be invoked.
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

    // -------------------------------------------------------------------------
    // Static reference to the current call so IncomingCallActivity and
    // InCallActivity can answer, reject, or hang up without needing a binding.
    // Only one call is active at a time on these devices so a single static
    // reference is safe and avoids the complexity of a bound service.
    // -------------------------------------------------------------------------
    @SuppressLint("StaticFieldLeak")
    private static Call currentCall;

    // Static instance reference so InCallActivity can call setMuted/setSpeaker
    private static HmdmInCallService instance;

    // -------------------------------------------------------------------------
    // Static accessors
    // -------------------------------------------------------------------------

    public static Call getCurrentCall() {
        return currentCall;
    }

    public static HmdmInCallService getInstance() {
        return instance;
    }

    // -------------------------------------------------------------------------
    // Audio control — called by InCallActivity mute and speaker buttons
    // -------------------------------------------------------------------------

    /**
     * Mutes or unmutes the microphone during an active call.
     */
// Renamed to muteCall() to avoid colliding with InCallService.setMuted() which is final.
// InCallService.setMuted(boolean) IS the correct API — we just call it directly.
    public void muteCall(boolean muted) {
        setMuted(muted);
    }

    public void setSpeakerRoute(boolean speaker) {
        setAudioRoute(speaker
                ? CallAudioState.ROUTE_SPEAKER
                : CallAudioState.ROUTE_EARPIECE);
    }

    // -------------------------------------------------------------------------
    // Called by the Android telecom framework when any call is added
    // (incoming or outgoing). This is the entry point for all call control.
    // -------------------------------------------------------------------------
    @Override
    public void onCallAdded(Call call) {
        instance = this;
        super.onCallAdded(call);
        currentCall = call;

        int state = call.getState();
        Log.d(TAG, "onCallAdded — state=" + stateToString(state));

        if (state == Call.STATE_RINGING) {
            // Incoming call — enforce whitelist, show incoming UI if allowed
            handleIncomingCall(call);
        }
        // Outgoing calls are already enforced by DialerActivity/ConfirmCallActivity
        // before TelecomManager.placeCall() is ever called, so no whitelist
        // check is needed here. InCallActivity is launched via STATE_ACTIVE below.

        // -------------------------------------------------------------------------
        // Resolve caller info now so it is available inside the anonymous callback
        // below without needing another lookup when STATE_ACTIVE fires.
        // -------------------------------------------------------------------------
        final String resolvedNumber = (call.getDetails().getHandle() != null)
                ? call.getDetails().getHandle().getSchemeSpecificPart()
                : "";
        final String resolvedName = resolveCallerName(resolvedNumber);

        // -------------------------------------------------------------------------
        // Register state-change callback.
        // Handles:
        //   STATE_ACTIVE       -> launch InCallActivity (in-call screen)
        //   STATE_DISCONNECTED -> clean up currentCall reference
        // -------------------------------------------------------------------------
        call.registerCallback(new Call.Callback() {

            // Guard against launching InCallActivity more than once per call
            // in case STATE_ACTIVE fires multiple times (e.g. hold/unhold)
            private boolean inCallLaunched = false;

            @Override
            public void onStateChanged(Call call, int newState) {
                Log.d(TAG, "Call state changed -> " + stateToString(newState));

                // -----------------------------------------------------------------
                // STATE_ACTIVE — call is connected, show in-call screen.
                // Triggered when:
                //   (a) User answers an incoming call in IncomingCallActivity
                //   (b) An outgoing call is answered by the remote party
                // -----------------------------------------------------------------
                if (newState == Call.STATE_ACTIVE && !inCallLaunched) {
                    inCallLaunched = true;
                    Log.d(TAG, "STATE_ACTIVE — launching InCallActivity for: "
                            + resolvedNumber);

                    Intent inCallIntent = new Intent(
                            HmdmInCallService.this, InCallActivity.class);
                    inCallIntent.addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK |
                                    Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
                    inCallIntent.putExtra(
                            InCallActivity.EXTRA_CALLER_NAME,   resolvedName);
                    inCallIntent.putExtra(
                            InCallActivity.EXTRA_CALLER_NUMBER, resolvedNumber);
                    startActivity(inCallIntent);
                }

                // -----------------------------------------------------------------
                // DISCONNECTED / DISCONNECTING — clean up.
                // InCallActivity listens for this via its own Call.Callback
                // and finishes itself, so we only need to clear the reference here.
                // -----------------------------------------------------------------
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

    // -------------------------------------------------------------------------
    // Called when a call is removed from the telecom framework (ended).
    // -------------------------------------------------------------------------
    @Override
    public void onCallRemoved(Call call) {
        super.onCallRemoved(call);
        Log.d(TAG, "onCallRemoved");
        if (call.equals(currentCall)) {
            currentCall = null;
        }
    }

    // -------------------------------------------------------------------------
    // Core whitelist enforcement for incoming calls.
    // Blocked calls are rejected silently — no UI shown to the user.
    // Allowed calls launch IncomingCallActivity (answer/reject screen).
    // -------------------------------------------------------------------------
    private void handleIncomingCall(Call call) {
        Uri handle = call.getDetails().getHandle();
        String number = (handle != null) ? handle.getSchemeSpecificPart() : "";

        Log.d(TAG, "Incoming call from: [" + number + "]");

        boolean allowed = CallWhitelistManager.getInstance(this).isAllowed(number);

        if (!allowed) {
            Log.d(TAG, "BLOCKING call from: " + number);
            call.reject(false, null);
            currentCall = null;
            return;
        }

        Log.d(TAG, "ALLOWING call from: " + number);

        String callerName = resolveCallerName(number);

        Intent intent = new Intent(this, IncomingCallActivity.class);
        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT |
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        );
        intent.putExtra(IncomingCallActivity.EXTRA_CALLER_NAME,   callerName);
        intent.putExtra(IncomingCallActivity.EXTRA_CALLER_NUMBER, number);
        startActivity(intent);
    }

    // -------------------------------------------------------------------------
    // Contact name lookup from local contacts database.
    // Works on AOSP and Google Play. Returns number if no match found.
    // -------------------------------------------------------------------------
    private String resolveCallerName(String number) {
        if (number == null || number.isEmpty()) {
            return "Unknown";
        }
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME},
                    ContactsContract.CommonDataKinds.Phone.NUMBER + " = ?",
                    new String[]{number},
                    null
            );
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && !name.isEmpty()) {
                    Log.d(TAG, "Resolved contact: " + name);
                    return name;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Contact lookup failed: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return number;
    }

    // -------------------------------------------------------------------------
    // Helper — converts Call state integer to readable string for logging.
    // -------------------------------------------------------------------------
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