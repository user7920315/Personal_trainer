package ru.sv.personaltrainer.exercises;

import ru.sv.personaltrainer.model.ExerciseInfo;
import java.util.ArrayList;
import java.util.List;

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


        list.add(new ExerciseInfo(
                "LUNGE",
                "Выпады",
                "Квадрицепс, ягодицы, баланс",
                "🦵",
                "Средний",
                0xFF1A472A,
                "Квадрицепс · Ягодицы · Баланс"
        ));

        list.add(new ExerciseInfo(
                "GLUTE_BRIDGE",
                "Ягодичный мостик",
                "Ягодицы, бицепс бедра, кор",
                "\uD83C\uDF09",
                "Лёгкий",
                0xFF4A1942,
                "Ягодицы · Бицепс бедра · Кор"
        ));

        list.add(new ExerciseInfo(
                "BURPEE",
                "Берпи",
                "Всё тело, кардио",
                "🔥",
                "Тяжёлый",
                0xFFB5451B,
                "Всё тело · Кардио · Выносливость"
        ));

        list.add(new ExerciseInfo(
                "PULL_UP",
                "Подтягивания",
                "Спина, бицепс, плечи",
                "💪",
                "Тяжёлый",
                0xFF1B3A4B,
                "Спина · Бицепс · Плечи"
        ));

        return list;
    }

    public static BaseExercise createExercise(String id) {
        switch (id) {
            case "PUSH_UP":      return new PushUpExercise();
            case "SQUAT":        return new SquatExercise();
            case "PLANK":        return new PlankExercise();
            case "LUNGE":        return new LungeExercise();
            case "GLUTE_BRIDGE": return new GluteBridgeExercise();
            case "BURPEE":       return new BurpeeExercise();
            case "PULL_UP":      return new PullUpExercise();
            default: throw new IllegalArgumentException(
                    "Неизвестное упражнение: " + id);
        }
    }
}