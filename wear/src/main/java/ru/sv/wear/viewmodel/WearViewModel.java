package ru.sv.wear.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import ru.sv.wear.repository.WearRepository;

public class WearViewModel extends AndroidViewModel {

    private final WearRepository repository;

    private final MutableLiveData<Boolean> connectionStatus = new MutableLiveData<>(false);
    private final LiveData<String> connectionText;
    private final LiveData<Integer> connectionColor;

    public WearViewModel(@NonNull Application application) {
        super(application);
        repository = new WearRepository();

        connectionText = Transformations.map(connectionStatus, connected ->
                connected ? "●●●" : "○○○"
        );
        connectionColor = Transformations.map(connectionStatus, connected ->
                connected ? 0xFF00FF88 : 0xFFFF5555
        );
    }

    public LiveData<String> getPhaseText() { return repository.getPhaseText(); }
    public LiveData<Integer> getPhaseColor() { return repository.getPhaseColor(); }
    public LiveData<String> getErrorText() { return repository.getErrorText(); }
    public LiveData<Boolean> getErrorVisible() { return repository.getErrorVisible(); }
    public LiveData<String> getRepCount() { return repository.getRepCount(); }
    public LiveData<String> getConnectionText() { return connectionText; }
    public LiveData<Integer> getConnectionColor() { return connectionColor; }

    public WearRepository getRepository() {
        return repository;
    }

    public void setConnected(boolean connected) {
        connectionStatus.setValue(connected);
        repository.setConnected(connected);
    }
}