package com.hmdm.launcher.ui;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.hmdm.launcher.R;
import com.hmdm.launcher.util.LegacyUtils;

/**
 * There is a weird bug: MainActivity cannot open intent ACTION_ADD_DEVICE_ADMIN
 * because it is declared as "singleInstance" (???).
 * To work around this issue, I created a simple proxy activity which does the same
 */
public class AdminModeRequestActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ComponentName adminComponentName = LegacyUtils.getAdminComponentName(this);
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponentName);
        try {
            startActivityForResult(intent, 1);
        } catch (/*ActivityNotFound*/Exception e) {
            Toast.makeText(this, getString(R.string.admin_not_supported), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        setResult(resultCode);
        finish();
    }
}
