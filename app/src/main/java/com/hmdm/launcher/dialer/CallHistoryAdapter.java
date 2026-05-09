/*
 * Pure Speech Fork — CallHistoryAdapter
 * RecyclerView adapter for the call history list.
 * Displays name, number, call type, date, and duration.
 * Dpad-compatible with click-to-redial support.
 */

package com.hmdm.launcher.dialer;

import android.graphics.Color;
import android.text.format.DateUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hmdm.launcher.R;

import java.util.ArrayList;
import java.util.List;

public class CallHistoryAdapter extends RecyclerView.Adapter<CallHistoryAdapter.ViewHolder> {

    public interface OnHistoryItemSelectedListener {
        void onHistoryItemSelected(CallHistoryItem item);
    }

    private List<CallHistoryItem> items = new ArrayList<>();
    private final OnHistoryItemSelectedListener listener;

    public CallHistoryAdapter(OnHistoryItemSelectedListener listener) {
        this.listener = listener;
    }

    public void setItems(List<CallHistoryItem> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_call_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // -------------------------------------------------------------------------
    // ViewHolder
    // -------------------------------------------------------------------------

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView nameView;
        private final TextView numberView;
        private final TextView typeView;
        private final TextView dateView;
        private final TextView durationView;

        ViewHolder(View itemView) {
            super(itemView);
            nameView     = itemView.findViewById(R.id.history_name);
            numberView   = itemView.findViewById(R.id.history_number);
            typeView     = itemView.findViewById(R.id.history_type);
            dateView     = itemView.findViewById(R.id.history_date);
            durationView = itemView.findViewById(R.id.history_duration);
        }

        void bind(CallHistoryItem item, OnHistoryItemSelectedListener listener) {
            nameView.setText(item.name);
            numberView.setText(item.number);
            durationView.setText(item.getFormattedDuration());

            // Relative date/time e.g. "2 hours ago", "Yesterday"
            CharSequence relativeDate = DateUtils.getRelativeTimeSpanString(
                    item.date,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE
            );
            dateView.setText(relativeDate);

            // Type label and colour
            typeView.setText(item.getTypeLabel());
            switch (item.type) {
                case CallHistoryItem.TYPE_INCOMING:
                    typeView.setTextColor(Color.parseColor("#4CAF50")); // green
                    nameView.setTextColor(Color.WHITE);
                    break;
                case CallHistoryItem.TYPE_OUTGOING:
                    typeView.setTextColor(Color.parseColor("#2196F3")); // blue
                    nameView.setTextColor(Color.WHITE);
                    break;
                case CallHistoryItem.TYPE_MISSED:
                    typeView.setTextColor(Color.parseColor("#F44336")); // red
                    nameView.setTextColor(Color.parseColor("#F44336")); // red name = missed
                    break;
                default:
                    typeView.setTextColor(Color.GRAY);
                    nameView.setTextColor(Color.WHITE);
                    break;
            }

            // Click or dpad center — redial
            itemView.setOnClickListener(v -> listener.onHistoryItemSelected(item));
            itemView.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN &&
                        (keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                keyCode == KeyEvent.KEYCODE_ENTER ||
                                keyCode == KeyEvent.KEYCODE_CALL)) {
                    listener.onHistoryItemSelected(item);
                    return true;
                }
                return false;
            });
        }
    }
}