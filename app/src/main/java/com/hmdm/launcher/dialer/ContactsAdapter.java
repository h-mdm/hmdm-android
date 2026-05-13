package com.hmdm.launcher.dialer;

import android.graphics.Color;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import com.hmdm.launcher.util.CallWhitelistManager;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hmdm.launcher.R;

import java.util.ArrayList;
import java.util.List;

public class ContactsAdapter extends RecyclerView.Adapter<ContactsAdapter.ViewHolder> {

    public interface OnContactSelectedListener {
        void onContactSelected(ContactItem contact);
    }

    private List<ContactItem> contacts = new ArrayList<>();
    private OnContactSelectedListener listener;

    public ContactsAdapter(OnContactSelectedListener listener) {
        this.listener = listener;
    }

    public void setContacts(List<ContactItem> contacts) {
        this.contacts = contacts;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_contact, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ContactItem contact = contacts.get(position);
        holder.bind(contact, listener);
    }

    @Override
    public int getItemCount() {
        return contacts.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView nameView;
        private final TextView numberView;
        private final View statusDot;
        // lockIcon removed — replaced by statusLabel in layout

        ViewHolder(View itemView) {
            super(itemView);
            nameView = itemView.findViewById(R.id.contact_name);
            numberView = itemView.findViewById(R.id.contact_number);
            statusDot = itemView.findViewById(R.id.contact_status_dot);
        }


        /*
         * ContactsAdapter — light theme color updates only.
         *
         * Replace the body of the bind() method in your existing ViewHolder
         * with this version. Everything else in ContactsAdapter.java stays the same.
         */

        void bind(ContactItem contact, OnContactSelectedListener listener) {
            nameView.setText(contact.name);
            numberView.setText(contact.number);

            // Live whitelist check — offline safe
            boolean isAllowed = CallWhitelistManager
                    .getInstance(itemView.getContext())
                    .isAllowed(contact.number);

            TextView statusLabel = itemView.findViewById(R.id.contact_status_label);

            if (isAllowed) {
                statusDot.setBackgroundResource(R.drawable.status_dot);
                nameView.setTextColor(android.graphics.Color.parseColor("#1C1C1E"));
                numberView.setTextColor(android.graphics.Color.parseColor("#8E8E93"));
                statusLabel.setText("ALLOWED");
                statusLabel.setTextColor(android.graphics.Color.parseColor("#34C759"));
            } else {
                statusDot.setBackgroundColor(android.graphics.Color.parseColor("#C7C7CC"));
                nameView.setTextColor(android.graphics.Color.parseColor("#8E8E93"));
                numberView.setTextColor(android.graphics.Color.parseColor("#C7C7CC"));
                statusLabel.setText("BLOCKED");
                statusLabel.setTextColor(android.graphics.Color.parseColor("#FF3B30"));
            }

            // When row is focused via dpad, flip text to white so it's readable on blue
            itemView.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    nameView.setTextColor(android.graphics.Color.WHITE);
                    numberView.setTextColor(android.graphics.Color.parseColor("#E5E5EA"));
                    statusLabel.setTextColor(android.graphics.Color.WHITE);
                } else {
                    // Restore original colors
                    if (isAllowed) {
                        nameView.setTextColor(android.graphics.Color.parseColor("#1C1C1E"));
                        numberView.setTextColor(android.graphics.Color.parseColor("#8E8E93"));
                        statusLabel.setTextColor(android.graphics.Color.parseColor("#34C759"));
                    } else {
                        nameView.setTextColor(android.graphics.Color.parseColor("#8E8E93"));
                        numberView.setTextColor(android.graphics.Color.parseColor("#C7C7CC"));
                        statusLabel.setTextColor(android.graphics.Color.parseColor("#FF3B30"));
                    }
                }
            });

            itemView.setOnClickListener(v -> listener.onContactSelected(contact));
            itemView.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == android.view.KeyEvent.ACTION_DOWN &&
                        (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                                keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                                keyCode == android.view.KeyEvent.KEYCODE_CALL)) {
                    listener.onContactSelected(contact);
                    return true;
                }
                return false;
            });
        }
    }
}