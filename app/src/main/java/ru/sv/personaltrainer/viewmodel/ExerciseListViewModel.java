package ru.sv.personaltrainer.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

import ru.sv.personaltrainer.model.ExerciseInfo;
import ru.sv.personaltrainer.repository.ExerciseRepository;

public class ExerciseListViewModel extends ViewModel {

    private final ExerciseRepository repository;
    private final List<ExerciseInfo> allExercises;
    private final MutableLiveData<List<ExerciseInfo>> filteredExercises = new MutableLiveData<>();

    public ExerciseListViewModel() {
        this.repository = new ExerciseRepository();
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
            if (ex.getTitle().toLowerCase().contains(lower)
                    || ex.getMuscleGroup().toLowerCase().contains(lower)) {
                result.add(ex);
            }
        }
        filteredExercises.setValue(result);
    }
}