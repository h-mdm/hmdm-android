/*
 * Pure Speech Fork — CallHistoryItem
 * Data model for a single entry in the call log.
 * Populated from CallLog.Calls content provider.
 */

package com.hmdm.launcher.dialer;

public class CallHistoryItem {

    // Call types matching CallLog.Calls constants
    public static final int TYPE_INCOMING = 1;
    public static final int TYPE_OUTGOING = 2;
    public static final int TYPE_MISSED   = 3;

    public final String name;       // Contact name or number if no contact
    public final String number;     // Raw phone number
    public final int    type;       // TYPE_INCOMING / TYPE_OUTGOING / TYPE_MISSED
    public final long   date;       // Call date in milliseconds since epoch
    public final long   duration;   // Call duration in seconds

    public CallHistoryItem(String name, String number, int type, long date, long duration) {
        this.name     = (name != null && !name.isEmpty()) ? name : number;
        this.number   = number != null ? number : "";
        this.type     = type;
        this.date     = date;
        this.duration = duration;
    }

    /**
     * Returns a human-readable duration string.
     * e.g. "2m 34s", "45s", "Missed"
     */
    public String getFormattedDuration() {
        if (type == TYPE_MISSED) return "Missed";
        if (duration <= 0) return "0s";

        long minutes = duration / 60;
        long seconds = duration % 60;

        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        } else {
            return seconds + "s";
        }
    }

    /**
     * Returns a label string for the call type.
     */
    public String getTypeLabel() {
        switch (type) {
            case TYPE_INCOMING: return "Incoming";
            case TYPE_OUTGOING: return "Outgoing";
            case TYPE_MISSED:   return "Missed";
            default:            return "Unknown";
        }
    }
}