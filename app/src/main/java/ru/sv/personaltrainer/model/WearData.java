package ru.sv.personaltrainer.model;

public class WearData {
    public final String phaseText;
    public final int phaseColor;
    public final String repText;
    public final String errorText;

    public WearData(String phaseText, int phaseColor, String repText, String errorText) {
        this.phaseText = phaseText;
        this.phaseColor = phaseColor;
        this.repText = repText;
        this.errorText = errorText;
    }
}