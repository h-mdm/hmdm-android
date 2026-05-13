/*
 * Pure Speech Fork — LockScreenActivity
 *
 * Custom lock screen shown when:
 *   (a) End call button is pressed (via DevicePolicyManager.lockNow())
 *   (b) Screen wakes after being locked
 *
 * Shows:
 *   - Current time (large, updates every minute)
 *   - Current date
 *   - Missed call count from call log
 *   - "Swipe up or press any key to unlock" hint
 *
 * Dismiss: any key press or swipe up gesture
 *
 * Runs above the system lock screen via FLAG_SHOW_WHEN_LOCKED.
 * Does NOT require a PIN — swipe/keypress to dismiss only.
 */

package com.hmdm.launcher.ui;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.CallLog;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.hmdm.launcher.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LockScreenActivity extends AppCompatActivity {

    private static final String TAG = "LockScreenActivity";

    private TextView timeView;
    private TextView dateView;
    private TextView missedCallsView;

    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private GestureDetector gestureDetector;

    // =========================================================================
    // Clock tick — updates every minute
    // =========================================================================
    private final Runnable clockRunnable = new Runnable() {
        @Override
        public void run() {
            updateClock();
            // Schedule next update at the start of the next minute
            long now = System.currentTimeMillis();
            long nextMinute = (now / 60000 + 1) * 60000;
            clockHandler.postDelayed(this, nextMinute - now);
        }
    };

    // =========================================================================
    // Receiver — refresh missed calls when screen turns on
    // =========================================================================
    private final BroadcastReceiver screenOnReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                updateMissedCalls();
                updateClock();
            }
        }
    };

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Show above system lock screen and keep screen on
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON   |
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        // Full screen, no status bar
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                        View.SYSTEM_UI_FLAG_FULLSCREEN       |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        setContentView(R.layout.activity_lock_screen);

        timeView        = findViewById(R.id.lock_time);
        dateView        = findViewById(R.id.lock_date);
        missedCallsView = findViewById(R.id.lock_missed_calls);

        updateClock();
        updateMissedCalls();

        // Start clock updater
        clockHandler.post(clockRunnable);

        // Swipe-up gesture to dismiss
        gestureDetector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    private static final int SWIPE_THRESHOLD    = 80;
                    private static final int SWIPE_VELOCITY     = 100;

                    @Override
                    public boolean onFling(MotionEvent e1, MotionEvent e2,
                                           float vX, float vY) {
                        if (e1 == null || e2 == null) return false;
                        float dY = e2.getY() - e1.getY();
                        float dX = e2.getX() - e1.getX();
                        // Swipe UP (negative dY) with more vertical than horizontal
                        if (Math.abs(dY) > Math.abs(dX) &&
                                Math.abs(dY) > SWIPE_THRESHOLD &&
                                Math.abs(vY) > SWIPE_VELOCITY &&
                                dY < 0) {
                            unlock();
                            return true;
                        }
                        return false;
                    }

                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        // Double tap also unlocks
                        unlock();
                        return true;
                    }
                });

        // Register screen-on receiver
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(screenOnReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(screenOnReceiver, filter);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateClock();
        updateMissedCalls();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        clockHandler.removeCallbacks(clockRunnable);
        try { unregisterReceiver(screenOnReceiver); } catch (Exception e) { /* ignore */ }
    }

    // =========================================================================
    // Touch — pass to gesture detector
    // =========================================================================

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    // =========================================================================
    // Key press — any key unlocks (except volume keys)
    // =========================================================================

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
                // Let volume keys work normally
                return super.onKeyDown(keyCode, event);
            case KeyEvent.KEYCODE_POWER:
            case KeyEvent.KEYCODE_ENDCALL:
                // Power/end call just stays locked
                return true;
            default:
                // Any other key (call key, dpad, number keys) = unlock
                unlock();
                return true;
        }
    }

    // Back button stays locked
    @Override
    public void onBackPressed() {
        // Do nothing — cannot back out of lock screen
    }

    // =========================================================================
    // Data updates
    // =========================================================================

    private void updateClock() {
        Date now = new Date();
        timeView.setText(new SimpleDateFormat("h:mm", Locale.getDefault()).format(now));
        dateView.setText(new SimpleDateFormat(
                "EEEE, MMMM d", Locale.getDefault()).format(now));
    }

    private void updateMissedCalls() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            missedCallsView.setVisibility(View.GONE);
            return;
        }

        try {
            Cursor cursor = getContentResolver().query(
                    CallLog.Calls.CONTENT_URI,
                    new String[]{CallLog.Calls._ID},
                    CallLog.Calls.TYPE + " = ? AND " + CallLog.Calls.IS_READ + " = 0",
                    new String[]{String.valueOf(CallLog.Calls.MISSED_TYPE)},
                    null);

            int count = (cursor != null) ? cursor.getCount() : 0;
            if (cursor != null) cursor.close();

            if (count > 0) {
                missedCallsView.setVisibility(View.VISIBLE);
                missedCallsView.setText(count == 1
                        ? "1 missed call"
                        : count + " missed calls");
            } else {
                missedCallsView.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            missedCallsView.setVisibility(View.GONE);
        }
    }

    // =========================================================================
    // Unlock
    // =========================================================================

    private void unlock() {
        finish();
        overridePendingTransition(0, android.R.anim.fade_out);
    }
}