package ru.sv.personaltrainer.exercises;

import android.util.Log;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;

import java.util.List;

public class SquatExercise extends BaseExercise {

    private static final String TAG = "SquatExercise";

    private static final float DEPTH_TOO_SHALLOW_ERR = -0.20f;
    private static final float DEPTH_TOO_SHALLOW_WARN = -0.10f;
    private static final float DEPTH_TOO_DEEP_ERR = 0.15f;
    private static final float DEPTH_TOO_DEEP_WARN = 0.07f;

    private static final float BACK_DIFF_WARN = 20f;
    private static final float BACK_DIFF_ERROR = 35f;

    private static final float HEEL_NORMAL_OFFSET = 0.90f;
    private static final float HEEL_WARN = 0.10f;
    private static final float HEEL_ERROR = 0.20f;

    private static final float KNEE_OVER_TOE = 0.06f;

    private static final float KNEE_CAVE_ERROR = 0.70f;
    private static final float KNEE_CAVE_WARN = 0.82f;

    private static final float KNEE_ASYMMETRY = 14f;

    private static final float SIDE_THRESHOLD = 0.12f;
    private static final int STABLE_FRAMES = 8;

    private static final float EMA_ALPHA = 0.15f;

    private float emaLHeelY = -1f;
    private float emaLFootIdxY = -1f;
    private float emaLAnkleY = -1f;
    private float emaLHipY = -1f;
    private float emaLKneeY = -1f;

    private float emaRHeelY = -1f;
    private float emaRFootIdxY = -1f;
    private float emaRAnkleY = -1f;
    private float emaRHipY = -1f;
    private float emaRKneeY = -1f;

    private float emaShX = -1f, emaShY = -1f;
    private float emaHiX = -1f, emaHiY = -1f;
    private float emaKnX = -1f, emaKnY = -1f;
    private float emaAnX = -1f, emaAnY = -1f;

    private float emaShoulderWidth = -1f;

    private ViewMode currentView = ViewMode.UNKNOWN;
    private ViewMode candidateView = ViewMode.UNKNOWN;
    private int candidateCount = 0;

    private enum ViewMode {SIDE, FRONT, UNKNOWN}


    @Override
    public String getName() {
        return "🏋 Приседания";
    }

    @Override
    public AnalysisResult analyze(List<NormalizedLandmark> lm) {
        AnalysisResult result = new AnalysisResult();

        if (!isValidData(lm)) {
            result.mainFeedback = "Встаньте полностью в кадр";
            return result;
        }

        updateEMA(lm);

        ViewMode view = updateView(lm);
        if (view == ViewMode.UNKNOWN) {
            result.mainFeedback =
                    "Встаньте боком или лицом к камере";
            result.phase = "";
            return result;
        }

        Side side = selectBestSide(lm);
        if (side == null || side.kneeAngle < 0) {
            result.mainFeedback =
                    "Не видно колена — встаньте в кадр";
            result.phase = "";
            return result;
        }

        updatePhase(result, side.kneeAngle);

        Log.d(TAG, "View=" + view
                + " knee=" + String.format("%.1f°", side.kneeAngle)
                + " phase=" + result.phase);

        if (view == ViewMode.SIDE) {
            analyzeSide(lm, result, side);
        } else {
            analyzeFront(lm, result, side);
        }

        result.repCount = repCount;
        result.mainFeedback = result.errors.isEmpty()
                ? buildFeedback(result.phase, view)
                : result.errors.get(0);

        return result;
    }

    private void analyzeSide(List<NormalizedLandmark> lm,
                             AnalysisResult result,
                             Side s) {

        checkDepth(result, s, lm);

        updateBackEMA(lm, s);
        if (s.hasShoulder && s.hasHip
                && s.hasKnee && s.hasAnkle) {
            checkBack(result, s);
        }
        if (s.isLeft) {
            if (s.hasHeel && s.hasToe
                    && emaLHeelY > 0 && emaLFootIdxY > 0 && emaLAnkleY > 0) {
                checkHeel(result,
                        emaLHeelY, emaLFootIdxY, emaLAnkleY,
                        true, s.heelIdx, s.toeIdx);
            }
        } else {
            if (s.hasHeel && s.hasToe
                    && emaRHeelY > 0 && emaRFootIdxY > 0 && emaRAnkleY > 0) {
                checkHeel(result,
                        emaRHeelY, emaRFootIdxY, emaRAnkleY,
                        false, s.heelIdx, s.toeIdx);
            }
        }

        if (s.hasToe && s.hasAnkle && s.hasKnee) {
            checkKneeOverToe(lm, result, s);
        }
    }

    private void analyzeFront(List<NormalizedLandmark> lm,
                              AnalysisResult result,
                              Side s) {


        checkDepth(result, s, lm);

        if (allVisible(lm, LEFT_KNEE, RIGHT_KNEE,
                LEFT_ANKLE, RIGHT_ANKLE)) {
            checkKneesInward(lm, result);
        }

        if (isVisible(lm, LEFT_HEEL)
                && isVisible(lm, LEFT_FOOT_INDEX)
                && isVisible(lm, LEFT_ANKLE)
                && emaLHeelY > 0
                && emaLFootIdxY > 0
                && emaLAnkleY > 0) {
            checkHeel(result,
                    emaLHeelY, emaLFootIdxY, emaLAnkleY,
                    true, LEFT_HEEL, LEFT_FOOT_INDEX);
        }

        if (isVisible(lm, RIGHT_HEEL)
                && isVisible(lm, RIGHT_FOOT_INDEX)
                && isVisible(lm, RIGHT_ANKLE)
                && emaRHeelY > 0
                && emaRFootIdxY > 0
                && emaRAnkleY > 0) {
            checkHeel(result,
                    emaRHeelY, emaRFootIdxY, emaRAnkleY,
                    false, RIGHT_HEEL, RIGHT_FOOT_INDEX);
        }

        if (allVisible(lm, LEFT_HIP, RIGHT_HIP,
                LEFT_KNEE, RIGHT_KNEE,
                LEFT_ANKLE, RIGHT_ANKLE)) {
            checkAsymmetry(lm, result);
        }
    }

    private void checkDepth(AnalysisResult result,
                            Side s,
                            List<NormalizedLandmark> lm) {

        if (!result.phase.equals("DOWN")) return;

        if (!s.hasHip || !s.hasKnee) return;

        float hipY = s.isLeft ? emaLHipY : emaRHipY;
        float kneeY = s.isLeft ? emaLKneeY : emaRKneeY;

        if (hipY <= 0 || kneeY <= 0) return;

        float hipKneeDist = Math.abs(kneeY - hipY);
        if (hipKneeDist < 0.05f) {
            Log.w(TAG, "Depth: hipKneeDist too small=" + hipKneeDist);
            return;
        }

        float ratio = (hipY - kneeY) / hipKneeDist;

        Log.d(TAG, String.format(
                "Depth[%s]: hipY=%.3f kneeY=%.3f dist=%.3f ratio=%.3f",
                s.isLeft ? "L" : "R",
                hipY, kneeY, hipKneeDist, ratio));

        if (ratio < DEPTH_TOO_SHALLOW_ERR) {
            result.addError(
                    "⚠ Приседайте глубже — "
                            + "опустите таз до уровня колен",
                    s.hipIdx, s.kneeIdx);

        } else if (ratio < DEPTH_TOO_SHALLOW_WARN) {
            result.addError(
                    "⚠ Чуть глубже — почти достаточно!",
                    s.hipIdx, s.kneeIdx);

        } else if (ratio > DEPTH_TOO_DEEP_ERR) {
            result.addError(
                    "⚠ Слишком глубокий присед — "
                            + "поднимитесь чуть выше",
                    s.hipIdx, s.kneeIdx);

        } else if (ratio > DEPTH_TOO_DEEP_WARN) {
            result.addError(
                    "⚠ Очень глубокий присед — "
                            + "следите за коленями",
                    s.hipIdx, s.kneeIdx);
        }

    }

    private void checkBack(AnalysisResult result, Side s) {

        if (!isFinite(emaShX) || !isFinite(emaShY)
                || !isFinite(emaHiX) || !isFinite(emaHiY)
                || !isFinite(emaKnX) || !isFinite(emaKnY)
                || !isFinite(emaAnX) || !isFinite(emaAnY)) return;


        double backDx = emaHiX - emaShX;
        double backDy = emaHiY - emaShY;


        double shinDx = emaAnX - emaKnX;
        double shinDy = emaAnY - emaKnY;


        double backAngle = Math.toDegrees(Math.atan2(backDy, backDx));
        double shinAngle = Math.toDegrees(Math.atan2(shinDy, shinDx));


        double diff = Math.abs(backAngle - shinAngle);
        if (diff > 180.0) diff = 360.0 - diff;

        Log.d(TAG, String.format(
                "Back[%s]: back=%.1f° shin=%.1f° diff=%.1f°",
                s.isLeft ? "L" : "R",
                backAngle, shinAngle, diff));

        if (diff > BACK_DIFF_ERROR) {
            result.addError(
                    "⚠ Спина не параллельна голени — "
                            + "выпрямите корпус",
                    s.shoulderIdx, s.hipIdx);
        } else if (diff > BACK_DIFF_WARN) {
            result.addError(
                    "⚠ Держите спину параллельно голени",
                    s.hipIdx);
        }
    }

    private void checkHeel(AnalysisResult result,
                           float heelY,
                           float footIndexY,
                           float ankleY,
                           boolean isLeft,
                           int heelIdx,
                           int footIdxIdx) {

        float ankleHeelDist = Math.abs(ankleY - heelY);
        if (ankleHeelDist < 0.01f) {
            ankleHeelDist = 0.05f;
        }

        float liftRatio = (footIndexY - heelY) / ankleHeelDist;

        float deviation = liftRatio - HEEL_NORMAL_OFFSET;

        String name = isLeft ? "Левая" : "Правая";

        Log.d(TAG, String.format(
                "Heel[%s]: heelY=%.3f footIdxY=%.3f ankleY=%.3f "
                        + "dist=%.3f liftRatio=%.3f deviation=%.3f",
                name, heelY, footIndexY, ankleY,
                ankleHeelDist, liftRatio, deviation));

        if (deviation > HEEL_ERROR) {
            result.addError(
                    "⚠ " + name + " пятка оторвана! "
                            + "Прижимайте пятку к полу",
                    heelIdx, footIdxIdx);

        } else if (deviation > HEEL_WARN) {
            result.addError(
                    "⚠ " + name + " пятка начинает подниматься",
                    heelIdx);
        }
    }

    private void checkKneeOverToe(List<NormalizedLandmark> lm,
                                  AnalysisResult result,
                                  Side s) {
        float kneeX = lm.get(s.kneeIdx).x();
        float toeX = lm.get(s.toeIdx).x();
        float ankleX = lm.get(s.ankleIdx).x();

        float footDir = toeX - ankleX;
        if (Math.abs(footDir) < 0.01f) return;

        float kneeRel = (kneeX - ankleX) / Math.abs(footDir);
        float toeRel = (toeX - ankleX) / Math.abs(footDir);

        if (Math.abs(kneeRel) > Math.abs(toeRel) + KNEE_OVER_TOE) {
            result.addError(
                    "⚠ Колено выходит за носок — "
                            + "перенесите вес на пятки",
                    s.kneeIdx, s.toeIdx);
        }
    }

    private void checkKneesInward(List<NormalizedLandmark> lm,
                                  AnalysisResult result) {
        float kW = distX(lm, LEFT_KNEE, RIGHT_KNEE);
        float aW = distX(lm, LEFT_ANKLE, RIGHT_ANKLE);

        if (kW < 0 || aW <= 0) return;

        float ratio = kW / aW;
        Log.d(TAG, "KneeCave=" + String.format("%.2f", ratio));

        if (ratio < KNEE_CAVE_ERROR) {
            result.addError(
                    "⚠ Колени заваливаются внутрь — "
                            + "разведите по линии стоп",
                    LEFT_KNEE, RIGHT_KNEE);
        } else if (ratio < KNEE_CAVE_WARN) {
            result.addError(
                    "⚠ Слегка разведите колени",
                    LEFT_KNEE, RIGHT_KNEE);
        }
    }

    private void checkAsymmetry(List<NormalizedLandmark> lm,
                                AnalysisResult result) {
        float lA = getAngle(lm, LEFT_HIP, LEFT_KNEE, LEFT_ANKLE);
        float rA = getAngle(lm, RIGHT_HIP, RIGHT_KNEE, RIGHT_ANKLE);

        if (lA < 0 || rA < 0) return;

        float diff = Math.abs(lA - rA);
        if (diff > KNEE_ASYMMETRY) {
            result.addError(lA > rA
                            ? "⚠ Левое колено сгибается меньше — "
                            + "выровняйте нагрузку"
                            : "⚠ Правое колено сгибается меньше — "
                            + "выровняйте нагрузку",
                    lA > rA ? LEFT_KNEE : RIGHT_KNEE);
        }
    }

    private ViewMode updateView(List<NormalizedLandmark> lm) {
        ViewMode raw = detectRawView(lm);

        if (raw == ViewMode.UNKNOWN) {
            return currentView != ViewMode.UNKNOWN
                    ? currentView : ViewMode.UNKNOWN;
        }

        if (raw == candidateView) {
            candidateCount++;
        } else {
            candidateView = raw;
            candidateCount = 1;
        }

        if (candidateCount >= STABLE_FRAMES
                && currentView != candidateView) {
            Log.d(TAG, "View: "
                    + currentView + " → " + candidateView);
            resetEMA();
            currentView = candidateView;
        }

        return currentView != ViewMode.UNKNOWN
                ? currentView : raw;
    }

    private ViewMode detectRawView(List<NormalizedLandmark> lm) {
        if (emaShoulderWidth > 0) {
            return emaShoulderWidth < SIDE_THRESHOLD
                    ? ViewMode.SIDE : ViewMode.FRONT;
        }
        if (allVisible(lm, LEFT_HIP, RIGHT_HIP)) {
            float w = distX(lm, LEFT_HIP, RIGHT_HIP);
            if (w >= 0) {
                return w < SIDE_THRESHOLD
                        ? ViewMode.SIDE : ViewMode.FRONT;
            }
        }
        return ViewMode.UNKNOWN;
    }

    private void updateEMA(List<NormalizedLandmark> lm) {


        emaLHeelY = emaPointY(emaLHeelY, lm, LEFT_HEEL);
        emaLFootIdxY = emaPointY(emaLFootIdxY, lm, LEFT_FOOT_INDEX);
        emaLAnkleY = emaPointY(emaLAnkleY, lm, LEFT_ANKLE);
        emaLHipY = emaPointY(emaLHipY, lm, LEFT_HIP);
        emaLKneeY = emaPointY(emaLKneeY, lm, LEFT_KNEE);


        emaRHeelY = emaPointY(emaRHeelY, lm, RIGHT_HEEL);
        emaRFootIdxY = emaPointY(emaRFootIdxY, lm, RIGHT_FOOT_INDEX);
        emaRAnkleY = emaPointY(emaRAnkleY, lm, RIGHT_ANKLE);
        emaRHipY = emaPointY(emaRHipY, lm, RIGHT_HIP);
        emaRKneeY = emaPointY(emaRKneeY, lm, RIGHT_KNEE);


        if (allVisible(lm, LEFT_SHOULDER, RIGHT_SHOULDER)) {
            float w = distX(lm, LEFT_SHOULDER, RIGHT_SHOULDER);
            emaShoulderWidth = emaVal(emaShoulderWidth, w);
        } else {
            emaShoulderWidth = -1f;
        }
    }

    private float emaPointY(float prev,
                            List<NormalizedLandmark> lm,
                            int idx) {
        if (!isVisible(lm, idx)) return -1f;
        float val = lm.get(idx).y();
        return emaVal(prev, val);
    }

    private float emaVal(float prev, float newVal) {
        if (newVal < 0
                || newVal > 1.0f
                || Float.isNaN(newVal)
                || Float.isInfinite(newVal)) return prev;
        if (prev < 0) return newVal;
        return prev + EMA_ALPHA * (newVal - prev);
    }

    private void updateBackEMA(List<NormalizedLandmark> lm, Side s) {
        if (s.hasShoulder) {
            emaShX = emaVal(emaShX, lm.get(s.shoulderIdx).x());
            emaShY = emaVal(emaShY, lm.get(s.shoulderIdx).y());
        } else {
            emaShX = emaShY = -1f;
        }

        if (s.hasHip) {
            emaHiX = emaVal(emaHiX, lm.get(s.hipIdx).x());
            emaHiY = emaVal(emaHiY, lm.get(s.hipIdx).y());
        } else {
            emaHiX = emaHiY = -1f;
        }

        if (s.hasKnee) {
            emaKnX = emaVal(emaKnX, lm.get(s.kneeIdx).x());
            emaKnY = emaVal(emaKnY, lm.get(s.kneeIdx).y());
        } else {
            emaKnX = emaKnY = -1f;
        }

        if (s.hasAnkle) {
            emaAnX = emaVal(emaAnX, lm.get(s.ankleIdx).x());
            emaAnY = emaVal(emaAnY, lm.get(s.ankleIdx).y());
        } else {
            emaAnX = emaAnY = -1f;
        }
    }

    private boolean isFinite(float v) {
        return !Float.isNaN(v) && !Float.isInfinite(v) && v > 0;
    }

    private static class Side {
        boolean isLeft;
        int shoulderIdx, hipIdx, kneeIdx;
        int ankleIdx, heelIdx, toeIdx;
        float kneeAngle;
        boolean hasShoulder, hasHip, hasKnee;
        boolean hasAnkle, hasHeel, hasToe;
    }

    private Side buildSide(List<NormalizedLandmark> lm,
                           boolean left) {
        Side s = new Side();
        s.isLeft = left;
        s.shoulderIdx = left ? LEFT_SHOULDER : RIGHT_SHOULDER;
        s.hipIdx = left ? LEFT_HIP : RIGHT_HIP;
        s.kneeIdx = left ? LEFT_KNEE : RIGHT_KNEE;
        s.ankleIdx = left ? LEFT_ANKLE : RIGHT_ANKLE;
        s.heelIdx = left ? LEFT_HEEL : RIGHT_HEEL;
        s.toeIdx = left ? LEFT_FOOT_INDEX : RIGHT_FOOT_INDEX;

        s.hasShoulder = isVisible(lm, s.shoulderIdx);
        s.hasHip = isVisible(lm, s.hipIdx);
        s.hasKnee = isVisible(lm, s.kneeIdx);
        s.hasAnkle = isVisible(lm, s.ankleIdx);
        s.hasHeel = isVisible(lm, s.heelIdx);
        s.hasToe = isVisible(lm, s.toeIdx);

        s.kneeAngle = (s.hasHip && s.hasKnee && s.hasAnkle)
                ? getAngle(lm, s.hipIdx, s.kneeIdx, s.ankleIdx)
                : -1f;
        return s;
    }

    private Side selectBestSide(List<NormalizedLandmark> lm) {
        Side l = buildSide(lm, true);
        Side r = buildSide(lm, false);
        return score(l) >= score(r) ? l : r;
    }

    private int score(Side s) {
        int sc = 0;
        if (s.kneeAngle > 0) sc += 5;
        if (s.hasShoulder) sc += 3;
        if (s.hasAnkle) sc += 2;
        if (s.hasHeel) sc += 2;
        if (s.hasToe) sc += 2;
        if (s.hasHip) sc += 1;
        return sc;
    }

    private void updatePhase(AnalysisResult r, float angle) {
        if (angle < 105f) {
            if (!isDown) isDown = true;
            r.phase = "DOWN";
        } else if (angle > 165f && isDown) {
            isDown = false;
            repCount++;
            r.phase = "UP";
        } else {
            r.phase = angle < 135f ? "DOWN" : "UP";
        }
    }

    private String buildFeedback(String phase, ViewMode view) {
        String hint = view == ViewMode.FRONT
                ? " (встаньте боком для анализа спины)" : "";
        switch (phase) {
            case "DOWN":
                return "✅ Хорошо! Держите спину" + hint;
            case "UP":
                return "✅ Повторений: " + repCount + hint;
            default:
                return "✅ Начните приседать" + hint;
        }
    }

    private void resetEMA() {
        emaLHeelY = emaRHeelY = -1f;
        emaLFootIdxY = emaRFootIdxY = -1f;
        emaLAnkleY = emaRAnkleY = -1f;
        emaLHipY = emaRHipY = -1f;
        emaLKneeY = emaRKneeY = -1f;
        emaShX = emaShY = emaHiX = emaHiY = -1f;
        emaKnX = emaKnY = emaAnX = emaAnY = -1f;
        Log.d(TAG, "EMA reset");
    }

    @Override
    public void reset() {
        super.reset();
        resetEMA();
        emaShoulderWidth = -1f;
        currentView = ViewMode.UNKNOWN;
        candidateView = ViewMode.UNKNOWN;
        candidateCount = 0;
    }
}