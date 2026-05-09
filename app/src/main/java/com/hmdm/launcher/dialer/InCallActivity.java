/*
 * Pure Speech Fork — InCallActivity
 *
 * Displays the active call screen once a call is answered or connected.
 * Launched by HmdmInCallService when call state transitions to STATE_ACTIVE.
 *
 * Features:
 *   - Live call duration timer (counts up from 0)
 *   - Caller name and number
 *   - Mute toggle
 *   - Speaker toggle
 *   - Hang up button
 *
 * Dpad navigation for Kyocera E4610:
 *   Red end key  → hang up
 *   Dpad center  → activates focused button
 */

package com.hmdm.launcher.dialer;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Call;
import android.telecom.CallAudioState;
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

    // -------------------------------------------------------------------------
    // UI references
    // -------------------------------------------------------------------------
    private TextView timerView;
    private TextView nameView;
    private TextView numberView;
    private TextView statusView;
    private Button   muteBtn;
    private Button   speakerBtn;
    private Button   hangupBtn;

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------
    private boolean isMuted   = false;
    private boolean isSpeaker = false;

    // -------------------------------------------------------------------------
    // Timer
    // -------------------------------------------------------------------------
    private final Handler  timerHandler  = new Handler(Looper.getMainLooper());
    private long           callStartTime = 0;

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            long elapsed = System.currentTimeMillis() - callStartTime;
            long seconds = (elapsed / 1000) % 60;
            long minutes = (elapsed / 1000) / 60;
            long hours   = minutes / 60;
            minutes      = minutes % 60;

            String formatted;
            if (hours > 0) {
                formatted = String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
            } else {
                formatted = String.format(Locale.US, "%02d:%02d", minutes, seconds);
            }

            if (timerView != null) {
                timerView.setText(formatted);
            }
            timerHandler.postDelayed(this, 1000);
        }
    };

    // -------------------------------------------------------------------------
    // Call state callback — finish activity when call ends
    // -------------------------------------------------------------------------
    private final Call.Callback callCallback = new Call.Callback() {
        @Override
        public void onStateChanged(Call call, int newState) {
            Log.d(TAG, "Call state changed: " + newState);
            if (newState == Call.STATE_DISCONNECTED ||
                    newState == Call.STATE_DISCONNECTING) {
                runOnUiThread(() -> {
                    Log.d(TAG, "Call ended — finishing InCallActivity");
                    finish();
                });
            }
        }
    };

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_in_call);

        String name   = getIntent().getStringExtra(EXTRA_CALLER_NAME);
        String number = getIntent().getStringExtra(EXTRA_CALLER_NUMBER);

        if (name == null || name.isEmpty()) name = number;
        if (number == null) number = "";

        Log.d(TAG, "InCallActivity started — " + name + " / " + number);

        // Bind views
        timerView   = findViewById(R.id.incall_timer);
        nameView    = findViewById(R.id.incall_name);
        numberView  = findViewById(R.id.incall_number);
        statusView  = findViewById(R.id.incall_status);
        muteBtn     = findViewById(R.id.incall_mute);
        speakerBtn  = findViewById(R.id.incall_speaker);
        hangupBtn   = findViewById(R.id.incall_hangup);

        nameView.setText(name);
        numberView.setText(number);
        statusView.setText("Connected");

        // Register callback on current call
        Call call = HmdmInCallService.getCurrentCall();
        if (call != null) {
            call.registerCallback(callCallback);
        } else {
            Log.w(TAG, "No current call found on InCallActivity start");
            finish();
            return;
        }

        // Start timer
        callStartTime = System.currentTimeMillis();
        timerHandler.post(timerRunnable);

        // Initial focus on hang up — most important action
        hangupBtn.requestFocus();

        // -------------------------------------------------------------------------
        // Mute
        // -------------------------------------------------------------------------
        muteBtn.setOnClickListener(v -> toggleMute());
        muteBtn.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN &&
                    (keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                            keyCode == KeyEvent.KEYCODE_ENTER)) {
                toggleMute();
                return true;
            }
            return false;
        });

        // -------------------------------------------------------------------------
        // Speaker
        // -------------------------------------------------------------------------
        speakerBtn.setOnClickListener(v -> toggleSpeaker());
        speakerBtn.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN &&
                    (keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                            keyCode == KeyEvent.KEYCODE_ENTER)) {
                toggleSpeaker();
                return true;
            }
            return false;
        });

        // -------------------------------------------------------------------------
        // Hang up
        // -------------------------------------------------------------------------
        hangupBtn.setOnClickListener(v -> hangUp());
        hangupBtn.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN &&
                    (keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                            keyCode == KeyEvent.KEYCODE_ENTER)) {
                hangUp();
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timerHandler.removeCallbacks(timerRunnable);
        Call call = HmdmInCallService.getCurrentCall();
        if (call != null) {
            call.unregisterCallback(callCallback);
        }
        Log.d(TAG, "onDestroy");
    }

    // -------------------------------------------------------------------------
    // Hardware keys
    // -------------------------------------------------------------------------

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENDCALL:
                // Red end key — hang up
                hangUp();
                return true;

            case KeyEvent.KEYCODE_BACK:
                // Back does nothing during an active call
                // to prevent accidental navigation away
                return true;

            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    // -------------------------------------------------------------------------
    // Call control
    // -------------------------------------------------------------------------

    private void hangUp() {
        Log.d(TAG, "hangUp()");
        Call call = HmdmInCallService.getCurrentCall();
        if (call != null) {
            call.disconnect();
        }
        finish();
    }

    private void toggleMute() {
        isMuted = !isMuted;
        Log.d(TAG, "Mute toggled: " + isMuted);

        HmdmInCallService service = HmdmInCallService.getInstance();
        if (service != null) {
            service.setMuted(isMuted);
        }

        muteBtn.setText(isMuted ? "UNMUTE" : "MUTE");
        muteBtn.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        isMuted
                                ? android.graphics.Color.parseColor("#E65100")
                                : android.graphics.Color.parseColor("#1565C0")
                )
        );
    }

    private void toggleSpeaker() {
        isSpeaker = !isSpeaker;
        Log.d(TAG, "Speaker toggled: " + isSpeaker);

        HmdmInCallService service = HmdmInCallService.getInstance();
        if (service != null) {
            service.setSpeaker(isSpeaker);
        }

        speakerBtn.setText(isSpeaker ? "SPEAKER ON" : "SPEAKER");
        speakerBtn.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        isSpeaker
                                ? android.graphics.Color.parseColor("#1B5E20")
                                : android.graphics.Color.parseColor("#1565C0")
                )
        );
    }
}
