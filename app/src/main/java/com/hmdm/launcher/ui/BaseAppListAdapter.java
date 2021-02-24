package com.hmdm.launcher.ui;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hmdm.launcher.Const;
import com.hmdm.launcher.R;
import com.hmdm.launcher.databinding.ItemAppBinding;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.json.ServerConfig;
import com.hmdm.launcher.util.AppInfo;
import com.hmdm.launcher.util.Utils;
import com.squareup.picasso.Picasso;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BaseAppListAdapter extends RecyclerView.Adapter<BaseAppListAdapter.ViewHolder> {
    protected LayoutInflater layoutInflater;
    protected List<AppInfo> items;
    protected Map<Integer, AppInfo> shortcuts;        // Keycode -> Application, filled in getInstalledApps()
    protected MainAppListAdapter.OnAppChooseListener appChooseListener;
    protected MainAppListAdapter.SwitchAdapterListener switchAdapterListener;
    protected Context context;
    protected SettingsHelper settingsHelper;
    protected int spanCount;
    protected int selectedItem = -1;
    protected RecyclerView.LayoutManager layoutManager;
    protected GradientDrawable selectedItemBorder;
    protected boolean focused = true;

    public BaseAppListAdapter(Context context, MainAppListAdapter.OnAppChooseListener appChooseListener, MainAppListAdapter.SwitchAdapterListener switchAdapterListener) {
        layoutInflater = LayoutInflater.from(context);

        this.appChooseListener = appChooseListener;
        this.switchAdapterListener = switchAdapterListener;
        this.context = context;
        this.settingsHelper = SettingsHelper.getInstance( context );

        boolean isDarkBackground = true;
        ServerConfig config = settingsHelper.getConfig();
        if (config != null && config.getBackgroundColor() != null) {
            try {
                isDarkBackground = !Utils.isLightColor(Color.parseColor(config.getBackgroundColor()));
            } catch (Exception e) {
            }
        }
        selectedItemBorder = new GradientDrawable();
        selectedItemBorder.setColor(0); // transparent background
        selectedItemBorder.setStroke(2, isDarkBackground ? 0xa0ffffff : 0xa0000000); // white or black border with some transparency
    }

    protected void initShortcuts() {
        shortcuts = new HashMap<>();
        for (AppInfo item : items) {
            if (item.keyCode != null) {
                shortcuts.put(item.keyCode, item);
            }
        }
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

        if (settingsHelper.getConfig().getTextColor() != null && !settingsHelper.getConfig().getTextColor().trim().equals("")) {
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
                // TODO: for BuildConfig.TRUST_ANY_CERTIFICATE, use custom Picasso builder as in MainActivity.java
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

            holder.itemView.setBackground(position == selectedItem ? selectedItemBorder : null);

        } catch (Exception e) {
            // Here we handle PackageManager.NameNotFoundException as well as
            // DeadObjectException (when a device is being turned off)
            e.printStackTrace();
            holder.binding.imageView.setImageResource(R.drawable.ic_android_white_50dp);
        }
    }

    public static final class ViewHolder extends RecyclerView.ViewHolder{
        ItemAppBinding binding;

        public ViewHolder(View itemView) {
            super(itemView);
            binding = ItemAppBinding.bind(itemView);
        }
    }

    @Override
    public void onAttachedToRecyclerView(final RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        layoutManager = recyclerView.getLayoutManager();
    }

    @Override
    public int getItemCount() {
        return items == null ? 0 : items.size();
    }

    public void setSpanCount(int spanCount) {
        this.spanCount = spanCount;
    }

    public void setFocused(boolean focused) {
        selectedItem = focused ? 0 : -1;
        notifyDataSetChanged();
        if (selectedItem == 0 && layoutManager != null) {
            layoutManager.scrollToPosition(selectedItem);
        }
    }

    public interface OnAppChooseListener{
        void onAppChoose(@NonNull AppInfo resolveInfo);
    }

    // Let the parent know that the user wants to switch the adapter
    // Send the direction; if the parent returns true, this means
    // it switched the adapter - unfocus self
    public interface SwitchAdapterListener {
        boolean switchAppListAdapter(BaseAppListAdapter adapter, int direction);
    }

    protected View.OnClickListener onClickListener = v -> chooseApp((AppInfo)v.getTag());

    protected void chooseApp(AppInfo appInfo) {
        switch (appInfo.type) {
            case AppInfo.TYPE_APP:
                Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(
                        appInfo.packageName);
                if (launchIntent != null) {
                    // These magic flags are found in the source code of the default Android launcher
                    // These flags preserve the app activity stack (otherwise a launch activity appears at the top which is not correct)
                    launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    context.startActivity(launchIntent);
                }
                break;
            case AppInfo.TYPE_WEB:
                if (appInfo.url != null) {
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setData(Uri.parse(appInfo.url));

                    if (appInfo.useKiosk != 0) {
                        i.setComponent(new ComponentName(Const.KIOSK_BROWSER_PACKAGE_NAME, Const.KIOSK_BROWSER_PACKAGE_NAME + ".MainActivity"));
                    }

                    try {
                        context.startActivity(i);
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(context, R.string.browser_not_found, Toast.LENGTH_LONG).show();
                    }
                }
                break;
        }
        if (appChooseListener != null) {
            appChooseListener.onAppChoose(appInfo);
        }
    }

    public boolean onKey(final int keyCode) {
        AppInfo shortcutAppInfo = shortcuts.get(new Integer(keyCode));
        if (shortcutAppInfo != null) {
            chooseApp(shortcutAppInfo);
            return true;
        }
        if (!focused) {
            return false;
        }

        int switchAdapterDirection = -1;
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (tryMoveSelection(layoutManager, 1)) {
                    return true;
                } else {
                    switchAdapterDirection = Const.DIRECTION_RIGHT;
                };
                break;
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (tryMoveSelection(layoutManager, -1)) {
                    return true;
                } else {
                    switchAdapterDirection = Const.DIRECTION_LEFT;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (tryMoveSelection(layoutManager, spanCount)) {
                    return true;
                } else {
                    switchAdapterDirection = Const.DIRECTION_DOWN;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (tryMoveSelection(layoutManager, -spanCount)) {
                    return true;
                } else {
                    switchAdapterDirection = Const.DIRECTION_UP;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
                chooseSelectedItem();
                return true;
        }
        if (switchAdapterListener != null && switchAdapterListener.switchAppListAdapter(this, switchAdapterDirection)) {
            // Adapter switch accepted, unfocus
            setFocused(false);
        }

        return false;
    }

    private boolean tryMoveSelection(RecyclerView.LayoutManager lm, int offset) {
        int trySelectedItem = selectedItem + offset;

        if (trySelectedItem < 0) {
            trySelectedItem = 0;
        }
        if (trySelectedItem >= getItemCount()) {
            trySelectedItem = getItemCount() - 1;
        }

        if (trySelectedItem != selectedItem) {
            selectedItem = trySelectedItem;
            notifyDataSetChanged();
            if (lm != null) {
                lm.scrollToPosition(trySelectedItem);
            }
            return true;
        }

        return false;
    }

    private void chooseSelectedItem() {
        if (items == null || selectedItem < 0 || selectedItem >= getItemCount()) {
            return;
        }
        chooseApp(items.get(selectedItem));
    }

}
