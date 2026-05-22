package ru.sv.personaltrainer.model;

import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;

import java.util.List;

public class WorkoutResult {
    public String phase;
    public int repCount;
    public int holdSeconds;
    public String mainFeedback;
    public List<String> errors;
    public List<Integer> errorLandmarks;
    public String repText;
    public String phaseText;
    public int phaseColor;
    public String qualityText;
    public int qualityColor;

    public PoseLandmarkerResult poseResult;
    public int previewWidth;
    public int previewHeight;
}