package ru.sv.wear.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import ru.sv.wear.repository.WearRepository;

public class WearViewModel extends AndroidViewModel {

    private final MutableLiveData<String> phaseText = new MutableLiveData<>("⏳ Поиск...");
    private final MutableLiveData<Integer> phaseColor = new MutableLiveData<>(0xFFAAAAAA);
    private final MutableLiveData<String> errorText = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> errorVisible = new MutableLiveData<>(false);
    private final MutableLiveData<String> repCount = new MutableLiveData<>("0");
    private final MutableLiveData<String> connectionStatus = new MutableLiveData<>("○○○");
    private final MutableLiveData<Integer> connectionColor = new MutableLiveData<>(0xFFFF5555);

    public LiveData<String> getPhaseText() { return phaseText; }
    public LiveData<Integer> getPhaseColor() { return phaseColor; }
    public LiveData<String> getErrorText() { return errorText; }
    public LiveData<Boolean> getErrorVisible() { return errorVisible; }
    public LiveData<String> getRepCount() { return repCount; }
    public LiveData<String> getConnectionStatus() { return connectionStatus; }
    public LiveData<Integer> getConnectionColor() { return connectionColor; }

    private WearRepository repository;

    public WearViewModel(@NonNull Application application) {
        super(application);
    }

    public void initRepository() {
        repository = new WearRepository();
        repository.setCallback(new WearRepository.WearCallback() {
            @Override
            public void onPhaseUpdate(String phase, int color) {
                phaseText.postValue(phase);
                phaseColor.postValue(color);
            }

            @Override
            public void onErrorUpdate(String error) {
                if (error != null && !error.isEmpty()) {
                    errorText.postValue(error);
                    errorVisible.postValue(true);
                } else {
                    errorVisible.postValue(false);
                }
            }

            @Override
            public void onRepCountUpdate(String repText) {
                repCount.postValue(repText);
            }

            @Override
            public void onReset() {
                phaseText.postValue("ГОТОВ");
                phaseColor.postValue(0xFFFFFFFF);
                errorVisible.postValue(false);
                connectionStatus.postValue("●●●");
                connectionColor.postValue(0xFF00FF88);
                repCount.postValue("0");
            }

            @Override
            public void onConnectionChanged(boolean connected) {
                if (connected) {
                    connectionStatus.postValue("●●●");
                    connectionColor.postValue(0xFF00FF88);
                    if ("⏳ Поиск...".equals(phaseText.getValue())) {
                        phaseText.postValue("ГОТОВ");
                        phaseColor.postValue(0xFFFFFFFF);
                    }
                }
            }
        });
    }

    public WearRepository getRepository() {
        return repository;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
    }
}