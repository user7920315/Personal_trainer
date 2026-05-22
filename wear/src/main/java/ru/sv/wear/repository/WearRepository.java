package ru.sv.wear.repository;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.MutableLiveData;

import java.util.HashMap;
import java.util.Map;

public class WearRepository {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, String> lastValues = new HashMap<>();

    public interface WearCallback {
        void onPhaseUpdate(String phase, int color);
        void onErrorUpdate(String error);
        void onRepCountUpdate(String repText);
        void onReset();
        void onConnectionChanged(boolean connected);
    }

    private WearCallback callback;

    public void setCallback(WearCallback callback) {
        this.callback = callback;
    }

    public void updatePhase(String phase, int color) {
        String key = "phase|" + phase;
        if (!key.equals(lastValues.get("phase"))) {
            lastValues.put("phase", key);
            mainHandler.post(() -> {
                if (callback != null) callback.onPhaseUpdate(phase, color);
            });
        }
    }

    public void updateError(String error) {
        if (!error.equals(lastValues.get("error"))) {
            lastValues.put("error", error);
            mainHandler.post(() -> {
                if (callback != null) callback.onErrorUpdate(error);
            });
        }
    }

    public void updateRepCount(String repText) {
        if (!repText.equals(lastValues.get("reps"))) {
            lastValues.put("reps", repText);
            mainHandler.post(() -> {
                if (callback != null) callback.onRepCountUpdate(repText);
            });
        }
    }

    public void reset() {
        lastValues.clear();
        mainHandler.post(() -> {
            if (callback != null) callback.onReset();
        });
    }

    public void setConnected(boolean connected) {
        mainHandler.post(() -> {
            if (callback != null) callback.onConnectionChanged(connected);
        });
    }
}