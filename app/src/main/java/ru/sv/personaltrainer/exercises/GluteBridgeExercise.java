package ru.sv.personaltrainer.exercises;

import android.util.Log;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;

import java.util.List;

import ru.sv.personaltrainer.R;

public class GluteBridgeExercise extends BaseExercise {

    private static final String TAG = "GluteBridgeExercise";

    private static final float PHASE_UP_ENTER = 0.07f;
    private static final float PHASE_DOWN_EXIT = 0.03f;

    private static final float HIP_RISE_GOOD = 0.15f;
    private static final float HIP_POS_SAG_WARN = 0.10f;
    private static final float HIP_POS_SAG_ERROR = 0.20f;
    private static final float HIP_POS_HIGH_WARN = -0.10f;
    private static final float HIP_POS_HIGH_ERROR = -0.20f;


    private static final float KNEE_TOO_CLOSE = 65f;
    private static final float KNEE_TOO_FAR = 120f;

    private static final float HEEL_LIFT_WARN = 0.04f;
    private static final float HEEL_LIFT_ERROR = 0.5f;

    private static final float ARM_LIFT_WARN = 0.04f;
    private static final float ARM_LIFT_ERROR = 0.08f;
    private static final float WRIST_LIFT_WARN = 0.04f;
    private static final float WRIST_LIFT_ERROR = 0.08f;

    private static final float SHOULDER_LIFT_WARN = 0.03f;
    private static final float SHOULDER_LIFT_ERROR = 0.06f;

    private static final float SIDE_THRESHOLD = 0.12f;
    private static final int STABLE_FRAMES = 8;
    private static final float EMA_ALPHA = 0.15f;

    private float emaLShoulderY = -1f, emaLShoulderX = -1f;
    private float emaRShoulderY = -1f, emaRShoulderX = -1f;
    private float emaLHipY = -1f;
    private float emaRHipY = -1f;
    private float emaLKneeY = -1f;
    private float emaRKneeY = -1f;
    private float emaLAnkleY = -1f;
    private float emaRAnkleY = -1f;
    private float emaLHeelY = -1f;
    private float emaRHeelY = -1f;
    private float emaLElbowY = -1f;
    private float emaRElbowY = -1f;
    private float emaShoulderWidth = -1f;

    private float baseShoulderY = -1f;
    private float baseHipY = -1f;
    private float baseHeelY = -1f;
    private int baseFrameCount = 0;
    private static final int BASE_FRAMES = 30;

    private boolean baselineCaptured = false;

    private ViewMode currentView = ViewMode.UNKNOWN;
    private ViewMode candidateView = ViewMode.UNKNOWN;
    private int candidateCount = 0;

    private enum ViewMode {SIDE, FRONT, UNKNOWN}

    @Override
    public String getName() {
        return getString(R.string.exercise_glute_bridge_name);
    }

    @Override
    public AnalysisResult analyze(List<NormalizedLandmark> lm) {
        AnalysisResult result = new AnalysisResult();

        if (!isValidData(lm)) {
            result.mainFeedback = getString(R.string.msg_full_frame);
            return result;
        }

        if (!anyVisible(lm, LEFT_HIP, RIGHT_HIP)) {
            result.mainFeedback = getString(R.string.msg_not_visible_hips);
            result.phase = "";
            return result;
        }

        updateEMA(lm);
        updateView();

        if (!baselineCaptured) {
            captureBaseline();
            result.mainFeedback = baselineCaptured
                    ? getString(R.string.feedback_glute_ready)
                    : String.format(getString(R.string.msg_calibration_progress),
                    baseFrameCount * 100 / BASE_FRAMES);
            result.phase = "";
            return result;
        }

        float currentHipY = getAvgHipY();
        if (currentHipY < 0) {
            result.mainFeedback = getString(R.string.msg_not_visible_hips);
            result.phase = "";
            return result;
        }


        float hipRise = baseHipY - currentHipY;

        Log.d(TAG, String.format(
                "baseHipY=%.3f curHipY=%.3f hipRise=%.3f",
                baseHipY, currentHipY, hipRise));

        updatePhase(result, hipRise);

        if (result.phase.equals("UP")) {
            checkShouldersOnFloor(result);
            checkHeelsOnFloor(result);
            checkArmsOnFloor(result, lm);
            checkWristsOnFloor(result, lm);
            checkHipAlignment(result, lm);
        }

        if (result.phase.equals("DOWN")) {
            float avgKneeAngle = getAvgKneeAngle(lm);
            if (avgKneeAngle >= 0) {
                checkKneeStartPosition(result, avgKneeAngle);
            }
        }

        result.repCount = repCount;
        result.mainFeedback = result.errors.isEmpty()
                ? buildFeedback(result.phase, hipRise)
                : result.errors.get(0);

        return result;
    }


    private void updatePhase(AnalysisResult r, float hipRise) {
        if (hipRise > PHASE_UP_ENTER) {
            if (!isDown) isDown = true;
            r.phase = "UP";
        } else if (hipRise < PHASE_DOWN_EXIT && isDown) {
            isDown = false;
            repCount++;
            r.phase = "DOWN";
        } else {
            r.phase = isDown ? "UP" : "DOWN";
        }

        Log.d(TAG, String.format(
                "Phase: hipRise=%.3f → %s reps=%d",
                hipRise, r.phase, repCount));
    }


    private void checkHipAlignment(AnalysisResult result,
                                   List<NormalizedLandmark> lm) {
        float shoulderY = getAvgShoulderY();
        float kneeY = getAvgKneeY();
        float hipY = getAvgHipY();

        if (shoulderY < 0 || kneeY < 0 || hipY < 0) return;

        float expectedHipY = (kneeY + shoulderY) / 2f;
        float span = Math.abs(kneeY - shoulderY);

        if (span < 0.05f) return;

        float deviation = (hipY - expectedHipY) / span;

        Log.d(TAG, String.format(
                "HipAlign: shY=%.3f knY=%.3f "
                        + "exp=%.3f real=%.3f dev=%.3f",
                shoulderY, kneeY, expectedHipY, hipY, deviation));

        if (deviation > HIP_POS_SAG_ERROR) {
            result.addError(
                    getString(R.string.error_glute_hips_sag_strong),
                    LEFT_HIP, RIGHT_HIP);
        } else if (deviation > HIP_POS_SAG_WARN) {
            result.addError(
                    getString(R.string.error_glute_hips_sag_weak),
                    LEFT_HIP, RIGHT_HIP);
        } else if (deviation < HIP_POS_HIGH_ERROR) {
            result.addError(
                    getString(R.string.error_glute_hips_high_strong),
                    LEFT_HIP, RIGHT_HIP);
        } else if (deviation < HIP_POS_HIGH_WARN) {
            result.addError(
                    getString(R.string.error_glute_hips_high_weak),
                    LEFT_HIP, RIGHT_HIP);
        }
    }


    private void checkKneeStartPosition(AnalysisResult result,
                                        float angle) {
        if (angle > KNEE_TOO_FAR) {
            result.addError(
                    getString(R.string.error_glute_knee_far),
                    LEFT_KNEE, RIGHT_KNEE);
        } else if (angle < KNEE_TOO_CLOSE) {
            result.addError(
                    getString(R.string.error_glute_knee_close),
                    LEFT_KNEE, RIGHT_KNEE);
        }
    }


    private void checkShouldersOnFloor(AnalysisResult result) {
        if (baseShoulderY < 0) return;
        float currentShY = getAvgShoulderY();
        if (currentShY < 0) return;

        float lift = baseShoulderY - currentShY;

        Log.d(TAG, String.format(
                "Shoulder: base=%.3f cur=%.3f lift=%.3f",
                baseShoulderY, currentShY, lift));

        if (lift > SHOULDER_LIFT_ERROR) {
            result.addError(
                    getString(R.string.error_glute_shoulders_lift_strong),
                    LEFT_SHOULDER, RIGHT_SHOULDER);
        } else if (lift > SHOULDER_LIFT_WARN) {
            result.addError(
                    getString(R.string.error_glute_shoulders_lift_weak),
                    LEFT_SHOULDER, RIGHT_SHOULDER);
        }
    }


    private void checkHeelsOnFloor(AnalysisResult result) {
        if (baseHeelY < 0) return;

        if (emaLHeelY > 0) {
            float lift = baseHeelY - emaLHeelY;
            Log.d(TAG, String.format("LHeel lift=%.3f", lift));
            if (lift > HEEL_LIFT_ERROR) {
                result.addError(
                        getString(R.string.error_glute_heel_lift_left_strong),
                        LEFT_HEEL);
            } else if (lift > HEEL_LIFT_WARN) {
                result.addError(
                        getString(R.string.error_glute_heel_lift_left_weak),
                        LEFT_HEEL);
            }
        }

        if (emaRHeelY > 0) {
            float lift = baseHeelY - emaRHeelY;
            Log.d(TAG, String.format("RHeel lift=%.3f", lift));
            if (lift > HEEL_LIFT_ERROR) {
                result.addError(
                        getString(R.string.error_glute_heel_lift_right_strong),
                        RIGHT_HEEL);
            } else if (lift > HEEL_LIFT_WARN) {
                result.addError(
                        getString(R.string.error_glute_heel_lift_right_weak),
                        RIGHT_HEEL);
            }
        }
    }

    private void checkWristsOnFloor(AnalysisResult result,
                                    List<NormalizedLandmark> lm) {
        if (isVisible(lm, LEFT_WRIST)
                && isVisible(lm, LEFT_ELBOW)) {
            float wristY = lm.get(LEFT_WRIST).y();
            float elbowY = lm.get(LEFT_ELBOW).y();
            float lift = elbowY - wristY;

            Log.d(TAG, String.format(
                    "LWrist: wristY=%.3f elbowY=%.3f lift=%.3f",
                    wristY, elbowY, lift));

            if (lift > WRIST_LIFT_ERROR) {
                result.addError(
                        getString(R.string.error_glute_wrist_lift_left_strong),
                        LEFT_WRIST);
            } else if (lift > WRIST_LIFT_WARN) {
                result.addError(
                        getString(R.string.error_glute_wrist_lift_left_weak),
                        LEFT_WRIST);
            }
        }


        if (isVisible(lm, RIGHT_WRIST)
                && isVisible(lm, RIGHT_ELBOW)) {
            float wristY = lm.get(RIGHT_WRIST).y();
            float elbowY = lm.get(RIGHT_ELBOW).y();
            float lift = elbowY - wristY;

            Log.d(TAG, String.format(
                    "RWrist: wristY=%.3f elbowY=%.3f lift=%.3f",
                    wristY, elbowY, lift));

            if (lift > WRIST_LIFT_ERROR) {
                result.addError(
                        getString(R.string.error_glute_wrist_lift_right_strong),
                        RIGHT_WRIST);
            } else if (lift > WRIST_LIFT_WARN) {
                result.addError(
                        getString(R.string.error_glute_wrist_lift_right_weak),
                        RIGHT_WRIST);
            }
        }
    }


    private void checkArmsOnFloor(AnalysisResult result,
                                  List<NormalizedLandmark> lm) {

        if (isVisible(lm, LEFT_ELBOW)
                && isVisible(lm, LEFT_SHOULDER)) {

            float elbowY = lm.get(LEFT_ELBOW).y();
            float shoulderY = lm.get(LEFT_SHOULDER).y();
            float lift = shoulderY - elbowY;

            Log.d(TAG, String.format(
                    "LArmFloor: elbowY=%.3f shY=%.3f lift=%.3f",
                    elbowY, shoulderY, lift));

            if (lift > ARM_LIFT_ERROR) {
                result.addError(
                        getString(R.string.error_glute_arm_lift_left_strong),
                        LEFT_ELBOW, LEFT_SHOULDER);
            } else if (lift > ARM_LIFT_WARN) {
                result.addError(
                        getString(R.string.error_glute_arm_lift_left_weak),
                        LEFT_ELBOW);
            }
        }


        if (isVisible(lm, RIGHT_ELBOW)
                && isVisible(lm, RIGHT_SHOULDER)) {

            float elbowY = lm.get(RIGHT_ELBOW).y();
            float shoulderY = lm.get(RIGHT_SHOULDER).y();
            float lift = shoulderY - elbowY;

            Log.d(TAG, String.format(
                    "RArmFloor: elbowY=%.3f shY=%.3f lift=%.3f",
                    elbowY, shoulderY, lift));

            if (lift > ARM_LIFT_ERROR) {
                result.addError(
                        getString(R.string.error_glute_arm_lift_right_strong),
                        RIGHT_ELBOW, RIGHT_SHOULDER);
            } else if (lift > ARM_LIFT_WARN) {
                result.addError(
                        getString(R.string.error_glute_arm_lift_right_weak),
                        RIGHT_ELBOW);
            }
        }
    }


    private void captureBaseline() {
        if (baseFrameCount >= BASE_FRAMES) {
            baselineCaptured = true;
            return;
        }

        float hipY = getAvgHipY();
        float heelY = getAvgHeelY();
        float shY = getAvgShoulderY();

        if (hipY < 0 || heelY < 0 || shY < 0) return;


        if (baseHipY > 0 && (baseHipY - hipY) > PHASE_UP_ENTER) {
            Log.d(TAG, "Baseline: таз поднят, пропускаем кадр");
            return;
        }

        if (baseFrameCount == 0) {
            baseHipY = hipY;
            baseHeelY = heelY;
            baseShoulderY = shY;
        } else {
            baseHipY = baseHipY * 0.8f + hipY * 0.2f;
            baseHeelY = baseHeelY * 0.8f + heelY * 0.2f;
            baseShoulderY = baseShoulderY * 0.8f + shY * 0.2f;
        }
        baseFrameCount++;

        Log.d(TAG, String.format(
                "Baseline[%d/%d]: hipY=%.3f heelY=%.3f shY=%.3f",
                baseFrameCount, BASE_FRAMES,
                baseHipY, baseHeelY, baseShoulderY));

        if (baseFrameCount >= BASE_FRAMES) {
            baselineCaptured = true;
            Log.d(TAG, "Baseline DONE");
        }
    }


    private float getAvgHipY() {
        if (emaLHipY > 0 && emaRHipY > 0)
            return (emaLHipY + emaRHipY) / 2f;
        if (emaLHipY > 0) return emaLHipY;
        if (emaRHipY > 0) return emaRHipY;
        return -1f;
    }

    private float getAvgHeelY() {
        if (emaLHeelY > 0 && emaRHeelY > 0)
            return (emaLHeelY + emaRHeelY) / 2f;
        if (emaLHeelY > 0) return emaLHeelY;
        if (emaRHeelY > 0) return emaRHeelY;
        if (emaLAnkleY > 0 && emaRAnkleY > 0)
            return (emaLAnkleY + emaRAnkleY) / 2f;
        if (emaLAnkleY > 0) return emaLAnkleY;
        if (emaRAnkleY > 0) return emaRAnkleY;
        return -1f;
    }

    private float getAvgShoulderY() {
        if (emaLShoulderY > 0 && emaRShoulderY > 0)
            return (emaLShoulderY + emaRShoulderY) / 2f;
        if (emaLShoulderY > 0) return emaLShoulderY;
        if (emaRShoulderY > 0) return emaRShoulderY;
        return -1f;
    }

    private float getAvgKneeY() {
        if (emaLKneeY > 0 && emaRKneeY > 0)
            return (emaLKneeY + emaRKneeY) / 2f;
        if (emaLKneeY > 0) return emaLKneeY;
        if (emaRKneeY > 0) return emaRKneeY;
        return -1f;
    }

    private float getAvgKneeAngle(List<NormalizedLandmark> lm) {
        float lA = getAngle(lm, LEFT_HIP, LEFT_KNEE, LEFT_ANKLE);
        float rA = getAngle(lm, RIGHT_HIP, RIGHT_KNEE, RIGHT_ANKLE);
        if (lA < 0 && rA < 0) return -1f;
        if (lA < 0) return rA;
        if (rA < 0) return lA;
        return (lA + rA) / 2f;
    }

    private String buildFeedback(String phase, float hipRise) {
        switch (phase) {
            case "UP":
                return hipRise >= HIP_RISE_GOOD
                        ? getString(R.string.feedback_glute_up_good)
                        : getString(R.string.feedback_glute_up_higher);
            case "DOWN":
                return repCount > 0
                        ? getString(R.string.feedback_glute_down, repCount)
                        : getString(R.string.feedback_glute_start);
            default:
                return getString(R.string.feedback_glute_lie_down);
        }
    }


    private void updateEMA(List<NormalizedLandmark> lm) {
        if (isVisible(lm, LEFT_SHOULDER)) {
            emaLShoulderY = emaVal(emaLShoulderY,
                    lm.get(LEFT_SHOULDER).y());
            emaLShoulderX = emaVal(emaLShoulderX,
                    lm.get(LEFT_SHOULDER).x());
        }
        if (isVisible(lm, RIGHT_SHOULDER)) {
            emaRShoulderY = emaVal(emaRShoulderY,
                    lm.get(RIGHT_SHOULDER).y());
            emaRShoulderX = emaVal(emaRShoulderX,
                    lm.get(RIGHT_SHOULDER).x());
        }
        if (isVisible(lm, LEFT_HIP))
            emaLHipY = emaVal(emaLHipY, lm.get(LEFT_HIP).y());
        if (isVisible(lm, RIGHT_HIP))
            emaRHipY = emaVal(emaRHipY, lm.get(RIGHT_HIP).y());
        if (isVisible(lm, LEFT_KNEE))
            emaLKneeY = emaVal(emaLKneeY, lm.get(LEFT_KNEE).y());
        if (isVisible(lm, RIGHT_KNEE))
            emaRKneeY = emaVal(emaRKneeY, lm.get(RIGHT_KNEE).y());
        if (isVisible(lm, LEFT_ANKLE))
            emaLAnkleY = emaVal(emaLAnkleY, lm.get(LEFT_ANKLE).y());
        if (isVisible(lm, RIGHT_ANKLE))
            emaRAnkleY = emaVal(emaRAnkleY, lm.get(RIGHT_ANKLE).y());
        if (isVisible(lm, LEFT_HEEL))
            emaLHeelY = emaVal(emaLHeelY, lm.get(LEFT_HEEL).y());
        if (isVisible(lm, RIGHT_HEEL))
            emaRHeelY = emaVal(emaRHeelY, lm.get(RIGHT_HEEL).y());
        if (isVisible(lm, LEFT_ELBOW))
            emaLElbowY = emaVal(emaLElbowY, lm.get(LEFT_ELBOW).y());
        if (isVisible(lm, RIGHT_ELBOW))
            emaRElbowY = emaVal(emaRElbowY, lm.get(RIGHT_ELBOW).y());
        if (allVisible(lm, LEFT_SHOULDER, RIGHT_SHOULDER))
            emaShoulderWidth = emaVal(emaShoulderWidth,
                    distX(lm, LEFT_SHOULDER, RIGHT_SHOULDER));
    }


    private void updateView() {
        ViewMode raw = emaShoulderWidth > 0
                ? (emaShoulderWidth < SIDE_THRESHOLD
                ? ViewMode.SIDE : ViewMode.FRONT)
                : ViewMode.UNKNOWN;

        if (raw == ViewMode.UNKNOWN) return;

        if (raw == candidateView) {
            candidateCount++;
        } else {
            candidateView = raw;
            candidateCount = 1;
        }
        if (candidateCount >= STABLE_FRAMES
                && currentView != candidateView) {
            Log.d(TAG, "View: " + currentView + " → " + candidateView);
            currentView = candidateView;
        }
    }

    private float emaVal(float prev, float newVal) {
        if (newVal < 0 || newVal > 1f
                || Float.isNaN(newVal)
                || Float.isInfinite(newVal)) return prev;
        if (prev < 0) return newVal;
        return prev + EMA_ALPHA * (newVal - prev);
    }


    @Override
    public void reset() {
        super.reset();
        emaLShoulderY = emaLShoulderX = emaRShoulderY = emaRShoulderX = -1f;
        emaLHipY = emaRHipY = -1f;
        emaLKneeY = emaRKneeY = -1f;
        emaLAnkleY = emaRAnkleY = -1f;
        emaLHeelY = emaRHeelY = -1f;
        emaLElbowY = emaRElbowY = -1f;
        emaShoulderWidth = -1f;
        baseShoulderY = -1f;
        baseHipY = -1f;
        baseHeelY = -1f;
        baseFrameCount = 0;
        baselineCaptured = false;
        currentView = ViewMode.UNKNOWN;
        candidateView = ViewMode.UNKNOWN;
        candidateCount = 0;
    }
}