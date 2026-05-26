package ru.sv.wear.repository;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.HashMap;
import java.util.Map;
import androidx.core.content.ContextCompat;

import ru.sv.wear.R;

public class WearRepository {

    private android.content.Context context;

    public WearRepository() {
        this.phaseText = new MutableLiveData<>("⏳ Поиск...");
        this.phaseColor = new MutableLiveData<>(0xFFAAAAAA);
        this.errorText = new MutableLiveData<>("");
        this.errorVisible = new MutableLiveData<>(false);
        this.repCount = new MutableLiveData<>("0");
        this.connected = new MutableLiveData<>(false);
    }

    public WearRepository(android.content.Context context) {
        this.context = context;
        this.phaseText = new MutableLiveData<>(context.getString(R.string.phase_search));
        this.phaseColor = new MutableLiveData<>(ContextCompat.getColor(context, R.color.phase_search_gray));
        this.errorText = new MutableLiveData<>("");
        this.errorVisible = new MutableLiveData<>(false);
        this.repCount = new MutableLiveData<>("0");
        this.connected = new MutableLiveData<>(false);
    }

    public void setContext(android.content.Context context) {
        this.context = context;
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, String> lastValues = new HashMap<>();

    private final MutableLiveData<String> phaseText;
    private final MutableLiveData<Integer> phaseColor;
    private final MutableLiveData<String> errorText;
    private final MutableLiveData<Boolean> errorVisible;
    private final MutableLiveData<String> repCount;
    private final MutableLiveData<Boolean> connected;

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
            phaseText.setValue(context != null ? context.getString(R.string.phase_ready) : "ГОТОВ");
            phaseColor.setValue(context != null ? ContextCompat.getColor(context, R.color.white) : 0xFFFFFFFF);
            errorVisible.setValue(false);
            repCount.setValue("0");
        });
    }

    public void setConnected(boolean isConnected) {
        mainHandler.post(() -> connected.setValue(isConnected));
    }
}