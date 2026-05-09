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


        void bind(ContactItem contact, OnContactSelectedListener listener) {
            nameView.setText(contact.name);
            // Format the number for display
            numberView.setText(contact.number);

            TextView statusLabel = itemView.findViewById(R.id.contact_status_label);

            boolean isAllowed = CallWhitelistManager
                    .getInstance(itemView.getContext())
                    .isAllowed(contact.number);

            if (isAllowed) {                statusDot.setBackgroundResource(R.drawable.status_dot);
                nameView.setTextColor(Color.WHITE);
                numberView.setTextColor(Color.parseColor("#888888"));
                statusLabel.setText("ALLOWED");
                statusLabel.setTextColor(Color.parseColor("#4CAF50"));
                statusLabel.setBackground(null);
            } else {
                statusDot.setBackgroundColor(Color.parseColor("#333333"));
                nameView.setTextColor(Color.parseColor("#666666"));
                numberView.setTextColor(Color.parseColor("#444444"));
                statusLabel.setText("BLOCKED");
                statusLabel.setTextColor(Color.parseColor("#F44336"));
                statusLabel.setBackground(null);
            }

            itemView.setOnClickListener(v -> listener.onContactSelected(contact));
            itemView.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN &&
                        (keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                keyCode == KeyEvent.KEYCODE_ENTER ||
                                keyCode == KeyEvent.KEYCODE_CALL)) {
                    listener.onContactSelected(contact);
                    return true;
                }
                return false;
            });
        }
    }
}