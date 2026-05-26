package ru.sv.personaltrainer;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.sv.personaltrainer.databinding.ActivityExerciseBinding;
import ru.sv.personaltrainer.exercises.BaseExercise;
import ru.sv.personaltrainer.exercises.ExerciseRegistry;
import ru.sv.personaltrainer.model.WearData;
import ru.sv.personaltrainer.model.WorkoutResult;
import ru.sv.personaltrainer.viewmodel.ExerciseViewModel;
import ru.sv.personaltrainer.viewmodel.PermissionViewModel;
import ru.sv.personaltrainer.wear.WearHelper;

public class ExerciseActivity extends AppCompatActivity {

    private static final int AUDIO_PERMISSION_CODE = 200;

    private ActivityExerciseBinding binding;
    private ExerciseViewModel viewModel;
    private PermissionViewModel permissionViewModel;

    private TextToSpeech textToSpeech;
    private boolean ttsInitialized = false;
    private String lastSpokenError = null;
    private long lastErrorStartTime = 0;
    private long lastSpeechTime = 0;
    private static final long TTS_THRESHOLD_MS = 500L;
    private static final long MIN_SPEECH_INTERVAL_MS = 2500L;

    private ExecutorService cameraExecutor;

    private VideoRecorder videoRecorder;
    private boolean isRecording = false;
    private ScreenRecordService recordService;
    private boolean serviceBound = false;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private int recordSeconds = 0;
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            recordSeconds++;
            binding.tvRecordingTimer.setText(String.format(Locale.US, "%02d:%02d", recordSeconds / 60, recordSeconds % 60));
            timerHandler.postDelayed(this, 1000);
        }
    };
    private Animation blinkAnimation;

    private WearHelper wearHelper;
    private String lastSentError = null;

    private String exerciseId;
    private BaseExercise currentExercise;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        binding = ActivityExerciseBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        keepScreenOn(true);

        exerciseId = getIntent().getStringExtra("EXERCISE_ID");
        if (exerciseId == null) exerciseId = "SQUAT";

        try {
            currentExercise = ExerciseRegistry.createExercise(exerciseId, this);
        } catch (Exception e) {
            finish();
            return;
        }

        viewModel = new ViewModelProvider(this, new ExerciseViewModel.Factory(getApplication(), currentExercise)).get(ExerciseViewModel.class);

        permissionViewModel = new ViewModelProvider(this).get(PermissionViewModel.class);
        setupPermissionObserver();
        setupExerciseObservers();

        initViews();
        applyInsets();
        initTextToSpeech();
        initBlinkAnimation();
        initVideoRecorder();

        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private void keepScreenOn(boolean keepOn) {
        if (keepOn) {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void setupPermissionObserver() {
        permissionViewModel.getCameraPermissionState().observe(this, state -> {
            if (state == null) return;

            switch (state) {
                case CHECKING:
                    break;
                case GRANTED:
                    startCameraAndWear();
                    break;
                case REQUEST_DIALOG:
                    permissionViewModel.markRequestLaunched();
                    requestCameraPermission();
                    break;
                case SHOW_RATIONALE:
                    showRationaleDialog();
                    break;
                case GO_TO_SETTINGS:
                    showGoToSettingsDialog();
                    break;
                case WAITING_RESULT:
                    break;
            }
        });
    }

    private void requestCameraPermission() {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
    }

    private final ActivityResultLauncher<String> cameraPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                boolean shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
                        this, Manifest.permission.CAMERA);
                permissionViewModel.onPermissionResult(isGranted, shouldShowRationale);
            });

    @Override
    protected void onResume() {
        super.onResume();
        boolean canShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.CAMERA);
        permissionViewModel.checkCameraPermission(canShowRationale);
    }

    private void showRationaleDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_camera_title))
                .setMessage(getString(R.string.dialog_camera_message))
                .setPositiveButton(getString(R.string.dialog_camera_allow), (dialog, which) -> {
                    permissionViewModel.onRationaleShown();
                })
                .setNegativeButton(getString(R.string.dialog_camera_cancel), (dialog, which) -> {
                    Toast.makeText(this, getString(R.string.toast_no_camera), Toast.LENGTH_LONG).show();
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    private void showGoToSettingsDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_settings_title))
                .setMessage(getString(R.string.dialog_settings_message))
                .setPositiveButton(getString(R.string.dialog_settings_open), (dialog, which) -> {
                    permissionViewModel.openSettings();
                    finish();
                })
                .setNegativeButton(getString(R.string.dialog_camera_cancel), (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    private void setupExerciseObservers() {
        viewModel.getWorkoutResult().observe(this, this::onWorkoutResult);
        viewModel.getIsRecording().observe(this, this::updateRecordingUI);
        viewModel.getTtsEnabled().observe(this, this::updateTtsButton);
        viewModel.getTtsEvent().observe(this, event -> {
            String error = event.getContentIfNotHandled();
            if (error != null && ttsInitialized && viewModel.isTtsEnabled()) {
                speakError(error);
            }
        });
        viewModel.getWearData().observe(this, this::sendDataToWear);
    }

    private void onWorkoutResult(WorkoutResult result) {
        if (result == null) return;

        binding.tvRepCount.setText(result.repText);
        binding.tvFeedback.setText(result.mainFeedback);
        binding.tvPhase.setText(result.phaseText);
        binding.tvPhase.setTextColor(result.phaseColor);
        binding.tvQuality.setText(result.qualityText);
        binding.tvQuality.setTextColor(result.qualityColor);

        if (result.poseResult != null) {
            binding.poseOverlay.updateResults(result.poseResult, binding.previewView.getWidth(), binding.previewView.getHeight(), result.errorLandmarks);
        } else {
            binding.poseOverlay.updateResults(null, 0, 0, null);
        }

        if (result.errors != null && result.errors.size() > 1) {
            binding.layoutErrors.setVisibility(android.view.View.VISIBLE);
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < result.errors.size(); i++) {
                sb.append(result.errors.get(i));
                if (i < result.errors.size() - 1) sb.append("\n");
            }
            binding.tvErrors.setText(sb.toString());
        } else {
            binding.layoutErrors.setVisibility(android.view.View.GONE);
        }
    }

    private void initTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(new Locale("ru", "RU"));
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                } else {
                    ttsInitialized = true;
                    textToSpeech.setSpeechRate(0.9f);
                    textToSpeech.setPitch(1.0f);
                    textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override
                        public void onStart(String id) {
                        }

                        @Override
                        public void onDone(String id) {
                        }

                        @Override
                        public void onError(String id) {
                        }
                    });
                }
            }
        });
    }

    private void speakError(String error) {
        long now = System.currentTimeMillis();
        if (!error.equals(lastSpokenError)) {
            lastErrorStartTime = now;
            lastSpokenError = error;
        } else if (now - lastErrorStartTime >= TTS_THRESHOLD_MS && !textToSpeech.isSpeaking() && (now - lastSpeechTime) >= MIN_SPEECH_INTERVAL_MS) {
            String cleanMessage = error.replace(getString(R.string.error_prefix_warning), "").replace(getString(R.string.error_prefix_ok), "");
            android.os.Bundle params = new android.os.Bundle();
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "error_feedback");
            textToSpeech.speak(cleanMessage, TextToSpeech.QUEUE_FLUSH, params, "error_feedback");
            lastSpeechTime = now;
            lastErrorStartTime = now + MIN_SPEECH_INTERVAL_MS;
        }
    }

    private void startCameraAndWear() {
        startRecordService();
        startCamera();
        wearHelper = new WearHelper(this);
    }

    private void initViews() {
        binding.tvExerciseName.setText(currentExercise.getName());
        if ("PLANK".equals(exerciseId)) {
            binding.tvRepCount.setText(getString(R.string.hold_time_zero));
        }
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnReset.setOnClickListener(v -> {
            viewModel.resetExercise();
            resetUI();
        });
        binding.btnRecord.setOnClickListener(v -> onRecordClick());
        if (binding.btnToggleTts != null) {
            binding.btnToggleTts.setOnClickListener(v -> viewModel.toggleTts());
        }
    }

    private void resetUI() {
        if ("PLANK".equals(exerciseId)) {
            binding.tvRepCount.setText(getString(R.string.hold_time_zero));
        } else {
            binding.tvRepCount.setText(String.format(getString(R.string.vm_reps_format), 0));
        }
        binding.tvFeedback.setText(getString(R.string.feedback_start));
        binding.tvPhase.setText(getString(R.string.phase_ready));
        binding.tvPhase.setTextColor(ContextCompat.getColor(this, R.color.video_white));
        binding.tvQuality.setText(getString(R.string.vm_quality_full));
        binding.tvQuality.setTextColor(ContextCompat.getColor(this, R.color.accent));
        binding.layoutErrors.setVisibility(android.view.View.GONE);
        if (wearHelper != null) wearHelper.sendReset();
        lastSentError = null;
    }

    private void updateTtsButton(boolean enabled) {
        if (binding.btnToggleTts == null) return;
        binding.btnToggleTts.setText(enabled ? getString(R.string.tts_enabled_emoji) : getString(R.string.tts_disabled_emoji));
        binding.btnToggleTts.setAlpha(enabled ? 1.0f : 0.5f);
        if (!enabled && ttsInitialized && textToSpeech != null && textToSpeech.isSpeaking()) {
            textToSpeech.stop();
        }
    }

    private void applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.previewView, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());

            int topOffset = insets.top + getResources().getDimensionPixelSize(R.dimen.exercise_top_margin);
            int bottomOffset = insets.bottom + getResources().getDimensionPixelSize(R.dimen.feedback_card_margin);

            android.view.ViewGroup.MarginLayoutParams repParams = (android.view.ViewGroup.MarginLayoutParams) binding.tvRepCount.getLayoutParams();
            repParams.topMargin = topOffset;
            binding.tvRepCount.setLayoutParams(repParams);

            android.view.ViewGroup.MarginLayoutParams nameParams = (android.view.ViewGroup.MarginLayoutParams) binding.tvExerciseName.getLayoutParams();
            nameParams.topMargin = topOffset;
            binding.tvExerciseName.setLayoutParams(nameParams);

            android.view.View card = binding.cardFeedback;
            android.view.ViewGroup.MarginLayoutParams cardParams = (android.view.ViewGroup.MarginLayoutParams) card.getLayoutParams();
            cardParams.bottomMargin = bottomOffset;
            card.setLayoutParams(cardParams);

            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void initVideoRecorder() {
        videoRecorder = new VideoRecorder(this);
        videoRecorder.setCallback(new VideoRecorder.RecordingCallback() {
            @Override
            public void onRecordingStarted() {
            }

            @Override
            public void onRecordingSaved(String filePath) {
                Toast.makeText(ExerciseActivity.this, getString(R.string.toast_video_saved), Toast.LENGTH_LONG).show();
            }

            @Override
            public void onRecordingError(String error) {
                Toast.makeText(ExerciseActivity.this, String.format(getString(R.string.error_format), error), Toast.LENGTH_SHORT).show();
                isRecording = false;
                viewModel.setRecording(false);
                timerHandler.removeCallbacks(timerRunnable);
            }
        });
    }

    private void startRecordService() {
        Intent si = new Intent(this, ScreenRecordService.class);
        ContextCompat.startForegroundService(this, si);
        bindService(si, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            ScreenRecordService.LocalBinder lb = (ScreenRecordService.LocalBinder) binder;
            recordService = lb.getService();
            serviceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            recordService = null;
        }
    };

    private void onRecordClick() {
        if (isRecording) {
            stopRecording();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO_PERMISSION_CODE);
        } else {
            startRecording();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == AUDIO_PERMISSION_CODE) startRecording();
    }

    private void startRecording() {
        videoRecorder.startRecording();
        isRecording = true;
        viewModel.setRecording(true);
        recordSeconds = 0;
        updateRecordingUI(true);
        timerHandler.postDelayed(timerRunnable, 1000);
        Toast.makeText(this, getString(R.string.toast_recording_started), Toast.LENGTH_SHORT).show();
    }

    private void stopRecording() {
        if (!isRecording) return;
        videoRecorder.stopRecording();
        isRecording = false;
        viewModel.setRecording(false);
        updateRecordingUI(false);
        timerHandler.removeCallbacks(timerRunnable);
    }

    private void updateRecordingUI(boolean rec) {
        if (rec) {
            binding.btnRecord.setText(getString(R.string.btn_stop));
            binding.btnRecord.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.holo_red_dark));
            binding.layoutRecordingIndicator.setVisibility(android.view.View.VISIBLE);
            binding.viewRecordingDot.startAnimation(blinkAnimation);
            binding.tvRecordingTimer.setText("00:00");
        } else {
            binding.btnRecord.setText(getString(R.string.btn_record));
            binding.btnRecord.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.darker_gray));
            binding.layoutRecordingIndicator.setVisibility(android.view.View.GONE);
            binding.viewRecordingDot.clearAnimation();
        }
    }

    private void initBlinkAnimation() {
        blinkAnimation = new AlphaAnimation(1.0f, 0.0f);
        blinkAnimation.setDuration(600);
        blinkAnimation.setRepeatMode(Animation.REVERSE);
        blinkAnimation.setRepeatCount(Animation.INFINITE);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(binding.previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build();
                analysis.setAnalyzer(cameraExecutor, this::analyzeFrame);

                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis);
            } catch (Exception e) {
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyzeFrame(ImageProxy imageProxy) {
        viewModel.analyzeFrame(imageProxy);
    }

    private void sendDataToWear(WearData data) {
        if (wearHelper == null || !wearHelper.isAvailable()) return;
        wearHelper.sendPhase(data.phaseText, data.phaseColor);
        wearHelper.sendRepCount(data.repText);
        if (data.errorText != null && !data.errorText.isEmpty()) {
            if (!data.errorText.equals(lastSentError)) {
                wearHelper.sendError(data.errorText);
                lastSentError = data.errorText;
            }
        } else {
            if (lastSentError != null) {
                wearHelper.sendError("");
                lastSentError = null;
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isRecording) stopRecording();
        viewModel.finishWorkout(exerciseId);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        keepScreenOn(false);
        binding = null;
        timerHandler.removeCallbacks(timerRunnable);
        if (isRecording && videoRecorder != null) videoRecorder.stopRecording();
        if (serviceBound) {
            try {
                if (recordService != null) recordService.stopRecordingService();
                unbindService(serviceConnection);
            } catch (Exception e) {
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
    }
}