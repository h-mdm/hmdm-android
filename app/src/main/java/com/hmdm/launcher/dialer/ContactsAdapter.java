package com.hmdm.launcher.dialer;

import android.graphics.Color;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

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
        private final ImageView lockIcon;

        ViewHolder(View itemView) {
            super(itemView);
            nameView = itemView.findViewById(R.id.contact_name);
            numberView = itemView.findViewById(R.id.contact_number);
            statusDot = itemView.findViewById(R.id.contact_status_dot);
            lockIcon = itemView.findViewById(R.id.contact_lock_icon);
        }

        void bind(ContactItem contact, OnContactSelectedListener listener) {
            nameView.setText(contact.name);
            numberView.setText(contact.number);

            if (contact.isAllowed) {
                // Green dot, full brightness
                statusDot.setBackgroundResource(R.drawable.status_dot);
                nameView.setTextColor(Color.WHITE);
                numberView.setTextColor(Color.parseColor("#AAAAAA"));
                lockIcon.setVisibility(View.GONE);
            } else {
                // Grey dot, dimmed text, lock icon
                statusDot.setBackgroundColor(Color.parseColor("#555555"));
                nameView.setTextColor(Color.parseColor("#777777"));
                numberView.setTextColor(Color.parseColor("#555555"));
                lockIcon.setVisibility(View.VISIBLE);
            }

            // Handle dpad center and touch
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