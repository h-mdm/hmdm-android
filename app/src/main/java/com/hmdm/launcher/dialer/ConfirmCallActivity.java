package com.hmdm.launcher.dialer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.TelecomManager;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.hmdm.launcher.R;

public class ConfirmCallActivity extends AppCompatActivity {

    private Button callButton;
    private Button cancelButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_confirm_call);

        String name = getIntent().getStringExtra("name");
        String number = getIntent().getStringExtra("number");
        boolean allowed = getIntent().getBooleanExtra("allowed", false);

        TextView nameView = findViewById(R.id.confirm_name);
        TextView numberView = findViewById(R.id.confirm_number);
        TextView blockedMessage = findViewById(R.id.confirm_blocked_message);
        callButton = findViewById(R.id.confirm_call_button);
        cancelButton = findViewById(R.id.confirm_cancel_button);

        nameView.setText(name);
        numberView.setText(number);

        if (!allowed) {
            // Show blocked state — disable call button entirely
            callButton.setVisibility(View.GONE);
            blockedMessage.setVisibility(View.VISIBLE);
            // Move initial focus to cancel since call is gone
            cancelButton.requestFocus();
        } else {
            callButton.requestFocus();
        }

        callButton.setOnClickListener(v -> placeCall(number));
        cancelButton.setOnClickListener(v -> finish());

        // Dpad key handling
        callButton.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN &&
                    (keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                            keyCode == KeyEvent.KEYCODE_ENTER)) {
                placeCall(number);
                return true;
            }
            return false;
        });

        cancelButton.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN &&
                    (keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                            keyCode == KeyEvent.KEYCODE_ENTER)) {
                finish();
                return true;
            }
            return false;
        });
    }

    private void placeCall(String number) {
        Uri uri = Uri.fromParts("tel", number, null);
        Intent callIntent = new Intent(Intent.ACTION_CALL, uri);
        callIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(callIntent);
        finish();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Green call key = confirm call if allowed
        if (keyCode == KeyEvent.KEYCODE_CALL && callButton.getVisibility() == View.VISIBLE) {
            placeCall(getIntent().getStringExtra("number"));
            return true;
        }
        // Red end key = cancel
        if (keyCode == KeyEvent.KEYCODE_ENDCALL) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}