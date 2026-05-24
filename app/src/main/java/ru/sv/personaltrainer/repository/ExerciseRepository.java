package ru.sv.personaltrainer.repository;

import java.util.List;

import ru.sv.personaltrainer.exercises.ExerciseRegistry;
import ru.sv.personaltrainer.model.ExerciseInfo;

public class ExerciseRepository {
    public List<ExerciseInfo> getAllExercises() {
        return ExerciseRegistry.getAll();
    }

    public ExerciseInfo getExerciseById(String id) {
        for (ExerciseInfo info : ExerciseRegistry.getAll()) {
            if (info.getId().equals(id)) return info;
        }
        return null;
    }
}