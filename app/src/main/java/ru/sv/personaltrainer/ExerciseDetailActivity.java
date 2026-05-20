package ru.sv.personaltrainer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import ru.sv.personaltrainer.exercises.ExerciseRegistry;
import ru.sv.personaltrainer.model.ExerciseInfo;

public class ExerciseDetailActivity extends AppCompatActivity {

    private VideoView videoView;
    private TextView tvTitle;
    private TextView tvMeta;
    private TextView tvDescription;
    private Button btnStartExercise;
    private View btnBack;

    private ExerciseInfo exerciseInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exercise_detail);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.scrollViewDetail), (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() |
                            WindowInsetsCompat.Type.displayCutout());
            // Видео остаётся edge-to-edge, а контент прокручивается над navigation bar
            v.setPadding(bars.left, 0, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.btnDetailBack), (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() |
                            WindowInsetsCompat.Type.displayCutout());
            // 12dp из оригинального margin + высота статус-бара
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) v.getLayoutParams();
            params.topMargin = bars.top + (int) (12 * getResources().getDisplayMetrics().density);
            v.setLayoutParams(params);
            return WindowInsetsCompat.CONSUMED;
        });

        String exerciseId = getIntent().getStringExtra("EXERCISE_ID");
        if (exerciseId == null) {
            finish();
            return;
        }

        exerciseInfo = findExerciseInfo(exerciseId);
        if (exerciseInfo == null) {
            finish();
            return;
        }

        initViews();
        fillData();
        setupVideo();
        setupButtons(exerciseId);
    }

    private ExerciseInfo findExerciseInfo(String id) {
        for (ExerciseInfo info : ExerciseRegistry.getAll()) {
            if (info.getId().equals(id)) return info;
        }
        return null;
    }

    private void initViews() {
        videoView = findViewById(R.id.videoView);
        tvTitle = findViewById(R.id.tvDetailTitle);
        tvMeta = findViewById(R.id.tvDetailMeta);
        tvDescription = findViewById(R.id.tvDetailDescription);
        btnStartExercise = findViewById(R.id.btnStartExercise);
        btnBack = findViewById(R.id.btnDetailBack);
    }

    private void fillData() {
        tvTitle.setText(
                exerciseInfo.getEmoji() + " " + exerciseInfo.getTitle());

        tvMeta.setText(
                exerciseInfo.getMuscleGroup()
                        + "  •  " + exerciseInfo.getDifficulty());

        tvDescription.setText(exerciseInfo.getDescription());
    }

    private void setupVideo() {
        String videoFile = exerciseInfo.getVideoFileName();

        try {
            Uri videoUri = Uri.parse(
                    "android.resource://asset/videos/" + videoFile);

            String path = "file:///android_asset/videos/" + videoFile;

            videoView.setVideoPath(path);

            MediaController mediaController =
                    new MediaController(this);
            mediaController.setAnchorView(videoView);
            videoView.setMediaController(mediaController);

            videoView.setOnPreparedListener(mp -> {
                mp.setLooping(true);
                mp.start();
            });

            videoView.setOnErrorListener((mp, what, extra) -> {
                videoView.setVisibility(View.GONE);
                return true;
            });

            videoView.requestFocus();

        } catch (Exception e) {
            videoView.setVisibility(View.GONE);
        }
    }

    private void setupButtons(String exerciseId) {

        btnStartExercise.setOnClickListener(v -> {
            Intent intent = new Intent(this, ExerciseActivity.class);
            intent.putExtra("EXERCISE_ID", exerciseId);
            startActivity(intent);
        });

        btnBack.setOnClickListener(v -> finish());
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoView != null && videoView.isPlaying()) {
            videoView.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (videoView != null) {
            videoView.start();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (videoView != null) videoView.stopPlayback();
    }
}