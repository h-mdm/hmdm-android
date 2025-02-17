package com.hmdm.launcher.ui.custom;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.view.View;
import android.widget.TextView;

import com.hmdm.launcher.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class StatusBarUpdater {
    private Context context;
    private TextView clockView;
    private BatteryStateView batteryStateView;
    private final Handler handler = new Handler();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private boolean visible = false;

    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            if (visible) {
                updateStatusBar();
            }
            handler.postDelayed(this, 5000);
        }
    };

    private void updateStatusBar() {
        String currentTime = timeFormat.format(new Date());
        clockView.setText(currentTime);

        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);

        Intent batteryStatus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                context.registerReceiver(null, ifilter, Context.RECEIVER_EXPORTED) :
                context.registerReceiver(null, ifilter);
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        if (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL) {
            int chargePlug = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
            batteryStateView.setPlugged(chargePlug == BatteryManager.BATTERY_PLUGGED_USB ||
                chargePlug == BatteryManager.BATTERY_PLUGGED_AC);
        } else {
            batteryStateView.setPlugged(false);
        }

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        batteryStateView.setChargePercent(level * 100 / scale);
    }

    public void startUpdating(Context context, TextView clockView, BatteryStateView batteryStateView) {
        this.context = context;
        this.clockView = clockView;
        this.batteryStateView = batteryStateView;
        handler.post(updateRunnable);
    }

    public void stopUpdating() {
        handler.removeCallbacks(updateRunnable);
    }

    public void updateControlsState(boolean visible, boolean darkBackground) {
        this.visible = visible;
        if (clockView != null) {
            clockView.setVisibility(visible ? View.VISIBLE : View.GONE);
            clockView.setTextColor(context.getResources().getColor(darkBackground ? R.color.statusBarLight : R.color.statusBarDark));
        }
        if (batteryStateView != null) {
            batteryStateView.setVisibility(visible ? View.VISIBLE : View.GONE);
            batteryStateView.setDarkBackground(darkBackground);
        }
    }
}
