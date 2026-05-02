package ru.sv.personaltrainer;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;

import ru.sv.personaltrainer.exercises.BaseExercise;
import ru.sv.personaltrainer.exercises.ExerciseRegistry;
import ru.sv.personaltrainer.overlay.PoseOverlayView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExerciseActivity extends AppCompatActivity {

    private static final String TAG        = "ExerciseActivity";
    private static final String MODEL_FILE = "pose_landmarker_full.task";

    // ── UI элементы ───────────────────────────────────
    private PreviewView   previewView;
    private PoseOverlayView poseOverlay;
    private TextView      tvRepCount;
    private TextView      tvExerciseName;
    private TextView      tvFeedback;
    private TextView      tvErrors;
    private TextView      tvPhase;
    private TextView      tvQuality;
    private LinearLayout  layoutErrors;
    private Button        btnBack;
    private Button        btnReset;

    // ── MediaPipe ─────────────────────────────────────
    private PoseLandmarker poseLandmarker;

    // ── Упражнение ────────────────────────────────────
    private BaseExercise currentExercise;
    private String       exerciseId;

    // ── Потоки ───────────────────────────────────────
    private ExecutorService cameraExecutor;
    private final Handler   mainHandler = new Handler(Looper.getMainLooper());

    // ═════════════════════════════════════════════════
    //  onCreate
    // ═════════════════════════════════════════════════
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exercise);

        // Получаем данные из Intent
        exerciseId = getIntent().getStringExtra("EXERCISE_ID");
        if (exerciseId == null) exerciseId = "SQUAT"; // fallback

        // Создаём упражнение через реестр
        try {
            currentExercise = ExerciseRegistry.createExercise(exerciseId);
        } catch (Exception e) {
            Log.e(TAG, "Ошибка создания упражнения: " + e.getMessage());
            finish();
            return;
        }

        initViews();
        initMediaPipe();

        cameraExecutor = Executors.newSingleThreadExecutor();
        startCamera();
    }

    // ═════════════════════════════════════════════════
    //  Инициализация UI
    // ═════════════════════════════════════════════════
    private void initViews() {
        previewView    = findViewById(R.id.previewView);
        poseOverlay    = findViewById(R.id.poseOverlay);
        tvRepCount     = findViewById(R.id.tvRepCount);
        tvExerciseName = findViewById(R.id.tvExerciseName);
        tvFeedback     = findViewById(R.id.tvFeedback);
        tvErrors       = findViewById(R.id.tvErrors);
        tvPhase        = findViewById(R.id.tvPhase);
        tvQuality      = findViewById(R.id.tvQuality);
        layoutErrors   = findViewById(R.id.layoutErrors);
        btnBack        = findViewById(R.id.btnBack);
        btnReset       = findViewById(R.id.btnReset);

        // Название упражнения
        tvExerciseName.setText(currentExercise.getName());

        // Кнопка назад
        btnBack.setOnClickListener(v -> finish());

        // Кнопка сброс счётчика
        btnReset.setOnClickListener(v -> {
            currentExercise.reset();
            tvRepCount.setText("Повторений: 0");
            tvFeedback.setText("Встаньте в кадр для начала");
            tvPhase.setText("● ГОТОВ");
            tvPhase.setTextColor(0xFFFFFFFF);
            tvQuality.setText("●●●●●");
            tvQuality.setTextColor(0xFF00FF88);
            layoutErrors.setVisibility(View.GONE);
        });
    }

    // ═════════════════════════════════════════════════
    //  Инициализация MediaPipe
    // ═════════════════════════════════════════════════
    private void initMediaPipe() {
        try {
            BaseOptions baseOptions = BaseOptions.builder()
                    .setModelAssetPath(MODEL_FILE)
                    .build();

            PoseLandmarker.PoseLandmarkerOptions options =
                    PoseLandmarker.PoseLandmarkerOptions.builder()
                            .setBaseOptions(baseOptions)
                            .setRunningMode(RunningMode.LIVE_STREAM)
                            .setNumPoses(1)
                            .setMinPoseDetectionConfidence(0.5f)
                            .setMinPosePresenceConfidence(0.5f)
                            .setMinTrackingConfidence(0.5f)
                            .setResultListener(this::onPoseResult)
                            .setErrorListener(e ->
                                    Log.e(TAG, "MediaPipe error: " + e.getMessage()))
                            .build();

            poseLandmarker = PoseLandmarker.createFromOptions(this, options);
            Log.d(TAG, "PoseLandmarker инициализирован успешно");

        } catch (Exception e) {
            Log.e(TAG, "Ошибка инициализации MediaPipe: " + e.getMessage());
            Toast.makeText(this,
                    "Ошибка загрузки модели AI: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            finish();
        }
    }

    // ═════════════════════════════════════════════════
    //  Запуск камеры
    // ═════════════════════════════════════════════════
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();

                // Preview
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // Анализ кадров
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(
                                ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(
                                ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeFrame);

                // Используем фронтальную камеру
                CameraSelector cameraSelector =
                        CameraSelector.DEFAULT_FRONT_CAMERA;

                provider.unbindAll();
                provider.bindToLifecycle(
                        this,
                        cameraSelector,
                        preview,
                        imageAnalysis
                );

                Log.d(TAG, "Камера запущена");

            } catch (Exception e) {
                Log.e(TAG, "Ошибка запуска камеры: " + e.getMessage());
                mainHandler.post(() ->
                        Toast.makeText(this,
                                "Ошибка камеры: " + e.getMessage(),
                                Toast.LENGTH_LONG).show()
                );
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // ═════════════════════════════════════════════════
    //  Анализ кадра с камеры
    // ═════════════════════════════════════════════════
    private void analyzeFrame(ImageProxy imageProxy) {
        try {
            // Получаем поворот кадра от сенсора камеры
            int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();

            // Конвертируем в Bitmap
            Bitmap bitmap = imageProxy.toBitmap();

            // Поворачиваем и зеркалим для фронтальной камеры
            Matrix matrix = new Matrix();
            matrix.postRotate(rotationDegrees);
            matrix.postScale(
                    -1f, 1f,
                    bitmap.getWidth() / 2f,
                    bitmap.getHeight() / 2f
            );

            Bitmap rotatedBitmap = Bitmap.createBitmap(
                    bitmap,
                    0, 0,
                    bitmap.getWidth(),
                    bitmap.getHeight(),
                    matrix,
                    true
            );

            // Отправляем в MediaPipe
            MPImage mpImage = new BitmapImageBuilder(rotatedBitmap).build();
            long frameTime = imageProxy.getImageInfo().getTimestamp();
            poseLandmarker.detectAsync(mpImage, frameTime);

        } catch (Exception e) {
            Log.e(TAG, "Ошибка анализа кадра: " + e.getMessage());
        } finally {
            imageProxy.close();
        }
    }

    // ═════════════════════════════════════════════════
    //  Получение результата от MediaPipe
    // ═════════════════════════════════════════════════
    private void onPoseResult(PoseLandmarkerResult result, MPImage input) {

        // Человек не найден в кадре
        if (result == null || result.landmarks().isEmpty()) {
            mainHandler.post(() -> {
                tvFeedback.setText("Встаньте полностью в кадр");
                tvPhase.setText("● ПОИСК...");
                tvPhase.setTextColor(0xFFAAAAAA);
                layoutErrors.setVisibility(View.GONE);
                poseOverlay.updateResults(null, 0, 0, null);
            });
            return;
        }

        // Анализируем позу через текущее упражнение
        BaseExercise.AnalysisResult analysis =
                currentExercise.analyze(result.landmarks().get(0));

        // Обновляем UI в главном потоке
        mainHandler.post(() -> updateUI(result, analysis));
    }

    // ═════════════════════════════════════════════════
    //  Обновление UI
    // ═════════════════════════════════════════════════
    private void updateUI(PoseLandmarkerResult result,
                          BaseExercise.AnalysisResult analysis) {

        // Счётчик повторений
        tvRepCount.setText("Повторений: " + analysis.repCount);

        // Главная подсказка
        tvFeedback.setText(analysis.mainFeedback);

        // Фаза движения
        updatePhaseIndicator(analysis.phase);

        // Индикатор качества техники
        updateQualityIndicator(analysis.errors.size());

        // Дополнительные ошибки
        updateErrorsBlock(analysis);

        // Обновляем отрисовку скелета
        poseOverlay.updateResults(
                result,
                previewView.getWidth(),
                previewView.getHeight(),
                analysis.errorLandmarks
        );
    }

    // ── Индикатор фазы ────────────────────────────────
    private void updatePhaseIndicator(String phase) {
        switch (phase) {
            case "DOWN":
                tvPhase.setText("▼ ВНИЗ");
                tvPhase.setTextColor(0xFF00FF88);
                break;
            case "UP":
                tvPhase.setText("▲ ВВЕРХ");
                tvPhase.setTextColor(0xFF00AAFF);
                break;
            case "HOLD":
                tvPhase.setText("⏸ ДЕРЖИ");
                tvPhase.setTextColor(0xFFFFAA00);
                break;
            default:
                tvPhase.setText("● ГОТОВ");
                tvPhase.setTextColor(0xFFFFFFFF);
        }
    }

    // ── Индикатор качества ────────────────────────────
    private void updateQualityIndicator(int errorCount) {
        String quality;
        int    color;

        if (errorCount == 0) {
            quality = "●●●●●";
            color   = 0xFF00FF88; // зелёный — отлично
        } else if (errorCount == 1) {
            quality = "●●●●○";
            color   = 0xFF88FF00; // светло-зелёный
        } else if (errorCount == 2) {
            quality = "●●●○○";
            color   = 0xFFFFFF00; // жёлтый
        } else if (errorCount == 3) {
            quality = "●●○○○";
            color   = 0xFFFF8800; // оранжевый
        } else {
            quality = "●○○○○";
            color   = 0xFFFF0000; // красный — много ошибок
        }

        tvQuality.setText(quality);
        tvQuality.setTextColor(color);
    }

    // ── Блок дополнительных ошибок ────────────────────
    private void updateErrorsBlock(BaseExercise.AnalysisResult analysis) {
        if (analysis.errors.size() > 1) {
            layoutErrors.setVisibility(View.VISIBLE);

            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < analysis.errors.size(); i++) {
                sb.append(analysis.errors.get(i));
                if (i < analysis.errors.size() - 1) {
                    sb.append("\n");
                }
            }
            tvErrors.setText(sb.toString());
        } else {
            layoutErrors.setVisibility(View.GONE);
        }
    }

    // ═════════════════════════════════════════════════
    //  Lifecycle
    // ═════════════════════════════════════════════════
    @Override
    protected void onPause() {
        super.onPause();

        // Сохраняем тренировку если были повторения
        if (currentExercise != null && currentExercise.getRepCount() > 0) {
            String icon = "💪";
            if (exerciseId.equals("SQUAT"))  icon = "🏋";
            if (exerciseId.equals("PLANK"))  icon = "🧘";

            ProfileActivity.saveWorkout(
                    this,
                    exerciseId,
                    currentExercise.getName(),
                    icon,
                    currentExercise.getRepCount()
            );
        }

        if (currentExercise != null) {
            currentExercise.reset();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Останавливаем камеру
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }

        // Закрываем MediaPipe
        if (poseLandmarker != null) {
            poseLandmarker.close();
        }

        Log.d(TAG, "ExerciseActivity уничтожена");
    }
}