/*
 * Pure Speech Fork — DialerActivity
 *
 * Contact list + direct dial screen.
 *
 * How to dial:
 *   - Select a contact from the list → ConfirmCallActivity
 *   - Type a number → tap green CALL button → ConfirmCallActivity
 *   - Type a number → press # key → ConfirmCallActivity
 *   - Type a number → press green physical call key → ConfirmCallActivity
 *   - Type a number → press dpad OK on search field → ConfirmCallActivity
 *
 * Whitelist is always enforced in ConfirmCallActivity before the call is placed.
 */

package com.hmdm.launcher.dialer;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberFormattingTextWatcher;
import android.text.Editable;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

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
    private Button dialButton;
    private List<ContactItem> allContacts = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dialer);

        searchField = findViewById(R.id.dialer_search);
        contactList  = findViewById(R.id.dialer_contact_list);
        dialButton   = findViewById(R.id.dialer_dial_button);

        adapter = new ContactsAdapter(this);
        contactList.setLayoutManager(new LinearLayoutManager(this));
        contactList.setAdapter(adapter);

        // -------------------------------------------------------------------------
        // Auto-format + live contact filter + dial button visibility
        // -------------------------------------------------------------------------
        searchField.addTextChangedListener(new PhoneNumberFormattingTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                super.afterTextChanged(s);
                String raw = s.toString();
                filterContacts(raw);

                // Show formatted preview when it looks like a number
                TextView preview = findViewById(R.id.dialer_formatted_preview);
                String digitsOnly = raw.replaceAll("[^0-9]", "");
                if (digitsOnly.length() >= 3) {
                    preview.setVisibility(View.VISIBLE);
                    preview.setText("Dial: " + raw.trim());
                    // Show the green CALL button
                    dialButton.setVisibility(View.VISIBLE);
                } else {
                    preview.setVisibility(View.GONE);
                    // Hide the CALL button — not enough digits yet
                    dialButton.setVisibility(View.GONE);
                }
            }
        });

        // -------------------------------------------------------------------------
        // Green CALL button — dials whatever is currently in the search field
        // -------------------------------------------------------------------------
        dialButton.setOnClickListener(v -> dialTypedNumber());
        dialButton.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN &&
                    (keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                            keyCode == KeyEvent.KEYCODE_ENTER ||
                            keyCode == KeyEvent.KEYCODE_CALL)) {
                dialTypedNumber();
                return true;
            }
            return false;
        });

        // -------------------------------------------------------------------------
        // DEL button — single tap deletes one char, long press clears all
        // -------------------------------------------------------------------------
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

        // -------------------------------------------------------------------------
        // Search field key handling
        // -------------------------------------------------------------------------
        searchField.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;

            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    // Move focus into contact list
                    contactList.requestFocus();
                    return true;

                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    // If the field has digits, treat OK as dial
                    if (hasDialableContent()) {
                        dialTypedNumber();
                        return true;
                    }
                    return false;

                case KeyEvent.KEYCODE_POUND:
                case KeyEvent.KEYCODE_CALL:
                    // # key or green key with typed number = dial it
                    if (hasDialableContent()) {
                        dialTypedNumber();
                        return true;
                    }
                    return false;

                default:
                    return false;
            }
        });

        // -------------------------------------------------------------------------
        // RECENT link
        // -------------------------------------------------------------------------
        TextView historyLink = findViewById(R.id.dialer_history_link);
        if (historyLink != null) {
            historyLink.setOnClickListener(v ->
                    startActivity(new Intent(this, CallHistoryActivity.class)));
            historyLink.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN &&
                        (keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                keyCode == KeyEvent.KEYCODE_ENTER)) {
                    startActivity(new Intent(this, CallHistoryActivity.class));
                    return true;
                }
                return false;
            });
        }

        // Pre-fill digit if launched from home screen keypress
        String prefill = getIntent().getStringExtra("prefill_digit");
        if (prefill != null && !prefill.isEmpty()) {
            searchField.setText(prefill);
            searchField.setSelection(prefill.length());
        }

        loadContacts();
    }

    // -------------------------------------------------------------------------
    // Dial the number currently typed in the search field.
    // Whitelist enforcement happens in ConfirmCallActivity.
    // -------------------------------------------------------------------------
    private void dialTypedNumber() {
        String typed = searchField.getText().toString().trim();
        if (typed.isEmpty()) return;

        // Strip formatting for the actual number to pass through
        String digitsOnly = typed.replaceAll("[^0-9+]", "");
        if (digitsOnly.isEmpty()) return;

        boolean allowed = CallWhitelistManager.getInstance(this).isAllowed(digitsOnly);

        Intent intent = new Intent(this, ConfirmCallActivity.class);
        intent.putExtra("name",    typed);   // show formatted version as label
        intent.putExtra("number",  digitsOnly);
        intent.putExtra("allowed", allowed);
        startActivity(intent);
    }

    private boolean hasDialableContent() {
        String raw = searchField.getText().toString();
        return raw.replaceAll("[^0-9]", "").length() >= 3;
    }

    // -------------------------------------------------------------------------
    // Load contacts from device
    // -------------------------------------------------------------------------
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
                    String name   = cursor.getString(0);
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
        String lower  = query.toLowerCase();
        String digits = query.replaceAll("[^0-9]", "");
        List<ContactItem> filtered = new ArrayList<>();
        for (ContactItem c : allContacts) {
            boolean nameMatch   = c.name.toLowerCase().contains(lower);
            boolean numberMatch = !digits.isEmpty() &&
                    c.number.replaceAll("[^0-9]", "").contains(digits);
            if (nameMatch || numberMatch) {
                filtered.add(c);
            }
        }
        adapter.setContacts(filtered);
    }

    // -------------------------------------------------------------------------
    // Contact selected from list
    // -------------------------------------------------------------------------
    @Override
    public void onContactSelected(ContactItem contact) {
        Intent intent = new Intent(this, ConfirmCallActivity.class);
        intent.putExtra("name",    contact.name);
        intent.putExtra("number",  contact.number);
        intent.putExtra("allowed", contact.isAllowed);
        startActivity(intent);
    }

    // -------------------------------------------------------------------------
    // Hardware key handling
    // -------------------------------------------------------------------------
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CALL:
                // Green key: if there are typed digits, dial them
                // If not, focus the search field
                if (hasDialableContent()) {
                    dialTypedNumber();
                } else {
                    searchField.requestFocus();
                }
                return true;

            case KeyEvent.KEYCODE_BACK:
                finish();
                return true;

            default:
                return super.onKeyDown(keyCode, event);
        }
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