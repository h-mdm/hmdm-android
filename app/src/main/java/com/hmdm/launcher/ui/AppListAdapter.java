/*
 * Headwind MDM: Open Source Android MDM Software
 * https://h-mdm.com
 *
 * Copyright (C) 2019 Headwind Solutions LLC (http://h-sms.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hmdm.launcher.ui;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hmdm.launcher.R;
import com.hmdm.launcher.databinding.ItemAppBinding;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.json.Application;
import com.hmdm.launcher.json.ServerConfig;
import com.hmdm.launcher.util.AppInfo;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Ivan Lozenko on 21.02.2017.
 */

public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.ViewHolder>{

    private LayoutInflater layoutInflater;
    private List<AppInfo> items;
    private OnAppChooseListener listener;
    private Context context;
    private SettingsHelper settingsHelper;

    public AppListAdapter(Context context, OnAppChooseListener listener){
        layoutInflater = LayoutInflater.from(context);
        items = getInstalledApps(context);
        this.listener = listener;
        this.context = context;
        this.settingsHelper = SettingsHelper.getInstance( context );
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        ViewHolder viewHolder = new ViewHolder(layoutInflater.inflate(R.layout.item_app, parent, false));
        viewHolder.binding.rootLinearLayout.setOnClickListener(onClickListener);
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        AppInfo appInfo = items.get(position);
        holder.binding.rootLinearLayout.setTag(appInfo);
        holder.binding.textView.setText(appInfo.name);

        if ( settingsHelper.getConfig().getTextColor() != null ) {
            try {
                holder.binding.textView.setTextColor(Color.parseColor(settingsHelper.getConfig().getTextColor()));
            } catch (Exception e) {
                // Invalid color
                e.printStackTrace();
            }
        }

        try {
            Integer iconScale = settingsHelper.getConfig().getIconSize();
            if (iconScale == null) {
                iconScale = ServerConfig.DEFAULT_ICON_SIZE;
            }
            int iconSize = context.getResources().getDimensionPixelOffset(R.dimen.app_icon_size) * iconScale / 100;
            holder.binding.imageView.getLayoutParams().width = iconSize;
            holder.binding.imageView.getLayoutParams().height = iconSize;
            if (appInfo.iconUrl != null) {
                // Load the icon
                Picasso.with(context).load(appInfo.iconUrl).into(holder.binding.imageView);
            } else {
                switch (appInfo.type) {
                    case AppInfo.TYPE_APP:
                        holder.binding.imageView.setImageDrawable(context.getPackageManager().getApplicationIcon(appInfo.packageName));
                        break;
                    case AppInfo.TYPE_WEB:
                        holder.binding.imageView.setImageDrawable(context.getResources().getDrawable(R.drawable.weblink));
                        break;
                }
            }
        } catch (Exception e) {
            // Here we handle PackageManager.NameNotFoundException as well as
            // DeadObjectException (when a device is being turned off)
            e.printStackTrace();
            holder.binding.imageView.setImageResource(R.drawable.ic_android_white_50dp);
        }
    }

    @Override
    public int getItemCount() {
        return items == null ? 0 : items.size();
    }

    public static final class ViewHolder extends RecyclerView.ViewHolder{
        ItemAppBinding binding;

        public ViewHolder(View itemView) {
            super(itemView);
            binding = ItemAppBinding.bind(itemView);
        }
    }

    private List<AppInfo> getInstalledApps(Context context) {
        Map<String, Application> requiredPackages = new HashMap();
        Map<String, Application> requiredLinks = new HashMap();
        SettingsHelper config = SettingsHelper.getInstance( context );
        if ( config.getConfig() != null ) {
            List< Application > applications = SettingsHelper.getInstance( context ).getConfig().getApplications();
            for ( Application application : applications ) {
                if (application.isShowIcon() && !application.isRemove()) {
                    if (application.getType() == null || application.getType().equals(Application.TYPE_APP)) {
                        requiredPackages.put(application.getPkg(), application);
                    } else if (application.getType().equals(Application.TYPE_WEB)) {
                        requiredLinks.put(application.getUrl(), application);
                    }
                }
            }
        }

        List<AppInfo> appInfos = new ArrayList<>();
        List<ApplicationInfo> packs = context.getPackageManager().getInstalledApplications(0);
        if (packs == null) {
            return new ArrayList<AppInfo>();
        }
        // First we display app icons
        for(int i=0;i < packs.size();i++) {
            ApplicationInfo p = packs.get(i);
            if ( context.getPackageManager().getLaunchIntentForPackage(p.packageName) != null &&
                    requiredPackages.containsKey( p.packageName ) ) {
                Application app = requiredPackages.get(p.packageName);
                AppInfo newInfo = new AppInfo();
                newInfo.type = AppInfo.TYPE_APP;
                newInfo.name = app.getIconText() != null ? app.getIconText() : p.loadLabel(context.getPackageManager()).toString();
                newInfo.packageName = p.packageName;
                newInfo.iconUrl = app.getIcon();
                appInfos.add(newInfo);
            }
        }

        // Then we display weblinks
        for (Map.Entry<String, Application> entry : requiredLinks.entrySet()) {
            AppInfo newInfo = new AppInfo();
            newInfo.type = AppInfo.TYPE_WEB;
            newInfo.name = entry.getValue().getIconText();
            newInfo.url = entry.getValue().getUrl();
            newInfo.iconUrl = entry.getValue().getIcon();
            appInfos.add(newInfo);
        }

        return appInfos;
    }

    public interface OnAppChooseListener{
        void onAppChoose(@NonNull AppInfo resolveInfo);
    }

    private View.OnClickListener onClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            AppInfo resolveInfo = (AppInfo)v.getTag();
            switch (resolveInfo.type) {
                case AppInfo.TYPE_APP:
                    Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(
                            resolveInfo.packageName);
                    if (launchIntent != null) {
                        // These magic flags are found in the source code of the default Android launcher
                        // These flags preserve the app activity stack (otherwise a launch activity appears at the top which is not correct)
                        launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                        context.startActivity(launchIntent);
                    }
                    break;
                case AppInfo.TYPE_WEB:
                    if (resolveInfo.url != null) {
                        Intent i = new Intent(Intent.ACTION_VIEW);
                        i.setData(Uri.parse(resolveInfo.url));
                        try {
                            context.startActivity(i);
                        } catch (ActivityNotFoundException e) {
                            Toast.makeText(context, R.string.browser_not_found, Toast.LENGTH_LONG).show();
                        }
                    }
                    break;
            }
            if (listener != null) {
                listener.onAppChoose(resolveInfo);
            }
        }
    };

}
