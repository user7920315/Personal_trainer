package ru.sv.personaltrainer.repository;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;

import androidx.core.content.ContextCompat;

public class PermissionRepository {

    public static final int MAX_DENIALS = 2;
    private static final String PREFS_NAME = "PermissionPrefs";
    private static final String KEY_CAMERA_DENIAL_COUNT = "camera_denial_count";
    private static final String KEY_HAS_SHOWN_RATIONALE = "has_shown_rationale";

    private final Context context;
    private final SharedPreferences prefs;

    public PermissionRepository(Application application) {
        this.context = application.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isCameraGranted() {
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    public int getCameraDenialCount() {
        return prefs.getInt(KEY_CAMERA_DENIAL_COUNT, 0);
    }

    public void recordCameraDenial() {
        int count = getCameraDenialCount() + 1;
        prefs.edit().putInt(KEY_CAMERA_DENIAL_COUNT, count).apply();
    }

    public void resetCameraDenials() {
        prefs.edit().remove(KEY_CAMERA_DENIAL_COUNT).remove(KEY_HAS_SHOWN_RATIONALE).apply();
    }

    public boolean hasShownRationale() {
        return prefs.getBoolean(KEY_HAS_SHOWN_RATIONALE, false);
    }

    public void markRationaleShown() {
        prefs.edit().putBoolean(KEY_HAS_SHOWN_RATIONALE, true).apply();
    }

    public boolean shouldRedirectToSettings() {
        return getCameraDenialCount() >= MAX_DENIALS;
    }

    public void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", context.getPackageName(), null);
        intent.setData(uri);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}