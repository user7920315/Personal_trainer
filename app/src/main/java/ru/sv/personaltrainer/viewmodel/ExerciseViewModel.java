package ru.sv.personaltrainer.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import ru.sv.personaltrainer.exercises.BaseExercise;
import ru.sv.personaltrainer.model.WorkoutResult;
import ru.sv.personaltrainer.repository.PoseRepository;

public class ExerciseViewModel extends AndroidViewModel {

    private final MutableLiveData<WorkoutResult> workoutResult = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isRecording = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> ttsEnabled = new MutableLiveData<>(true);

    public LiveData<WorkoutResult> getWorkoutResult() { return workoutResult; }
    public LiveData<Boolean> getIsRecording() { return isRecording; }
    public LiveData<Boolean> getTtsEnabled() { return ttsEnabled; }

    private PoseRepository poseRepository;

    public ExerciseViewModel(@NonNull Application application) {
        super(application);
    }

    public void initExercise(android.content.Context context, BaseExercise exercise) {
        try {
            poseRepository = new PoseRepository(context, exercise);
            poseRepository.setCallback(result -> workoutResult.postValue(result));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void analyzeFrame(androidx.camera.core.ImageProxy imageProxy) {
        if (poseRepository != null) {
            poseRepository.analyzeFrame(imageProxy);
        }
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
        if (poseRepository != null) {
            poseRepository.resetExercise();
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (poseRepository != null) poseRepository.shutdown();
    }
}