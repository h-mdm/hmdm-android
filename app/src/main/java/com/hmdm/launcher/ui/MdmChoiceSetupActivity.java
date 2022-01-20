package com.hmdm.launcher.ui;

import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

import com.hmdm.launcher.AdminReceiver;
import com.hmdm.launcher.BuildConfig;
import com.hmdm.launcher.Const;
import com.hmdm.launcher.R;
import com.hmdm.launcher.databinding.ActivityMdmChoiceBinding;
import com.hmdm.launcher.helper.SettingsHelper;

import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_MODE;
import static android.app.admin.DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE;

public class MdmChoiceSetupActivity extends AppCompatActivity {
    private ActivityMdmChoiceBinding binding;

    @Override
    protected void onCreate( Bundle savedInstanceState ) {
        super.onCreate(savedInstanceState);

        Log.d(Const.LOG_TAG, "Launching the provisioning mode choice activity");

        binding = DataBindingUtil.setContentView(this, R.layout.activity_mdm_choice);

        Intent intent = getIntent();
        PersistableBundle bundle = intent.getParcelableExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AdminReceiver.updateSettings(this, bundle);
        }

        SettingsHelper settingsHelper = SettingsHelper.getInstance(this);
        if (settingsHelper.getDeviceId() == null || settingsHelper.getDeviceId().length() == 0) {
            Log.d(Const.LOG_TAG, "Device ID is empty");
            String deviceIdUse = settingsHelper.getDeviceIdUse();
            String deviceId = null;
            Log.d(Const.LOG_TAG, "Device ID choice: " + deviceIdUse);
            if (BuildConfig.DEVICE_ID_CHOICE.equals("imei") || "imei".equals(deviceIdUse)) {
                deviceId = intent.getStringExtra(DevicePolicyManager.EXTRA_PROVISIONING_IMEI);
            } else if (BuildConfig.DEVICE_ID_CHOICE.equals("serial") || "serial".equals(deviceIdUse)) {
                deviceId = intent.getStringExtra(DevicePolicyManager.EXTRA_PROVISIONING_SERIAL_NUMBER);
            }
            settingsHelper.setDeviceId(deviceId);
        }
    }

    @Override
    public void onBackPressed() {
        setResult(RESULT_CANCELED);
        super.onBackPressed();
    }

    public void continueSetup(View view) {
        final Intent intent = new Intent();
        intent.putExtra(EXTRA_PROVISIONING_MODE, PROVISIONING_MODE_FULLY_MANAGED_DEVICE);
        setResult(RESULT_OK, intent);
        finish();
    }

}
