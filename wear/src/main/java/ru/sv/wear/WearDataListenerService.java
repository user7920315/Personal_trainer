package ru.sv.wear;

import android.app.Application;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import ru.sv.wear.repository.WearRepository;
import ru.sv.wear.viewmodel.WearViewModel;

public class WearDataListenerService extends WearableListenerService {

    private static final String PATH_ERROR = "/exercise/error";
    private static final String PATH_PHASE = "/exercise/phase";
    private static final String PATH_RESET = "/exercise/reset";
    private static final String PATH_REPS = "/exercise/reps";

    private WearRepository repository;

    @Override
    public void onCreate() {
        super.onCreate();
        Application app = getApplication();
        if (app instanceof WearApplication) {
            WearViewModel vm = ((WearApplication) app).getWearViewModel();
            if (vm != null) {
                repository = vm.getRepository();
            }
        }
        if (repository == null) {
            repository = new WearRepository();
        }
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);

        if (repository == null) {
            return;
        }

        String path = messageEvent.getPath();
        String message = new String(messageEvent.getData());

        switch (path) {
            case PATH_ERROR:
                handleError(message);
                break;
            case PATH_PHASE:
                handlePhase(message);
                break;
            case PATH_RESET:
                handleReset();
                break;
            case PATH_REPS:
                handleRepCount(message);
                break;
            default:
        }
    }

    private void handlePhase(String data) {
        String[] parts = data.split("\\|");
        if (parts.length != 2) {
            return;
        }
        try {
            String phaseText = parts[0];
            int color = (int) Long.parseLong(parts[1], 16);
            repository.updatePhase(phaseText, color);
        } catch (NumberFormatException e) {
        }
    }

    private void handleRepCount(String repText) {
        repository.updateRepCount(repText);
    }

    private void handleError(String errorText) {
        repository.updateError(errorText);
    }

    private void handleReset() {
        repository.reset();
    }
}