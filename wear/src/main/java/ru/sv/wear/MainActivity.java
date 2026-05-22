package ru.sv.wear;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.ComponentActivity;
import androidx.lifecycle.ViewModelProvider;

import ru.sv.wear.databinding.ActivityMainBinding;
import ru.sv.wear.viewmodel.WearViewModel;

public class MainActivity extends ComponentActivity {

    private static final String TAG = "WearMainActivity";

    private ActivityMainBinding binding;
    private WearViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(WearViewModel.class);
        viewModel.initRepository();

        WearDataListenerService.setRepository(viewModel.getRepository());

        viewModel.getPhaseText().observe(this, text -> {
            binding.tvPhase.setText(text);
        });

        viewModel.getPhaseColor().observe(this, color -> {
            binding.tvPhase.setTextColor(color);
        });

        viewModel.getErrorText().observe(this, text -> {
            binding.tvError.setText(text);
        });

        viewModel.getErrorVisible().observe(this, visible -> {
            binding.tvError.setVisibility(visible ? android.view.View.VISIBLE : android.view.View.GONE);
            if (visible) {
                binding.tvError.setTextColor(Color.parseColor("#FF5555"));
            }
        });

        viewModel.getRepCount().observe(this, text -> {
            binding.tvRepCount.setText(text);
        });

        viewModel.getConnectionStatus().observe(this, status -> {
            binding.tvConnection.setText(status);
        });

        viewModel.getConnectionColor().observe(this, color -> {
            binding.tvConnection.setTextColor(color);
        });

        binding.tvPhase.setText("⏳ Поиск...");
        binding.tvPhase.setTextColor(0xFFAAAAAA);
        binding.tvError.setVisibility(android.view.View.GONE);
        binding.tvConnection.setText("○○○");
        binding.tvConnection.setTextColor(0xFFFF5555);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        WearDataListenerService.setRepository(null);
        Log.d(TAG, "onDestroy");
        binding = null;
    }
}