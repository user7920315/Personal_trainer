package ru.sv.personaltrainer.exercises;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;

import java.util.List;

import ru.sv.personaltrainer.R;

public class PushUpExercise extends BaseExercise {

    @Override
    public String getName() {
        return getString(R.string.exercise_push_up_name);
    }

    @Override
    public AnalysisResult analyze(List<NormalizedLandmark> lm) {
        AnalysisResult result = new AnalysisResult();

        if (!isValidData(lm)) {
            result.mainFeedback = getString(R.string.msg_full_frame);
            return result;
        }

        boolean hasShoulders = allVisible(lm, LEFT_SHOULDER, RIGHT_SHOULDER);
        boolean hasElbows = allVisible(lm, LEFT_ELBOW, RIGHT_ELBOW);
        boolean hasWrists = allVisible(lm, LEFT_WRIST, RIGHT_WRIST);
        boolean hasHips = allVisible(lm, LEFT_HIP, RIGHT_HIP);
        boolean hasKnees = allVisible(lm, LEFT_KNEE, RIGHT_KNEE);
        boolean hasAnkles = allVisible(lm, LEFT_ANKLE, RIGHT_ANKLE);
        boolean hasHeels = allVisible(lm, LEFT_HEEL, RIGHT_HEEL);
        boolean hasNose = isVisible(lm, NOSE);

        if (!hasShoulders) {
            result.mainFeedback = getString(R.string.msg_camera_shoulders);
            result.phase = "";
            return result;
        }

        float leftElbow = hasElbows ? getAngle(lm, LEFT_SHOULDER, LEFT_ELBOW, LEFT_WRIST) : -1f;
        float rightElbow = hasElbows ? getAngle(lm, RIGHT_SHOULDER, RIGHT_ELBOW, RIGHT_WRIST) : -1f;

        float avgElbow;
        if (leftElbow < 0 && rightElbow < 0) {
            result.mainFeedback = getString(R.string.msg_not_visible_joints);
            return result;
        } else if (leftElbow < 0) avgElbow = rightElbow;
        else if (rightElbow < 0) avgElbow = leftElbow;
        else avgElbow = (leftElbow + rightElbow) / 2f;

        if (avgElbow < 90f) {
            if (!isDown) isDown = true;
            result.phase = "DOWN";
        } else if (avgElbow > 160f && isDown) {
            isDown = false;
            repCount++;
            result.phase = "UP";
        } else {
            result.phase = avgElbow < 130f ? "DOWN" : "UP";
        }

        if (hasShoulders && hasElbows) {
            float shoulderW = distX(lm, LEFT_SHOULDER, RIGHT_SHOULDER);
            float elbowW = distX(lm, LEFT_ELBOW, RIGHT_ELBOW);
            if (shoulderW > 0 && elbowW > 0 && elbowW > shoulderW * 1.5f) {
                result.addError(getString(R.string.error_push_up_elbows_wide), LEFT_ELBOW, RIGHT_ELBOW);
            }
        }

        if (result.phase.equals("UP") && avgElbow < 150f) {
            result.addError(getString(R.string.error_push_up_not_full_extension), LEFT_ELBOW, RIGHT_ELBOW);
        }

        if (result.phase.equals("DOWN") && avgElbow > 110f) {
            result.addError(getString(R.string.error_push_up_not_low_enough), LEFT_ELBOW, RIGHT_ELBOW);
        }

        analyzeBackAndHips(lm, result, hasShoulders, hasHips, hasKnees, hasAnkles, hasWrists, hasNose);

        result.repCount = repCount;
        result.mainFeedback = result.errors.isEmpty() ? (result.phase.equals("DOWN") ? getString(R.string.feedback_push_up_down) : getString(R.string.feedback_push_up_up, repCount)) : result.errors.get(0);

        return result;
    }

    private void analyzeBackAndHips(List<NormalizedLandmark> lm, AnalysisResult result, boolean hasShoulders, boolean hasHips, boolean hasKnees, boolean hasAnkles, boolean hasWrists, boolean hasNose) {

        boolean hasElbows = allVisible(lm, LEFT_ELBOW, RIGHT_ELBOW);

        if (hasElbows && hasHips) {

            float elbowY = avgY(lm, LEFT_ELBOW, RIGHT_ELBOW);
            float hipY = avgY(lm, LEFT_HIP, RIGHT_HIP);

            if (elbowY >= 0 && hipY >= 0) {

                float diff = hipY - elbowY;

                if (diff > 0.04f) {
                    result.addError(getString(R.string.error_push_up_hips_sag), LEFT_HIP, RIGHT_HIP);

                } else if (diff < -0.04f) {
                    result.addError(getString(R.string.error_push_up_hips_high), LEFT_HIP, RIGHT_HIP);
                }

                if (hasKnees) {
                    float backAngle = getAngle(lm, LEFT_SHOULDER, LEFT_HIP, LEFT_KNEE);
                    if (backAngle >= 0 && backAngle < 150f) {
                        result.addError(getString(R.string.error_push_up_back_not_straight), LEFT_HIP, RIGHT_HIP);
                    }
                }
            }
            return;
        }


        if (hasElbows && hasShoulders) {

            float shoulderY = avgY(lm, LEFT_SHOULDER, RIGHT_SHOULDER);

            float leftShoulderY = lm.get(LEFT_SHOULDER).y();
            float rightShoulderY = lm.get(RIGHT_SHOULDER).y();
            float tilt = Math.abs(leftShoulderY - rightShoulderY);

            if (tilt > 0.05f) {
                result.addError(getString(R.string.error_push_up_shoulders_tilted), LEFT_SHOULDER, RIGHT_SHOULDER);
            }

            if (hasNose) {
                float noseY = lm.get(NOSE).y();
                if (shoulderY >= 0) {
                    if (noseY > shoulderY + 0.08f) {
                        result.addError(getString(R.string.error_push_up_head_down), NOSE);
                    } else if (noseY < shoulderY - 0.08f) {
                        result.addError(getString(R.string.error_push_up_head_up), NOSE);
                    }
                }
            }
            return;
        }

        if (hasShoulders && hasNose) {

            float leftShoulderY = lm.get(LEFT_SHOULDER).y();
            float rightShoulderY = lm.get(RIGHT_SHOULDER).y();
            float shoulderY = (leftShoulderY + rightShoulderY) / 2f;
            float tilt = Math.abs(leftShoulderY - rightShoulderY);
            float noseY = lm.get(NOSE).y();

            if (tilt > 0.05) {
                result.addError(getString(R.string.error_push_up_shoulders_tilted), LEFT_SHOULDER, RIGHT_SHOULDER);
            }

            if (noseY > shoulderY + 0.08f) {
                result.addError(getString(R.string.error_push_up_head_down), NOSE);
            } else if (noseY < shoulderY - 0.08f) {
                result.addError(getString(R.string.error_push_up_head_up), NOSE);
            }
        }
    }
}