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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.sv.personaltrainer.exercises.BaseExercise;

public class PoseRepository {

    private static final String MODEL_FILE = "pose_landmarker_full.task";

    private final PoseLandmarker poseLandmarker;
    private final BaseExercise currentExercise;
    private final ExecutorService cameraExecutor;
    private final Handler mainHandler;
    private final ErrorDebouncer errorDebouncer;

    public interface PoseCallback {
        void onAnalysis(BaseExercise.AnalysisResult analysis, PoseLandmarkerResult poseResult);
    }

    private PoseCallback callback;

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
                if (callback != null) callback.onAnalysis(null, null);
            });
            return;
        }

        BaseExercise.AnalysisResult rawAnalysis = currentExercise.analyze(result.landmarks().get(0));
        long nowMs = System.currentTimeMillis();
        rawAnalysis.errors = errorDebouncer.filter(rawAnalysis.errors, nowMs);

        mainHandler.post(() -> {
            if (callback != null) callback.onAnalysis(rawAnalysis, result);
        });
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