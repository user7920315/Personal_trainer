package ru.sv.wear.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import ru.sv.wear.R;
import ru.sv.wear.repository.WearRepository;
import androidx.core.content.ContextCompat;

public class WearViewModel extends AndroidViewModel {

    private final WearRepository repository;

    private final MutableLiveData<Boolean> connectionStatus = new MutableLiveData<>(false);
    private final LiveData<String> connectionText;
    private final LiveData<Integer> connectionColor;

    public WearViewModel(@NonNull Application application) {
        super(application);
        repository = new WearRepository(application);
        repository.setContext(application);

        connectionText = Transformations.map(connectionStatus, connected ->
                connected ? getApplication().getString(R.string.connection_connected) : getApplication().getString(R.string.connection_disconnected)
        );
        connectionColor = Transformations.map(connectionStatus, connected ->
                connected ? ContextCompat.getColor(getApplication(), R.color.success_green) : ContextCompat.getColor(getApplication(), R.color.error_red)
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