package ru.sv.personaltrainer.exercises;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import java.util.List;

public class SquatExercise extends BaseExercise {

    // ── Пороги ────────────────────────────────────
    private static final float SQUAT_BOTTOM      = 100f; // глубокий присед ✅
    private static final float SQUAT_WARN        = 120f; // предупреждение
    private static final float BACK_OK           = 160f; // спина прямая
    private static final float BACK_WARN         = 150f; // небольшой наклон
    private static final float KNEE_OVER_TOE     = 0.07f;// колено за носком
    private static final float KNEE_CAVE_ERROR   = 0.72f;// колени внутрь
    private static final float KNEE_ASYMMETRY    = 12f;  // асимметрия

    // Пятки — через X координату (вид сбоку)
    // Пятка должна быть на одной вертикали с лодыжкой
    private static final float HEEL_X_THRESHOLD  = 0.05f;// порог смещения X

    @Override
    public String getName() { return "🏋 Приседания"; }

    @Override
    public AnalysisResult analyze(List<NormalizedLandmark> lm) {
        AnalysisResult result = new AnalysisResult();

        if (!isValidData(lm)) {
            result.mainFeedback = "Встаньте полностью в кадр";
            return result;
        }

        // ── Видимость точек ──────────────────────────
        boolean hasLeftLeg  = allVisible(lm,
                LEFT_HIP, LEFT_KNEE, LEFT_ANKLE);
        boolean hasRightLeg = allVisible(lm,
                RIGHT_HIP, RIGHT_KNEE, RIGHT_ANKLE);

        if (!hasLeftLeg && !hasRightLeg) {
            result.mainFeedback =
                    "Направьте камеру — должны быть видны ноги целиком";
            result.phase = "";
            return result;
        }

        // ── Выбираем рабочую сторону ─────────────────
        // Берём ту сторону у которой выше видимость
        boolean usLeft = chooseSide(lm, hasLeftLeg, hasRightLeg);

        int HIP    = usLeft ? LEFT_HIP    : RIGHT_HIP;
        int KNEE   = usLeft ? LEFT_KNEE   : RIGHT_KNEE;
        int ANKLE  = usLeft ? LEFT_ANKLE  : RIGHT_ANKLE;
        int HEEL   = usLeft ? LEFT_HEEL   : RIGHT_HEEL;
        int TOE    = usLeft ? LEFT_FOOT_INDEX : RIGHT_FOOT_INDEX;
        int SHLDR  = usLeft ? LEFT_SHOULDER   : RIGHT_SHOULDER;

        // ── Угол колена ──────────────────────────────
        float kneeAngle = getAngle(lm, HIP, KNEE, ANKLE);
        if (kneeAngle < 0) {
            result.mainFeedback = "Не видно колена — встаньте в кадр";
            return result;
        }

        // ── Фаза движения ────────────────────────────
        updatePhase(result, kneeAngle);

        // ════════════════════════════════════════════
        //  ПРОВЕРКИ (только вид сбоку)
        // ════════════════════════════════════════════

        // 1. Глубина приседа
        checkDepth(result, kneeAngle);

        // 2. Ровность спины
        if (isVisible(lm, SHLDR)) {
            checkBack(lm, result, SHLDR, HIP, KNEE, ANKLE);
        }

        // 3. Пятка (через X и Z координаты)
        if (isVisible(lm, HEEL) && isVisible(lm, ANKLE)) {
            checkHeelLift(lm, result, HEEL, ANKLE, TOE, usLeft);
        }

        // 4. Колено за носком
        if (isVisible(lm, TOE)) {
            checkKneeOverToe(lm, result, KNEE, TOE, ANKLE);
        }

        // 5. Колени внутрь (если видны оба)
        if (allVisible(lm, LEFT_KNEE,  RIGHT_KNEE,
                LEFT_ANKLE, RIGHT_ANKLE)) {
            checkKneeCave(lm, result);
        }

        // 6. Асимметрия (если видны обе ноги)
        if (hasLeftLeg && hasRightLeg) {
            float lKnee = getAngle(lm,
                    LEFT_HIP,  LEFT_KNEE,  LEFT_ANKLE);
            float rKnee = getAngle(lm,
                    RIGHT_HIP, RIGHT_KNEE, RIGHT_ANKLE);
            if (lKnee >= 0 && rKnee >= 0) {
                checkKneeSymmetry(result, lKnee, rKnee);
            }
        }

        result.repCount     = repCount;
        result.mainFeedback = result.errors.isEmpty()
                ? buildFeedback(result.phase)
                : result.errors.get(0);

        return result;
    }

    // ════════════════════════════════════════════════
    //  1. Глубина приседа
    // ════════════════════════════════════════════════
    private void checkDepth(AnalysisResult result, float angle) {
        if (!result.phase.equals("DOWN")) return;
        if (angle > SQUAT_WARN) {
            result.addError(
                    "⚠ Приседайте глубже — угол колена должен быть ~90°",
                    LEFT_KNEE, RIGHT_KNEE);
        } else if (angle > SQUAT_BOTTOM) {
            result.addError(
                    "⚠ Чуть глубже — почти достигли нужной глубины",
                    LEFT_KNEE, RIGHT_KNEE);
        }
    }

    // ════════════════════════════════════════════════
    //  2. Ровность спины
    //
    //  Правило с картинки:
    //  Плечо → Бедро → Колено = прямая линия
    //  Угол между этими тремя точками ≈ 180°
    //
    //  Дополнительно:
    //  Угол наклона спины ≈ угол наклона голени
    //  (спина параллельна голени)
    // ════════════════════════════════════════════════
    private void checkBack(List<NormalizedLandmark> lm,
                           AnalysisResult result,
                           int shldr, int hip,
                           int knee,  int ankle) {

        // Угол плечо → бедро → колено
        float backAngle = getAngle(lm, shldr, hip, knee);
        if (backAngle < 0) return;

        // Угол голени к вертикали (knee → ankle)
        float shinTilt = calcTilt(lm, knee, ankle);

        // Угол спины к вертикали (shoulder → hip)
        float backTilt = calcTilt(lm, shldr, hip);

        // Разница наклонов
        float tiltDiff = backTilt - shinTilt;

        // ── Определяем тип ошибки ─────────────────────

        if (backAngle < BACK_WARN) {
            // Сильный горб — спина явно сгорблена
            result.addError(
                    "⚠ Спина сгорблена! "
                            + "Держите плечо-бедро-колено на одной линии",
                    shldr, hip);

        } else if (backAngle < BACK_OK) {
            // Небольшое отклонение
            result.addError(
                    "⚠ Выпрямите спину — "
                            + "плечо, бедро и колено должны быть на одной линии",
                    shldr, hip);

        } else if (tiltDiff > 25f) {
            // Спина прямая, но корпус наклонён вперёд
            result.addError(
                    "⚠ Корпус наклонён вперёд — "
                            + "тяните плечи назад и вверх",
                    shldr);
        }
    }

    // ════════════════════════════════════════════════
    //  3. Подъём пятки
    //
    //  Новая логика через Z и X координаты:
    //
    //  MediaPipe даёт координату Z (глубина)
    //  При подъёме пятки:
    //    heel.Z становится меньше ankle.Z
    //    (пятка ближе к камере чем лодыжка)
    //
    //  Запасной метод через X:
    //    При подъёме пятки её X смещается
    //    относительно лодыжки
    // ════════════════════════════════════════════════
    private void checkHeelLift(List<NormalizedLandmark> lm,
                               AnalysisResult result,
                               int heelIdx, int ankleIdx,
                               int toeIdx,  boolean isLeft) {

        NormalizedLandmark heel  = lm.get(heelIdx);
        NormalizedLandmark ankle = lm.get(ankleIdx);

        // ── Метод 1: через Z координату ──────────────
        // Z в MediaPipe: меньше = ближе к камере
        // При подъёме пятки: heel.z < ankle.z
        float heelZ  = heel.z();
        float ankleZ = ankle.z();
        float zDiff  = ankleZ - heelZ; // > 0 если пятка ближе (поднята)

        // ── Метод 2: через Y координату ──────────────
        // Работает при виде сбоку когда стопа видна
        float heelY = heel.y();
        float ankleY = ankle.y();

        // При правильном положении пятка ниже или на уровне лодыжки
        // heel.Y > ankle.Y (пятка ниже по экрану)
        float yDiff = heelY - ankleY; // < 0 если пятка выше лодыжки

        // ── Метод 3: носок vs пятка по Y ─────────────
        // Дополнительная проверка если виден носок
        float toeHeelDiff = 0f;
        boolean hasToe = isVisible(lm, toeIdx);
        if (hasToe) {
            float toeY = lm.get(toeIdx).y();
            // В норме toe.Y > heel.Y (носок ниже пятки при виде сбоку)
            // Если heel.Y > toe.Y → пятка опустилась ниже носка (не может быть на полу)
            // Нас интересует: heel поднят если heel.Y значительно < toe.Y
            toeHeelDiff = toeY - heelY; // < 0 если пятка выше носка
        }

        // ── Комбинируем методы ───────────────────────
        // Считаем пятку поднятой если минимум 2 метода согласны
        int errorCount = 0;
        int warnCount  = 0;

        // Z метод
        if (zDiff > 0.05f)       errorCount++;
        else if (zDiff > 0.02f)  warnCount++;

        // Y метод (пятка выше лодыжки)
        if (yDiff < -0.04f)      errorCount++;
        else if (yDiff < -0.01f) warnCount++;

        // Носок-пятка метод
        if (hasToe) {
            if (toeHeelDiff < -0.05f)      errorCount++;
            else if (toeHeelDiff < -0.02f) warnCount++;
        }

        String side = isLeft ? "Левая" : "Правая";
        int hIdx    = heelIdx;

        if (errorCount >= 2) {
            // Уверены — пятка поднята
            result.addError(
                    "⚠ " + side + " пятка оторвана от пола! "
                            + "Давите пяткой в пол",
                    hIdx, ankleIdx);

        } else if (errorCount == 1 || warnCount >= 2) {
            // Возможно поднята
            result.addError(
                    "⚠ " + side + " пятка начинает отрываться — "
                            + "держите её на полу",
                    hIdx);
        }
    }

    // ════════════════════════════════════════════════
    //  4. Колено выходит за носок
    // ════════════════════════════════════════════════
    private void checkKneeOverToe(List<NormalizedLandmark> lm,
                                  AnalysisResult result,
                                  int kneeIdx,
                                  int toeIdx,
                                  int ankleIdx) {
        // Сбоку: сравниваем X колена и X носка
        float kneeX  = lm.get(kneeIdx).x();
        float toeX   = lm.get(toeIdx).x();
        float ankleX = lm.get(ankleIdx).x();

        // Направление движения зависит от стороны
        // Нормализуем: насколько колено вышло за носок
        float footDir = toeX - ankleX; // направление стопы
        if (Math.abs(footDir) < 0.01f) return;

        float kneeRelative = (kneeX - ankleX) / Math.abs(footDir);
        float toeRelative  = (toeX  - ankleX) / Math.abs(footDir);

        // Колено должно быть не дальше носка
        if (kneeRelative > toeRelative + KNEE_OVER_TOE) {
            result.addError(
                    "⚠ Колено выходит за носок — "
                            + "перенесите вес назад на пятки",
                    kneeIdx, toeIdx);
        }
    }

    // ════════════════════════════════════════════════
    //  5. Колени заваливаются внутрь
    // ════════════════════════════════════════════════
    private void checkKneeCave(List<NormalizedLandmark> lm,
                               AnalysisResult result) {
        float kneeW  = distX(lm, LEFT_KNEE,  RIGHT_KNEE);
        float ankleW = distX(lm, LEFT_ANKLE, RIGHT_ANKLE);

        if (kneeW < 0 || ankleW <= 0) return;
        float ratio = kneeW / ankleW;

        if (ratio < KNEE_CAVE_ERROR) {
            result.addError(
                    "⚠ Колени заваливаются внутрь — "
                            + "разведите по линии стоп",
                    LEFT_KNEE, RIGHT_KNEE);
        }
    }

    // ════════════════════════════════════════════════
    //  6. Асимметрия колен
    // ════════════════════════════════════════════════
    private void checkKneeSymmetry(AnalysisResult result,
                                   float l, float r) {
        float diff = Math.abs(l - r);
        if (diff <= KNEE_ASYMMETRY) return;

        if (l > r) {
            result.addError(
                    "⚠ Левое колено сгибается меньше — "
                            + "выровняйте нагрузку",
                    LEFT_KNEE);
        } else {
            result.addError(
                    "⚠ Правое колено сгибается меньше — "
                            + "выровняйте нагрузку",
                    RIGHT_KNEE);
        }
    }

    // ════════════════════════════════════════════════
    //  Вспомогательные методы
    // ════════════════════════════════════════════════

    // Угол наклона вектора к вертикали (градусы)
    private float calcTilt(List<NormalizedLandmark> lm,
                           int fromIdx, int toIdx) {
        if (!allVisible(lm, fromIdx, toIdx)) return 0f;
        float dx = lm.get(toIdx).x() - lm.get(fromIdx).x();
        float dy = lm.get(toIdx).y() - lm.get(fromIdx).y();
        return (float) Math.toDegrees(Math.atan2(Math.abs(dx), Math.abs(dy)));
    }

    // Выбираем сторону с лучшей видимостью
    private boolean chooseSide(List<NormalizedLandmark> lm,
                               boolean hasLeft, boolean hasRight) {
        if (hasLeft  && !hasRight) return true;
        if (hasRight && !hasLeft)  return false;

        // Обе видны — выбираем по средней видимости
        float leftVis = (getVisibility(lm, LEFT_HIP)
                + getVisibility(lm, LEFT_KNEE)
                + getVisibility(lm, LEFT_ANKLE)) / 3f;
        float rightVis = (getVisibility(lm, RIGHT_HIP)
                + getVisibility(lm, RIGHT_KNEE)
                + getVisibility(lm, RIGHT_ANKLE)) / 3f;
        return leftVis >= rightVis;
    }

    private void updatePhase(AnalysisResult result, float angle) {
        if (angle < 100f) {
            if (!isDown) isDown = true;
            result.phase = "DOWN";
        } else if (angle > 160f && isDown) {
            isDown = false;
            repCount++;
            result.phase = "UP";
        } else {
            result.phase = angle < 130f ? "DOWN" : "UP";
        }
    }

    private String buildFeedback(String phase) {
        switch (phase) {
            case "DOWN": return "✅ Хорошо! Держите спину прямо";
            case "UP":   return "✅ Отлично! Повторений: " + repCount;
            default:     return "✅ Встаньте в исходную позицию";
        }
    }
}