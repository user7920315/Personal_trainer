package ru.sv.wear;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.activity.ComponentActivity;

public class MainActivity extends ComponentActivity {

    private static final String TAG = "WearMainActivity";

    private TextView tvPhase;
    private TextView tvError;
    private TextView tvConnection;
    private TextView tvRepCount;   // ✅ НОВОЕ — счётчик повторений

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvPhase      = findViewById(R.id.tvPhase);
        tvError      = findViewById(R.id.tvError);
        tvConnection = findViewById(R.id.tvConnection);
        tvRepCount   = findViewById(R.id.tvRepCount);  // ✅ НОВОЕ

        WearDataListenerService.setMainActivity(this);

        tvPhase.setText("⏳ Поиск...");
        tvPhase.setTextColor(0xFFAAAAAA);
        tvError.setVisibility(View.GONE);
        tvConnection.setText("○○○");
        tvConnection.setTextColor(0xFFFF5555);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // ✅ Очищаем ссылку при уничтожении
        WearDataListenerService.setMainActivity(null);
        Log.d(TAG, "onDestroy");
    }

    public void markConnected() {
        runOnUiThread(() -> {
            tvConnection.setText("●●●");
            tvConnection.setTextColor(0xFF00FF88);
            if (tvPhase.getText().toString().contains("Поиск")) {
                tvPhase.setText("ГОТОВ");
                tvPhase.setTextColor(0xFFFFFFFF);
            }
        });
    }

    public void updatePhase(String phase, int color) {
        runOnUiThread(() -> {
            if (tvPhase != null) {
                tvPhase.setText(phase);
                tvPhase.setTextColor(color);
            }
        });
    }

    public void updateError(String error) {
        runOnUiThread(() -> {
            if (tvError == null) return;
            if (error != null && !error.isEmpty()) {
                tvError.setText(error);
                tvError.setVisibility(View.VISIBLE);
                tvError.setTextColor(Color.parseColor("#FF5555"));
            } else {
                tvError.setVisibility(View.GONE);
            }
        });
    }

    // ✅ НОВОЕ — обновление счётчика
    public void updateRepCount(String repText) {
        runOnUiThread(() -> {
            if (tvRepCount != null) {
                tvRepCount.setText(repText);
            }
        });
    }

    public void resetDisplay() {
        runOnUiThread(() -> {
            if (tvPhase      != null) { tvPhase.setText("ГОТОВ");      tvPhase.setTextColor(Color.WHITE); }
            if (tvError      != null) { tvError.setVisibility(View.GONE); }
            if (tvConnection != null) { tvConnection.setText("●●●");   tvConnection.setTextColor(0xFF00FF88); }
            if (tvRepCount   != null) { tvRepCount.setText("0");        } // ✅
        });
    }
}