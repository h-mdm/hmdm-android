/*
 * Pure Speech Fork — InCallActivity
 *
 * Shows the active call screen for both outgoing and incoming calls.
 *
 * States:
 *   CALLING  — outgoing call is dialing, not yet answered
 *              Status shows "Calling..." in amber, timer hidden
 *   CONNECTED — call is answered / active
 *              Status shows "Connected" in green, timer counts up
 *
 * Transition from CALLING → CONNECTED is triggered by a local broadcast
 * sent by HmdmInCallService when STATE_ACTIVE fires.
 */

package com.hmdm.launcher.dialer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Call;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.hmdm.launcher.R;
import com.hmdm.launcher.service.HmdmInCallService;

import java.util.Locale;

public class InCallActivity extends AppCompatActivity {

    private static final String TAG = "InCallActivity";

    public static final String EXTRA_CALLER_NAME   = "caller_name";
    public static final String EXTRA_CALLER_NUMBER = "caller_number";
    /** True if the call is already active when the activity is created */
    public static final String EXTRA_IS_CONNECTED  = "is_connected";

    // -------------------------------------------------------------------------
    // UI
    // -------------------------------------------------------------------------
    private TextView statusView;
    private TextView timerView;
    private TextView nameView;
    private TextView numberView;
    private Button   muteBtn;
    private Button   speakerBtn;
    private Button   hangupBtn;

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------
    private boolean isMuted    = false;
    private boolean isSpeaker  = false;
    private boolean isConnected = false;

    // -------------------------------------------------------------------------
    // Timer
    // -------------------------------------------------------------------------
    private final Handler  timerHandler  = new Handler(Looper.getMainLooper());
    private long           callStartTime = 0;

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isConnected) return;
            long elapsed = System.currentTimeMillis() - callStartTime;
            long seconds = (elapsed / 1000) % 60;
            long minutes = (elapsed / 1000) / 60;
            long hours   = minutes / 60;
            minutes      = minutes % 60;
            String formatted = (hours > 0)
                    ? String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
                    : String.format(Locale.US, "%02d:%02d", minutes, seconds);
            timerView.setText(formatted);
            timerHandler.postDelayed(this, 1000);
        }
    };

    // -------------------------------------------------------------------------
    // Broadcast receiver — listens for "call connected" signal from service
    // -------------------------------------------------------------------------
    private final BroadcastReceiver connectedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (HmdmInCallService.ACTION_CALL_CONNECTED.equals(intent.getAction())) {
                Log.d(TAG, "Received ACTION_CALL_CONNECTED — switching to connected state");
                transitionToConnected();
            }
        }
    };

    // -------------------------------------------------------------------------
    // Call.Callback — finishes activity when call ends
    // -------------------------------------------------------------------------
    private final Call.Callback callCallback = new Call.Callback() {
        @Override
        public void onStateChanged(Call call, int newState) {
            Log.d(TAG, "Call state: " + newState);
            if (newState == Call.STATE_ACTIVE && !isConnected) {
                runOnUiThread(() -> transitionToConnected());
            }
            if (newState == Call.STATE_DISCONNECTED ||
                    newState == Call.STATE_DISCONNECTING) {
                runOnUiThread(() -> finish());
            }
        }
    };

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_in_call);

        String name   = getIntent().getStringExtra(EXTRA_CALLER_NAME);
        String number = getIntent().getStringExtra(EXTRA_CALLER_NUMBER);
        isConnected   = getIntent().getBooleanExtra(EXTRA_IS_CONNECTED, false);

        if (name == null || name.isEmpty()) name = number;
        if (number == null) number = "";

        Log.d(TAG, "InCallActivity — " + name + " / " + number
                + " connected=" + isConnected);

        // Bind views
        statusView  = findViewById(R.id.incall_status);
        timerView   = findViewById(R.id.incall_timer);
        nameView    = findViewById(R.id.incall_name);
        numberView  = findViewById(R.id.incall_number);
        muteBtn     = findViewById(R.id.incall_mute);
        speakerBtn  = findViewById(R.id.incall_speaker);
        hangupBtn   = findViewById(R.id.incall_hangup);

        nameView.setText(name);
        numberView.setText(number);

        // Show correct initial state
        if (isConnected) {
            transitionToConnected();
        } else {
            showCallingState();
        }

        // Register call state callback
        Call call = HmdmInCallService.getCurrentCall();
        if (call != null) {
            call.registerCallback(callCallback);
        } else {
            Log.w(TAG, "No current call on create — finishing");
            finish();
            return;
        }

        hangupBtn.requestFocus();

        // ---- Mute ----
        muteBtn.setOnClickListener(v -> toggleMute());
        muteBtn.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN &&
                    (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
                toggleMute(); return true;
            }
            return false;
        });

        // ---- Speaker ----
        speakerBtn.setOnClickListener(v -> toggleSpeaker());
        speakerBtn.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN &&
                    (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
                toggleSpeaker(); return true;
            }
            return false;
        });

        // ---- Hang up ----
        hangupBtn.setOnClickListener(v -> hangUp());
        hangupBtn.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN &&
                    (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
                hangUp(); return true;
            }
            return false;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Register for the "call connected" broadcast from HmdmInCallService
        IntentFilter filter = new IntentFilter(HmdmInCallService.ACTION_CALL_CONNECTED);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(connectedReceiver, filter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(connectedReceiver, filter);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(connectedReceiver); } catch (Exception e) { /* already unregistered */ }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timerHandler.removeCallbacks(timerRunnable);
        Call call = HmdmInCallService.getCurrentCall();
        if (call != null) {
            try { call.unregisterCallback(callCallback); } catch (Exception e) { /* ignore */ }
        }
        Log.d(TAG, "onDestroy");
    }

    // =========================================================================
    // UI state transitions
    // =========================================================================

    /** Shown while call is dialing / not yet answered */
    private void showCallingState() {
        statusView.setText("Calling...");
        statusView.setTextColor(android.graphics.Color.parseColor("#FFA000")); // amber
        timerView.setText("");
        timerView.setVisibility(android.view.View.INVISIBLE);
    }

    /** Shown once the remote party answers */
    private void transitionToConnected() {
        if (isConnected) return; // guard against double-call
        isConnected   = true;
        callStartTime = System.currentTimeMillis();
        statusView.setText("Connected");
        statusView.setTextColor(android.graphics.Color.parseColor("#4CAF50")); // green
        timerView.setVisibility(android.view.View.VISIBLE);
        timerHandler.post(timerRunnable);
        Log.d(TAG, "Transitioned to CONNECTED");
    }

    // =========================================================================
    // Hardware keys
    // =========================================================================

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENDCALL:
                hangUp();
                return true;
            case KeyEvent.KEYCODE_BACK:
                // Back does nothing during a call
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    // =========================================================================
    // Call control
    // =========================================================================

    private void hangUp() {
        Log.d(TAG, "hangUp()");
        Call call = HmdmInCallService.getCurrentCall();
        if (call != null) call.disconnect();
        finish();
    }

    private void toggleMute() {
        isMuted = !isMuted;
        HmdmInCallService service = HmdmInCallService.getInstance();
        if (service != null) service.muteCall(isMuted);
        muteBtn.setText(isMuted ? "UNMUTE" : "MUTE");
        muteBtn.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        android.graphics.Color.parseColor(
                                isMuted ? "#E65100" : "#1565C0")));
    }

    private void toggleSpeaker() {
        isSpeaker = !isSpeaker;
        HmdmInCallService service = HmdmInCallService.getInstance();
        if (service != null) service.setSpeakerRoute(isSpeaker);
        speakerBtn.setText(isSpeaker ? "SPEAKER ON" : "SPEAKER");
        speakerBtn.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        android.graphics.Color.parseColor(
                                isSpeaker ? "#1B5E20" : "#1565C0")));
    }
}