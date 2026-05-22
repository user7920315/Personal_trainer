package ru.sv.personaltrainer.repository;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Handler;
import android.os.Looper;

import androidx.camera.core.ImageProxy;

import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.sv.personaltrainer.exercises.BaseExercise;
import ru.sv.personaltrainer.model.WorkoutResult;

public class PoseRepository {

    private static final String MODEL_FILE = "pose_landmarker_full.task";

    private final PoseLandmarker poseLandmarker;
    private final BaseExercise currentExercise;
    private final ExecutorService cameraExecutor;
    private final Handler mainHandler;
    private final ErrorDebouncer errorDebouncer;

    public interface PoseCallback {
        void onResult(WorkoutResult result);
    }

    private PoseCallback callback;
    private String lastRepText = "Повторений: 0";

    public PoseRepository(Context context, BaseExercise exercise) throws Exception {
        this.currentExercise = exercise;
        this.cameraExecutor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.errorDebouncer = new ErrorDebouncer();
        this.poseLandmarker = createPoseLandmarker(context);
    }

    private PoseLandmarker createPoseLandmarker(Context context) throws Exception {
        BaseOptions base = BaseOptions.builder().setModelAssetPath(MODEL_FILE).build();
        PoseLandmarker.PoseLandmarkerOptions opts = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(base)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumPoses(1)
                .setMinPoseDetectionConfidence(0.5f)
                .setMinPosePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setResultListener((result, image) -> onPoseResultInternal(result))
                .setErrorListener(e -> {})
                .build();
        return PoseLandmarker.createFromOptions(context, opts);
    }

    public void setCallback(PoseCallback callback) {
        this.callback = callback;
    }

    public void analyzeFrame(ImageProxy imageProxy) {
        cameraExecutor.execute(() -> {
            try {
                int rotation = imageProxy.getImageInfo().getRotationDegrees();
                long tsNs = imageProxy.getImageInfo().getTimestamp();
                Bitmap bitmap = imageProxy.toBitmap();

                Matrix matrix = new Matrix();
                matrix.postRotate(rotation);
                matrix.postScale(-1f, 1f, bitmap.getWidth() / 2f, bitmap.getHeight() / 2f);
                Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

                poseLandmarker.detectAsync(new BitmapImageBuilder(rotated).build(), tsNs);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                imageProxy.close();
            }
        });
    }

    private void onPoseResultInternal(PoseLandmarkerResult result) {
        if (result == null || result.landmarks().isEmpty()) {
            mainHandler.post(() -> {
                if (callback != null) {
                    WorkoutResult r = new WorkoutResult();
                    r.mainFeedback = "Встаньте полностью в кадр";
                    r.phaseText = "● ПОИСК...";
                    r.phaseColor = 0xFFAAAAAA;
                    r.repText = lastRepText;
                    r.qualityText = "●●●●●";
                    r.qualityColor = 0xFF00FF88;
                    r.errors = new ArrayList<>();
                    r.errorLandmarks = new ArrayList<>();
                    r.poseResult = null;
                    callback.onResult(r);
                }
            });
            return;
        }

        BaseExercise.AnalysisResult rawAnalysis = currentExercise.analyze(result.landmarks().get(0));
        long nowMs = System.currentTimeMillis();
        List<String> filteredErrors = errorDebouncer.filter(rawAnalysis.errors, nowMs);
        WorkoutResult workoutResult = buildWorkoutResult(rawAnalysis, filteredErrors, result);

        lastRepText = workoutResult.repText;

        mainHandler.post(() -> {
            if (callback != null) callback.onResult(workoutResult);
        });
    }

    private WorkoutResult buildWorkoutResult(BaseExercise.AnalysisResult raw,
                                             List<String> filteredErrors,
                                             PoseLandmarkerResult poseResult) {
        WorkoutResult r = new WorkoutResult();
        r.poseResult = poseResult;
        r.phase = raw.phase;
        r.repCount = raw.repCount;
        r.holdSeconds = raw.holdSeconds;

        if (filteredErrors.isEmpty()) {
            r.mainFeedback = raw.mainFeedback.startsWith("⚠")
                    ? buildPositiveFeedback(raw.phase) : raw.mainFeedback;
            r.errors = new ArrayList<>();
            r.errorLandmarks = new ArrayList<>();
        } else {
            r.errors = new ArrayList<>(filteredErrors);
            r.errorLandmarks = new ArrayList<>(raw.errorLandmarks);
            r.mainFeedback = filteredErrors.get(0);
        }

        r.repText = (r.holdSeconds >= 0)
                ? formatHoldTime(r.holdSeconds)
                : "Повторений: " + r.repCount;
        r.phaseText = phaseToText(r.phase);
        r.phaseColor = phaseToColor(r.phase);
        r.qualityText = qualityToText(r.errors.size());
        r.qualityColor = qualityToColor(r.errors.size());

        return r;
    }

    private String formatHoldTime(int seconds) {
        return seconds == 0 ? "⏱ Время: 0с" : "⏱ Время: " + seconds + "с";
    }

    private String buildPositiveFeedback(String phase) {
        switch (phase) {
            case "DOWN": return "✅ Хорошо! Держите позицию";
            case "UP": return "✅ Отлично!";
            case "HOLD": return "✅ Держите!";
            default: return "✅ Начните упражнение";
        }
    }

    private String phaseToText(String phase) {
        switch (phase) {
            case "DOWN": return "▼ ВНИЗ";
            case "UP": return "▲ ВВЕРХ";
            case "HOLD": return "⏸ ДЕРЖИ";
            default: return "● ГОТОВ";
        }
    }

    private int phaseToColor(String phase) {
        switch (phase) {
            case "DOWN": return 0xFF00FF88;
            case "UP": return 0xFF00AAFF;
            case "HOLD": return 0xFFFFAA00;
            default: return 0xFFFFFFFF;
        }
    }

    private String qualityToText(int errorCount) {
        if (errorCount == 0) return "●●●●●";
        if (errorCount == 1) return "●●●●○";
        if (errorCount == 2) return "●●●○○";
        if (errorCount == 3) return "●●○○○";
        return "●○○○○";
    }

    private int qualityToColor(int errorCount) {
        if (errorCount == 0) return 0xFF00FF88;
        if (errorCount == 1) return 0xFF88FF00;
        if (errorCount == 2) return 0xFFFFFF00;
        if (errorCount == 3) return 0xFFFF8800;
        return 0xFFFF0000;
    }

    public void resetExercise() {
        currentExercise.reset();
        errorDebouncer.reset();
    }

    public void shutdown() {
        cameraExecutor.shutdown();
        if (poseLandmarker != null) poseLandmarker.close();
    }
}