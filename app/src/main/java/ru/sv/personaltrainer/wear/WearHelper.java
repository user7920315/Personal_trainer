package ru.sv.personaltrainer.wear;

import android.content.Context;
import android.util.Log;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

public class WearHelper {

    private static final String TAG = "WearHelper";
    public static final String PATH_ERROR = "/exercise/error";
    public static final String PATH_PHASE = "/exercise/phase";
    public static final String PATH_RESET = "/exercise/reset";

    private final Context context;
    private final MessageClient messageClient;
    private boolean apiReady = false;
    public static final String PATH_REPS  = "/exercise/reps";

    public WearHelper(Context context) {
        this.context = context.getApplicationContext();
        this.messageClient = Wearable.getMessageClient(this.context);
        checkApi();
    }

    private void checkApi() {
        GoogleApiAvailability api = GoogleApiAvailability.getInstance();
        int status = api.isGooglePlayServicesAvailable(context);
        if (status == ConnectionResult.SUCCESS) {
            apiReady = true;
            Log.d(TAG, "✅ Wear API доступен");
        } else {
            apiReady = false;
            Log.w(TAG, "⚠️ Wear API недоступен (код: " + status + "). Ожидание сопряжения или реального устройства...");
        }
    }

    public boolean isAvailable() { return apiReady; }

    public void sendRepCount(String repText) {
        if (!apiReady) return;
        send(PATH_REPS, repText != null ? repText : "0");
    }

    public void sendPhase(String text, int color) {
        if (!apiReady) return;
        String hexColor = String.format("%08X", color);
        send(PATH_PHASE, text + "|" + hexColor);
    }

    public void sendError(String text) {
        if (!apiReady) return;
        send(PATH_ERROR, text != null ? text : "");
    }

    public void sendReset() {
        if (!apiReady) return;
        send(PATH_RESET, "reset");
    }

    private void send(String path, String msg) {
        Wearable.getNodeClient(context).getConnectedNodes()
                .addOnSuccessListener(nodes -> {
                    if (nodes.isEmpty()) {
                        Log.w(TAG, "⌚ Нет подключенных узлов");
                        return;
                    }
                    for (Node node : nodes) {
                        messageClient.sendMessage(node.getId(), path, msg.getBytes());
                    }
                });

                //.addOnFailureListener(e -> Log.e(TAG, "Ошибка сети Wear", e));
    }
}