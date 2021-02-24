package com.hmdm.launcher.ui;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.json.Application;
import com.hmdm.launcher.util.AppInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AppShortcutManager {

    private static AppShortcutManager instance;

    public static AppShortcutManager getInstance() {
        if (instance == null) {
            instance = new AppShortcutManager();
        }
        return instance;
    }

    public int getInstalledAppCount(Context context, boolean bottom) {
        Map<String, Application> requiredPackages = new HashMap();
        Map<String, Application> requiredLinks = new HashMap();
        getConfiguredApps(context, bottom, requiredPackages, requiredLinks);
        List<ApplicationInfo> packs = context.getPackageManager().getInstalledApplications(0);
        if (packs == null) {
            return requiredLinks.size();
        }
        // Calculate applications
        int packageCount = 0;
        for(int i = 0; i < packs.size(); i++) {
            ApplicationInfo p = packs.get(i);
            if (context.getPackageManager().getLaunchIntentForPackage(p.packageName) != null &&
                    requiredPackages.containsKey(p.packageName)) {
                packageCount++;
            }
        }
        return requiredLinks.size() + packageCount;
    }

    public List<AppInfo> getInstalledApps(Context context, boolean bottom) {
        Map<String, Application> requiredPackages = new HashMap();
        Map<String, Application> requiredLinks = new HashMap();
        getConfiguredApps(context, bottom, requiredPackages, requiredLinks);

        List<AppInfo> appInfos = new ArrayList<>();
        List<ApplicationInfo> packs = context.getPackageManager().getInstalledApplications(0);
        if (packs == null) {
            return new ArrayList<AppInfo>();
        }
        // First we display app icons
        for(int i = 0; i < packs.size(); i++) {
            ApplicationInfo p = packs.get(i);
            if ( context.getPackageManager().getLaunchIntentForPackage(p.packageName) != null &&
                    requiredPackages.containsKey( p.packageName ) ) {
                Application app = requiredPackages.get(p.packageName);
                AppInfo newInfo = new AppInfo();
                newInfo.type = AppInfo.TYPE_APP;
                newInfo.keyCode = app.getKeyCode();
                newInfo.name = app.getIconText() != null ? app.getIconText() : p.loadLabel(context.getPackageManager()).toString();
                newInfo.packageName = p.packageName;
                newInfo.iconUrl = app.getIcon();
                newInfo.screenOrder = app.getScreenOrder();
                appInfos.add(newInfo);
            }
        }

        // Then we display weblinks
        for (Map.Entry<String, Application> entry : requiredLinks.entrySet()) {
            AppInfo newInfo = new AppInfo();
            newInfo.type = AppInfo.TYPE_WEB;
            newInfo.keyCode = entry.getValue().getKeyCode();
            newInfo.name = entry.getValue().getIconText();
            newInfo.url = entry.getValue().getUrl();
            newInfo.iconUrl = entry.getValue().getIcon();
            newInfo.screenOrder = entry.getValue().getScreenOrder();
            newInfo.useKiosk = entry.getValue().isUseKiosk() ? 1 : 0;
            appInfos.add(newInfo);
        }

        // Apply manually set order
        Collections.sort(appInfos, new AppInfosComparator());

        return appInfos;
    }

    private void getConfiguredApps(Context context, boolean bottom, Map<String, Application> requiredPackages, Map<String, Application> requiredLinks) {
        SettingsHelper config = SettingsHelper.getInstance( context );
        if ( config.getConfig() != null ) {
            List< Application > applications = SettingsHelper.getInstance( context ).getConfig().getApplications();
            for ( Application application : applications ) {
                if (application.isShowIcon() && !application.isRemove() && (bottom == application.isBottom())) {
                    if (application.getType() == null || application.getType().equals(Application.TYPE_APP)) {
                        requiredPackages.put(application.getPkg(), application);
                    } else if (application.getType().equals(Application.TYPE_WEB)) {
                        requiredLinks.put(application.getUrl(), application);
                    }
                }
            }
        }
    }

    public class AppInfosComparator implements Comparator<AppInfo> {
        @Override
        public int compare(AppInfo o1, AppInfo o2) {
            if (o1.screenOrder == null) {
                if (o2.screenOrder == null) {
                    return 0;
                }
                return 1;
            }
            if (o2.screenOrder == null) {
                return -1;
            }
            return Integer.compare(o1.screenOrder, o2.screenOrder);
        }
    }

}
