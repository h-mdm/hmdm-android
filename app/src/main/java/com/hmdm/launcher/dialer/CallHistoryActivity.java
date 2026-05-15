/*
 * Pure Speech Fork — CallHistoryActivity
 *
 * Displays call history grouped by type.
 * Tabs: ALL / MISSED / INCOMING / OUTGOING
 *
 * Tapping or pressing dpad-center on any entry opens ConfirmCallActivity
 * to redial that number (whitelist enforced there as normal).
 *
 * Reads from CallLog.Calls content provider — local device storage.
 * Works fully offline.
 *
 * Requires READ_CALL_LOG permission (already declared in manifest).
 */

package com.hmdm.launcher.dialer;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hmdm.launcher.R;
import com.hmdm.launcher.util.CallWhitelistManager;

import java.util.ArrayList;
import java.util.List;

public class CallHistoryActivity extends AppCompatActivity
        implements CallHistoryAdapter.OnHistoryItemSelectedListener {

    private static final String TAG = "CallHistoryActivity";

    // Current active tab filter
    // -1 = ALL, otherwise matches CallLog.Calls type constants
    private int currentFilter = -1;

    private CallHistoryAdapter adapter;
    private List<CallHistoryItem> allItems = new ArrayList<>();

    // Tab buttons
    private Button tabAll;
    private Button tabMissed;
    private Button tabIncoming;
    private Button tabOutgoing;
    private TextView emptyView;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call_history);

        RecyclerView recyclerView = findViewById(R.id.history_list);
        emptyView  = findViewById(R.id.history_empty);
        tabAll      = findViewById(R.id.tab_all);
        tabMissed   = findViewById(R.id.tab_missed);
        tabIncoming = findViewById(R.id.tab_incoming);
        tabOutgoing = findViewById(R.id.tab_outgoing);

        adapter = new CallHistoryAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        // Tab click listeners
        tabAll.setOnClickListener(v      -> setFilter(-1));
        tabMissed.setOnClickListener(v   -> setFilter(CallHistoryItem.TYPE_MISSED));
        tabIncoming.setOnClickListener(v -> setFilter(CallHistoryItem.TYPE_INCOMING));
        tabOutgoing.setOnClickListener(v -> setFilter(CallHistoryItem.TYPE_OUTGOING));

        // Tab dpad key listeners
        setTabKeyListener(tabAll,      recyclerView);
        setTabKeyListener(tabMissed,   recyclerView);
        setTabKeyListener(tabIncoming, recyclerView);
        setTabKeyListener(tabOutgoing, recyclerView);

        // Start on ALL tab
        setFilter(-1);
        tabAll.requestFocus();

        loadCallHistory();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh on return from a call
        loadCallHistory();
    }

    // -------------------------------------------------------------------------
    // Tab management
    // -------------------------------------------------------------------------

    private void setFilter(int filter) {
        currentFilter = filter;
        updateTabStyles();
        applyFilter();
    }

    private void updateTabStyles() {
        int active   = android.graphics.Color.parseColor("#1565C0");
        int inactive = android.graphics.Color.parseColor("#1A1A1A");

        tabAll.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        currentFilter == -1 ? active : inactive));
        tabMissed.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        currentFilter == CallHistoryItem.TYPE_MISSED ? active : inactive));
        tabIncoming.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        currentFilter == CallHistoryItem.TYPE_INCOMING ? active : inactive));
        tabOutgoing.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        currentFilter == CallHistoryItem.TYPE_OUTGOING ? active : inactive));
    }

    private void applyFilter() {
        if (allItems.isEmpty()) {
            adapter.setItems(new ArrayList<>());
            emptyView.setVisibility(android.view.View.VISIBLE);
            return;
        }

        List<CallHistoryItem> filtered = new ArrayList<>();
        for (CallHistoryItem item : allItems) {
            if (currentFilter == -1 || item.type == currentFilter) {
                filtered.add(item);
            }
        }

        adapter.setItems(filtered);

        if (filtered.isEmpty()) {
            emptyView.setVisibility(android.view.View.VISIBLE);
            emptyView.setText(getEmptyMessage());
        } else {
            emptyView.setVisibility(android.view.View.GONE);
        }
    }

    private String getEmptyMessage() {
        switch (currentFilter) {
            case CallHistoryItem.TYPE_MISSED:   return "No missed calls";
            case CallHistoryItem.TYPE_INCOMING: return "No incoming calls";
            case CallHistoryItem.TYPE_OUTGOING: return "No outgoing calls";
            default:                            return "No call history";
        }
    }

    private String lookupContactName(String number) {
        Uri lookupUri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number));
        Cursor c = getContentResolver().query(lookupUri,
                new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME},
                null, null, null);
        try {
            if (c != null && c.moveToFirst()) {
                return c.getString(0);
            }
        } finally {
            if (c != null) c.close();
        }
        return number; // fall back to number if no contact found
    }
    // -------------------------------------------------------------------------
    // Load call log from device
    // -------------------------------------------------------------------------

    private void loadCallHistory() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_CALL_LOG}, 2);
            return;
        }

        AsyncTask.execute(() -> {
            List<CallHistoryItem> loaded = new ArrayList<>();

            Cursor cursor = getContentResolver().query(
                    CallLog.Calls.CONTENT_URI,
                    new String[]{
                            CallLog.Calls.CACHED_NAME,
                            CallLog.Calls.NUMBER,
                            CallLog.Calls.TYPE,
                            CallLog.Calls.DATE,
                            CallLog.Calls.DURATION
                    },
                    null,
                    null,
                    CallLog.Calls.DATE + " DESC" // most recent first
            );

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String name     = cursor.getString(0);
                    String number   = cursor.getString(1);
                    // If cached name is missing, do a live contacts lookup
                    if (name == null || name.isEmpty()) {
                        name = lookupContactName(number);
                    }
                    int    type     = cursor.getInt(2);
                    long   date     = cursor.getLong(3);
                    long   duration = cursor.getLong(4);

                    // Only show known types
                    if (type == CallLog.Calls.INCOMING_TYPE ||
                            type == CallLog.Calls.OUTGOING_TYPE ||
                            type == CallLog.Calls.MISSED_TYPE) {

                        // Map CallLog type to our type constants
                        int mappedType;
                        switch (type) {
                            case CallLog.Calls.INCOMING_TYPE:
                                mappedType = CallHistoryItem.TYPE_INCOMING; break;
                            case CallLog.Calls.OUTGOING_TYPE:
                                mappedType = CallHistoryItem.TYPE_OUTGOING; break;
                            default:
                                mappedType = CallHistoryItem.TYPE_MISSED;   break;
                        }

                        loaded.add(new CallHistoryItem(name, number, mappedType, date, duration));
                    }
                }
                cursor.close();
            }

            allItems = loaded;
            runOnUiThread(this::applyFilter);
        });
    }

    // -------------------------------------------------------------------------
    // Item selected — redial via ConfirmCallActivity
    // -------------------------------------------------------------------------

    @Override
    public void onHistoryItemSelected(CallHistoryItem item) {
        // Whitelist is enforced in ConfirmCallActivity as normal
        boolean allowed = CallWhitelistManager.getInstance(this).isAllowed(item.number);

        Intent intent = new Intent(this, ConfirmCallActivity.class);
        intent.putExtra("name",    item.name);
        intent.putExtra("number",  item.number);
        intent.putExtra("allowed", allowed);
        startActivity(intent);
    }

    // -------------------------------------------------------------------------
    // Hardware back key — return to dialer
    // -------------------------------------------------------------------------

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // -------------------------------------------------------------------------
    // Permission result
    // -------------------------------------------------------------------------

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 2 && grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadCallHistory();
        }
    }

    // -------------------------------------------------------------------------
    // Helper — attach dpad handler to tab buttons so down arrow
    // moves focus into the list rather than cycling tabs
    // -------------------------------------------------------------------------

    private void setTabKeyListener(Button tab, RecyclerView list) {
        tab.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                        keyCode == KeyEvent.KEYCODE_ENTER) {
                    // Activate the tab
                    v.performClick();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    // Move focus into the list
                    list.requestFocus();
                    return true;
                }
            }
            return false;
        });
    }
}