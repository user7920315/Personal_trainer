package ru.sv.personaltrainer.exercises;

import android.util.Log;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import java.util.List;

public class LungeExercise extends BaseExercise {

    private static final String TAG = "LungeExercise";
    //Константы
    private static final float SIDE_FRONT_KNEE_TOO_DEEP   = 75f;
    private static final float SIDE_FRONT_KNEE_IDEAL_MIN  = 80f;
    private static final float SIDE_FRONT_KNEE_IDEAL_MAX  = 100f;
    private static final float SIDE_FRONT_KNEE_SHALLOW    = 115f;

    private static final float SIDE_BACK_KNEE_IDEAL_MAX   = 105f;
    private static final float SIDE_BACK_KNEE_SHALLOW     = 120f;

    private static final float FRONT_KNEE_TOO_DEEP        = 75f;
    private static final float FRONT_KNEE_IDEAL_MIN       = 80f;
    private static final float FRONT_KNEE_IDEAL_MAX       = 100f;
    private static final float FRONT_KNEE_SHALLOW         = 115f;

    private static final float PHASE_DOWN_DEEP  = 110f;
    private static final float PHASE_DOWN_ENTER = 130f;
    private static final float PHASE_UP_EXIT    = 158f;

    private static final float TRUNK_LEAN_WARN  = 25f;
    private static final float TRUNK_LEAN_ERROR = 42f;

    private static final float KNEE_OVER_TOE_THRESHOLD = 0.05f;
    private static final float HEEL_LIFT_WARN  = 0.08f;
    private static final float HEEL_LIFT_ERROR = 0.16f;

    private static final float FRONT_KNEE_CAVE_ERROR = 0.72f;
    private static final float FRONT_KNEE_CAVE_WARN  = 0.85f;
    private static final float FRONT_ASYMMETRY_WARN  = 18f;
    private static final float FRONT_ASYMMETRY_ERROR = 30f;

    private static final float SIDE_THRESHOLD = 0.12f;
    private static final int   STABLE_FRAMES  = 8;

    private static final float EMA_ALPHA = 0.15f;
    private float emaLHipY    = -1f, emaLHipX    = -1f;
    private float emaRHipY    = -1f, emaRHipX    = -1f;
    private float emaLKneeY   = -1f, emaLKneeX   = -1f;
    private float emaRKneeY   = -1f, emaRKneeX   = -1f;
    private float emaLAnkleY  = -1f, emaLAnkleX  = -1f;
    private float emaRAnkleY  = -1f, emaRAnkleX  = -1f;
    private float emaLHeelY   = -1f;
    private float emaRHeelY   = -1f;
    private float emaLFootY   = -1f;
    private float emaRFootY   = -1f;
    private float emaLShoulderY = -1f, emaLShoulderX = -1f;
    private float emaRShoulderY = -1f, emaRShoulderX = -1f;
    private float emaShoulderWidth = -1f;

    private ViewMode currentView   = ViewMode.UNKNOWN;
    private ViewMode candidateView = ViewMode.UNKNOWN;
    private int      candidateCount = 0;

    private enum ViewMode { SIDE, FRONT, UNKNOWN }

    // ═════════════════════════════════════════════════
    @Override
    public String getName() { return "🦵 Выпады"; }

    @Override
    public AnalysisResult analyze(List<NormalizedLandmark> lm) {
        AnalysisResult result = new AnalysisResult();

        if (!isValidData(lm)) {
            result.mainFeedback = "Встаньте полностью в кадр";
            return result;
        }

        if (!anyVisible(lm, LEFT_KNEE, RIGHT_KNEE)) {
            result.mainFeedback = "Не видно колен — отойдите от камеры";
            result.phase = "";
            return result;
        }

        updateEMA(lm);

        ViewMode view = updateView();
        if (view == ViewMode.UNKNOWN) {
            result.mainFeedback = "Встаньте боком или лицом к камере";
            result.phase = "";
            return result;
        }

        if (view == ViewMode.SIDE) {
            analyzeSide(lm, result);
        } else {
            analyzeFront(lm, result);
        }

        result.repCount     = repCount;
        result.mainFeedback = result.errors.isEmpty()
                ? buildFeedback(result.phase, view)
                : result.errors.get(0);

        return result;
    }


    private void analyzeSide(List<NormalizedLandmark> lm,
                             AnalysisResult result) {

        boolean leftIsForward = chooseForwardLegSide(lm);

        int frontHip   = leftIsForward ? LEFT_HIP        : RIGHT_HIP;
        int frontKnee  = leftIsForward ? LEFT_KNEE       : RIGHT_KNEE;
        int frontAnkle = leftIsForward ? LEFT_ANKLE      : RIGHT_ANKLE;
        int frontHeel  = leftIsForward ? LEFT_HEEL       : RIGHT_HEEL;
        int frontToe   = leftIsForward ? LEFT_FOOT_INDEX : RIGHT_FOOT_INDEX;
        int backHip    = leftIsForward ? RIGHT_HIP       : LEFT_HIP;
        int backKnee   = leftIsForward ? RIGHT_KNEE      : LEFT_KNEE;
        int backAnkle  = leftIsForward ? RIGHT_ANKLE     : LEFT_ANKLE;

        float frontKneeAngle = getAngle(lm, frontHip, frontKnee, frontAnkle);

        if (frontKneeAngle < 0) {
            result.mainFeedback = "Не видно суставов — встаньте боком";
            result.phase = "";
            return;
        }


        updatePhase(result, frontKneeAngle);

        Log.d(TAG, "SIDE forward=" + (leftIsForward ? "L" : "R")
                + " frontKnee=" + String.format("%.1f°", frontKneeAngle)
                + " phase=" + result.phase);

        if (result.phase.equals("DOWN")) {

            checkSideFrontKneeDepth(result, frontKneeAngle, frontKnee);

            float backKneeAngle = getAngle(lm, backHip, backKnee, backAnkle);
            if (backKneeAngle >= 0) {
                checkSideBackKneeDepth(result, backKneeAngle, backKnee);
            }


            checkTrunkLeanSide(result, leftIsForward);

            checkKneeOverToe(result, lm, frontKnee, frontAnkle, frontToe);

            checkHeelLiftSide(result, leftIsForward, frontHeel);
        }
    }


    private boolean chooseForwardLegSide(List<NormalizedLandmark> lm) {
        float lAngle = getAngle(lm, LEFT_HIP,  LEFT_KNEE,  LEFT_ANKLE);
        float rAngle = getAngle(lm, RIGHT_HIP, RIGHT_KNEE, RIGHT_ANKLE);

        if (lAngle < 0 && rAngle < 0) return true;
        if (lAngle < 0) return false;
        if (rAngle < 0) return true;


        return lAngle <= rAngle;
    }


    private void checkSideFrontKneeDepth(AnalysisResult result,
                                         float angle,
                                         int kneeIdx) {
        if (angle > SIDE_FRONT_KNEE_SHALLOW) {
            result.addError(
                    "⚠ Опуститесь глубже — согните колено до 90°",
                    kneeIdx);
        } else if (angle > SIDE_FRONT_KNEE_IDEAL_MAX) {
            result.addError(
                    "⚠ Чуть глубже — почти достаточно!",
                    kneeIdx);
        } else if (angle < SIDE_FRONT_KNEE_TOO_DEEP) {
            result.addError(
                    "⚠ Слишком глубокий выпад — поднимитесь чуть выше",
                    kneeIdx);
        }
    }


    private void checkSideBackKneeDepth(AnalysisResult result,
                                        float angle,
                                        int kneeIdx) {
        if (angle > SIDE_BACK_KNEE_SHALLOW) {
            result.addError(
                    "⚠ Опустите заднее колено ближе к полу",
                    kneeIdx);
        } else if (angle > SIDE_BACK_KNEE_IDEAL_MAX) {
            result.addError(
                    "⚠ Заднее колено чуть высоко — опустите немного",
                    kneeIdx);
        }
    }


    private void checkTrunkLeanSide(AnalysisResult result,
                                    boolean leftForward) {
        float shX = leftForward ? emaLShoulderX : emaRShoulderX;
        float shY = leftForward ? emaLShoulderY : emaRShoulderY;
        float hiX = leftForward ? emaLHipX      : emaRHipX;
        float hiY = leftForward ? emaLHipY      : emaRHipY;

        if (shX < 0 || shY < 0 || hiX < 0 || hiY < 0) return;

        double dx    = shX - hiX;
        double dy    = shY - hiY;
        double angle = Math.toDegrees(
                Math.atan2(Math.abs(dx), Math.abs(dy)));

        Log.d(TAG, String.format(
                "TrunkLeanSide: dx=%.3f dy=%.3f angle=%.1f°",
                dx, dy, angle));

        int shIdx = leftForward ? LEFT_SHOULDER : RIGHT_SHOULDER;
        int hiIdx = leftForward ? LEFT_HIP      : RIGHT_HIP;

        if (angle > TRUNK_LEAN_ERROR) {
            result.addError(
                    "⚠ Сильный наклон вперёд — держите спину прямо",
                    shIdx, hiIdx);
        } else if (angle > TRUNK_LEAN_WARN) {
            result.addError(
                    "⚠ Немного наклонены вперёд — выпрямите корпус",
                    shIdx);
        }
    }

    private void checkKneeOverToe(AnalysisResult result,
                                  List<NormalizedLandmark> lm,
                                  int kneeIdx,
                                  int ankleIdx,
                                  int toeIdx) {
        if (!allVisible(lm, kneeIdx, ankleIdx, toeIdx)) return;

        float kneeX  = lm.get(kneeIdx).x();
        float ankleX = lm.get(ankleIdx).x();
        float toeX   = lm.get(toeIdx).x();

        float footLen = Math.abs(toeX - ankleX);
        if (footLen < 0.01f) return;

        float overToe = (kneeX - toeX) / footLen;

        Log.d(TAG, String.format(
                "KneeOverToe: kneeX=%.3f toeX=%.3f overToe=%.3f",
                kneeX, toeX, overToe));

        if (Math.abs(overToe) > KNEE_OVER_TOE_THRESHOLD) {
            result.addError(
                    "⚠ Колено выходит за носок — перенесите вес на пятку",
                    kneeIdx, toeIdx);
        }
    }

    private void checkHeelLiftSide(AnalysisResult result,
                                   boolean leftForward,
                                   int heelIdx) {
        float heelY  = leftForward ? emaLHeelY  : emaRHeelY;
        float footY  = leftForward ? emaLFootY  : emaRFootY;
        float ankleY = leftForward ? emaLAnkleY : emaRAnkleY;

        if (heelY < 0 || footY < 0 || ankleY < 0) return;

        float ankleHeelDist = Math.abs(ankleY - heelY);
        if (ankleHeelDist < 0.01f) ankleHeelDist = 0.05f;

        float liftRatio = (heelY - footY) / ankleHeelDist;

        Log.d(TAG, String.format(
                "HeelLift: heelY=%.3f footY=%.3f liftRatio=%.3f",
                heelY, footY, liftRatio));

        if (liftRatio > HEEL_LIFT_ERROR) {
            result.addError(
                    "⚠ Пятка оторвана от пола — прижмите её",
                    heelIdx);
        } else if (liftRatio > HEEL_LIFT_WARN) {
            result.addError(
                    "⚠ Пятка начинает отрываться — следите за ней",
                    heelIdx);
        }
    }

    private void analyzeFront(List<NormalizedLandmark> lm,
                              AnalysisResult result) {

        float lAngle = getAngle(lm, LEFT_HIP,  LEFT_KNEE,  LEFT_ANKLE);
        float rAngle = getAngle(lm, RIGHT_HIP, RIGHT_KNEE, RIGHT_ANKLE);

        if (lAngle < 0 && rAngle < 0) {
            result.mainFeedback = "Не видно суставов — встаньте прямо";
            result.phase = "";
            return;
        }

        float workingAngle;
        if      (lAngle < 0) workingAngle = rAngle;
        else if (rAngle < 0) workingAngle = lAngle;
        else                 workingAngle = Math.min(lAngle, rAngle);


        updatePhase(result, workingAngle);


        Log.d(TAG, "FRONT lKnee=" + String.format("%.1f°", lAngle)
                + " rKnee=" + String.format("%.1f°", rAngle)
                + " working=" + String.format("%.1f°", workingAngle)
                + " phase=" + result.phase);

        if (result.phase.equals("DOWN")) {

            boolean leftIsWorking = (lAngle >= 0 && lAngle <= workingAngle);
            checkFrontKneeDepth(result, workingAngle, leftIsWorking);

            if (lAngle >= 0 && rAngle >= 0) {
                checkFrontAsymmetry(result, lAngle, rAngle);
            }

            checkFrontKneeCave(result, lm);

            checkTrunkLeanFront(result);

            checkHipTiltFront(result);
        }
    }


    private void checkFrontKneeDepth(AnalysisResult result,
                                     float angle,
                                     boolean leftIsWorking) {
        int kneeIdx = leftIsWorking ? LEFT_KNEE : RIGHT_KNEE;

        if (angle > FRONT_KNEE_SHALLOW) {
            result.addError(
                    "⚠ Опуститесь глубже — согните колено до 90°",
                    kneeIdx);
        } else if (angle > FRONT_KNEE_IDEAL_MAX) {
            result.addError(
                    "⚠ Чуть глубже — почти достаточно!",
                    kneeIdx);
        } else if (angle < FRONT_KNEE_TOO_DEEP) {
            result.addError(
                    "⚠ Слишком глубокий выпад — поднимитесь чуть выше",
                    kneeIdx);
        }
    }


    private void checkFrontAsymmetry(AnalysisResult result,
                                     float lAngle,
                                     float rAngle) {
        float diff = Math.abs(lAngle - rAngle);

        Log.d(TAG, String.format(
                "FrontAsymmetry: L=%.1f° R=%.1f° diff=%.1f°",
                lAngle, rAngle, diff));

        if (diff > FRONT_ASYMMETRY_ERROR) {
            String msg = lAngle > rAngle
                    ? "⚠ Левая нога недостаточно согнута — выровняйте нагрузку"
                    : "⚠ Правая нога недостаточно согнута — выровняйте нагрузку";
            result.addError(msg,
                    lAngle > rAngle ? LEFT_KNEE : RIGHT_KNEE);

        } else if (diff > FRONT_ASYMMETRY_WARN) {
            result.addError(
                    "⚠ Небольшая асимметрия ног — тяните нагрузку равномерно",
                    LEFT_KNEE, RIGHT_KNEE);
        }
    }


    private void checkFrontKneeCave(AnalysisResult result,
                                    List<NormalizedLandmark> lm) {
        if (!allVisible(lm, LEFT_KNEE,  RIGHT_KNEE,
                LEFT_ANKLE, RIGHT_ANKLE)) return;

        float kneeW  = distX(lm, LEFT_KNEE,  RIGHT_KNEE);
        float ankleW = distX(lm, LEFT_ANKLE, RIGHT_ANKLE);

        if (kneeW < 0 || ankleW <= 0) return;

        float ratio = kneeW / ankleW;

        Log.d(TAG, String.format(
                "FrontKneeCave: kneeW=%.3f ankleW=%.3f ratio=%.2f",
                kneeW, ankleW, ratio));

        if (ratio < FRONT_KNEE_CAVE_ERROR) {
            result.addError(
                    "⚠ Колени заваливаются внутрь — разведите по линии стоп",
                    LEFT_KNEE, RIGHT_KNEE);
        } else if (ratio < FRONT_KNEE_CAVE_WARN) {
            result.addError(
                    "⚠ Немного разведите колени в стороны",
                    LEFT_KNEE, RIGHT_KNEE);
        }
    }


    private void checkTrunkLeanFront(AnalysisResult result) {
        float shX = avgEma(emaLShoulderX, emaRShoulderX);
        float shY = avgEma(emaLShoulderY, emaRShoulderY);
        float hiX = avgEma(emaLHipX,      emaRHipX);
        float hiY = avgEma(emaLHipY,      emaRHipY);

        if (shX < 0 || shY < 0 || hiX < 0 || hiY < 0) return;

        double dx    = shX - hiX;
        double dy    = shY - hiY;
        double angle = Math.toDegrees(
                Math.atan2(Math.abs(dx), Math.abs(dy)));

        Log.d(TAG, String.format(
                "TrunkLeanFront: dx=%.3f dy=%.3f angle=%.1f°",
                dx, dy, angle));

        if (angle > TRUNK_LEAN_ERROR) {
            result.addError(
                    "⚠ Сильный наклон вперёд — держите спину прямо",
                    LEFT_SHOULDER, RIGHT_SHOULDER,
                    LEFT_HIP,      RIGHT_HIP);
        } else if (angle > TRUNK_LEAN_WARN) {
            result.addError(
                    "⚠ Немного наклонены вперёд — выпрямите корпус",
                    LEFT_SHOULDER, RIGHT_SHOULDER);
        }
    }


    private void checkHipTiltFront(AnalysisResult result) {
        if (emaLHipY < 0 || emaRHipY < 0) return;

        float diff = Math.abs(emaLHipY - emaRHipY);

        float scale = emaShoulderWidth > 0 ? emaShoulderWidth : 0.15f;
        float tilt  = diff / scale;

        Log.d(TAG, String.format(
                "HipTiltFront: LhipY=%.3f RhipY=%.3f tilt=%.3f",
                emaLHipY, emaRHipY, tilt));

        if (tilt > 0.30f) {
            String msg = emaLHipY > emaRHipY
                    ? "⚠ Левый бок таза опущен — выровняйте таз"
                    : "⚠ Правый бок таза опущен — выровняйте таз";
            result.addError(msg, LEFT_HIP, RIGHT_HIP);
        } else if (tilt > 0.15f) {
            result.addError(
                    "⚠ Таз немного перекошен — держите его ровно",
                    LEFT_HIP, RIGHT_HIP);
        }
    }


    private void updatePhase(AnalysisResult r, float angle) {
        if (angle < PHASE_DOWN_DEEP) {
            if (!isDown) isDown = true;
            r.phase = "DOWN";
        } else if (angle > PHASE_UP_EXIT && isDown) {
            isDown = false;
            repCount++;
            r.phase = "UP";
        } else {
            r.phase = angle < PHASE_DOWN_ENTER ? "DOWN" : "UP";
        }
    }


    private void updateEMA(List<NormalizedLandmark> lm) {
        if (isVisible(lm, LEFT_HIP)) {
            emaLHipY = emaVal(emaLHipY, lm.get(LEFT_HIP).y());
            emaLHipX = emaVal(emaLHipX, lm.get(LEFT_HIP).x());
        }
        if (isVisible(lm, RIGHT_HIP)) {
            emaRHipY = emaVal(emaRHipY, lm.get(RIGHT_HIP).y());
            emaRHipX = emaVal(emaRHipX, lm.get(RIGHT_HIP).x());
        }
        if (isVisible(lm, LEFT_KNEE)) {
            emaLKneeY = emaVal(emaLKneeY, lm.get(LEFT_KNEE).y());
            emaLKneeX = emaVal(emaLKneeX, lm.get(LEFT_KNEE).x());
        }
        if (isVisible(lm, RIGHT_KNEE)) {
            emaRKneeY = emaVal(emaRKneeY, lm.get(RIGHT_KNEE).y());
            emaRKneeX = emaVal(emaRKneeX, lm.get(RIGHT_KNEE).x());
        }
        if (isVisible(lm, LEFT_ANKLE)) {
            emaLAnkleY = emaVal(emaLAnkleY, lm.get(LEFT_ANKLE).y());
            emaLAnkleX = emaVal(emaLAnkleX, lm.get(LEFT_ANKLE).x());
        }
        if (isVisible(lm, RIGHT_ANKLE)) {
            emaRAnkleY = emaVal(emaRAnkleY, lm.get(RIGHT_ANKLE).y());
            emaRAnkleX = emaVal(emaRAnkleX, lm.get(RIGHT_ANKLE).x());
        }
        if (isVisible(lm, LEFT_HEEL)) {
            emaLHeelY = emaVal(emaLHeelY, lm.get(LEFT_HEEL).y());
        }
        if (isVisible(lm, RIGHT_HEEL)) {
            emaRHeelY = emaVal(emaRHeelY, lm.get(RIGHT_HEEL).y());
        }
        if (isVisible(lm, LEFT_FOOT_INDEX)) {
            emaLFootY = emaVal(emaLFootY, lm.get(LEFT_FOOT_INDEX).y());
        }
        if (isVisible(lm, RIGHT_FOOT_INDEX)) {
            emaRFootY = emaVal(emaRFootY, lm.get(RIGHT_FOOT_INDEX).y());
        }
        if (isVisible(lm, LEFT_SHOULDER)) {
            emaLShoulderY = emaVal(emaLShoulderY, lm.get(LEFT_SHOULDER).y());
            emaLShoulderX = emaVal(emaLShoulderX, lm.get(LEFT_SHOULDER).x());
        }
        if (isVisible(lm, RIGHT_SHOULDER)) {
            emaRShoulderY = emaVal(emaRShoulderY, lm.get(RIGHT_SHOULDER).y());
            emaRShoulderX = emaVal(emaRShoulderX, lm.get(RIGHT_SHOULDER).x());
        }
        if (allVisible(lm, LEFT_SHOULDER, RIGHT_SHOULDER)) {
            emaShoulderWidth = emaVal(emaShoulderWidth,
                    distX(lm, LEFT_SHOULDER, RIGHT_SHOULDER));
        }
    }


    private ViewMode updateView() {
        ViewMode raw = emaShoulderWidth > 0
                ? (emaShoulderWidth < SIDE_THRESHOLD
                ? ViewMode.SIDE : ViewMode.FRONT)
                : ViewMode.UNKNOWN;

        if (raw == ViewMode.UNKNOWN) {
            return currentView != ViewMode.UNKNOWN
                    ? currentView : ViewMode.UNKNOWN;
        }
        if (raw == candidateView) {
            candidateCount++;
        } else {
            candidateView  = raw;
            candidateCount = 1;
        }
        if (candidateCount >= STABLE_FRAMES
                && currentView != candidateView) {
            Log.d(TAG, "View: " + currentView + " → " + candidateView);
            currentView = candidateView;
        }
        return currentView != ViewMode.UNKNOWN ? currentView : raw;
    }


    private float emaVal(float prev, float newVal) {
        if (newVal < 0 || newVal > 1f
                || Float.isNaN(newVal)
                || Float.isInfinite(newVal)) return prev;
        if (prev < 0) return newVal;
        return prev + EMA_ALPHA * (newVal - prev);
    }


    private float avgEma(float a, float b) {
        if (a < 0 && b < 0) return -1f;
        if (a < 0) return b;
        if (b < 0) return a;
        return (a + b) / 2f;
    }

    private String buildFeedback(String phase, ViewMode view) {
        String hint = view == ViewMode.FRONT
                ? " (для анализа пятки встаньте боком)" : "";
        switch (phase) {
            case "DOWN": return "✅ Хорошо! Держите позицию" + hint;
            case "UP":   return "✅ Повторений: " + repCount + hint;
            default:     return "✅ Начните выпад" + hint;
        }
    }


    @Override
    public void reset() {
        super.reset();
        emaLHipY    = emaLHipX    = emaRHipY    = emaRHipX    = -1f;
        emaLKneeY   = emaLKneeX   = emaRKneeY   = emaRKneeX   = -1f;
        emaLAnkleY  = emaLAnkleX  = emaRAnkleY  = emaRAnkleX  = -1f;
        emaLHeelY   = emaRHeelY   = emaLFootY   = emaRFootY   = -1f;
        emaLShoulderY = emaLShoulderX = emaRShoulderY = emaRShoulderX = -1f;
        emaShoulderWidth = -1f;
        currentView    = ViewMode.UNKNOWN;
        candidateView  = ViewMode.UNKNOWN;
        candidateCount = 0;
    }
}