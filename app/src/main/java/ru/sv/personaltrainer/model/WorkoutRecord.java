package ru.sv.personaltrainer.model;

public class WorkoutRecord {
    public String exerciseId;
    public String exerciseName;
    public int iconResId;
    public int reps;
    public long timestamp;

    public WorkoutRecord(String exerciseId, String exerciseName, int iconResId, int reps, long timestamp) {
        this.exerciseId = exerciseId;
        this.exerciseName = exerciseName;
        this.iconResId = iconResId;
        this.reps = reps;
        this.timestamp = timestamp;
    }
}