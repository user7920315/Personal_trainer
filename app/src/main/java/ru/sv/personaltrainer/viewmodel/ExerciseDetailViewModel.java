package ru.sv.personaltrainer.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import ru.sv.personaltrainer.model.ExerciseInfo;
import ru.sv.personaltrainer.repository.ExerciseRepository;

public class ExerciseDetailViewModel extends AndroidViewModel {

    private final ExerciseRepository repository;
    private final MutableLiveData<ExerciseInfo> exerciseInfo = new MutableLiveData<>();

    public ExerciseDetailViewModel(@NonNull Application application) {
        super(application);
        this.repository = new ExerciseRepository(application);
    }

    public void loadExercise(String id) {
        exerciseInfo.setValue(repository.getExerciseById(id));
    }

    public LiveData<ExerciseInfo> getExerciseInfo() {
        return exerciseInfo;
    }
}