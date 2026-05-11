package ru.sv.wear;

import android.content.Intent;
import android.util.Log;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import java.lang.ref.WeakReference;  // ✅ WeakReference против утечки

public class WearDataListenerService extends WearableListenerService {

    private static final String TAG        = "WearDataListener";
    private static final String PATH_ERROR = "/exercise/error";
    private static final String PATH_PHASE = "/exercise/phase";
    private static final String PATH_RESET = "/exercise/reset";
    private static final String PATH_REPS  = "/exercise/reps";

    // ✅ ИСПРАВЛЕНО: WeakReference — не держит Activity в памяти
    private static WeakReference<MainActivity> mainActivityRef = null;

    public static void setMainActivity(MainActivity activity) {
        if (activity != null) {
            mainActivityRef = new WeakReference<>(activity);
        } else {
            mainActivityRef = null;
        }
        Log.d(TAG, "MainActivity reference " +
                (activity != null ? "set" : "cleared"));
    }

    // Вспомогательный метод для безопасного получения Activity
    private MainActivity getActivity() {
        if (mainActivityRef == null) return null;
        return mainActivityRef.get();  // null если Activity уничтожена
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);

        String path    = messageEvent.getPath();
        String message = new String(messageEvent.getData());

        Log.d(TAG, "Получено: path=" + path + " data=" + message);

        switch (path) {
            case PATH_ERROR: handleError(message); break;
            case PATH_PHASE: handlePhase(message); break;
            case PATH_RESET: handleReset(); break;
            case PATH_REPS: handleRepCount(message); break;
            default: Log.w(TAG, "Неизвестный путь: " + path);
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

            // ✅ ИСПРАВЛЕНО: парсим HEX строку "FF00FF88"
            int color = (int) Long.parseLong(parts[1], 16);

            MainActivity activity = getActivity();
            if (activity != null) {
                activity.markConnected();
                activity.updatePhase(phaseText, color);
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Ошибка парсинга цвета: " + parts[1], e);
        }
    }
    private void handleRepCount(String repText) {
        MainActivity activity = getActivity();
        if (activity != null) {
            activity.updateRepCount(repText);
        }
    }

    private void handleError(String errorText) {
        Log.d(TAG, "Ошибка: " + errorText);
        MainActivity activity = getActivity();
        if (activity != null) {
            activity.markConnected();
            activity.updateError(errorText);
        }
    }

    private void handleReset() {
        Log.d(TAG, "Сброс");
        MainActivity activity = getActivity();
        if (activity != null) {
            activity.resetDisplay();
        }
    }
}