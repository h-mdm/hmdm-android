package com.hmdm.launcher.ui.custom;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.hmdm.launcher.R;

public class BatteryStateView extends LinearLayout {
    private Context context;
    private TextView percentView;
    private ImageView pluggedView;
    private ImageView batteryView;
    private boolean darkBackground = true;
    private int chargePercent = 100;
    private boolean plugged = false;

    private int[] lightImages = {
            R.drawable.ic_battery_1_red,
            R.drawable.ic_battery_2_white,
            R.drawable.ic_battery_3_white,
            R.drawable.ic_battery_4_white,
            R.drawable.ic_battery_5_white,
            R.drawable.ic_battery_6_white
    };

    private int[] darkImages = {
            R.drawable.ic_battery_1_red,
            R.drawable.ic_battery_2_black,
            R.drawable.ic_battery_3_black,
            R.drawable.ic_battery_4_black,
            R.drawable.ic_battery_5_black,
            R.drawable.ic_battery_6_black
    };

    public BatteryStateView(Context context) {
        super(context);
        init(context);
    }

    public BatteryStateView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public BatteryStateView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        this.context = context;

        setOrientation(HORIZONTAL);
        setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);

        percentView = new TextView(context);
        pluggedView = new ImageView(context);
        batteryView = new ImageView(context);

        LayoutParams textParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        LayoutParams imageParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

        percentView.setLayoutParams(textParams);
        pluggedView.setLayoutParams(imageParams);
        batteryView.setLayoutParams(imageParams);

        addView(percentView);
        addView(pluggedView);
        addView(batteryView);

        pluggedView.setVisibility(View.GONE);
    }

    public void setDarkBackground(boolean darkBackground) {
        if (this.darkBackground != darkBackground) {
            this.darkBackground = darkBackground;
            updateControls();
        }
    }

    public void setChargePercent(int chargePercent) {
        if (this.chargePercent != chargePercent) {
            this.chargePercent = chargePercent;
            updateControls();
        }
    }

    public void setPlugged(boolean plugged) {
        if (this.plugged != plugged) {
            this.plugged = plugged;
            updateControls();
        }
    }

    private void updateControls() {
        percentView.setTextColor(context.getResources().getColor(darkBackground ? R.color.statusBarLight : R.color.statusBarDark));
        percentView.setText(chargePercent + "%");
        int imageIndex = chargePercent / 18;
        batteryView.setImageResource(darkBackground ? lightImages[imageIndex] : darkImages[imageIndex]);
        pluggedView.setImageResource(darkBackground ? R.drawable.ic_charger_white : R.drawable.ic_charger_black);
        if (plugged) {
            percentView.setVisibility(View.GONE);
            pluggedView.setVisibility(View.VISIBLE);
        } else {
            percentView.setVisibility(View.VISIBLE);
            pluggedView.setVisibility(View.GONE);
        }
    }
}
