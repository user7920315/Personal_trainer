package ru.sv.personaltrainer.model;

/**
 * Модель данных одного упражнения
 * Добавляй новые упражнения просто создав новый объект!
 */
public class ExerciseInfo {

    private final String id;           // уникальный ID
    private final String title;        // название
    private final String description;  // описание
    private final String emoji;        // иконка
    private final String difficulty;   // сложность
    private final int    color;        // цвет карточки
    private final String muscleGroup;  // группа мышц

    public ExerciseInfo(String id,
                        String title,
                        String description,
                        String emoji,
                        String difficulty,
                        int color,
                        String muscleGroup) {
        this.id          = id;
        this.title       = title;
        this.description = description;
        this.emoji       = emoji;
        this.difficulty  = difficulty;
        this.color       = color;
        this.muscleGroup = muscleGroup;
    }

    public String getId()          { return id; }
    public String getTitle()       { return title; }
    public String getDescription() { return description; }
    public String getEmoji()       { return emoji; }
    public String getDifficulty()  { return difficulty; }
    public int    getColor()       { return color; }
    public String getMuscleGroup() { return muscleGroup; }
}