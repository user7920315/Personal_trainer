package ru.sv.personaltrainer;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import ru.sv.personaltrainer.databinding.ActivityExerciseBinding;
import ru.sv.personaltrainer.exercises.BaseExercise;
import ru.sv.personaltrainer.exercises.ExerciseRegistry;
import ru.sv.personaltrainer.exercises.PlankExercise;
import ru.sv.personaltrainer.overlay.PoseOverlayView;
import ru.sv.personaltrainer.wear.WearHelper;

public class ExerciseActivity extends AppCompatActivity {

    private static final String TAG = "ExerciseActivity";
    private static final String MODEL_FILE = "pose_landmarker_full.task";
    private static final int AUDIO_PERMISSION_CODE = 200;

    private ActivityExerciseBinding binding;
    private ActivityResultLauncher<String> cameraPermissionLauncher;

    private PreviewView previewView;
    private PoseOverlayView poseOverlay;
    private TextView tvRepCount;
    private TextView tvExerciseName;
    private TextView tvFeedback;
    private TextView tvErrors;
    private TextView tvPhase;
    private TextView tvQuality;
    private LinearLayout layoutErrors;
    private Button btnBack;
    private Button btnReset;
    private Button btnRecord;
    private LinearLayout layoutRecordingIndicator;
    private View viewRecordingDot;
    private TextView tvRecordingTimer;
    private Button btnToggleTts;

    private PoseLandmarker poseLandmarker;
    private WearHelper wearHelper;
    private String lastSentError = null;
    private BaseExercise currentExercise;
    private String exerciseId;

    private volatile PoseLandmarkerResult lastPoseResult = null;
    private volatile List<Integer> lastErrorLandmarks = null;
    private volatile String lastRepText = "Повторений: 0";
    private volatile String lastPhaseText = "● ГОТОВ";
    private volatile String lastFeedbackText = "";
    private volatile int lastPhaseColor = 0xFFFFFFFF;
    private volatile String lastQualityText = "●●●●●";
    private volatile int lastQualityColor = 0xFF00FF88;

    private VideoRecorder videoRecorder;
    private boolean isRecording = false;
    private ScreenRecordService recordService;
    private boolean serviceBound = false;
    private final ErrorDebouncer errorDebouncer = new ErrorDebouncer();

    private TextToSpeech textToSpeech;
    private boolean ttsInitialized = false;
    private String lastSpokenError = null;
    private long lastErrorStartTime = 0;
    private long lastSpeechTime = 0;
    private static final long TTS_THRESHOLD_MS = 500L;
    private static final long MIN_SPEECH_INTERVAL_MS = 2500L;
    private boolean isTtsEnabled = true;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            ScreenRecordService.LocalBinder lb = (ScreenRecordService.LocalBinder) binder;
            recordService = lb.getService();
            serviceBound = true;
            Log.d(TAG, "Сервис подключён");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            recordService = null;
        }
    };

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private int recordSeconds = 0;
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            recordSeconds++;
            tvRecordingTimer.setText(String.format(Locale.US, "%02d:%02d",
                    recordSeconds / 60, recordSeconds % 60));
            timerHandler.postDelayed(this, 1000);
        }
    };

    private Animation blinkAnimation;
    private ExecutorService cameraExecutor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private void initTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(new Locale("ru", "RU"));
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                        result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "TTS: язык не поддерживается");
                } else {
                    ttsInitialized = true;
                    textToSpeech.setSpeechRate(0.9f);
                    textToSpeech.setPitch(1.0f);
                    textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override public void onStart(String id) {}
                        @Override public void onDone(String id) {}
                        @Override public void onError(String id) {}
                    });
                }
            } else {
                Log.e(TAG, "TTS: ошибка инициализации, статус=" + status);
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        binding = ActivityExerciseBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        exerciseId = getIntent().getStringExtra("EXERCISE_ID");
        if (exerciseId == null) exerciseId = "SQUAT";

        try {
            currentExercise = ExerciseRegistry.createExercise(exerciseId);
        } catch (Exception e) {
            finish();
            return;
        }

        initViews();
        applyInsets();
        initTextToSpeech();
        initMediaPipe();
        initBlinkAnimation();
        initVideoRecorder();

        cameraExecutor = Executors.newSingleThreadExecutor();
        setupCameraPermission();
        wearHelper = new WearHelper(this);
        if (!wearHelper.isAvailable()) {
            Log.w(TAG, "⌚ Часы не подключены.");
        }
    }

    private void setupCameraPermission() {
        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        startCameraAndWear();
                    } else {
                        Toast.makeText(this, "Для тренировки необходим доступ к камере",
                                Toast.LENGTH_LONG).show();
                        finish();
                    }
                });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCameraAndWear();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startCameraAndWear() {
        startRecordService();
        startCamera();
        wearHelper = new WearHelper(this);
        if (!wearHelper.isAvailable()) {
            Log.w(TAG, "⌚ Часы не подключены.");
        }
    }

    private void applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.previewView, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() |
                            WindowInsetsCompat.Type.displayCutout());

            int topOffset = insets.top + getResources().getDimensionPixelSize(R.dimen.exercise_top_margin);
            int bottomOffset = insets.bottom + getResources().getDimensionPixelSize(R.dimen.feedback_card_margin);

            ViewGroup.MarginLayoutParams repParams = (ViewGroup.MarginLayoutParams) tvRepCount.getLayoutParams();
            repParams.topMargin = topOffset;
            tvRepCount.setLayoutParams(repParams);

            ViewGroup.MarginLayoutParams nameParams = (ViewGroup.MarginLayoutParams) tvExerciseName.getLayoutParams();
            nameParams.topMargin = topOffset;
            tvExerciseName.setLayoutParams(nameParams);

            View card = binding.cardFeedback;
            ViewGroup.MarginLayoutParams cardParams = (ViewGroup.MarginLayoutParams) card.getLayoutParams();
            cardParams.bottomMargin = bottomOffset;
            card.setLayoutParams(cardParams);

            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void initVideoRecorder() {
        videoRecorder = new VideoRecorder(this);
        videoRecorder.setCallback(new VideoRecorder.RecordingCallback() {
            @Override public void onRecordingStarted() {}
            @Override
            public void onRecordingSaved(String filePath) {
                Toast.makeText(ExerciseActivity.this, "✅ Видео сохранено в Галерею", Toast.LENGTH_LONG).show();
            }
            @Override
            public void onRecordingError(String error) {
                Toast.makeText(ExerciseActivity.this, "Ошибка: " + error, Toast.LENGTH_SHORT).show();
                isRecording = false;
                updateRecordingUI(false);
                timerHandler.removeCallbacks(timerRunnable);
            }
        });
    }

    private void startRecordService() {
        Intent si = new Intent(this, ScreenRecordService.class);
        ContextCompat.startForegroundService(this, si);
        bindService(si, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void initViews() {
        previewView = binding.previewView;
        poseOverlay = binding.poseOverlay;
        tvRepCount = binding.tvRepCount;
        tvExerciseName = binding.tvExerciseName;
        tvFeedback = binding.tvFeedback;
        tvErrors = binding.tvErrors;
        tvPhase = binding.tvPhase;
        tvQuality = binding.tvQuality;
        layoutErrors = binding.layoutErrors;
        btnBack = binding.btnBack;
        btnReset = binding.btnReset;
        btnRecord = binding.btnRecord;
        layoutRecordingIndicator = binding.layoutRecordingIndicator;
        viewRecordingDot = binding.viewRecordingDot;
        tvRecordingTimer = binding.tvRecordingTimer;
        btnToggleTts = binding.btnToggleTts;

        tvExerciseName.setText(currentExercise.getName());

        if ("PLANK".equals(exerciseId)) {
            tvRepCount.setText("⏱ Время: 0с");
        }

        btnBack.setOnClickListener(v -> finish());
        btnReset.setOnClickListener(v -> resetExercise());
        btnRecord.setOnClickListener(v -> onRecordClick());

        if (btnToggleTts != null) {
            btnToggleTts.setOnClickListener(v -> toggleTts());
            updateTtsButton();
        }
    }

    private void toggleTts() {
        isTtsEnabled = !isTtsEnabled;
        updateTtsButton();
        if (!isTtsEnabled && ttsInitialized && textToSpeech != null && textToSpeech.isSpeaking()) {
            textToSpeech.stop();
        }
    }

    private void updateTtsButton() {
        if (btnToggleTts == null) return;
        btnToggleTts.setText(isTtsEnabled ? "🔊" : "🔇");
        btnToggleTts.setAlpha(isTtsEnabled ? 1.0f : 0.5f);
    }

    private void resetExercise() {
        currentExercise.reset();
        errorDebouncer.reset();

        if ("PLANK".equals(exerciseId)) {
            tvRepCount.setText("⏱ Время: 0с");
            lastRepText = "⏱ Время: 0с";
        } else {
            tvRepCount.setText("Повторений: 0");
            lastRepText = "Повторений: 0";
        }
        if (wearHelper != null) wearHelper.sendReset();
        lastSentError = null;

        tvFeedback.setText("Встаньте в кадр для начала");
        tvPhase.setText("● ГОТОВ");
        tvPhase.setTextColor(0xFFFFFFFF);
        tvQuality.setText("●●●●●");
        tvQuality.setTextColor(0xFF00FF88);
        layoutErrors.setVisibility(View.GONE);
        lastPhaseText = "● ГОТОВ";
        lastFeedbackText = "";
        lastPhaseColor = 0xFFFFFFFF;
    }

    private void initBlinkAnimation() {
        blinkAnimation = new AlphaAnimation(1.0f, 0.0f);
        blinkAnimation.setDuration(600);
        blinkAnimation.setRepeatMode(Animation.REVERSE);
        blinkAnimation.setRepeatCount(Animation.INFINITE);
    }

    private void onRecordClick() {
        if (isRecording) {
            stopRecording();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO_PERMISSION_CODE);
        } else {
            startRecording();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == AUDIO_PERMISSION_CODE) startRecording();
    }

    private void startRecording() {
        videoRecorder.startRecording();
        isRecording = true;
        recordSeconds = 0;
        updateRecordingUI(true);
        timerHandler.postDelayed(timerRunnable, 1000);
        Toast.makeText(this, "Запись началась", Toast.LENGTH_SHORT).show();
    }

    private void stopRecording() {
        if (!isRecording) return;
        videoRecorder.stopRecording();
        isRecording = false;
        updateRecordingUI(false);
        timerHandler.removeCallbacks(timerRunnable);
    }

    private void updateRecordingUI(boolean rec) {
        if (rec) {
            btnRecord.setText("⏹ Стоп");
            btnRecord.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.holo_red_dark));
            layoutRecordingIndicator.setVisibility(View.VISIBLE);
            viewRecordingDot.startAnimation(blinkAnimation);
            tvRecordingTimer.setText("00:00");
        } else {
            btnRecord.setText("⏺ Запись");
            btnRecord.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.darker_gray));
            layoutRecordingIndicator.setVisibility(View.GONE);
            viewRecordingDot.clearAnimation();
        }
    }

    private void initMediaPipe() {
        try {
            BaseOptions base = BaseOptions.builder().setModelAssetPath(MODEL_FILE).build();
            PoseLandmarker.PoseLandmarkerOptions opts = PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(base)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setNumPoses(1)
                    .setMinPoseDetectionConfidence(0.5f)
                    .setMinPosePresenceConfidence(0.5f)
                    .setMinTrackingConfidence(0.5f)
                    .setResultListener(this::onPoseResult)
                    .setErrorListener(e -> Log.e(TAG, "MP: " + e.getMessage()))
                    .build();
            poseLandmarker = PoseLandmarker.createFromOptions(this, opts);
        } catch (Exception e) {
            Log.e(TAG, "MediaPipe: " + e.getMessage());
            finish();
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build();
                analysis.setAnalyzer(cameraExecutor, this::analyzeFrame);

                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis);
            } catch (Exception e) {
                Log.e(TAG, "Camera: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private String formatHoldTime(int seconds) {
        if (seconds == 0) return "⏱ Время: 0с";
        return "⏱ Время: " + seconds + "с";
    }

    private void analyzeFrame(ImageProxy imageProxy) {
        try {
            int rotation = imageProxy.getImageInfo().getRotationDegrees();
            long tsNs = imageProxy.getImageInfo().getTimestamp();
            Bitmap bitmap = imageProxy.toBitmap();

            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);
            matrix.postScale(-1f, 1f, bitmap.getWidth() / 2f, bitmap.getHeight() / 2f);
            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

            poseLandmarker.detectAsync(new BitmapImageBuilder(rotated).build(), tsNs);

            if (isRecording) {
                videoRecorder.submitFrame(rotated, lastPoseResult, lastErrorLandmarks,
                        lastRepText, lastPhaseText, lastFeedbackText, lastPhaseColor,
                        currentExercise.getName(), lastQualityText, lastQualityColor, tsNs);
            }
        } catch (Exception e) {
            Log.e(TAG, "analyzeFrame: " + e.getMessage());
        } finally {
            imageProxy.close();
        }
    }

    private BaseExercise.AnalysisResult buildFilteredAnalysis(
            BaseExercise.AnalysisResult raw, List<String> filteredErrors) {
        BaseExercise.AnalysisResult filtered = new BaseExercise.AnalysisResult();
        filtered.phase = raw.phase;
        filtered.repCount = raw.repCount;
        filtered.holdSeconds = raw.holdSeconds;

        if (filteredErrors.isEmpty()) {
            filtered.mainFeedback = raw.mainFeedback.startsWith("⚠")
                    ? buildPositiveFeedback(raw.phase) : raw.mainFeedback;
            return filtered;
        }

        filtered.errors.addAll(filteredErrors);
        filtered.errorLandmarks.addAll(raw.errorLandmarks);
        filtered.mainFeedback = filteredErrors.get(0);
        return filtered;
    }

    private String buildPositiveFeedback(String phase) {
        switch (phase) {
            case "DOWN": return "✅ Хорошо! Держите позицию";
            case "UP": return "✅ Отлично!";
            case "HOLD": return "✅ Держите!";
            default: return "✅ Начните упражнение";
        }
    }

    private void onPoseResult(PoseLandmarkerResult result, MPImage input) {
        if (result == null || result.landmarks().isEmpty()) {
            mainHandler.post(() -> {
                tvFeedback.setText("Встаньте полностью в кадр");
                tvPhase.setText("● ПОИСК...");
                tvPhase.setTextColor(0xFFAAAAAA);
                layoutErrors.setVisibility(View.GONE);
                poseOverlay.updateResults(null, 0, 0, null);
            });
            lastPoseResult = null;
            lastErrorLandmarks = null;
            return;
        }

        BaseExercise.AnalysisResult rawAnalysis = currentExercise.analyze(result.landmarks().get(0));
        long nowMs = System.currentTimeMillis();
        List<String> filteredErrors = errorDebouncer.filter(rawAnalysis.errors, nowMs);
        BaseExercise.AnalysisResult filteredAnalysis = buildFilteredAnalysis(rawAnalysis, filteredErrors);

        if (!filteredAnalysis.errors.isEmpty() && ttsInitialized && isTtsEnabled) {
            String currentError = filteredAnalysis.errors.get(0);
            long now = System.currentTimeMillis();

            if (!currentError.equals(lastSpokenError)) {
                lastErrorStartTime = now;
                lastSpokenError = currentError;
            } else if (now - lastErrorStartTime >= TTS_THRESHOLD_MS
                    && !textToSpeech.isSpeaking()
                    && (now - lastSpeechTime) >= MIN_SPEECH_INTERVAL_MS) {
                String cleanMessage = currentError.replace("⚠ ", "").replace("✅ ", "");
                Bundle params = new Bundle();
                params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "error_feedback");
                textToSpeech.speak(cleanMessage, TextToSpeech.QUEUE_FLUSH, params, "error_feedback");
                lastSpeechTime = now;
                lastErrorStartTime = now + MIN_SPEECH_INTERVAL_MS;
            }
        } else if (filteredAnalysis.errors.isEmpty()) {
            lastSpokenError = null;
            lastErrorStartTime = 0;
        }

        lastPoseResult = result;
        lastErrorLandmarks = filteredAnalysis.errorLandmarks;
        if (filteredAnalysis.holdSeconds >= 0) {
            lastRepText = formatHoldTime(filteredAnalysis.holdSeconds);
        } else {
            lastRepText = "Повторений: " + filteredAnalysis.repCount;
        }
        lastPhaseText = phaseToText(filteredAnalysis.phase);
        lastPhaseColor = phaseToColor(filteredAnalysis.phase);
        lastFeedbackText = filteredAnalysis.mainFeedback;

        mainHandler.post(() -> updateUI(result, filteredAnalysis));
        sendDataToWear(filteredAnalysis);
    }

    private void updateUI(PoseLandmarkerResult result, BaseExercise.AnalysisResult analysis) {
        tvRepCount.setText(lastRepText);
        tvFeedback.setText(analysis.mainFeedback);
        tvPhase.setText(lastPhaseText);
        tvPhase.setTextColor(lastPhaseColor);
        updateQualityIndicator(analysis.errors.size());
        updateErrorsBlock(analysis);
        poseOverlay.updateResults(result, previewView.getWidth(), previewView.getHeight(), analysis.errorLandmarks);
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

    private void updateQualityIndicator(int errorCount) {
        String q;
        int c;
        if (errorCount == 0) { q = "●●●●●"; c = 0xFF00FF88; }
        else if (errorCount == 1) { q = "●●●●○"; c = 0xFF88FF00; }
        else if (errorCount == 2) { q = "●●●○○"; c = 0xFFFFFF00; }
        else if (errorCount == 3) { q = "●●○○○"; c = 0xFFFF8800; }
        else { q = "●○○○○"; c = 0xFFFF0000; }

        lastQualityText = q;
        lastQualityColor = c;
        tvQuality.setText(q);
        tvQuality.setTextColor(c);
    }

    private void updateErrorsBlock(BaseExercise.AnalysisResult a) {
        if (a.errors.size() > 1) {
            layoutErrors.setVisibility(View.VISIBLE);
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < a.errors.size(); i++) {
                sb.append(a.errors.get(i));
                if (i < a.errors.size() - 1) sb.append("\n");
            }
            tvErrors.setText(sb.toString());
        } else {
            layoutErrors.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isRecording) stopRecording();

        if (currentExercise != null) {
            if ("PLANK".equals(exerciseId)) {
                PlankExercise plank = (PlankExercise) currentExercise;
                if (plank.getBestHoldSeconds() > 0) {
                    ProfileActivity.saveWorkout(this, exerciseId,
                            currentExercise.getName(), "🧘", plank.getBestHoldSeconds());
                }
            } else if (currentExercise.getRepCount() > 0) {
                String icon;
                switch (exerciseId) {
                    case "SQUAT": icon = "🏋"; break;
                    case "LUNGE": icon = "🦵"; break;
                    case "GLUTE_BRIDGE": icon = "🍑"; break;
                    case "BURPEE": icon = "🔥"; break;
                    case "PULL_UP": icon = "🏅"; break;
                    default: icon = "💪"; break;
                }
                ProfileActivity.saveWorkout(this, exerciseId,
                        currentExercise.getName(), icon, currentExercise.getRepCount());
            }
            currentExercise.reset();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
        timerHandler.removeCallbacks(timerRunnable);
        if (isRecording && videoRecorder != null) videoRecorder.stopRecording();
        if (serviceBound) {
            try {
                if (recordService != null) recordService.stopRecordingService();
                unbindService(serviceConnection);
            } catch (Exception e) {
                Log.e(TAG, "unbind: " + e.getMessage());
            }
            serviceBound = false;
            recordService = null;
        }
        if (cameraExecutor != null) cameraExecutor.shutdown();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }
        ttsInitialized = false;
        if (poseLandmarker != null) poseLandmarker.close();
    }

    private void sendDataToWear(BaseExercise.AnalysisResult analysis) {
        if (wearHelper == null || !wearHelper.isAvailable()) return;
        wearHelper.sendPhase(lastPhaseText, lastPhaseColor);
        wearHelper.sendRepCount(lastRepText);
        if (!analysis.errors.isEmpty()) {
            String currentError = analysis.errors.get(0);
            if (!currentError.equals(lastSentError)) {
                String cleanError = currentError.replace("⚠ ", "").replace("✅ ", "").trim();
                wearHelper.sendError(cleanError);
                lastSentError = currentError;
            }
        } else {
            if (lastSentError != null) {
                wearHelper.sendError("");
                lastSentError = null;
            }
        }
    }

    private static class ErrorDebouncer {
        private static final long DEBOUNCE_MS = 250L;
        private final java.util.HashMap<String, Long> firstSeenMap = new java.util.HashMap<>();
        private final java.util.HashSet<String> confirmedErrors = new java.util.HashSet<>();

        public List<String> filter(List<String> rawErrors, long nowMs) {
            firstSeenMap.keySet().retainAll(rawErrors);
            confirmedErrors.retainAll(rawErrors);
            for (String error : rawErrors) {
                if (!firstSeenMap.containsKey(error)) firstSeenMap.put(error, nowMs);
            }
            List<String> result = new ArrayList<>();
            for (String error : rawErrors) {
                Long firstSeen = firstSeenMap.get(error);
                if (firstSeen != null && (nowMs - firstSeen) >= DEBOUNCE_MS) {
                    confirmedErrors.add(error);
                    result.add(error);
                }
            }
            return result;
        }

        public void reset() {
            firstSeenMap.clear();
            confirmedErrors.clear();
        }
    }
}