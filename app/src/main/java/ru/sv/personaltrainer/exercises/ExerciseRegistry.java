package ru.sv.personaltrainer.exercises;

import ru.sv.personaltrainer.model.ExerciseInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExerciseRegistry {

    public static List<ExerciseInfo> getAll() {
        List<ExerciseInfo> list = new ArrayList<>();

        list.add(new ExerciseInfo(
                "PUSH_UP",
                "Отжимания",
                "Грудь, трицепс, плечи",
                "💪",
                "Средний",
                0xFFE94560,
                "Грудь · Трицепс · Плечи"
        ));

        list.add(new ExerciseInfo(
                "SQUAT",
                "Приседания",
                "Квадрицепс, ягодицы, бёдра",
                "🏋",
                "Лёгкий",
                0xFF0F3460,
                "Квадрицепс · Ягодицы · Бёдра"
        ));

        list.add(new ExerciseInfo(
                "PLANK",
                "Планка",
                "Пресс, спина, всё тело",
                "🧘",
                "Лёгкий",
                0xFF16213E,
                "Пресс · Спина · Всё тело"
        ));


        return list;
    }

    public static BaseExercise createExercise(String id) {
        switch (id) {
            case "PUSH_UP": return new PushUpExercise();
            case "SQUAT":   return new SquatExercise();
            case "PLANK":   return new PlankExercise();
            default: throw new IllegalArgumentException(
                    "Неизвестное упражнение: " + id);
        }
    }
}