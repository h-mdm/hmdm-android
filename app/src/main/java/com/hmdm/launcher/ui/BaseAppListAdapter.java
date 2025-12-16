package com.hmdm.launcher.ui;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.hmdm.launcher.BuildConfig;
import com.hmdm.launcher.Const;
import com.hmdm.launcher.R;
import com.hmdm.launcher.databinding.ItemAppBinding;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.json.ServerConfig;
import com.hmdm.launcher.server.UnsafeOkHttpClient;
import com.hmdm.launcher.util.AppInfo;
import com.hmdm.launcher.util.InstallUtils;
import com.hmdm.launcher.util.Utils;
import com.jakewharton.picasso.OkHttp3Downloader;
import com.squareup.picasso.NetworkPolicy;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Cache;
import okhttp3.OkHttpClient;

public class BaseAppListAdapter extends RecyclerView.Adapter<BaseAppListAdapter.ViewHolder> {
    protected LayoutInflater layoutInflater;
    protected List<AppInfo> items;
    protected Map<Integer, AppInfo> shortcuts;        // Keycode -> Application, filled in getInstalledApps()
    protected MainAppListAdapter.OnAppChooseListener appChooseListener;
    protected MainAppListAdapter.SwitchAdapterListener switchAdapterListener;
    protected Activity parentActivity;
    protected SettingsHelper settingsHelper;
    protected int spanCount;
    protected int selectedItem = -1;
    protected RecyclerView.LayoutManager layoutManager;
    protected GradientDrawable selectedItemBorder;
    protected boolean focused = true;
    protected boolean dpadUsed = false;

    protected Picasso picasso = null;

    public BaseAppListAdapter(Activity parentActivity, MainAppListAdapter.OnAppChooseListener appChooseListener, MainAppListAdapter.SwitchAdapterListener switchAdapterListener) {
        layoutInflater = LayoutInflater.from(parentActivity);

        this.appChooseListener = appChooseListener;
        this.switchAdapterListener = switchAdapterListener;
        this.parentActivity = parentActivity;
        this.settingsHelper = SettingsHelper.getInstance(parentActivity);

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
        viewHolder.binding.rootLinearLayout.setOnLongClickListener(onLongClickListener);
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
            int iconSize = parentActivity.getResources().getDimensionPixelOffset(R.dimen.app_icon_size) * iconScale / 100;
            holder.binding.imageView.getLayoutParams().width = iconSize;
            holder.binding.imageView.getLayoutParams().height = iconSize;
            if (appInfo.iconUrl != null) {
                // Load the icon
                if (picasso == null) {
                    Picasso.Builder builder = new Picasso.Builder(parentActivity);
                    if (BuildConfig.TRUST_ANY_CERTIFICATE) {
                        builder.downloader(new OkHttp3Downloader(UnsafeOkHttpClient.getUnsafeOkHttpClient()));
                    } else {
                        // Add signature to all requests to protect against unauthorized API calls
                        // For TRUST_ANY_CERTIFICATE, we won't add signatures because it's unsafe anyway
                        // and is just a workaround to use Headwind MDM on the LAN
                        OkHttpClient clientWithSignature = new OkHttpClient.Builder()
                                .cache(new Cache(new File(parentActivity.getApplication().getCacheDir(), "image_cache"), 1000000L))
                                .addInterceptor(chain -> {
                                    okhttp3.Request.Builder requestBuilder = chain.request().newBuilder();
                                    String signature = InstallUtils.getRequestSignature(chain.request().url().toString());
                                    if (signature != null) {
                                        requestBuilder.addHeader("X-Request-Signature", signature);
                                    }
                                    return chain.proceed(requestBuilder.build());

                                })
                                .build();
                        builder.downloader(new OkHttp3Downloader(clientWithSignature));
                    }
                    builder.listener(new Picasso.Listener() {
                        @Override
                        public void onImageLoadFailed(Picasso picasso, Uri uri, Exception exception) {
                            // On fault, get the image from the cache
                            // This is a workaround against a bug in Picasso: it doesn't display cached images by default!
                            picasso.load(appInfo.iconUrl)
                                    .networkPolicy(NetworkPolicy.OFFLINE)
                                    .into(holder.binding.imageView);
                        }
                    });
                    picasso = builder.build();
                }

                picasso.load(appInfo.iconUrl)
                        .into(holder.binding.imageView);
            } else {
                switch (appInfo.type) {
                    case AppInfo.TYPE_APP:
                        holder.binding.imageView.setImageDrawable(parentActivity.getPackageManager().getApplicationIcon(appInfo.packageName));
                        break;
                    case AppInfo.TYPE_WEB:
                        holder.binding.imageView.setImageDrawable(
                                parentActivity.getResources().getDrawable(getDrawableResourceForWebApp(appInfo)));
                        break;
                    case AppInfo.TYPE_INTENT:
                        holder.binding.imageView.setImageDrawable(
                                parentActivity.getResources().getDrawable(getDrawableResourceForIntent(appInfo)));
                        break;
                }
            }

            holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
                holder.itemView.setBackground(holder.itemView.hasFocus() && dpadUsed ? selectedItemBorder : null);
            });

        } catch (Exception e) {
            // Here we handle PackageManager.NameNotFoundException as well as
            // DeadObjectException (when a device is being turned off)
            e.printStackTrace();
            holder.binding.imageView.setImageResource(R.drawable.ic_android_white_50dp);
        }
    }

    public int getDrawableResourceForWebApp(AppInfo appInfo) {
        if (appInfo.url.startsWith("file://")) {
            if (appInfo.url.endsWith("/")) {
                return R.drawable.localfolder;
            } else {
                return R.drawable.locallink;
            }
        } else {
            return R.drawable.weblink;
        }
    }

    public int getDrawableResourceForIntent(AppInfo appInfo) {
        if (appInfo.intent != null && appInfo.intent.equals("android.intent.action.DIAL")) {
            return R.drawable.dialer;
        } else {
            return R.drawable.settings;
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

    protected View.OnClickListener onClickListener = v -> {
        if (BuildConfig.SELECTED_ITEM_BY_CLICK && selectedItem >= 0) {
            // Some TV boxes mistakenly generate onClick() for the first item
            // when the OK button is clicked. Here's the workaround
            chooseSelectedItem();
        } else {
            chooseApp((AppInfo) v.getTag());
        }
    };

    protected View.OnLongClickListener onLongClickListener = v -> {
        AppInfo appInfo = (AppInfo) v.getTag();
        if (appInfo.type == AppInfo.TYPE_APP && appInfo.longTap == 1) {
            // Open app settings on long click
            openAppSettings(appInfo);
            return true;
        }
        return false;
    };

    protected void chooseApp(AppInfo appInfo) {
        switch (appInfo.type) {
            case AppInfo.TYPE_APP:
                Intent launchIntent = parentActivity.getPackageManager().getLaunchIntentForPackage(
                        appInfo.packageName);
                if (launchIntent != null) {
                    // These magic flags are found in the source code of the default Android launcher
                    // These flags preserve the app activity stack (otherwise a launch activity appears at the top which is not correct)
                    launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    parentActivity.startActivity(launchIntent);
                }
                break;
            case AppInfo.TYPE_WEB:
                if (appInfo.url != null) {
                    Intent i = new Intent(Intent.ACTION_VIEW);

                    Uri uri = Uri.parse(appInfo.url);
                    String mimeType = null;

                    if (uri.getScheme().equals("file")) {
                        // Avoid FileUriExposedException
                        String path = uri.getPath();
                        File file = new File(path);
                        try {
                            uri = FileProvider.getUriForFile(parentActivity, parentActivity.getApplicationContext().getPackageName() + ".provider", file);
                        } catch (/*IllegalArgument*/Exception e) {
                            Toast.makeText(parentActivity, R.string.invalid_web_link, Toast.LENGTH_LONG).show();
                            break;
                        }
                        if (appInfo.url.endsWith("/")) {
                            // This is the directory, we need to specify the MIME type explicitly
                            mimeType = DocumentsContract.Document.MIME_TYPE_DIR;
                        }
                        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    }

                    Log.d(Const.LOG_TAG, "BaseAppListAdapter: opening web app: " + uri.toString());
                    if (mimeType != null) {
                        i.setDataAndType(uri, mimeType);
                    } else {
                        i.setData(uri);
                    }
                    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);

                    if (appInfo.useKiosk != 0) {
                        Log.d(Const.LOG_TAG, "Component: " + Const.KIOSK_BROWSER_PACKAGE_NAME + ".MainActivity");
                        i.setComponent(new ComponentName(Const.KIOSK_BROWSER_PACKAGE_NAME, Const.KIOSK_BROWSER_PACKAGE_NAME + ".MainActivity"));
                    }

                    try {
                        parentActivity.startActivity(i);
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(parentActivity, R.string.browser_not_found, Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(parentActivity, R.string.invalid_web_link, Toast.LENGTH_LONG).show();
                    }
                }
                break;
            case AppInfo.TYPE_INTENT:
                if (appInfo.intent != null) {
                    try {
                        Intent i = new Intent(appInfo.intent);
                        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        parentActivity.startActivity(i);
                    } catch (Exception e) {
                        Toast.makeText(parentActivity, parentActivity.getString(R.string.activity_not_found, appInfo.intent), Toast.LENGTH_LONG).show();
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
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            dpadUsed = true;
        }
        return false;

        // This code (manual browsing of icons) follows by focusing just 1st item
        // (the workaround is provided by BuildConfig.SELECTED_ITEM_BY_CLICK but it won't work well
        // if an item is selected and another is tapped. Also, (info) and (reload) items become
        // unavailable. So just turn on displaying border by focus here (flag dpadUsed).

/*        if (!focused) {
            return false;
        }

        int switchAdapterDirection = -1;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (tryMoveSelection(layoutManager, 1)) {
                    return true;
                } else {
                    switchAdapterDirection = Const.DIRECTION_RIGHT;
                };
                break;
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

        return false; */
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

    private void openAppSettings(AppInfo appInfo) {
        parentActivity.startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + appInfo.packageName)));
    }
}
