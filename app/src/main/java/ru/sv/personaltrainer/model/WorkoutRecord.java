package ru.sv.personaltrainer.model;

public class WorkoutRecord {
    public String exerciseId;
    public String exerciseName;
    public String icon;
    public int reps;
    public long timestamp;

    public WorkoutRecord(String exerciseId, String exerciseName,
                         String icon, int reps, long timestamp) {
        this.exerciseId = exerciseId;
        this.exerciseName = exerciseName;
        this.icon = icon;
        this.reps = reps;
        this.timestamp = timestamp;
    }
}