package ru.sv.personaltrainer.exercises;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;

import java.util.List;

public class PushUpExercise extends BaseExercise {

    @Override
    public String getName() {
        return "💪 Отжимания";
    }

    @Override
    public AnalysisResult analyze(List<NormalizedLandmark> lm) {
        AnalysisResult result = new AnalysisResult();

        if (!isValidData(lm)) {
            result.mainFeedback = "Встаньте полностью в кадр";
            return result;
        }

        boolean hasShoulders = allVisible(lm,
                LEFT_SHOULDER, RIGHT_SHOULDER);
        boolean hasElbows = allVisible(lm,
                LEFT_ELBOW, RIGHT_ELBOW);
        boolean hasWrists = allVisible(lm,
                LEFT_WRIST, RIGHT_WRIST);
        boolean hasHips = allVisible(lm,
                LEFT_HIP, RIGHT_HIP);
        boolean hasKnees = allVisible(lm,
                LEFT_KNEE, RIGHT_KNEE);
        boolean hasAnkles = allVisible(lm,
                LEFT_ANKLE, RIGHT_ANKLE);
        boolean hasHeels = allVisible(lm,
                LEFT_HEEL, RIGHT_HEEL);
        boolean hasNose = isVisible(lm, NOSE);

        if (!hasShoulders) {
            result.mainFeedback = "Направьте камеру — должны быть видны плечи";
            result.phase = "";
            return result;
        }

        float leftElbow = hasElbows
                ? getAngle(lm, LEFT_SHOULDER, LEFT_ELBOW, LEFT_WRIST)
                : -1f;
        float rightElbow = hasElbows
                ? getAngle(lm, RIGHT_SHOULDER, RIGHT_ELBOW, RIGHT_WRIST)
                : -1f;

        float avgElbow;
        if (leftElbow < 0 && rightElbow < 0) {
            result.mainFeedback = "Не видно локтей — отойдите от камеры";
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
            if (shoulderW > 0 && elbowW > 0
                    && elbowW > shoulderW * 1.5f) {
                result.addError(
                        "⚠ Локти слишком широко — держите ближе к телу",
                        LEFT_ELBOW, RIGHT_ELBOW);
            }
        }

        if (result.phase.equals("UP") && avgElbow < 150f) {
            result.addError(
                    "⚠ Полностью разгибайте руки в верхней точке",
                    LEFT_ELBOW, RIGHT_ELBOW);
        }

        if (result.phase.equals("DOWN") && avgElbow > 110f) {
            result.addError(
                    "⚠ Опускайтесь ниже — грудь ближе к полу",
                    LEFT_ELBOW, RIGHT_ELBOW);
        }

        analyzeBackAndHips(lm, result,
                hasShoulders, hasHips, hasKnees,
                hasAnkles, hasWrists, hasNose);

        result.repCount = repCount;
        result.mainFeedback = result.errors.isEmpty()
                ? (result.phase.equals("DOWN")
                ? "✅ Хорошо! Опускайтесь ниже"
                : "✅ Отлично! Повторений: " + repCount)
                : result.errors.get(0);

        return result;
    }

    private void analyzeBackAndHips(List<NormalizedLandmark> lm,
                                    AnalysisResult result,
                                    boolean hasShoulders,
                                    boolean hasHips,
                                    boolean hasKnees,
                                    boolean hasAnkles,
                                    boolean hasWrists,
                                    boolean hasNose) {

        boolean hasElbows = allVisible(lm, LEFT_ELBOW, RIGHT_ELBOW);

        if (hasElbows && hasHips) {

            float elbowY = avgY(lm, LEFT_ELBOW, RIGHT_ELBOW);
            float hipY = avgY(lm, LEFT_HIP, RIGHT_HIP);

            if (elbowY >= 0 && hipY >= 0) {

                float diff = hipY - elbowY;

                if (diff > 0.04f) {
                    result.addError(
                            "⚠ Таз провисает — напрягите пресс и ягодицы",
                            LEFT_HIP, RIGHT_HIP);

                } else if (diff < -0.04f) {
                    result.addError(
                            "⚠ Таз задран вверх — опустите его",
                            LEFT_HIP, RIGHT_HIP);
                }

                if (hasKnees) {
                    float backAngle = getAngle(lm,
                            LEFT_SHOULDER, LEFT_HIP, LEFT_KNEE);
                    if (backAngle >= 0 && backAngle < 150f) {
                        result.addError(
                                "⚠ Выпрямите спину — не прогибайтесь в пояснице",
                                LEFT_HIP, RIGHT_HIP);
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
                result.addError(
                        "⚠ Плечи перекошены — держите их ровно",
                        LEFT_SHOULDER, RIGHT_SHOULDER);
            }

            if (hasNose) {
                float noseY = lm.get(NOSE).y();
                if (shoulderY >= 0) {
                    if (noseY > shoulderY + 0.08f) {
                        result.addError(
                                "⚠ Держите голову прямо — не опускайте подбородок",
                                NOSE);
                    } else if (noseY < shoulderY - 0.08f) {
                        result.addError(
                                "⚠ Не запрокидывайте голову",
                                NOSE);
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
                result.addError(
                        "⚠ Плечи перекошены — держите их ровно",
                        LEFT_SHOULDER, RIGHT_SHOULDER);
            }

            if (noseY > shoulderY + 0.08f) {
                result.addError(
                        "⚠ Держите голову прямо — не опускайте подбородок",
                        NOSE);
            } else if (noseY < shoulderY - 0.08f) {
                result.addError(
                        "⚠ Не запрокидывайте голову",
                        NOSE);
            }
        }
    }
}