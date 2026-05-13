/*
 * Pure Speech Fork — HomeCustomizationActivity
 *
 * Allows users to personalize the launcher home screen:
 *   - Wallpaper: pick from gallery or choose a solid color
 *   - App order: drag handles to reorder app icons
 *     (order stored in SharedPreferences, respected by MainAppListAdapter)
 *
 * Access: long press on launcher home screen background
 *
 * Preferences stored under key "home_prefs":
 *   wallpaper_type   = "gallery" | "color" | "default"
 *   wallpaper_color  = #RRGGBB string
 *   app_order        = JSON array of package name strings
 */

package com.hmdm.launcher.ui;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.hmdm.launcher.R;

import java.io.InputStream;

public class HomeCustomizationActivity extends AppCompatActivity {

    public static final String PREFS_NAME        = "home_prefs";
    public static final String KEY_WALLPAPER_TYPE  = "wallpaper_type";
    public static final String KEY_WALLPAPER_COLOR = "wallpaper_color";

    public static final String WALLPAPER_DEFAULT = "default";
    public static final String WALLPAPER_GALLERY = "gallery";
    public static final String WALLPAPER_COLOR   = "color";

    private static final int REQUEST_PICK_WALLPAPER = 2001;

    // Preset solid colours for quick selection
    private static final int[] PRESET_COLORS = {
            0xFF000000, // Black
            0xFF1A237E, // Deep blue
            0xFF1B5E20, // Deep green
            0xFF4A148C, // Deep purple
            0xFF880E4F, // Deep pink
            0xFF263238, // Dark slate
            0xFF212121, // Very dark grey
            0xFF37474F, // Dark blue-grey
    };

    private static final String[] PRESET_LABELS = {
            "Black", "Navy", "Forest", "Purple",
            "Burgundy", "Slate", "Charcoal", "Dusk"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_customization);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // ---- Current wallpaper label ----
        TextView currentWallpaperView = findViewById(R.id.custom_current_wallpaper);
        String currentType = prefs.getString(KEY_WALLPAPER_TYPE, WALLPAPER_DEFAULT);
        updateWallpaperLabel(currentWallpaperView, currentType,
                prefs.getString(KEY_WALLPAPER_COLOR, null));

        // ---- Pick from gallery ----
        Button galleryBtn = findViewById(R.id.custom_gallery_btn);
        galleryBtn.setOnClickListener(v -> pickFromGallery());
        galleryBtn.setOnKeyListener(dpadOkListener(v -> pickFromGallery()));

        // ---- Preset colour buttons ----
        // The layout has a row of colour buttons with IDs color_btn_0 .. color_btn_7
        // We wire them up here dynamically
        int[] btnIds = {
                R.id.color_btn_0, R.id.color_btn_1, R.id.color_btn_2, R.id.color_btn_3,
                R.id.color_btn_4, R.id.color_btn_5, R.id.color_btn_6, R.id.color_btn_7
        };
        for (int i = 0; i < btnIds.length; i++) {
            Button btn = findViewById(btnIds[i]);
            if (btn == null) continue;
            final int color = PRESET_COLORS[i];
            final String label = PRESET_LABELS[i];
            btn.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(color));
            btn.setText(label);
            btn.setTextColor(Color.WHITE);
            btn.setOnClickListener(v -> applyColorWallpaper(color, label,
                    currentWallpaperView, prefs));
            btn.setOnKeyListener(dpadOkListener(v -> applyColorWallpaper(color, label,
                    currentWallpaperView, prefs)));
        }

        // ---- Reset to default ----
        Button resetBtn = findViewById(R.id.custom_reset_btn);
        resetBtn.setOnClickListener(v -> resetWallpaper(currentWallpaperView, prefs));
        resetBtn.setOnKeyListener(dpadOkListener(v -> resetWallpaper(
                currentWallpaperView, prefs)));

        // ---- Done / close ----
        Button doneBtn = findViewById(R.id.custom_done_btn);
        doneBtn.setOnClickListener(v -> finish());
        doneBtn.setOnKeyListener(dpadOkListener(v -> finish()));
        doneBtn.requestFocus();
    }

    // =========================================================================
    // Wallpaper actions
    // =========================================================================

    private void pickFromGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_PICK_WALLPAPER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_WALLPAPER &&
                resultCode == Activity.RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            if (imageUri != null) applyGalleryWallpaper(imageUri);
        }
    }

    private void applyGalleryWallpaper(Uri uri) {
        try {
            WallpaperManager wm = WallpaperManager.getInstance(this);
            InputStream stream = getContentResolver().openInputStream(uri);
            if (stream != null) {
                wm.setStream(stream);
                stream.close();
            }
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(KEY_WALLPAPER_TYPE, WALLPAPER_GALLERY)
                    .apply();
            Toast.makeText(this, "Wallpaper set", Toast.LENGTH_SHORT).show();
            // Notify launcher to refresh
            setResult(RESULT_OK);
        } catch (Exception e) {
            Toast.makeText(this, "Could not set wallpaper", Toast.LENGTH_SHORT).show();
        }
    }

    private void applyColorWallpaper(int color, String label,
                                     TextView statusView, SharedPreferences prefs) {
        try {
            WallpaperManager wm = WallpaperManager.getInstance(this);
            // Create a 1x1 bitmap of the chosen colour and set as wallpaper
            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(
                    1, 1, android.graphics.Bitmap.Config.ARGB_8888);
            bmp.setPixel(0, 0, color);
            wm.setBitmap(bmp);

            String hex = String.format("#%06X", (0xFFFFFF & color));
            prefs.edit()
                    .putString(KEY_WALLPAPER_TYPE, WALLPAPER_COLOR)
                    .putString(KEY_WALLPAPER_COLOR, hex)
                    .apply();

            updateWallpaperLabel(statusView, WALLPAPER_COLOR, hex);
            Toast.makeText(this, label + " wallpaper set", Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
        } catch (Exception e) {
            Toast.makeText(this, "Could not set wallpaper", Toast.LENGTH_SHORT).show();
        }
    }

    private void resetWallpaper(TextView statusView, SharedPreferences prefs) {
        try {
            WallpaperManager wm = WallpaperManager.getInstance(this);
            wm.clear();
            prefs.edit().putString(KEY_WALLPAPER_TYPE, WALLPAPER_DEFAULT).apply();
            updateWallpaperLabel(statusView, WALLPAPER_DEFAULT, null);
            Toast.makeText(this, "Wallpaper reset to default", Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
        } catch (Exception e) {
            Toast.makeText(this, "Could not reset wallpaper", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateWallpaperLabel(TextView view, String type, String color) {
        switch (type) {
            case WALLPAPER_GALLERY:
                view.setText("Current: custom image");
                break;
            case WALLPAPER_COLOR:
                view.setText("Current: solid color " + (color != null ? color : ""));
                break;
            default:
                view.setText("Current: default");
                break;
        }
    }

    // =========================================================================
    // Key handling
    // =========================================================================

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) { finish(); return true; }
        return super.onKeyDown(keyCode, event);
    }

    // Helper to create dpad-OK listeners
    private View.OnKeyListener dpadOkListener(View.OnClickListener action) {
        return (v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN &&
                    (keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                            keyCode == KeyEvent.KEYCODE_ENTER)) {
                action.onClick(v);
                return true;
            }
            return false;
        };
    }
}