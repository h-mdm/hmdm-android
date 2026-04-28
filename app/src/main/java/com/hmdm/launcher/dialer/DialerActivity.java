package com.hmdm.launcher.dialer;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.widget.EditText;
import android.telephony.PhoneNumberFormattingTextWatcher;
import android.telephony.PhoneNumberUtils;
import android.widget.TextView;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hmdm.launcher.R;
import com.hmdm.launcher.util.CallWhitelistManager;

import java.util.ArrayList;
import java.util.List;

public class DialerActivity extends AppCompatActivity
        implements ContactsAdapter.OnContactSelectedListener {

    private EditText searchField;
    private RecyclerView contactList;
    private ContactsAdapter adapter;

    private List<ContactItem> allContacts = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dialer);

        searchField = findViewById(R.id.dialer_search);
        contactList = findViewById(R.id.dialer_contact_list);

        adapter = new ContactsAdapter(this);
        contactList.setLayoutManager(new LinearLayoutManager(this));
        contactList.setAdapter(adapter);

// Auto-format as digits are typed
        searchField.addTextChangedListener(new PhoneNumberFormattingTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                super.afterTextChanged(s);
                String raw = s.toString();
                filterContacts(raw);

                // Show formatted preview line when it looks like a number
                TextView preview = findViewById(R.id.dialer_formatted_preview);
                String digitsOnly = raw.replaceAll("[^0-9]", "");
                if (digitsOnly.length() >= 3) {
                    preview.setVisibility(View.VISIBLE);
                    preview.setText("Dial: " + raw.trim());
                } else {
                    preview.setVisibility(View.GONE);
                }
            }
        });

// Backspace button — single tap deletes one char, long press clears all
        Button backspaceBtn = findViewById(R.id.dialer_backspace);
        backspaceBtn.setOnClickListener(v -> {
            String current = searchField.getText().toString();
            if (!current.isEmpty()) {
                searchField.setText(current.substring(0, current.length() - 1));
                searchField.setSelection(searchField.getText().length());
            }
        });
        backspaceBtn.setOnLongClickListener(v -> {
            searchField.setText("");
            return true;
        });
        backspaceBtn.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN &&
                    keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                String current = searchField.getText().toString();
                if (!current.isEmpty()) {
                    searchField.setText(current.substring(0, current.length() - 1));
                    searchField.setSelection(searchField.getText().length());
                }
                return true;
            }
            return false;
        });

        // Allow dpad down from search field to move into list
        searchField.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN &&
                    keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                contactList.requestFocus();
                return true;
            }
            // # key triggers direct dial of typed number
            if (event.getAction() == KeyEvent.ACTION_DOWN &&
                    keyCode == KeyEvent.KEYCODE_POUND) {
                String typed = searchField.getText().toString().trim();
                if (!typed.isEmpty()) {
                    ContactItem direct = new ContactItem(
                            "Direct Dial",
                            typed,
                            CallWhitelistManager.getInstance(this).isAllowed(typed)
                    );
                    onContactSelected(direct);
                }
                return true;
            }
            return false;
        });

        loadContacts();
    }

    private void loadContacts() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_CONTACTS}, 1);
            return;
        }

        AsyncTask.execute(() -> {
            List<ContactItem> loaded = new ArrayList<>();
            CallWhitelistManager whitelistManager = CallWhitelistManager.getInstance(this);

            Cursor cursor = getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER
                    },
                    null, null,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            );

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String name = cursor.getString(0);
                    String number = cursor.getString(1);
                    if (name == null) name = number;
                    boolean allowed = whitelistManager.isAllowed(number);
                    loaded.add(new ContactItem(name, number, allowed));
                }
                cursor.close();
            }

            allContacts = loaded;
            runOnUiThread(() -> adapter.setContacts(allContacts));
        });
    }

    private void filterContacts(String query) {
        if (query.isEmpty()) {
            adapter.setContacts(allContacts);
            return;
        }
        String lower = query.toLowerCase();
        List<ContactItem> filtered = new ArrayList<>();
        for (ContactItem c : allContacts) {
            if (c.name.toLowerCase().contains(lower) ||
                    c.number.replace(" ", "").contains(query)) {
                filtered.add(c);
            }
        }
        adapter.setContacts(filtered);
    }

    @Override
    public void onContactSelected(ContactItem contact) {
        // Always go to confirmation screen — whitelist enforced there
        Intent intent = new Intent(this, ConfirmCallActivity.class);
        intent.putExtra("name", contact.name);
        intent.putExtra("number", contact.number);
        intent.putExtra("allowed", contact.isAllowed);
        startActivity(intent);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Green call key with no contact focused = focus search
        if (keyCode == KeyEvent.KEYCODE_CALL) {
            searchField.requestFocus();
            return true;
        }
        // Back key closes dialer
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadContacts();
        }
    }
}
