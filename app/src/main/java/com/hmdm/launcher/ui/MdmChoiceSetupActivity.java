package com.hmdm.launcher.ui;

import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_MODE;

import android.app.Dialog;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

import com.hmdm.launcher.AdminReceiver;
import com.hmdm.launcher.BuildConfig;
import com.hmdm.launcher.Const;
import com.hmdm.launcher.R;
import com.hmdm.launcher.databinding.ActivityMdmChoiceBinding;
import com.hmdm.launcher.databinding.DialogEnterDeviceIdBinding;
import com.hmdm.launcher.helper.SettingsHelper;

import java.util.ArrayList;
import java.util.List;

public class MdmChoiceSetupActivity extends AppCompatActivity {
    private ActivityMdmChoiceBinding binding;

    protected Dialog enterDeviceIdDialog;
    protected DialogEnterDeviceIdBinding enterDeviceIdDialogBinding;

    @Override
    protected void onCreate( Bundle savedInstanceState ) {
        super.onCreate(savedInstanceState);

        Log.d(Const.LOG_TAG, "Launching the provisioning mode choice activity");

        binding = DataBindingUtil.setContentView(this, R.layout.activity_mdm_choice);

        Intent intent = getIntent();
        PersistableBundle bundle = intent.getParcelableExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (bundle != null && bundle.getString(Const.QR_OPEN_WIFI_ATTR) != null) {
                try {
                    startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
            AdminReceiver.updateSettings(this, bundle);
        }

        SettingsHelper settingsHelper = SettingsHelper.getInstance(this);
        if (settingsHelper.getDeviceId() == null || settingsHelper.getDeviceId().length() == 0) {
            Log.d(Const.LOG_TAG, "Device ID is empty");
            String deviceIdUse = settingsHelper.getDeviceIdUse();
            String deviceId;
            Log.d(Const.LOG_TAG, "Device ID choice: " + deviceIdUse);
            if (BuildConfig.DEVICE_ID_CHOICE.equals("imei") || "imei".equals(deviceIdUse)) {
                // These extras could not be set so we should retry setting these values in InitialSetupActivity!
                deviceId = intent.getStringExtra(DevicePolicyManager.EXTRA_PROVISIONING_IMEI);
            } else if (BuildConfig.DEVICE_ID_CHOICE.equals("serial") || "serial".equals(deviceIdUse)) {
                deviceId = intent.getStringExtra(DevicePolicyManager.EXTRA_PROVISIONING_SERIAL_NUMBER);
            } else {
                displayEnterDeviceIdDialog(intent.getStringExtra(DevicePolicyManager.EXTRA_PROVISIONING_IMEI),
                        intent.getStringExtra(DevicePolicyManager.EXTRA_PROVISIONING_SERIAL_NUMBER));
                return;
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
        intent.putExtra(EXTRA_PROVISIONING_MODE, DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE);
        setResult(RESULT_OK, intent);
        finish();
    }

    protected void displayEnterDeviceIdDialog(String imei, String serial) {
        enterDeviceIdDialog = new Dialog(this);
        enterDeviceIdDialogBinding = DataBindingUtil.inflate(
                LayoutInflater.from( this ),
                R.layout.dialog_enter_device_id,
                null,
                false);
        SettingsHelper settingsHelper = SettingsHelper.getInstance(this);
        String serverUrl = settingsHelper.getBaseUrl();
        String serverPath = settingsHelper.getServerProject();
        if (serverPath.length() > 0) {
            serverUrl += "/" + serverPath;
        }
        enterDeviceIdDialogBinding.deviceIdPrompt.setText(getString(R.string.dialog_enter_device_id_title, serverUrl));
        enterDeviceIdDialogBinding.deviceIdError.setText(getString(R.string.dialog_enter_device_id_error, serverUrl));
        enterDeviceIdDialogBinding.setError(false);
        enterDeviceIdDialog.setCancelable(false);
        enterDeviceIdDialog.requestWindowFeature( Window.FEATURE_NO_TITLE );

        // Suggest variants to choose the device ID: IMEI or serial
        List<String> variantsList = new ArrayList<>();
        if (imei != null) {
            variantsList.add(imei);
        }
        if (serial != null && !serial.equals(Build.UNKNOWN)) {
            variantsList.add(serial);
        }
        if (variantsList.size() > 0) {
            String[] variantsArray = variantsList.toArray(new String[variantsList.size()]);
            enterDeviceIdDialogBinding.deviceId.setThreshold(0);
            enterDeviceIdDialogBinding.deviceId.setAdapter(new ArrayAdapter<String>(this,
                    android.R.layout.select_dialog_item, variantsArray));
        } else {
            enterDeviceIdDialogBinding.showDeviceIdVariants.setVisibility(View.GONE);
        }

        enterDeviceIdDialogBinding.showDeviceIdQrCode.setVisibility(View.GONE);

        enterDeviceIdDialog.setContentView( enterDeviceIdDialogBinding.getRoot() );
        enterDeviceIdDialog.show();
    }

    public void saveDeviceId( View view ) {
        String deviceId = enterDeviceIdDialogBinding.deviceId.getText().toString().trim();
        if ("".equals(deviceId)) {
            return;
        } else {
            SettingsHelper settingsHelper = SettingsHelper.getInstance(this);
            settingsHelper.setDeviceId( deviceId );
            if (enterDeviceIdDialog != null) {
                enterDeviceIdDialog.dismiss();
            }
        }
    }
}
