package ru.sv.wear;

import android.util.Log;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import java.lang.ref.WeakReference;

import ru.sv.wear.repository.WearRepository;

public class WearDataListenerService extends WearableListenerService {

    private static final String TAG = "WearDataListener";
    private static final String PATH_ERROR = "/exercise/error";
    private static final String PATH_PHASE = "/exercise/phase";
    private static final String PATH_RESET = "/exercise/reset";
    private static final String PATH_REPS = "/exercise/reps";

    private static WeakReference<WearRepository> repositoryRef = null;

    public static void setRepository(WearRepository repository) {
        if (repository != null) {
            repositoryRef = new WeakReference<>(repository);
        } else {
            repositoryRef = null;
        }
        Log.d(TAG, "Repository reference " + (repository != null ? "set" : "cleared"));
    }

    private WearRepository getRepository() {
        if (repositoryRef == null) return null;
        return repositoryRef.get();
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);

        String path = messageEvent.getPath();
        String message = new String(messageEvent.getData());

        Log.d(TAG, "Получено: path=" + path + " data=" + message);

        WearRepository repo = getRepository();
        if (repo == null) {
            Log.w(TAG, "Repository not available");
            return;
        }

        switch (path) {
            case PATH_ERROR:
                handleError(repo, message);
                break;
            case PATH_PHASE:
                handlePhase(repo, message);
                break;
            case PATH_RESET:
                handleReset(repo);
                break;
            case PATH_REPS:
                handleRepCount(repo, message);
                break;
            default:
                Log.w(TAG, "Неизвестный путь: " + path);
        }
    }

    private void handlePhase(WearRepository repo, String data) {
        String[] parts = data.split("\\|");
        if (parts.length != 2) {
            Log.e(TAG, "Неверный формат фазы: " + data);
            return;
        }
        try {
            String phaseText = parts[0];
            int color = (int) Long.parseLong(parts[1], 16);
            repo.setConnected(true);
            repo.updatePhase(phaseText, color);
        } catch (NumberFormatException e) {
            Log.e(TAG, "Ошибка парсинга цвета: " + parts[1], e);
        }
    }

    private void handleRepCount(WearRepository repo, String repText) {
        repo.updateRepCount(repText);
    }

    private void handleError(WearRepository repo, String errorText) {
        Log.d(TAG, "Ошибка: " + errorText);
        repo.setConnected(true);
        repo.updateError(errorText);
    }

    private void handleReset(WearRepository repo) {
        Log.d(TAG, "Сброс");
        repo.reset();
    }
}