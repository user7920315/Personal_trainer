package ru.sv.personaltrainer.viewmodel;

import android.app.Application;
import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;

import java.util.ArrayList;
import java.util.List;

import ru.sv.personaltrainer.R;
import ru.sv.personaltrainer.exercises.BaseExercise;
import ru.sv.personaltrainer.model.Event;
import ru.sv.personaltrainer.model.WearData;
import ru.sv.personaltrainer.model.WorkoutResult;
import ru.sv.personaltrainer.repository.PoseRepository;
import ru.sv.personaltrainer.repository.WorkoutRepository;

public class ExerciseViewModel extends AndroidViewModel {

    private final PoseRepository poseRepository;
    private final WorkoutRepository workoutRepository;
    private final BaseExercise currentExercise;

    private final MutableLiveData<WorkoutResult> workoutResult = new MutableLiveData<>();
    private final MutableLiveData<VideoFrame> videoFrame = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isRecording = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> ttsEnabled = new MutableLiveData<>(true);
    private final MutableLiveData<Event<String>> ttsEvent = new MutableLiveData<>();
    private final MutableLiveData<WearData> wearData = new MutableLiveData<>();

    private String lastRepText;

    public LiveData<WorkoutResult> getWorkoutResult() {
        return workoutResult;
    }

    public LiveData<VideoFrame> getVideoFrame() {
        return videoFrame;
    }

    public LiveData<Boolean> getIsRecording() {
        return isRecording;
    }

    public LiveData<Boolean> getTtsEnabled() {
        return ttsEnabled;
    }

    public LiveData<Event<String>> getTtsEvent() {
        return ttsEvent;
    }

    public LiveData<WearData> getWearData() {
        return wearData;
    }

    public ExerciseViewModel(@NonNull Application application, BaseExercise exercise) {
        super(application);
        this.currentExercise = exercise;
        this.lastRepText = getString(R.string.vm_reps_format, 0);
        this.workoutRepository = new WorkoutRepository(application);

        try {
            this.poseRepository = new PoseRepository(application, exercise);
            this.poseRepository.setCallback(this::onPoseResult);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create PoseRepository", e);
        }
    }

    private void onPoseResult(BaseExercise.AnalysisResult analysis, PoseLandmarkerResult poseResult, Bitmap frameBitmap) {
        WorkoutResult result = mapToWorkoutResult(analysis, poseResult);
        workoutResult.postValue(result);

        if (result.errors != null && !result.errors.isEmpty()) {
            ttsEvent.postValue(new Event<>(result.errors.get(0)));
        }

        if (frameBitmap != null) {
            videoFrame.postValue(new VideoFrame(frameBitmap, result.poseResult, result.errorLandmarks, result.repText, result.phaseText, result.mainFeedback, result.phaseColor, currentExercise.getName(), result.qualityText, result.qualityColor, System.nanoTime()));
        } else {
        }

        String error = (result.errors != null && !result.errors.isEmpty()) ? result.errors.get(0) : "";
        wearData.postValue(new WearData(result.phaseText, result.phaseColor, result.repText, error));
    }

    public void analyzeFrame(androidx.camera.core.ImageProxy imageProxy) {
        if (poseRepository != null) poseRepository.analyzeFrame(imageProxy);
    }

    public void toggleTts() {
        Boolean current = ttsEnabled.getValue();
        ttsEnabled.setValue(current == null || !current);
    }

    public boolean isTtsEnabled() {
        Boolean current = ttsEnabled.getValue();
        return current == null || current;
    }

    public void setRecording(boolean recording) {
        isRecording.setValue(recording);
    }

    public void resetExercise() {
        if (poseRepository != null) poseRepository.resetExercise();
    }

    public void finishWorkout(String exerciseId) {
        if (currentExercise == null) return;
        String icon = getIconForExercise(exerciseId);
        int reps = currentExercise.getRepCount();
        if (reps > 0) {
            workoutRepository.saveWorkout(exerciseId, currentExercise.getName(), icon, reps);
        }
        currentExercise.reset();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (poseRepository != null) poseRepository.shutdown();
    }

    private WorkoutResult mapToWorkoutResult(BaseExercise.AnalysisResult raw, PoseLandmarkerResult poseResult) {
        WorkoutResult r = new WorkoutResult();
        r.poseResult = poseResult;

        if (raw == null) {
            r.mainFeedback = getString(R.string.vm_search_pose);
            r.phaseText = getString(R.string.vm_phase_search);
            r.phaseColor = getColor(R.color.phase_search);
            r.repText = lastRepText;
            r.qualityText = getString(R.string.vm_quality_full);
            r.qualityColor = getColor(R.color.quality_perfect);
            r.errors = new ArrayList<>();
            r.errorLandmarks = new ArrayList<>();
            return r;
        }

        r.phase = raw.phase;
        r.repCount = raw.repCount;
        r.holdSeconds = raw.holdSeconds;
        r.errorLandmarks = raw.errorLandmarks != null ? raw.errorLandmarks : new ArrayList<>();

        if (raw.errors == null || raw.errors.isEmpty()) {
            r.mainFeedback = raw.mainFeedback != null && raw.mainFeedback.startsWith("⚠") ? buildPositiveFeedback(raw.phase) : raw.mainFeedback;
            r.errors = new ArrayList<>();
        } else {
            r.errors = new ArrayList<>(raw.errors);
            r.mainFeedback = raw.errors.get(0);
        }

        r.repText = (r.holdSeconds >= 0) ? formatHoldTime(r.holdSeconds) : getString(R.string.vm_reps_format, r.repCount);
        lastRepText = r.repText;

        r.phaseText = phaseToText(r.phase);
        r.phaseColor = phaseToColor(r.phase);
        r.qualityText = qualityToText(r.errors.size());
        r.qualityColor = qualityToColor(r.errors.size());

        return r;
    }

    private String formatHoldTime(int seconds) {
        return seconds == 0 ? getString(R.string.vm_hold_time_zero) : getString(R.string.vm_hold_time_format, seconds);
    }

    private String buildPositiveFeedback(String phase) {
        switch (phase) {
            case "DOWN":
                return getString(R.string.vm_feedback_down);
            case "UP":
                return getString(R.string.vm_feedback_up);
            case "HOLD":
                return getString(R.string.vm_feedback_hold);
            default:
                return getString(R.string.vm_feedback_start);
        }
    }

    private String phaseToText(String phase) {
        switch (phase) {
            case "DOWN":
                return getString(R.string.vm_phase_down);
            case "UP":
                return getString(R.string.vm_phase_up);
            case "HOLD":
                return getString(R.string.vm_phase_hold);
            default:
                return getString(R.string.vm_phase_ready);
        }
    }

    private int phaseToColor(String phase) {
        switch (phase) {
            case "DOWN":
                return getColor(R.color.phase_down);
            case "UP":
                return getColor(R.color.phase_up);
            case "HOLD":
                return getColor(R.color.phase_hold);
            default:
                return getColor(R.color.phase_ready);
        }
    }

    private String qualityToText(int errorCount) {
        if (errorCount == 0) return getString(R.string.vm_quality_full);
        if (errorCount == 1) return getString(R.string.vm_quality_1);
        if (errorCount == 2) return getString(R.string.vm_quality_2);
        if (errorCount == 3) return getString(R.string.vm_quality_3);
        return getString(R.string.vm_quality_bad);
    }

    private int qualityToColor(int errorCount) {
        if (errorCount == 0) return getColor(R.color.quality_perfect);
        if (errorCount == 1) return getColor(R.color.quality_good);
        if (errorCount == 2) return getColor(R.color.quality_ok);
        if (errorCount == 3) return getColor(R.color.quality_bad);
        return getColor(R.color.quality_poor);
    }

    private String getIconForExercise(String id) {
        switch (id) {
            case "SQUAT":
                return getString(R.string.icon_squat);
            case "LUNGE":
                return getString(R.string.icon_lunge);
            case "GLUTE_BRIDGE":
                return getString(R.string.icon_glute);
            case "BURPEE":
                return getString(R.string.icon_burpee);
            case "PULL_UP":
                return getString(R.string.icon_pullup);
            case "PLANK":
                return getString(R.string.icon_plank);
            default:
                return getString(R.string.icon_default);
        }
    }

    private String getString(int resId) {
        return getApplication().getString(resId);
    }

    private String getString(int resId, Object... formatArgs) {
        return getApplication().getString(resId, formatArgs);
    }

    private int getColor(int resId) {
        return getApplication().getResources().getColor(resId, null);
    }

    public static class VideoFrame {
        public final Bitmap bitmap;
        public final PoseLandmarkerResult poseResult;
        public final List<Integer> errorLandmarks;
        public final String repText;
        public final String phaseText;
        public final String feedbackText;
        public final int phaseColor;
        public final String exerciseName;
        public final String qualityText;
        public final int qualityColor;
        public final long timestampNs;

        public VideoFrame(Bitmap bitmap, PoseLandmarkerResult poseResult, List<Integer> errorLandmarks, String repText, String phaseText, String feedbackText, int phaseColor, String exerciseName, String qualityText, int qualityColor, long timestampNs) {
            this.bitmap = bitmap;
            this.poseResult = poseResult;
            this.errorLandmarks = errorLandmarks;
            this.repText = repText;
            this.phaseText = phaseText;
            this.feedbackText = feedbackText;
            this.phaseColor = phaseColor;
            this.exerciseName = exerciseName;
            this.qualityText = qualityText;
            this.qualityColor = qualityColor;
            this.timestampNs = timestampNs;
        }
    }

    public static class Factory implements ViewModelProvider.Factory {
        private final Application application;
        private final BaseExercise exercise;

        public Factory(Application application, BaseExercise exercise) {
            this.application = application;
            this.exercise = exercise;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            if (modelClass.isAssignableFrom(ExerciseViewModel.class)) {
                return (T) new ExerciseViewModel(application, exercise);
            }
            throw new IllegalArgumentException("Unknown ViewModel class");
        }
    }
}