package ru.sv.wear.repository;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.HashMap;
import java.util.Map;

public class WearRepository {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, String> lastValues = new HashMap<>();

    private final MutableLiveData<String> phaseText = new MutableLiveData<>("⏳ Поиск...");
    private final MutableLiveData<Integer> phaseColor = new MutableLiveData<>(0xFFAAAAAA);
    private final MutableLiveData<String> errorText = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> errorVisible = new MutableLiveData<>(false);
    private final MutableLiveData<String> repCount = new MutableLiveData<>("0");
    private final MutableLiveData<Boolean> connected = new MutableLiveData<>(false);

    public LiveData<String> getPhaseText() { return phaseText; }
    public LiveData<Integer> getPhaseColor() { return phaseColor; }
    public LiveData<String> getErrorText() { return errorText; }
    public LiveData<Boolean> getErrorVisible() { return errorVisible; }
    public LiveData<String> getRepCount() { return repCount; }
    public LiveData<Boolean> getConnected() { return connected; }

    public void updatePhase(String phase, int color) {
        String key = "phase|" + phase;
        if (!key.equals(lastValues.get("phase"))) {
            lastValues.put("phase", key);
            mainHandler.post(() -> {
                phaseText.setValue(phase);
                phaseColor.setValue(color);
                connected.setValue(true);
            });
        }
    }

    public void updateError(String error) {
        if (!error.equals(lastValues.get("error"))) {
            lastValues.put("error", error);
            mainHandler.post(() -> {
                if (error != null && !error.isEmpty()) {
                    errorText.setValue(error);
                    errorVisible.setValue(true);
                } else {
                    errorVisible.setValue(false);
                }
                connected.setValue(true);
            });
        }
    }

    public void updateRepCount(String repText) {
        if (!repText.equals(lastValues.get("reps"))) {
            lastValues.put("reps", repText);
            mainHandler.post(() -> repCount.setValue(repText));
        }
    }

    public void reset() {
        lastValues.clear();
        mainHandler.post(() -> {
            phaseText.setValue("ГОТОВ");
            phaseColor.setValue(0xFFFFFFFF);
            errorVisible.setValue(false);
            repCount.setValue("0");
        });
    }

    public void setConnected(boolean isConnected) {
        mainHandler.post(() -> connected.setValue(isConnected));
    }
}