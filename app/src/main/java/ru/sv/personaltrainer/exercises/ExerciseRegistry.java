package ru.sv.personaltrainer.exercises;

import ru.sv.personaltrainer.model.ExerciseInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Реестр всех упражнений
 * Чтобы добавить новое упражнение:
 * 1. Создай класс extends BaseExercise
 * 2. Добавь ExerciseInfo в getAll()
 * 3. Добавь case в createExercise()
 * Больше ничего менять не нужно!
 */
public class ExerciseRegistry {

    // ── Список всех упражнений ────────────────────────
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

        // ← Добавляй новые упражнения сюда!

        return list;
    }

    // ── Создание экземпляра упражнения по ID ─────────
    public static BaseExercise createExercise(String id) {
        switch (id) {
            case "PUSH_UP": return new PushUpExercise();
            case "SQUAT":   return new SquatExercise();
            case "PLANK":   return new PlankExercise();
            // ← Добавляй новые case сюда!
            default: throw new IllegalArgumentException(
                    "Неизвестное упражнение: " + id);
        }
    }
}