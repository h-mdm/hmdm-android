package com.hmdm.launcher.ui;

import android.app.Activity;
import android.view.LayoutInflater;

public class BottomAppListAdapter extends BaseAppListAdapter {
    private LayoutInflater layoutInflater;


    public BottomAppListAdapter(Activity parentActivity, OnAppChooseListener appChooseListener, SwitchAdapterListener switchAdapterListener) {
        super(parentActivity, appChooseListener, switchAdapterListener);
        updateShortcuts(parentActivity);
    }

    @Override
    public void updateShortcuts(Activity parentActivity) {
        items = AppShortcutManager.getInstance().getInstalledApps(parentActivity, true);
        initShortcuts();
    }
}
