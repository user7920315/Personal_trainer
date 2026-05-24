package ru.sv.personaltrainer.model;

public class WeightRecord {
    public float weight;
    public long timestamp;

    public WeightRecord(float weight, long timestamp) {
        this.weight = weight;
        this.timestamp = timestamp;
    }
}