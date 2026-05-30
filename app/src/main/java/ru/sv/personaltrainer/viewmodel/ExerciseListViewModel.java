package ru.sv.personaltrainer.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.List;

import ru.sv.personaltrainer.model.ExerciseInfo;
import ru.sv.personaltrainer.repository.ExerciseRepository;

public class ExerciseListViewModel extends AndroidViewModel {

    private final ExerciseRepository repository;
    private final List<ExerciseInfo> allExercises;
    private final MutableLiveData<List<ExerciseInfo>> filteredExercises = new MutableLiveData<>();

    public ExerciseListViewModel(@NonNull Application application) {
        super(application);
        this.repository = new ExerciseRepository(application);
        this.allExercises = repository.getAllExercises();
        this.filteredExercises.setValue(new ArrayList<>(allExercises));
    }

    public LiveData<List<ExerciseInfo>> getFilteredExercises() {
        return filteredExercises;
    }

    public void filter(String query) {
        if (query == null || query.trim().isEmpty()) {
            filteredExercises.setValue(new ArrayList<>(allExercises));
            return;
        }
        String lower = query.toLowerCase();
        List<ExerciseInfo> result = new ArrayList<>();
        for (ExerciseInfo ex : allExercises) {
            if (ex.getTitle().toLowerCase().contains(lower) || ex.getMuscleGroup().toLowerCase().contains(lower)) {
                result.add(ex);
            }
        }
        filteredExercises.setValue(result);
    }
}