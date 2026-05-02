package ru.sv.personaltrainer.model;

public class ExerciseInfo {

    private final String id;
    private final String title;
    private final String description;
    private final String emoji;
    private final String difficulty;
    private final int    color;
    private final String muscleGroup;

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