package ru.sv.personaltrainer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

import ru.sv.personaltrainer.exercises.ExerciseRegistry;
import ru.sv.personaltrainer.model.ExerciseInfo;

public class ExerciseDetailActivity extends AppCompatActivity {

    private VideoView   videoView;
    private TextView    tvTitle;
    private TextView    tvMeta;
    private TextView    tvDescription;
    private Button      btnStartExercise;
    private View        btnBack;

    private ExerciseInfo exerciseInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exercise_detail);

        // Получаем ID упражнения из Intent
        String exerciseId = getIntent().getStringExtra("EXERCISE_ID");
        if (exerciseId == null) { finish(); return; }

        // Ищем ExerciseInfo
        exerciseInfo = findExerciseInfo(exerciseId);
        if (exerciseInfo == null) { finish(); return; }

        initViews();
        fillData();
        setupVideo();
        setupButtons(exerciseId);
    }

    // ── Поиск ExerciseInfo по ID ─────────────────────────────────────
    private ExerciseInfo findExerciseInfo(String id) {
        for (ExerciseInfo info : ExerciseRegistry.getAll()) {
            if (info.getId().equals(id)) return info;
        }
        return null;
    }

    // ── Инициализация вью ────────────────────────────────────────────
    private void initViews() {
        videoView        = findViewById(R.id.videoView);
        tvTitle          = findViewById(R.id.tvDetailTitle);
        tvMeta           = findViewById(R.id.tvDetailMeta);
        tvDescription    = findViewById(R.id.tvDetailDescription);
        btnStartExercise = findViewById(R.id.btnStartExercise);
        btnBack          = findViewById(R.id.btnDetailBack);
    }

    // ── Заполнение данными ───────────────────────────────────────────
    private void fillData() {
        tvTitle.setText(
                exerciseInfo.getEmoji() + " " + exerciseInfo.getTitle());

        tvMeta.setText(
                exerciseInfo.getMuscleGroup()
                        + "  •  " + exerciseInfo.getDifficulty());

        tvDescription.setText(exerciseInfo.getDescription());
    }

    // ── Настройка видео из assets ────────────────────────────────────
    private void setupVideo() {
        String videoFile = exerciseInfo.getVideoFileName();

        try {
            // Путь к видео в папке assets/videos/
            Uri videoUri = Uri.parse(
                    "android.resource://asset/videos/" + videoFile);

            // Корректный способ воспроизведения из assets
            String path = "file:///android_asset/videos/" + videoFile;

            videoView.setVideoPath(path);

            MediaController mediaController =
                    new MediaController(this);
            mediaController.setAnchorView(videoView);
            videoView.setMediaController(mediaController);

            // Автозапуск с зацикливанием
            videoView.setOnPreparedListener(mp -> {
                mp.setLooping(true);
                mp.start();
            });

            // Если видео не найдено — скрываем плеер
            videoView.setOnErrorListener((mp, what, extra) -> {
                videoView.setVisibility(View.GONE);
                return true;
            });

            videoView.requestFocus();

        } catch (Exception e) {
            // Файл не найден — скрываем видео
            videoView.setVisibility(View.GONE);
        }
    }

    // ── Кнопки ───────────────────────────────────────────────────────
    private void setupButtons(String exerciseId) {

        // Запустить тренировку
        btnStartExercise.setOnClickListener(v -> {
            Intent intent = new Intent(this, ExerciseActivity.class);
            intent.putExtra("EXERCISE_ID", exerciseId);
            startActivity(intent);
        });

        // Назад к списку
        btnBack.setOnClickListener(v -> finish());
    }

    // ── Пауза видео при сворачивании ────────────────────────────────
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