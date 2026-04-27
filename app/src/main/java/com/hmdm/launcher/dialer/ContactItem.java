package com.hmdm.launcher.dialer;

public class ContactItem {
    public final String name;
    public final String number;
    public final boolean isAllowed;

    public ContactItem(String name, String number, boolean isAllowed) {
        this.name = name;
        this.number = number;
        this.isAllowed = isAllowed;
    }
}
