package ru.sv.personaltrainer.exercises;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseExercise {

    protected static final int NOSE             = 0;
    protected static final int LEFT_SHOULDER    = 11;
    protected static final int RIGHT_SHOULDER   = 12;
    protected static final int LEFT_ELBOW       = 13;
    protected static final int RIGHT_ELBOW      = 14;
    protected static final int LEFT_WRIST       = 15;
    protected static final int RIGHT_WRIST      = 16;
    protected static final int LEFT_HIP         = 23;
    protected static final int RIGHT_HIP        = 24;
    protected static final int LEFT_KNEE        = 25;
    protected static final int RIGHT_KNEE       = 26;
    protected static final int LEFT_ANKLE       = 27;
    protected static final int RIGHT_ANKLE      = 28;
    protected static final int LEFT_HEEL        = 29;
    protected static final int RIGHT_HEEL       = 30;
    protected static final int LEFT_FOOT_INDEX  = 31;
    protected static final int RIGHT_FOOT_INDEX = 32;

    protected static final float VISIBILITY_THRESHOLD = 0.6f;

    protected int     repCount = 0;
    protected boolean isDown   = false;

    public static class AnalysisResult {
        public List<String>  errors         = new ArrayList<>();
        public List<Integer> errorLandmarks = new ArrayList<>();
        public String        mainFeedback   = "";
        public String        phase          = "";
        public int           repCount       = 0;

        public void addError(String message, int... landmarks) {
            errors.add(message);
            for (int lm : landmarks) errorLandmarks.add(lm);
        }
    }

    public abstract AnalysisResult analyze(List<NormalizedLandmark> landmarks);
    public abstract String getName();

    public void reset() {
        repCount = 0;
        isDown   = false;
    }

    public int getRepCount() { return repCount; }


    protected boolean isVisible(List<NormalizedLandmark> lm, int idx) {
        if (idx >= lm.size()) return false;
        NormalizedLandmark point = lm.get(idx);
        if (point.visibility().isPresent()) {
            return point.visibility().get() >= VISIBILITY_THRESHOLD;
        }
        return false;
    }

    protected boolean allVisible(List<NormalizedLandmark> lm, int... indices) {
        for (int idx : indices) {
            if (!isVisible(lm, idx)) return false;
        }
        return true;
    }

    protected boolean anyVisible(List<NormalizedLandmark> lm, int... indices) {
        for (int idx : indices) {
            if (isVisible(lm, idx)) return true;
        }
        return false;
    }

    protected float getVisibility(List<NormalizedLandmark> lm, int idx) {
        if (idx >= lm.size()) return 0f;
        NormalizedLandmark point = lm.get(idx);
        if (point.visibility().isPresent()) {
            return point.visibility().get();
        }
        return 0f;
    }

    protected boolean isValidData(List<NormalizedLandmark> lm) {
        return lm != null && lm.size() >= 33;
    }

    protected float getAngle(List<NormalizedLandmark> lm,
                             int aIdx, int bIdx, int cIdx) {
        if (!allVisible(lm, aIdx, bIdx, cIdx)) return -1f;

        NormalizedLandmark a = lm.get(aIdx);
        NormalizedLandmark b = lm.get(bIdx);
        NormalizedLandmark c = lm.get(cIdx);

        double ax = a.x() - b.x();
        double ay = a.y() - b.y();
        double cx = c.x() - b.x();
        double cy = c.y() - b.y();

        double dot  = ax * cx + ay * cy;
        double magA = Math.sqrt(ax * ax + ay * ay);
        double magC = Math.sqrt(cx * cx + cy * cy);

        if (magA == 0 || magC == 0) return -1f;

        double cos = Math.max(-1.0, Math.min(1.0, dot / (magA * magC)));
        return (float) Math.toDegrees(Math.acos(cos));
    }

    protected float avgY(List<NormalizedLandmark> lm, int idx1, int idx2) {
        if (!allVisible(lm, idx1, idx2)) return -1f;
        return (lm.get(idx1).y() + lm.get(idx2).y()) / 2f;
    }

    protected float avgX(List<NormalizedLandmark> lm, int idx1, int idx2) {
        if (!allVisible(lm, idx1, idx2)) return -1f;
        return (lm.get(idx1).x() + lm.get(idx2).x()) / 2f;
    }

    protected float distX(List<NormalizedLandmark> lm, int idx1, int idx2) {
        if (!allVisible(lm, idx1, idx2)) return -1f;
        return Math.abs(lm.get(idx1).x() - lm.get(idx2).x());
    }

    protected float diffY(List<NormalizedLandmark> lm, int idx1, int idx2) {
        if (!allVisible(lm, idx1, idx2)) return -1f;
        return lm.get(idx1).y() - lm.get(idx2).y();
    }
}