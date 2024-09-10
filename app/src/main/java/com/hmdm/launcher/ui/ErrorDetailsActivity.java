package com.hmdm.launcher.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

import com.hmdm.launcher.Const;
import com.hmdm.launcher.R;
import com.hmdm.launcher.databinding.ActivityErrorDetailsBinding;
import com.hmdm.launcher.util.RemoteLogger;
import com.hmdm.launcher.util.Utils;

public class ErrorDetailsActivity extends AppCompatActivity {
    private ActivityErrorDetailsBinding binding;

    public static final String MESSAGE = "MESSAGE";
    public static final String RESET_ENABLED = "RESET_ENABLED";

    public static void display(Activity parent, String message, boolean resetEnabled) {
        Intent intent = new Intent(parent, ErrorDetailsActivity.class);
        intent.putExtra(MESSAGE, message);
        intent.putExtra(RESET_ENABLED, resetEnabled);
        parent.startActivity(intent);
    }

    @Override
    protected void onCreate( Bundle savedInstanceState ) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_error_details);

        Intent intent = getIntent();
        boolean resetEnabled = intent.getBooleanExtra(RESET_ENABLED, false);
        binding.resetButton.setVisibility(resetEnabled ? View.VISIBLE : View.GONE);

        String message = intent.getStringExtra(MESSAGE);
        binding.editMessage.setText(message);
    }

    public void resetClicked(View view) {
        // Factory reset!!!
        if (!Utils.factoryReset(this)) {
            RemoteLogger.log(this, Const.LOG_WARN, "Device reset failed");
        }
        finish();
    }

    public void closeClicked(View view) {
        finish();
    }
}
