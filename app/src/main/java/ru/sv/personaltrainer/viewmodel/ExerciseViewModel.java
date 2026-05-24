package ru.sv.personaltrainer.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;

import java.util.ArrayList;

import ru.sv.personaltrainer.exercises.BaseExercise;
import ru.sv.personaltrainer.model.Event;
import ru.sv.personaltrainer.model.WearData;
import ru.sv.personaltrainer.model.WorkoutResult;
import ru.sv.personaltrainer.repository.PoseRepository;
import ru.sv.personaltrainer.repository.WorkoutRepository;

public class ExerciseViewModel extends AndroidViewModel {

    private PoseRepository poseRepository;
    private final WorkoutRepository workoutRepository;
    private final BaseExercise currentExercise;

    private final MutableLiveData<WorkoutResult> workoutResult = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isRecording = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> ttsEnabled = new MutableLiveData<>(true);
    private final MutableLiveData<Event<String>> ttsEvent = new MutableLiveData<>();
    private final MutableLiveData<WearData> wearData = new MutableLiveData<>();

    private String lastRepText = "Повторений: 0";

    public LiveData<WorkoutResult> getWorkoutResult() { return workoutResult; }
    public LiveData<Boolean> getIsRecording() { return isRecording; }
    public LiveData<Boolean> getTtsEnabled() { return ttsEnabled; }
    public LiveData<Event<String>> getTtsEvent() { return ttsEvent; }
    public LiveData<WearData> getWearData() { return wearData; }

    public ExerciseViewModel(@NonNull Application application, BaseExercise exercise) {
        super(application);
        this.currentExercise = exercise;
        this.workoutRepository = new WorkoutRepository(application);
        try {
            this.poseRepository = new PoseRepository(application, exercise);
            this.poseRepository.setCallback((analysis, poseResult) -> {
                WorkoutResult result = mapToWorkoutResult(analysis, poseResult);
                workoutResult.postValue(result);

                if (result != null && result.errors != null && !result.errors.isEmpty()) {
                    ttsEvent.postValue(new Event<>(result.errors.get(0)));
                }
                if (result != null) {
                    String err = (result.errors != null && !result.errors.isEmpty())
                            ? result.errors.get(0) : "";
                    wearData.postValue(new WearData(
                            result.phaseText, result.phaseColor, result.repText, err));
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void analyzeFrame(androidx.camera.core.ImageProxy imageProxy) {
        if (poseRepository != null) poseRepository.analyzeFrame(imageProxy);
    }

    public void toggleTts() {
        Boolean cur = ttsEnabled.getValue();
        ttsEnabled.setValue(cur == null || !cur);
    }

    public boolean isTtsEnabled() {
        Boolean cur = ttsEnabled.getValue();
        return cur == null || cur;
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

    private String getIconForExercise(String id) {
        switch (id) {
            case "SQUAT": return "🏋";
            case "LUNGE": return "🦵";
            case "GLUTE_BRIDGE": return "🍑";
            case "BURPEE": return "🔥";
            case "PULL_UP": return "🏅";
            case "PLANK": return "🧘";
            default: return "💪";
        }
    }

    private WorkoutResult mapToWorkoutResult(BaseExercise.AnalysisResult raw,
                                             PoseLandmarkerResult poseResult) {
        WorkoutResult r = new WorkoutResult();
        r.poseResult = poseResult;

        if (raw == null) {
            r.mainFeedback = "Встаньте полностью в кадр";
            r.phaseText = "● ПОИСК...";
            r.phaseColor = 0xFFAAAAAA;
            r.repText = lastRepText;
            r.qualityText = "●●●●●";
            r.qualityColor = 0xFF00FF88;
            r.errors = new ArrayList<>();
            r.errorLandmarks = new ArrayList<>();
            return r;
        }

        r.phase = raw.phase;
        r.repCount = raw.repCount;
        r.holdSeconds = raw.holdSeconds;
        r.errorLandmarks = raw.errorLandmarks != null ? raw.errorLandmarks : new ArrayList<>();

        if (raw.errors == null || raw.errors.isEmpty()) {
            r.mainFeedback = raw.mainFeedback != null && raw.mainFeedback.startsWith("⚠")
                    ? buildPositiveFeedback(raw.phase) : raw.mainFeedback;
            r.errors = new ArrayList<>();
        } else {
            r.errors = new ArrayList<>(raw.errors);
            r.mainFeedback = raw.errors.get(0);
        }

        r.repText = (r.holdSeconds >= 0)
                ? formatHoldTime(r.holdSeconds)
                : "Повторений: " + r.repCount;
        lastRepText = r.repText;

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

    @Override
    protected void onCleared() {
        super.onCleared();
        if (poseRepository != null) poseRepository.shutdown();
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