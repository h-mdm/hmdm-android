package com.hmdm.launcher.ui;

import android.content.Context;
import android.view.LayoutInflater;

public class BottomAppListAdapter extends BaseAppListAdapter {
    private LayoutInflater layoutInflater;

    public BottomAppListAdapter(Context context, OnAppChooseListener appChooseListener, SwitchAdapterListener switchAdapterListener) {
        super(context, appChooseListener, switchAdapterListener);
        items = AppShortcutManager.getInstance().getInstalledApps(context, true);
        initShortcuts();
    }
}
