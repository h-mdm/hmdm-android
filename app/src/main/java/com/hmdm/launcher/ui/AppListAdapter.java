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

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hmdm.launcher.R;
import com.hmdm.launcher.databinding.ItemAppBinding;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.json.Application;
import com.hmdm.launcher.json.ServerConfig;
import com.hmdm.launcher.util.AppInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
            holder.binding.textView.setTextColor( Color.parseColor( settingsHelper.getConfig().getTextColor() ) );
        }

        try {
            Integer iconScale = settingsHelper.getConfig().getIconSize();
            if (iconScale == null) {
                iconScale = ServerConfig.DEFAULT_ICON_SIZE;
            }
            int iconSize = context.getResources().getDimensionPixelOffset(R.dimen.app_icon_size) * iconScale / 100;
            holder.binding.imageView.getLayoutParams().width = iconSize;
            holder.binding.imageView.getLayoutParams().height = iconSize;
            holder.binding.imageView.setImageDrawable(context.getPackageManager().getApplicationIcon(appInfo.packageName));
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
        Set< String > installedPackages = new HashSet();
        SettingsHelper config = SettingsHelper.getInstance( context );
        if ( config.getConfig() != null ) {
            List< Application > applications = SettingsHelper.getInstance( context ).getConfig().getApplications();
            for ( Application application : applications ) {
                if (application.isShowIcon() && !application.isRemove()) {
                    installedPackages.add(application.getPkg());
                }
            }
        }

        List<AppInfo> appInfos = new ArrayList<>();
        List<ApplicationInfo> packs = context.getPackageManager().getInstalledApplications(0);
        if (packs == null) {
            return new ArrayList<AppInfo>();
        }
        for(int i=0;i < packs.size();i++) {
            ApplicationInfo p = packs.get(i);
            if ( context.getPackageManager().getLaunchIntentForPackage(p.packageName) != null &&
                    installedPackages.contains( p.packageName ) ) {
                AppInfo newInfo = new AppInfo();
                newInfo.name = p.loadLabel(context.getPackageManager()).toString();
                newInfo.packageName = p.packageName;
                appInfos.add(newInfo);
            }
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
            Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(
                    resolveInfo.packageName );
            if ( launchIntent != null ) {
                context.startActivity( launchIntent );
                if ( listener != null ) {
                    listener.onAppChoose( resolveInfo );
                }
            }
        }
    };

}
