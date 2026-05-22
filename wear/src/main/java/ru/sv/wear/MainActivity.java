package ru.sv.wear;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.ComponentActivity;

import ru.sv.wear.databinding.ActivityMainBinding;

public class MainActivity extends ComponentActivity {

    private static final String TAG = "WearMainActivity";

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        WearDataListenerService.setMainActivity(this);

        binding.tvPhase.setText("⏳ Поиск...");
        binding.tvPhase.setTextColor(0xFFAAAAAA);
        binding.tvError.setVisibility(android.view.View.GONE);
        binding.tvConnection.setText("○○○");
        binding.tvConnection.setTextColor(0xFFFF5555);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        WearDataListenerService.setMainActivity(null);
        Log.d(TAG, "onDestroy");
        binding = null;
    }

    public void markConnected() {
        runOnUiThread(() -> {
            binding.tvConnection.setText("●●●");
            binding.tvConnection.setTextColor(0xFF00FF88);
            if (binding.tvPhase.getText().toString().contains("Поиск")) {
                binding.tvPhase.setText("ГОТОВ");
                binding.tvPhase.setTextColor(0xFFFFFFFF);
            }
        });
    }

    public void updatePhase(String phase, int color) {
        runOnUiThread(() -> {
            if (binding != null) {
                binding.tvPhase.setText(phase);
                binding.tvPhase.setTextColor(color);
            }
        });
    }

    public void updateError(String error) {
        runOnUiThread(() -> {
            if (binding == null) return;
            if (error != null && !error.isEmpty()) {
                binding.tvError.setText(error);
                binding.tvError.setVisibility(android.view.View.VISIBLE);
                binding.tvError.setTextColor(Color.parseColor("#FF5555"));
            } else {
                binding.tvError.setVisibility(android.view.View.GONE);
            }
        });
    }

    public void updateRepCount(String repText) {
        runOnUiThread(() -> {
            if (binding != null) {
                binding.tvRepCount.setText(repText);
            }
        });
    }

    public void resetDisplay() {
        runOnUiThread(() -> {
            if (binding == null) return;
            binding.tvPhase.setText("ГОТОВ");
            binding.tvPhase.setTextColor(Color.WHITE);
            binding.tvError.setVisibility(android.view.View.GONE);
            binding.tvConnection.setText("●●●");
            binding.tvConnection.setTextColor(0xFF00FF88);
            binding.tvRepCount.setText("0");
        });
    }
}