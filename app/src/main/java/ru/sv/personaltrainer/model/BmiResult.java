package ru.sv.personaltrainer.model;

public class BmiResult {
    public final float bmi;
    public final String category;
    public final int color;
    public final String normText;

    public BmiResult(float bmi, String category, int color, String normText) {
        this.bmi = bmi;
        this.category = category;
        this.color = color;
        this.normText = normText;
    }
}