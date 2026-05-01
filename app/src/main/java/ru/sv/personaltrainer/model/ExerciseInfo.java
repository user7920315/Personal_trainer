package ru.sv.personaltrainer.model;

public class ExerciseInfo {

    private final String id;
    private final String title;
    private final String emoji;
    private final String muscleGroup;
    private final String difficulty;
    private final int color;

    private final String description;
    private final String videoFileName;

    public ExerciseInfo(String id, String title, String emoji,
                        String muscleGroup, String difficulty,
                        int color,
                        String description, String videoFileName) {
        this.id = id;
        this.title = title;
        this.emoji = emoji;
        this.muscleGroup = muscleGroup;
        this.difficulty = difficulty;
        this.color = color;
        this.description = description;
        this.videoFileName = videoFileName;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getEmoji() {
        return emoji;
    }

    public String getMuscleGroup() {
        return muscleGroup;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public int getColor() {
        return color;
    }

    public String getDescription() {
        return description;
    }

    public String getVideoFileName() {
        return videoFileName;
    }
}