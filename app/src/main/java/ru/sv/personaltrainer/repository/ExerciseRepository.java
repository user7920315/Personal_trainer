package ru.sv.personaltrainer.repository;

import android.content.Context;

import java.util.List;

import ru.sv.personaltrainer.exercises.ExerciseRegistry;
import ru.sv.personaltrainer.model.ExerciseInfo;

public class ExerciseRepository {
    private final Context context;

    public ExerciseRepository(Context context) {
        this.context = context;
    }

    public List<ExerciseInfo> getAllExercises() {
        return ExerciseRegistry.getAll(context);
    }

    public ExerciseInfo getExerciseById(String id) {
        for (ExerciseInfo info : ExerciseRegistry.getAll(context)) {
            if (info.getId().equals(id)) return info;
        }
        return null;
    }
}