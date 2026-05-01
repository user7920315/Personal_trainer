package ru.sv.personaltrainer.exercises;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;

import java.util.List;

public class SquatExercise extends BaseExercise {

    // Допуски для проверок
    private static final float HEEL_LIFT_THRESHOLD     = 0.06f; // пятки
    private static final float KNEE_CAVE_THRESHOLD     = 0.75f; // колени внутрь
    private static final float BACK_ANGLE_THRESHOLD    = 140f;  // спина
    private static final float ASYMMETRY_THRESHOLD     = 15f;   // асимметрия колен
    private static final float TOE_ANGLE_THRESHOLD     = 0.15f; // угол носков
    private static final float TOE_SYMMETRY_THRESHOLD  = 0.08f; // симметрия носков

    @Override
    public String getName() { return "🏋 Приседания"; }

    @Override
    public AnalysisResult analyze(List<NormalizedLandmark> lm) {
        AnalysisResult result = new AnalysisResult();

        if (!isValidData(lm)) {
            result.mainFeedback = "Встаньте полностью в кадр";
            return result;
        }

        // ── Видимость групп точек ─────────────────────
        boolean hasHips    = allVisible(lm, LEFT_HIP,    RIGHT_HIP);
        boolean hasKnees   = allVisible(lm, LEFT_KNEE,   RIGHT_KNEE);
        boolean hasAnkles  = allVisible(lm, LEFT_ANKLE,  RIGHT_ANKLE);
        boolean hasHeels   = allVisible(lm, LEFT_HEEL,   RIGHT_HEEL);
        boolean hasToes    = allVisible(lm, LEFT_FOOT_INDEX, RIGHT_FOOT_INDEX);
        boolean hasShoulders = allVisible(lm, LEFT_SHOULDER, RIGHT_SHOULDER);

        // Минимум для анализа
        if (!hasKnees || !hasHips) {
            result.mainFeedback = "Направьте камеру — должны быть видны ноги";
            result.phase = "";
            return result;
        }

        // ── Углы колен ───────────────────────────────
        float leftKnee  = getAngle(lm, LEFT_HIP,  LEFT_KNEE,  LEFT_ANKLE);
        float rightKnee = getAngle(lm, RIGHT_HIP, RIGHT_KNEE, RIGHT_ANKLE);

        float avgKnee;
        if (leftKnee < 0 && rightKnee < 0) {
            result.mainFeedback = "Не видно колен — встаньте в кадр";
            return result;
        } else if (leftKnee  < 0) avgKnee = rightKnee;
        else if   (rightKnee < 0) avgKnee = leftKnee;
        else avgKnee = (leftKnee + rightKnee) / 2f;

        // ── Определяем фазу ──────────────────────────
        if (avgKnee < 100f) {
            if (!isDown) isDown = true;
            result.phase = "DOWN";
        } else if (avgKnee > 160f && isDown) {
            isDown = false;
            repCount++;
            result.phase = "UP";
        } else {
            result.phase = avgKnee < 130f ? "DOWN" : "UP";
        }

        // ════════════════════════════════════════════
        //  АНАЛИЗ ОШИБОК
        // ════════════════════════════════════════════

        // ── 1. Глубина приседа ────────────────────────
        checkSquatDepth(result, avgKnee);

        // ── 2. Колени заваливаются внутрь ────────────
        if (hasKnees && hasAnkles) {
            checkKneeCave(lm, result);
        }

        // ── 3. Пятки отрываются (мягкая проверка) ────
        if (hasHeels && hasToes) {
            checkHeels(lm, result);
        }

        // ── 4. Спина наклонена ────────────────────────
        if (hasShoulders && hasHips && hasKnees) {
            checkBack(lm, result);
        }

        // ── 5. Асимметрия колен ───────────────────────
        if (leftKnee >= 0 && rightKnee >= 0) {
            checkKneeSymmetry(result, leftKnee, rightKnee);
        }

        // ── 6. Направление носков ─────────────────────
        if (hasHeels && hasToes) {
            checkToeDirection(lm, result);
        }

        // ── Итоговая подсказка ────────────────────────
        result.repCount     = repCount;
        result.mainFeedback = result.errors.isEmpty()
                ? buildPositiveFeedback(result.phase)
                : result.errors.get(0);

        return result;
    }

    // ════════════════════════════════════════════════
    //  Проверка 1: Глубина приседа
    // ════════════════════════════════════════════════
    private void checkSquatDepth(AnalysisResult result, float avgKnee) {
        if (result.phase.equals("DOWN")) {
            if (avgKnee > 130f) {
                result.addError(
                        "⚠ Приседайте глубже — угол колена должен быть ~90°",
                        LEFT_KNEE, RIGHT_KNEE);
            } else if (avgKnee > 110f) {
                result.addError(
                        "⚠ Чуть глубже — почти достигли нужной глубины",
                        LEFT_KNEE, RIGHT_KNEE);
            }
        }
    }

    // ════════════════════════════════════════════════
    //  Проверка 2: Колени заваливаются внутрь
    // ════════════════════════════════════════════════
    private void checkKneeCave(List<NormalizedLandmark> lm,
                               AnalysisResult result) {
        float kneeW  = distX(lm, LEFT_KNEE,  RIGHT_KNEE);
        float ankleW = distX(lm, LEFT_ANKLE, RIGHT_ANKLE);

        if (kneeW < 0 || ankleW < 0 || ankleW == 0) return;

        float ratio = kneeW / ankleW;

        if (ratio < KNEE_CAVE_THRESHOLD) {
            // Сильное заваливание
            result.addError(
                    "⚠ Колени заваливаются внутрь — разведите по линии стоп",
                    LEFT_KNEE, RIGHT_KNEE);
        } else if (ratio < 0.85f) {
            // Небольшое заваливание
            result.addError(
                    "⚠ Следите за коленями — слегка разведите их",
                    LEFT_KNEE, RIGHT_KNEE);
        }
    }

    // ════════════════════════════════════════════════
    //  Проверка 3: Пятки — МЯГКАЯ логика
    // ════════════════════════════════════════════════
    private void checkHeels(List<NormalizedLandmark> lm,
                            AnalysisResult result) {
        float leftHeelY  = lm.get(LEFT_HEEL).y();
        float rightHeelY = lm.get(RIGHT_HEEL).y();
        float leftToeY   = lm.get(LEFT_FOOT_INDEX).y();
        float rightToeY  = lm.get(RIGHT_FOOT_INDEX).y();

        // Проверяем каждую ногу отдельно
        float leftDiff  = leftToeY  - leftHeelY;   // >0 носок ниже пятки
        float rightDiff = rightToeY - rightHeelY;   // >0 носок ниже пятки

        // Пятка оторвана если она ВЫШЕ носка
        // (пятка.Y < носок.Y означает пятка выше)
        boolean leftHeelUp  = leftDiff  < -HEEL_LIFT_THRESHOLD;
        boolean rightHeelUp = rightDiff < -HEEL_LIFT_THRESHOLD;

        // Мягкий порог — предупреждение
        boolean leftHeelWarning  = !leftHeelUp
                && leftDiff  < -HEEL_LIFT_THRESHOLD * 0.5f;
        boolean rightHeelWarning = !rightHeelUp
                && rightDiff < -HEEL_LIFT_THRESHOLD * 0.5f;

        if (leftHeelUp && rightHeelUp) {
            // Обе пятки оторваны
            result.addError(
                    "⚠ Пятки отрываются от пола — перенесите вес назад",
                    LEFT_HEEL, RIGHT_HEEL);

        } else if (leftHeelUp) {
            // Только левая пятка
            result.addError(
                    "⚠ Левая пятка отрывается — давите пяткой в пол",
                    LEFT_HEEL);

        } else if (rightHeelUp) {
            // Только правая пятка
            result.addError(
                    "⚠ Правая пятка отрывается — давите пяткой в пол",
                    RIGHT_HEEL);

        } else if (leftHeelWarning || rightHeelWarning) {
            // Мягкое предупреждение
            result.addError(
                    "⚠ Следите за пятками — держите их на полу",
                    LEFT_HEEL, RIGHT_HEEL);
        }
    }

    // ════════════════════════════════════════════════
    //  Проверка 4: Наклон спины
    // ════════════════════════════════════════════════
    private void checkBack(List<NormalizedLandmark> lm,
                           AnalysisResult result) {
        // Левая сторона
        float leftBack = getAngle(lm, LEFT_SHOULDER, LEFT_HIP, LEFT_KNEE);

        // Правая сторона
        float rightBack = getAngle(lm, RIGHT_SHOULDER, RIGHT_HIP, RIGHT_KNEE);

        float backAngle;
        if (leftBack < 0 && rightBack < 0) return;
        else if (leftBack  < 0) backAngle = rightBack;
        else if (rightBack < 0) backAngle = leftBack;
        else backAngle = (leftBack + rightBack) / 2f;

        if (backAngle < BACK_ANGLE_THRESHOLD) {
            if (backAngle < 110f) {
                result.addError(
                        "⚠ Сильный наклон вперёд — выпрямите спину",
                        LEFT_SHOULDER, RIGHT_SHOULDER);
            } else {
                result.addError(
                        "⚠ Держите спину прямее — не наклоняйтесь вперёд",
                        LEFT_SHOULDER, RIGHT_SHOULDER);
            }
        }
    }

    // ════════════════════════════════════════════════
    //  Проверка 5: Асимметрия колен
    // ════════════════════════════════════════════════
    private void checkKneeSymmetry(AnalysisResult result,
                                   float leftKnee,
                                   float rightKnee) {
        float diff = Math.abs(leftKnee - rightKnee);

        if (diff > ASYMMETRY_THRESHOLD) {
            // Определяем какая нога отстаёт
            if (leftKnee > rightKnee) {
                result.addError(
                        "⚠ Левое колено сгибается меньше — выровняйте нагрузку",
                        LEFT_KNEE);
            } else {
                result.addError(
                        "⚠ Правое колено сгибается меньше — выровняйте нагрузку",
                        RIGHT_KNEE);
            }
        }
    }

    // ════════════════════════════════════════════════
    //  Проверка 6: Направление носков
    // ════════════════════════════════════════════════
    private void checkToeDirection(List<NormalizedLandmark> lm,
                                   AnalysisResult result) {

        // Вектор стопы = носок - пятка
        // Если смотрим спереди:
        //   X — горизонталь (лево/право)
        //   Y — вертикаль   (верх/низ)

        float leftHeelX  = lm.get(LEFT_HEEL).x();
        float leftHeelY  = lm.get(LEFT_HEEL).y();
        float leftToeX   = lm.get(LEFT_FOOT_INDEX).x();
        float leftToeY   = lm.get(LEFT_FOOT_INDEX).y();

        float rightHeelX = lm.get(RIGHT_HEEL).x();
        float rightHeelY = lm.get(RIGHT_HEEL).y();
        float rightToeX  = lm.get(RIGHT_FOOT_INDEX).x();
        float rightToeY  = lm.get(RIGHT_FOOT_INDEX).y();

        // Горизонтальное отклонение носка от пятки
        // Левая нога: носок должен быть чуть левее пятки (развёрнут наружу)
        // Правая нога: носок должен быть чуть правее пятки
        float leftToeOffset  = leftToeX  - leftHeelX;
        float rightToeOffset = rightToeX - rightHeelX;

        // Разница между ногами — если сильно отличаются,
        // ноги развёрнуты несимметрично
        float symmetryDiff = Math.abs(
                Math.abs(leftToeOffset) - Math.abs(rightToeOffset)
        );

        // ── Носки смотрят внутрь (сильно) ─────────────
        // При съёмке спереди носки должны быть немного разведены
        // Если оба смещения близки к 0 — носки прямо или внутрь
        boolean leftToeIn  = leftToeOffset  > TOE_ANGLE_THRESHOLD;
        boolean rightToeIn = rightToeOffset < -TOE_ANGLE_THRESHOLD;

        if (leftToeIn && rightToeIn) {
            result.addError(
                    "⚠ Разверните носки чуть наружу — примерно 30-45°",
                    LEFT_FOOT_INDEX, RIGHT_FOOT_INDEX,
                    LEFT_HEEL,       RIGHT_HEEL);

        } else if (leftToeIn) {
            result.addError(
                    "⚠ Разверните левый носок чуть наружу",
                    LEFT_FOOT_INDEX, LEFT_HEEL);

        } else if (rightToeIn) {
            result.addError(
                    "⚠ Разверните правый носок чуть наружу",
                    RIGHT_FOOT_INDEX, RIGHT_HEEL);
        }

        // ── Сильная асимметрия разворота ──────────────
        if (symmetryDiff > TOE_SYMMETRY_THRESHOLD) {
            // Определяем какая нога развёрнута больше
            float leftAngle  = Math.abs(leftToeOffset);
            float rightAngle = Math.abs(rightToeOffset);

            if (leftAngle > rightAngle) {
                result.addError(
                        "⚠ Левая стопа развёрнута сильнее правой — выровняйте",
                        LEFT_FOOT_INDEX, LEFT_HEEL);
            } else {
                result.addError(
                        "⚠ Правая стопа развёрнута сильнее левой — выровняйте",
                        RIGHT_FOOT_INDEX, RIGHT_HEEL);
            }
        }

        // ── Стопы параллельны (носки прямо вперёд) ────
        // Это тоже ошибка — нужен небольшой разворот наружу
        float leftVertical  = Math.abs(leftToeY  - leftHeelY);
        float rightVertical = Math.abs(rightToeY - rightHeelY);

        // Если горизонтальное смещение мало по сравнению с вертикальным
        // — стопа смотрит прямо вперёд
        if (leftVertical > 0 && rightVertical > 0) {
            boolean leftStraight  = Math.abs(leftToeOffset)  / leftVertical  < 0.15f;
            boolean rightStraight = Math.abs(rightToeOffset) / rightVertical < 0.15f;

            if (leftStraight && rightStraight) {
                result.addError(
                        "⚠ Разверните стопы наружу на 30-45° для устойчивости",
                        LEFT_FOOT_INDEX, RIGHT_FOOT_INDEX);
            }
        }
    }

    // ════════════════════════════════════════════════
    //  Позитивная обратная связь
    // ════════════════════════════════════════════════
    private String buildPositiveFeedback(String phase) {
        switch (phase) {
            case "DOWN": return "✅ Хорошо! Держите спину прямо";
            case "UP":   return "✅ Отлично! Повторений: " + repCount;
            default:     return "✅ Встаньте прямо и начните приседать";
        }
    }
}