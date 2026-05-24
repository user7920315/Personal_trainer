package ru.sv.personaltrainer;

import android.content.Intent;
import android.os.Bundle;
import android.widget.MediaController;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import ru.sv.personaltrainer.databinding.ActivityExerciseDetailBinding;
import ru.sv.personaltrainer.model.ExerciseInfo;
import ru.sv.personaltrainer.viewmodel.ExerciseDetailViewModel;

public class ExerciseDetailActivity extends AppCompatActivity {

    private ActivityExerciseDetailBinding binding;
    private ExerciseDetailViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        binding = ActivityExerciseDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollViewDetail, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() |
                            WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, 0, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        ViewCompat.setOnApplyWindowInsetsListener(binding.btnDetailBack, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() |
                            WindowInsetsCompat.Type.displayCutout());
            android.widget.FrameLayout.LayoutParams params =
                    (android.widget.FrameLayout.LayoutParams) v.getLayoutParams();
            params.topMargin = bars.top + (int) (12 * getResources().getDisplayMetrics().density);
            v.setLayoutParams(params);
            return WindowInsetsCompat.CONSUMED;
        });

        String exerciseId = getIntent().getStringExtra("EXERCISE_ID");
        if (exerciseId == null) {
            finish();
            return;
        }

        viewModel = new ViewModelProvider(this).get(ExerciseDetailViewModel.class);
        viewModel.loadExercise(exerciseId);

        viewModel.getExerciseInfo().observe(this, info -> {
            if (info == null) {
                finish();
                return;
            }
            fillData(info);
            setupVideo(info);
            setupButtons(exerciseId);
        });
    }

    private void fillData(ExerciseInfo info) {
        binding.tvDetailTitle.setText(info.getEmoji() + " " + info.getTitle());
        binding.tvDetailMeta.setText(info.getMuscleGroup() + "  •  " + info.getDifficulty());
        binding.tvDetailDescription.setText(info.getDescription());
    }

    private void setupVideo(ExerciseInfo info) {
        String videoFile = info.getVideoFileName();
        try {
            String path = "file:///android_asset/videos/" + videoFile;
            binding.videoView.setVideoPath(path);

            MediaController mediaController = new MediaController(this);
            mediaController.setAnchorView(binding.videoView);
            binding.videoView.setMediaController(mediaController);

            binding.videoView.setOnPreparedListener(mp -> {
                mp.setLooping(true);
                mp.start();
            });

            binding.videoView.setOnErrorListener((mp, what, extra) -> {
                binding.videoView.setVisibility(android.view.View.GONE);
                return true;
            });

            binding.videoView.requestFocus();
        } catch (Exception e) {
            binding.videoView.setVisibility(android.view.View.GONE);
        }
    }

    private void setupButtons(String exerciseId) {
        binding.btnStartExercise.setOnClickListener(v -> {
            Intent intent = new Intent(this, ExerciseActivity.class);
            intent.putExtra("EXERCISE_ID", exerciseId);
            startActivity(intent);
        });
        binding.btnDetailBack.setOnClickListener(v -> finish());
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (binding.videoView.isPlaying()) binding.videoView.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.videoView.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding.videoView.stopPlayback();
        binding = null;
    }
}