package ru.sv.personaltrainer.exercises;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;

import java.util.List;

public class PlankExercise extends BaseExercise {

    @Override
    public String getName() { return "🧘 Планка"; }

    @Override
    public AnalysisResult analyze(List<NormalizedLandmark> lm) {
        AnalysisResult result = new AnalysisResult();
        result.phase = "HOLD";

        if (!isValidData(lm)) {
            result.mainFeedback = "Встаньте полностью в кадр";
            return result;
        }

        // Минимально необходимые точки для планки
        boolean hasTorso = allVisible(lm,
                LEFT_SHOULDER, RIGHT_SHOULDER,
                LEFT_HIP,      RIGHT_HIP);

        boolean hasLegs = allVisible(lm,
                LEFT_ANKLE, RIGHT_ANKLE);

        if (!hasTorso) {
            result.mainFeedback = "Направьте камеру — должно быть видно всё тело";
            result.phase = "";
            return result;
        }

        float shoulderY = avgY(lm, LEFT_SHOULDER, RIGHT_SHOULDER);
        float hipY      = avgY(lm, LEFT_HIP,      RIGHT_HIP);

        // 1. Прямая линия тела (нужны плечи + бёдра + лодыжки)
        if (hasLegs) {
            float ankleY  = avgY(lm, LEFT_ANKLE, RIGHT_ANKLE);
            if (ankleY >= 0 && shoulderY >= 0) {
                float expectedHipY = (shoulderY + ankleY) / 2f;

                // Таз провисает
                if (hipY >= 0 && hipY > expectedHipY + 0.04f) {
                    result.addError("⚠ Таз провисает — напрягите пресс",
                            LEFT_HIP, RIGHT_HIP);
                }
                // Таз задран
                else if (hipY >= 0 && hipY < expectedHipY - 0.04f) {
                    result.addError("⚠ Таз слишком высоко — опустите его",
                            LEFT_HIP, RIGHT_HIP);
                }

                // Общий угол тела
                if (allVisible(lm, LEFT_SHOULDER, LEFT_HIP, LEFT_ANKLE)) {
                    float bodyAngle = getAngle(lm,
                            LEFT_SHOULDER, LEFT_HIP, LEFT_ANKLE);
                    if (bodyAngle >= 0 && bodyAngle < 160f) {
                        result.addError("⚠ Выпрямите тело — от головы до пяток одна линия",
                                LEFT_HIP, RIGHT_HIP);
                    }
                }
            }
        } else {
            // Ног не видно — анализируем только верхнюю часть
            if (hipY >= 0 && shoulderY >= 0) {
                if (hipY > shoulderY + 0.15f) {
                    result.addError("⚠ Таз провисает — напрягите пресс",
                            LEFT_HIP, RIGHT_HIP);
                }
            }
        }

        // 2. Голова (нужен нос + плечи)
        if (isVisible(lm, NOSE) && shoulderY >= 0) {
            float noseY = lm.get(NOSE).y();
            if (noseY > shoulderY + 0.06f) {
                result.addError("⚠ Держите голову на одной линии с телом",
                        NOSE);
            }
        }

        // 3. Плечи над запястьями (нужны плечи + запястья)
        if (allVisible(lm, LEFT_SHOULDER, RIGHT_SHOULDER,
                LEFT_WRIST,    RIGHT_WRIST)) {
            float shoulderX = avgX(lm, LEFT_SHOULDER, RIGHT_SHOULDER);
            float wristX    = avgX(lm, LEFT_WRIST,    RIGHT_WRIST);
            if (shoulderX >= 0 && wristX >= 0
                    && Math.abs(shoulderX - wristX) > 0.1f) {
                result.addError("⚠ Плечи должны быть прямо над запястьями",
                        LEFT_SHOULDER, RIGHT_SHOULDER);
            }
        }

        // ── Итоговая подсказка ────────────────────────
        result.repCount     = repCount;
        result.mainFeedback = result.errors.isEmpty()
                ? "✅ Отличная планка! Держите позицию"
                : result.errors.get(0);

        return result;
    }
}