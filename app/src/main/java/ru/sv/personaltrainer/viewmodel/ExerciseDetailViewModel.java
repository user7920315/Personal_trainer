package ru.sv.personaltrainer.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import ru.sv.personaltrainer.model.ExerciseInfo;
import ru.sv.personaltrainer.repository.ExerciseRepository;

public class ExerciseDetailViewModel extends ViewModel {

    private final ExerciseRepository repository;
    private final MutableLiveData<ExerciseInfo> exerciseInfo = new MutableLiveData<>();

    public ExerciseDetailViewModel() {
        this.repository = new ExerciseRepository();
    }

    public void loadExercise(String id) {
        exerciseInfo.setValue(repository.getExerciseById(id));
    }

    public LiveData<ExerciseInfo> getExerciseInfo() {
        return exerciseInfo;
    }
}