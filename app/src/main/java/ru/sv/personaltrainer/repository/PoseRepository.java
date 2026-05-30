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

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import ru.sv.personaltrainer.exercises.BaseExercise;

public class PoseRepository {

    private static final String MODEL_FILE = "pose_landmarker_full.task";

    private final PoseLandmarker      poseLandmarker;
    private final BaseExercise        currentExercise;
    private final ExecutorService     cameraExecutor;
    private final Handler             mainHandler;
    private final ErrorDebouncer      errorDebouncer;

    private Bitmap       lastFrameBitmap;
    private final Object bitmapLock = new Object();

    private final AtomicLong lastTimestampUs = new AtomicLong(0L);

    public interface PoseCallback {
        void onAnalysis(BaseExercise.AnalysisResult analysis,
                        PoseLandmarkerResult poseResult,
                        Bitmap frameBitmap);
    }

    private PoseCallback callback;

    public PoseRepository(Context context, BaseExercise exercise) throws Exception {
        this.currentExercise = exercise;
        this.cameraExecutor  = Executors.newSingleThreadExecutor();
        this.mainHandler     = new Handler(Looper.getMainLooper());
        this.errorDebouncer  = new ErrorDebouncer();
        this.poseLandmarker  = createPoseLandmarker(context);
    }

    private PoseLandmarker createPoseLandmarker(Context context) throws Exception {
        BaseOptions base = BaseOptions.builder()
                .setModelAssetPath(MODEL_FILE)
                .build();

        PoseLandmarker.PoseLandmarkerOptions opts =
                PoseLandmarker.PoseLandmarkerOptions.builder()
                        .setBaseOptions(base)
                        .setRunningMode(RunningMode.LIVE_STREAM)
                        .setNumPoses(1)
                        .setMinPoseDetectionConfidence(0.5f)
                        .setMinPosePresenceConfidence(0.5f)
                        .setMinTrackingConfidence(0.5f)
                        .setResultListener((result, image) -> onPoseResultInternal(result))

                        .build();

        return PoseLandmarker.createFromOptions(context, opts);
    }

    public void setCallback(PoseCallback callback) {
        this.callback = callback;
    }

    public void analyzeFrame(ImageProxy imageProxy) {
        cameraExecutor.execute(() -> {
            Bitmap rotated = null;
            try {
                Bitmap source = convertImageProxyToBitmap(imageProxy);
                if (source == null) {
                    return;
                }

                int rotation = imageProxy.getImageInfo().getRotationDegrees();
                Matrix matrix = new Matrix();
                matrix.postRotate(rotation);

                matrix.postScale(-1f, 1f, source.getWidth() / 2f, source.getHeight() / 2f);

                rotated = Bitmap.createBitmap(
                        source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
                source.recycle();


                synchronized (bitmapLock) {
                    if (lastFrameBitmap != null) lastFrameBitmap.recycle();
                    lastFrameBitmap = rotated.copy(Bitmap.Config.ARGB_8888, false);
                }

                long tsUs = imageProxy.getImageInfo().getTimestamp() / 1000L;
                long prev = lastTimestampUs.get();
                if (tsUs <= prev) {
                    tsUs = prev + 1;
                }
                lastTimestampUs.set(tsUs);

                poseLandmarker.detectAsync(
                        new BitmapImageBuilder(rotated).build(),
                        tsUs
                );

            } catch (Exception e) {
            } finally {
                imageProxy.close();
            }
        });
    }

    private Bitmap convertImageProxyToBitmap(ImageProxy imageProxy) {
        try {
            ImageProxy.PlaneProxy plane  = imageProxy.getPlanes()[0];
            ByteBuffer            buffer = plane.getBuffer();
            int width      = imageProxy.getWidth();
            int height     = imageProxy.getHeight();
            int rowStride  = plane.getRowStride();
            int pixelStride = plane.getPixelStride();

            Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

            if (rowStride == width * pixelStride) {
                buffer.rewind();
                bmp.copyPixelsFromBuffer(buffer);
            } else {
                byte[] rowData = new byte[rowStride];
                byte[] pixelData = new byte[width * height * 4];
                int offset = 0;
                for (int row = 0; row < height; row++) {
                    buffer.position(row * rowStride);
                    buffer.get(rowData, 0, Math.min(rowStride, buffer.remaining()));
                    System.arraycopy(rowData, 0, pixelData, offset, width * pixelStride);
                    offset += width * pixelStride;
                }
                ByteBuffer pixelBuf = ByteBuffer.wrap(pixelData);
                bmp.copyPixelsFromBuffer(pixelBuf);
            }

            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    private void onPoseResultInternal(PoseLandmarkerResult result) {
        final Bitmap frameBitmap;
        synchronized (bitmapLock) {
            frameBitmap = (lastFrameBitmap != null && !lastFrameBitmap.isRecycled())
                    ? lastFrameBitmap.copy(Bitmap.Config.ARGB_8888, false)
                    : null;
        }


        if (result == null || result.landmarks().isEmpty()) {
            mainHandler.post(() -> {
                if (callback != null) callback.onAnalysis(null, null, frameBitmap);
            });
            return;
        }

        BaseExercise.AnalysisResult analysis =
                currentExercise.analyze(result.landmarks().get(0));

        long nowMs = System.currentTimeMillis();
        analysis.errors = errorDebouncer.filter(analysis.errors, nowMs);

        final BaseExercise.AnalysisResult finalAnalysis = analysis;
        final PoseLandmarkerResult        finalResult   = result;

        mainHandler.post(() -> {
            if (callback != null) callback.onAnalysis(finalAnalysis, finalResult, frameBitmap);
        });
    }

    public void resetExercise() {
        currentExercise.reset();
        errorDebouncer.reset();
    }

    public void shutdown() {
        cameraExecutor.shutdown();
        try {
            if (poseLandmarker != null) poseLandmarker.close();
        } catch (Exception ignored) {}
        synchronized (bitmapLock) {
            if (lastFrameBitmap != null) {
                lastFrameBitmap.recycle();
                lastFrameBitmap = null;
            }
        }
    }
}