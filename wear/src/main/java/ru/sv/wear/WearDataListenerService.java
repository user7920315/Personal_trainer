package ru.sv.wear;

import android.app.Application;
import android.util.Log;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import ru.sv.wear.repository.WearRepository;
import ru.sv.wear.viewmodel.WearViewModel;

public class WearDataListenerService extends WearableListenerService {

    private static final String TAG = "WearDataListener";
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
            Log.e(TAG, "Repository not available — создаём временный");
            repository = new WearRepository();
        }
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);

        if (repository == null) {
            Log.w(TAG, "Repository is null");
            return;
        }

        String path = messageEvent.getPath();
        String message = new String(messageEvent.getData());
        Log.d(TAG, "Получено: path=" + path + " data=" + message);

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
                Log.w(TAG, "Неизвестный путь: " + path);
        }
    }

    private void handlePhase(String data) {
        String[] parts = data.split("\\|");
        if (parts.length != 2) {
            Log.e(TAG, "Неверный формат фазы: " + data);
            return;
        }
        try {
            String phaseText = parts[0];
            int color = (int) Long.parseLong(parts[1], 16);
            repository.updatePhase(phaseText, color);
        } catch (NumberFormatException e) {
            Log.e(TAG, "Ошибка парсинга цвета: " + parts[1], e);
        }
    }

    private void handleRepCount(String repText) {
        repository.updateRepCount(repText);
    }

    private void handleError(String errorText) {
        Log.d(TAG, "Ошибка: " + errorText);
        repository.updateError(errorText);
    }

    private void handleReset() {
        Log.d(TAG, "Сброс");
        repository.reset();
    }
}