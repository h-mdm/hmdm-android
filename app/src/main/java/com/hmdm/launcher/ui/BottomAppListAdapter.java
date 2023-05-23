package com.hmdm.launcher.ui;

import android.app.Activity;
import android.view.LayoutInflater;

public class BottomAppListAdapter extends BaseAppListAdapter {
    private LayoutInflater layoutInflater;

    public BottomAppListAdapter(Activity parentActivity, OnAppChooseListener appChooseListener, SwitchAdapterListener switchAdapterListener) {
        super(parentActivity, appChooseListener, switchAdapterListener);
        items = AppShortcutManager.getInstance().getInstalledApps(parentActivity, true);
        initShortcuts();
    }
}
