package ru.sv.personaltrainer.exercises;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;

import java.util.List;

import ru.sv.personaltrainer.R;

public class PlankExercise extends BaseExercise {

    private static final float HIP_SAG_WARN = 0.15f;
    private static final float HIP_SAG_ERROR = 0.25f;
    private static final float HIP_HIGH_WARN = -0.20f;
    private static final float HIP_HIGH_ERROR = -0.35f;

    private static final float HEAD_NEUTRAL = 0.15f;
    private static final float HEAD_DROP_WARN = 0.25f;
    private static final float HEAD_DROP_ERROR = 0.40f;
    private static final float HEAD_HIGH_WARN = 0.25f;
    private static final float HEAD_HIGH_ERROR = 0.40f;

    private static final float ARM_BODY_ANGLE_WARN = 15f;
    private static final float ARM_BODY_ANGLE_ERROR = 25f;

    private static final float SHOULDER_TILT_WARN = 0.03f;
    private static final float SHOULDER_TILT_ERROR = 0.06f;
    private static final float KNEE_TILT_WARN = 0.05f;
    private static final float KNEE_TILT_ERROR = 0.10f;

    private static final float SIDE_THRESHOLD = 0.12f;
    private static final int STABLE_FRAMES = 8;
    private static final float EMA_ALPHA = 0.15f;
    private static final long ERROR_TOLERANCE_MS = 500L;

    private long cleanStreakStartMs = -1L;
    private long errorStartMs = -1L;
    private long accumulatedTimeMs = 0;
    private int bestHoldSeconds = 0;
    private int currentSeconds = 0;

    private float emaShoulderY = -1f, emaShoulderX = -1f;
    private float emaLShoulderY = -1f, emaRShoulderY = -1f;
    private float emaHipY = -1f;
    private float emaKneeY = -1f, emaKneeX = -1f;
    private float emaLKneeY = -1f, emaRKneeY = -1f;
    private boolean hasLKnee = false, hasRKnee = false;
    private float emaAnkleY = -1f, emaAnkleX = -1f;
    private float emaNoseY = -1f;
    private float emaWristX = -1f, emaWristY = -1f;
    private float emaShoulderWidth = -1f;

    private ViewMode currentView = ViewMode.UNKNOWN;
    private ViewMode candidateView = ViewMode.UNKNOWN;
    private int candidateCount = 0;

    private enum ViewMode {SIDE, FRONT, UNKNOWN}

    @Override
    public String getName() {
        return getString(R.string.exercise_plank_name);
    }

    @Override
    public AnalysisResult analyze(List<NormalizedLandmark> lm) {
        AnalysisResult result = new AnalysisResult();
        result.phase = "HOLD";

        if (!isValidData(lm)) {
            result.mainFeedback = getString(R.string.msg_full_frame);
            stopCleanStreak();
            result.holdSeconds = currentSeconds;
            return result;
        }

        if (!allVisible(lm, LEFT_SHOULDER, RIGHT_SHOULDER)) {
            result.mainFeedback = getString(R.string.msg_camera_shoulders);
            result.phase = "";
            stopCleanStreak();
            result.holdSeconds = currentSeconds;
            return result;
        }

        updateEMA(lm);

        ViewMode view = updateView();
        if (view == ViewMode.UNKNOWN) {
            result.mainFeedback = getString(R.string.msg_stand_side_or_front);
            result.phase = "";
            stopCleanStreak();
            result.holdSeconds = currentSeconds;
            return result;
        }


        if (view == ViewMode.SIDE) {
            analyzeSide(lm, result);
        } else {
            analyzeFront(lm, result);
        }

        updateTimer(result);

        result.repCount = repCount;
        result.holdSeconds = currentSeconds;
        result.mainFeedback = result.errors.isEmpty() ? buildFeedback() : result.errors.get(0);

        return result;
    }


    private void updateTimer(AnalysisResult result) {
        long nowMs = System.currentTimeMillis();
        boolean hasErrors = !result.errors.isEmpty();

        if (hasErrors) {
            if (errorStartMs < 0) {
                errorStartMs = nowMs;
            }
            long errorDuration = nowMs - errorStartMs;

            if (errorDuration > ERROR_TOLERANCE_MS && cleanStreakStartMs >= 0) {
                long sessionDuration = errorStartMs - cleanStreakStartMs;
                accumulatedTimeMs += sessionDuration;

                int sessionSeconds = (int) (sessionDuration / 1000);
                if (sessionSeconds > bestHoldSeconds) {
                    bestHoldSeconds = sessionSeconds;
                }

                cleanStreakStartMs = -1L;
            }
        } else {
            if (errorStartMs >= 0) {
                long errorDuration = nowMs - errorStartMs;

                if (errorDuration <= ERROR_TOLERANCE_MS) {
                } else {
                    if (cleanStreakStartMs >= 0) {
                        long sessionDuration = errorStartMs - cleanStreakStartMs;
                        accumulatedTimeMs += sessionDuration;

                        int sessionSeconds = (int) (sessionDuration / 1000);
                        if (sessionSeconds > bestHoldSeconds) {
                            bestHoldSeconds = sessionSeconds;
                        }
                    }
                    cleanStreakStartMs = nowMs;
                }
                errorStartMs = -1L;
            }

            if (cleanStreakStartMs < 0) {
                cleanStreakStartMs = nowMs;
            }

            long currentSessionDuration = nowMs - cleanStreakStartMs;
            long totalTimeMs = accumulatedTimeMs + currentSessionDuration;
            currentSeconds = (int) (totalTimeMs / 1000);

            int currentSessionSeconds = (int) (currentSessionDuration / 1000);
            if (currentSessionSeconds > bestHoldSeconds) {
                bestHoldSeconds = currentSessionSeconds;
            }
        }

    }

    private void stopCleanStreak() {
        if (cleanStreakStartMs >= 0) {
            long nowMs = System.currentTimeMillis();
            long sessionDuration;

            if (errorStartMs >= 0 && errorStartMs > cleanStreakStartMs) {
                sessionDuration = errorStartMs - cleanStreakStartMs;
            } else {
                sessionDuration = nowMs - cleanStreakStartMs;
            }

            accumulatedTimeMs += sessionDuration;
            int sessionSeconds = (int) (sessionDuration / 1000);
            if (sessionSeconds > bestHoldSeconds) {
                bestHoldSeconds = sessionSeconds;
            }
        }
        cleanStreakStartMs = -1L;
        errorStartMs = -1L;
        currentSeconds = (int) (accumulatedTimeMs / 1000);
    }

    private String buildFeedback() {
        if (bestHoldSeconds == 0) {
            return getString(R.string.feedback_plank_hold);
        }
        return getString(R.string.feedback_plank_best_time, bestHoldSeconds);
    }


    private void analyzeSide(List<NormalizedLandmark> lm, AnalysisResult result) {
        if (emaHipY > 0 && emaShoulderY > 0) {
            checkBodyLineSide(result);
        } else {
        }

        boolean hasWrist = emaWristX > 0 && emaWristY > 0;
        boolean hasBody = (emaAnkleX > 0 && emaAnkleY > 0) || (emaKneeX > 0 && emaKneeY > 0);

        if (emaShoulderX > 0 && emaShoulderY > 0 && hasWrist && hasBody) {
            checkArmBodyAngle(result);
        }

        if (emaNoseY > 0 && emaShoulderY > 0 && emaShoulderWidth > 0) {
            checkHead(result);
        }
    }

    private void analyzeFront(List<NormalizedLandmark> lm, AnalysisResult result) {
        if (emaLShoulderY > 0 && emaRShoulderY > 0) {
            checkShoulderTilt(result);
        }
        if (hasLKnee && hasRKnee) {
            checkKneeTilt(result);
        }
        if (isVisible(lm, NOSE) && allVisible(lm, LEFT_SHOULDER, RIGHT_SHOULDER)) {
            checkHeadCenter(lm, result);
        }
    }

    private void checkBodyLineSide(AnalysisResult result) {
        float diff = emaHipY - emaShoulderY;
        float scale = (emaKneeY > 0 && emaShoulderY > 0) ? Math.abs(emaKneeY - emaShoulderY) : 0.20f;
        if (scale < 0.05f) scale = 0.10f;

        float deviation = diff / scale;


        if (deviation > HIP_SAG_ERROR) {
            result.addError(getString(R.string.error_plank_hips_sag_strong), LEFT_HIP, RIGHT_HIP);
        } else if (deviation > HIP_SAG_WARN) {
            result.addError(getString(R.string.error_plank_hips_sag_weak), LEFT_HIP, RIGHT_HIP);
        } else if (deviation < HIP_HIGH_ERROR) {
            result.addError(getString(R.string.error_plank_hips_high_strong), LEFT_HIP, RIGHT_HIP);
        } else if (deviation < HIP_HIGH_WARN) {
            result.addError(getString(R.string.error_plank_hips_high_weak), LEFT_HIP, RIGHT_HIP);
        }
    }

    private void checkArmBodyAngle(AnalysisResult result) {
        float bodyEndY = emaAnkleY > 0 ? emaAnkleY : emaKneeY;
        float bodyEndX = emaAnkleX > 0 ? emaAnkleX : emaKneeX;
        if (bodyEndY <= 0 || bodyEndX <= 0) return;

        double bodyDx = bodyEndX - emaShoulderX;
        double bodyDy = bodyEndY - emaShoulderY;
        double armDx = emaShoulderX - emaWristX;
        double armDy = emaShoulderY - emaWristY;

        double bodyLen = Math.sqrt(bodyDx * bodyDx + bodyDy * bodyDy);
        double armLen = Math.sqrt(armDx * armDx + armDy * armDy);
        if (bodyLen < 0.01 || armLen < 0.01) return;

        double bodyAngle = Math.toDegrees(Math.atan2(bodyDy, bodyDx));
        double armAngle = Math.toDegrees(Math.atan2(armDy, armDx));
        double angleBetween = Math.abs(bodyAngle - armAngle);
        if (angleBetween > 180.0) angleBetween = 360.0 - angleBetween;
        double diff = Math.abs(angleBetween - 90.0);

        if (diff > ARM_BODY_ANGLE_ERROR) {
            result.addError(angleBetween < 90.0 ? getString(R.string.error_plank_shoulders_forward) : getString(R.string.error_plank_shoulders_back), LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_WRIST, RIGHT_WRIST);
        } else if (diff > ARM_BODY_ANGLE_WARN) {
            result.addError(getString(R.string.error_plank_arms_adjust), LEFT_SHOULDER, RIGHT_SHOULDER);
        }
    }

    private void checkHead(AnalysisResult result) {
        float scale = emaShoulderWidth > 0.02f ? emaShoulderWidth : 0.15f;
        float headDiff = emaNoseY - emaShoulderY;
        float deviation = headDiff / scale;

        if (Math.abs(deviation) <= HEAD_NEUTRAL) return;

        if (deviation > HEAD_DROP_ERROR) {
            result.addError(getString(R.string.error_plank_head_down_strong), NOSE);
        } else if (deviation > HEAD_DROP_WARN) {
            result.addError(getString(R.string.error_plank_head_down_weak), NOSE);
        } else if (deviation < -HEAD_HIGH_ERROR) {
            result.addError(getString(R.string.error_plank_head_up_strong), NOSE);
        } else if (deviation < -HEAD_HIGH_WARN) {
            result.addError(getString(R.string.error_plank_head_up_weak), NOSE);
        }
    }

    private void checkShoulderTilt(AnalysisResult result) {
        float diff = emaLShoulderY - emaRShoulderY;

        if (Math.abs(diff) > SHOULDER_TILT_ERROR) {
            result.addError(diff > 0 ? getString(R.string.error_plank_shoulder_tilt_left) : getString(R.string.error_plank_shoulder_tilt_right), LEFT_SHOULDER, RIGHT_SHOULDER);
        } else if (Math.abs(diff) > SHOULDER_TILT_WARN) {
            result.addError(getString(R.string.error_plank_shoulder_tilt_weak), LEFT_SHOULDER, RIGHT_SHOULDER);
        }
    }

    private void checkKneeTilt(AnalysisResult result) {
        float diff = emaLKneeY - emaRKneeY;

        if (Math.abs(diff) > KNEE_TILT_ERROR) {
            result.addError(diff > 0 ? getString(R.string.error_plank_knee_tilt_left) : getString(R.string.error_plank_knee_tilt_right), LEFT_KNEE, RIGHT_KNEE);
        } else if (Math.abs(diff) > KNEE_TILT_WARN) {
            result.addError(getString(R.string.error_plank_knee_tilt_weak), LEFT_KNEE, RIGHT_KNEE);
        }
    }

    private void checkHeadCenter(List<NormalizedLandmark> lm, AnalysisResult result) {
        float noseX = lm.get(NOSE).x();
        float lShX = lm.get(LEFT_SHOULDER).x();
        float rShX = lm.get(RIGHT_SHOULDER).x();
        float midShoulderX = (lShX + rShX) / 2f;
        float shoulderW = Math.abs(lShX - rShX);
        if (shoulderW < 0.02f) return;

        float offset = (noseX - midShoulderX) / shoulderW;
        if (Math.abs(offset) > 0.25f) {
            result.addError(getString(R.string.error_plank_head_side), NOSE);
        }
    }

    public int getBestHoldSeconds() {
        return bestHoldSeconds;
    }


    private void updateEMA(List<NormalizedLandmark> lm) {
        if (allVisible(lm, LEFT_HIP, RIGHT_HIP)) {
            emaHipY = emaVal(emaHipY, (lm.get(LEFT_HIP).y() + lm.get(RIGHT_HIP).y()) / 2f);
        } else if (isVisible(lm, LEFT_HIP)) {
            emaHipY = emaVal(emaHipY, lm.get(LEFT_HIP).y());
        } else if (isVisible(lm, RIGHT_HIP)) {
            emaHipY = emaVal(emaHipY, lm.get(RIGHT_HIP).y());
        }

        if (allVisible(lm, LEFT_SHOULDER, RIGHT_SHOULDER)) {
            float lShY = lm.get(LEFT_SHOULDER).y();
            float rShY = lm.get(RIGHT_SHOULDER).y();
            float lShX = lm.get(LEFT_SHOULDER).x();
            float rShX = lm.get(RIGHT_SHOULDER).x();
            emaShoulderY = emaVal(emaShoulderY, (lShY + rShY) / 2f);
            emaShoulderX = emaVal(emaShoulderX, (lShX + rShX) / 2f);
            emaLShoulderY = emaVal(emaLShoulderY, lShY);
            emaRShoulderY = emaVal(emaRShoulderY, rShY);
            emaShoulderWidth = emaVal(emaShoulderWidth, Math.abs(lShX - rShX));
        }

        if (allVisible(lm, LEFT_KNEE, RIGHT_KNEE)) {
            float lKnY = lm.get(LEFT_KNEE).y();
            float rKnY = lm.get(RIGHT_KNEE).y();
            float lKnX = lm.get(LEFT_KNEE).x();
            float rKnX = lm.get(RIGHT_KNEE).x();
            emaKneeY = emaVal(emaKneeY, (lKnY + rKnY) / 2f);
            emaKneeX = emaVal(emaKneeX, (lKnX + rKnX) / 2f);
            emaLKneeY = emaVal(emaLKneeY, lKnY);
            emaRKneeY = emaVal(emaRKneeY, rKnY);
            hasLKnee = hasRKnee = true;
        } else if (isVisible(lm, LEFT_KNEE)) {
            emaKneeY = emaVal(emaKneeY, lm.get(LEFT_KNEE).y());
            emaKneeX = emaVal(emaKneeX, lm.get(LEFT_KNEE).x());
            emaLKneeY = emaVal(emaLKneeY, lm.get(LEFT_KNEE).y());
            hasLKnee = true;
        } else if (isVisible(lm, RIGHT_KNEE)) {
            emaKneeY = emaVal(emaKneeY, lm.get(RIGHT_KNEE).y());
            emaKneeX = emaVal(emaKneeX, lm.get(RIGHT_KNEE).x());
            emaRKneeY = emaVal(emaRKneeY, lm.get(RIGHT_KNEE).y());
            hasRKnee = true;
        }

        if (allVisible(lm, LEFT_ANKLE, RIGHT_ANKLE)) {
            emaAnkleY = emaVal(emaAnkleY, (lm.get(LEFT_ANKLE).y() + lm.get(RIGHT_ANKLE).y()) / 2f);
            emaAnkleX = emaVal(emaAnkleX, (lm.get(LEFT_ANKLE).x() + lm.get(RIGHT_ANKLE).x()) / 2f);
        } else if (isVisible(lm, LEFT_ANKLE)) {
            emaAnkleY = emaVal(emaAnkleY, lm.get(LEFT_ANKLE).y());
            emaAnkleX = emaVal(emaAnkleX, lm.get(LEFT_ANKLE).x());
        } else if (isVisible(lm, RIGHT_ANKLE)) {
            emaAnkleY = emaVal(emaAnkleY, lm.get(RIGHT_ANKLE).y());
            emaAnkleX = emaVal(emaAnkleX, lm.get(RIGHT_ANKLE).x());
        } else if (allVisible(lm, LEFT_HEEL, RIGHT_HEEL)) {
            emaAnkleY = emaVal(emaAnkleY, (lm.get(LEFT_HEEL).y() + lm.get(RIGHT_HEEL).y()) / 2f);
            emaAnkleX = emaVal(emaAnkleX, (lm.get(LEFT_HEEL).x() + lm.get(RIGHT_HEEL).x()) / 2f);
        }

        if (isVisible(lm, NOSE)) {
            emaNoseY = emaVal(emaNoseY, lm.get(NOSE).y());
        }

        if (allVisible(lm, LEFT_WRIST, RIGHT_WRIST)) {
            emaWristX = emaVal(emaWristX, (lm.get(LEFT_WRIST).x() + lm.get(RIGHT_WRIST).x()) / 2f);
            emaWristY = emaVal(emaWristY, (lm.get(LEFT_WRIST).y() + lm.get(RIGHT_WRIST).y()) / 2f);
        } else if (isVisible(lm, LEFT_WRIST)) {
            emaWristX = emaVal(emaWristX, lm.get(LEFT_WRIST).x());
            emaWristY = emaVal(emaWristY, lm.get(LEFT_WRIST).y());
        } else if (isVisible(lm, RIGHT_WRIST)) {
            emaWristX = emaVal(emaWristX, lm.get(RIGHT_WRIST).x());
            emaWristY = emaVal(emaWristY, lm.get(RIGHT_WRIST).y());
        }
    }

    private ViewMode updateView() {
        ViewMode raw = detectRawView();

        if (raw == ViewMode.UNKNOWN) {
            return currentView != ViewMode.UNKNOWN ? currentView : ViewMode.UNKNOWN;
        }
        if (raw == candidateView) {
            candidateCount++;
        } else {
            candidateView = raw;
            candidateCount = 1;
        }
        if (candidateCount >= STABLE_FRAMES && currentView != candidateView) {
            resetEMA();
            currentView = candidateView;
        }
        return currentView != ViewMode.UNKNOWN ? currentView : raw;
    }

    private ViewMode detectRawView() {
        if (emaShoulderWidth > 0) {
            return emaShoulderWidth < SIDE_THRESHOLD ? ViewMode.SIDE : ViewMode.FRONT;
        }
        return ViewMode.UNKNOWN;
    }

    private float emaVal(float prev, float newVal) {
        if (newVal < 0 || newVal > 1.0f || Float.isNaN(newVal) || Float.isInfinite(newVal))
            return prev;
        if (prev < 0) return newVal;
        return prev + EMA_ALPHA * (newVal - prev);
    }

    private void resetEMA() {
        emaShoulderY = emaShoulderX = -1f;
        emaLShoulderY = emaRShoulderY = -1f;
        emaHipY = -1f;
        emaKneeY = emaKneeX = -1f;
        emaLKneeY = emaRKneeY = -1f;
        emaAnkleY = emaAnkleX = -1f;
        emaNoseY = -1f;
        emaWristX = emaWristY = -1f;
        hasLKnee = hasRKnee = false;
    }

    @Override
    public void reset() {
        super.reset();
        resetEMA();
        emaShoulderWidth = -1f;
        currentView = ViewMode.UNKNOWN;
        candidateView = ViewMode.UNKNOWN;
        candidateCount = 0;
        cleanStreakStartMs = -1L;
        errorStartMs = -1L;
        accumulatedTimeMs = 0;
        bestHoldSeconds = 0;
        currentSeconds = 0;
    }
}