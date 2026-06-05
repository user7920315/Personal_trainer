package ru.sv.personaltrainer.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class VideoCacheManager {

    private static final String PREFS_NAME = "video_cache";
    private static final String KEY_VERSION = "cache_version";
    private static final int CURRENT_VERSION = 1;

    private final Context context;

    public VideoCacheManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public File getVideoFile(String assetName) {
        File cacheDir = new File(context.getCacheDir(), "videos");
        File videoFile = new File(cacheDir, assetName);

        if (!videoFile.exists() || isCacheStale()) {
            copyFromAssets(assetName, videoFile);
        }

        return videoFile;
    }

    private boolean isCacheStale() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_VERSION, 0) != CURRENT_VERSION;
    }

    private void copyFromAssets(String assetName, File outFile) {
        File cacheDir = outFile.getParentFile();
        if (!cacheDir.exists()) cacheDir.mkdirs();

        try (InputStream in = context.getAssets().open("videos/" + assetName);
             OutputStream out = new FileOutputStream(outFile)) {

            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }

            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putInt(KEY_VERSION, CURRENT_VERSION)
                    .apply();

        } catch (IOException e) {
            throw new RuntimeException("Не удалось скопировать видео: " + assetName, e);
        }
    }

    public static void clearCache(Context context) {
        File cacheDir = new File(context.getCacheDir(), "videos");
        if (cacheDir.exists()) {
            for (File f : cacheDir.listFiles()) {
                f.delete();
            }
        }
    }
}