package ru.sv.personaltrainer.exercises;

import android.util.Log;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import java.util.List;

public class SquatExercise extends BaseExercise {

    private static final String TAG = "SquatExercise";

    // ════════════════════════════════════════════════
    //  Константы
    // ════════════════════════════════════════════════

    // Глубина (только для вида СБОКУ)
    // Угол уменьшается при приседе: стоя ~170°, глубоко ~80-90°
    private static final float DEPTH_SHALLOW_ERR  = 120f; // мало
    private static final float DEPTH_SHALLOW_WARN = 105f; // почти мало
    private static final float DEPTH_DEEP_ERR     = 55f;  // слишком глубоко
    private static final float DEPTH_DEEP_WARN    = 70f;  // почти слишком
    private static final float DEPTH_ACTIVE       = 160f; // начал приседать

    // Стоит прямо (для определения фазы)
    private static final float STANDING_ANGLE = 155f;

    // Спина (только сбоку)
    private static final float BACK_DIFF_WARN  = 20f;
    private static final float BACK_DIFF_ERROR = 35f;

    // Пятки — heel.Y vs ankle.Y нормализованное на длину ноги
    // liftRatio = (ankle.Y - heel.Y) / legLength
    // > 0 → пятка выше лодыжки → оторвана
    // < 0 → пятка ниже лодыжки → норма
    private static final float HEEL_ERROR = 0.06f;
    private static final float HEEL_WARN  = 0.02f;

    // Колено за носком (только сбоку)
    private static final float KNEE_OVER_TOE = 0.06f;

    // Колени внутрь (только спереди)
    private static final float KNEE_CAVE_ERROR = 0.70f;
    private static final float KNEE_CAVE_WARN  = 0.82f;

    // Асимметрия (только спереди)
    private static final float KNEE_ASYMMETRY = 14f;

    // Вид съёмки
    private static final float SIDE_THRESHOLD   = 0.12f;
    private static final int   STABLE_FRAMES    = 8;

    // EMA сглаживание
    private static final float EMA_ALPHA = 0.15f;

    // ════════════════════════════════════════════════
    //  EMA состояние
    // ════════════════════════════════════════════════

    // Пятки и лодыжки
    private float emaLHeelY  = -1f, emaRHeelY  = -1f;
    private float emaLAnkleY = -1f, emaRAnkleY = -1f;
    private float emaLHipY   = -1f, emaRHipY   = -1f;

    // Спина (EMA точек для сглаживания)
    private float emaShX = -1f, emaShY = -1f;
    private float emaHiX = -1f, emaHiY = -1f;
    private float emaKnX = -1f, emaKnY = -1f;
    private float emaAnX = -1f, emaAnY = -1f;

    // Ширина плеч
    private float emaShoulderWidth = -1f;

    // Стабилизация вида
    private ViewMode currentView   = ViewMode.UNKNOWN;
    private ViewMode candidateView = ViewMode.UNKNOWN;
    private int      candidateCount = 0;

    private enum ViewMode { SIDE, FRONT, UNKNOWN }

    // ════════════════════════════════════════════════
    //  Основной метод
    // ════════════════════════════════════════════════
    @Override
    public String getName() { return "🏋 Приседания"; }

    @Override
    public AnalysisResult analyze(List<NormalizedLandmark> lm) {
        AnalysisResult result = new AnalysisResult();

        if (!isValidData(lm)) {
            result.mainFeedback = "Встаньте полностью в кадр";
            return result;
        }

        // Обновляем EMA
        updateEMA(lm);

        // Определяем вид
        ViewMode view = updateView(lm);
        if (view == ViewMode.UNKNOWN) {
            result.mainFeedback =
                    "Встаньте боком или лицом к камере";
            result.phase = "";
            return result;
        }

        // Рабочая сторона
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

        result.repCount     = repCount;
        result.mainFeedback = result.errors.isEmpty()
                ? buildFeedback(result.phase, view)
                : result.errors.get(0);

        return result;
    }

    // ════════════════════════════════════════════════
    //  ВИД СБОКУ
    //  ✅ Глубина (мало и слишком глубоко)
    //  ✅ Спина (параллельность с голенью)
    //  ✅ Пятки (heel vs ankle)
    //  ✅ Колено за носком
    // ════════════════════════════════════════════════
    private void analyzeSide(List<NormalizedLandmark> lm,
                             AnalysisResult result,
                             Side s) {
        // 1. Глубина
        checkDepth(result, s.kneeAngle);

        // 2. Спина
        updateBackEMA(lm, s);
        if (s.hasShoulder && s.hasHip
                && s.hasKnee && s.hasAnkle) {
            checkBack(result, s);
        }

        // 3. Пятка рабочей стороны
        float heelY  = s.isLeft ? emaLHeelY  : emaRHeelY;
        float ankleY = s.isLeft ? emaLAnkleY : emaRAnkleY;
        float hipY   = s.isLeft ? emaLHipY   : emaRHipY;

        if (s.hasHeel && s.hasAnkle && s.hasHip
                && heelY > 0 && ankleY > 0 && hipY > 0) {
            checkHeel(result,
                    heelY, ankleY, hipY,
                    s.isLeft,
                    s.heelIdx, s.ankleIdx);
        }

        // 4. Колено за носком
        if (s.hasToe && s.hasAnkle) {
            checkKneeOverToe(lm, result, s);
        }
    }

    // ════════════════════════════════════════════════
    //  ВИД СПЕРЕДИ
    //  ✅ Колени внутрь
    //  ✅ Пятки обеих ног
    //  ✅ Асимметрия колен
    //  ❌ Глубина (удалена — ненадёжна спереди)
    //  ❌ Носки (удалены — не работали)
    // ════════════════════════════════════════════════
    private void analyzeFront(List<NormalizedLandmark> lm,
                              AnalysisResult result,
                              Side s) {
        // 1. Колени внутрь
        if (allVisible(lm, LEFT_KNEE,  RIGHT_KNEE,
                LEFT_ANKLE, RIGHT_ANKLE)) {
            checkKneesInward(lm, result);
        }

        // 2. Пятки обеих ног
        if (isVisible(lm, LEFT_HEEL)
                && isVisible(lm, LEFT_ANKLE)
                && isVisible(lm, LEFT_HIP)
                && emaLHeelY > 0 && emaLAnkleY > 0 && emaLHipY > 0) {
            checkHeel(result,
                    emaLHeelY, emaLAnkleY, emaLHipY,
                    true, LEFT_HEEL, LEFT_ANKLE);
        }

        if (isVisible(lm, RIGHT_HEEL)
                && isVisible(lm, RIGHT_ANKLE)
                && isVisible(lm, RIGHT_HIP)
                && emaRHeelY > 0 && emaRAnkleY > 0 && emaRHipY > 0) {
            checkHeel(result,
                    emaRHeelY, emaRAnkleY, emaRHipY,
                    false, RIGHT_HEEL, RIGHT_ANKLE);
        }

        // 3. Асимметрия колен
        if (allVisible(lm, LEFT_HIP,  RIGHT_HIP,
                LEFT_KNEE,  RIGHT_KNEE,
                LEFT_ANKLE, RIGHT_ANKLE)) {
            checkAsymmetry(lm, result);
        }
    }

    // ════════════════════════════════════════════════
    //  ГЛУБИНА (только сбоку)
    //
    //  Исправлена логика:
    //  Стоя:        angle ~ 170°
    //  Норма:       angle ~ 80-105°
    //  Мало:        angle > 120°
    //  Слишком:     angle < 55°
    //
    //  Проверяем только если начал приседать (< DEPTH_ACTIVE)
    // ════════════════════════════════════════════════
    private void checkDepth(AnalysisResult result, float angle) {
        // Только в фазе DOWN
        if (!result.phase.equals("DOWN")) return;

        // Не начал приседать
        if (angle >= DEPTH_ACTIVE) return;

        if (angle > DEPTH_SHALLOW_ERR) {
            // angle = 130° → мало (> 120)
            result.addError(
                    "⚠ Приседайте глубже — "
                            + "угол колена должен быть ~90°",
                    LEFT_KNEE, RIGHT_KNEE);

        } else if (angle > DEPTH_SHALLOW_WARN) {
            // angle = 110° → почти мало (> 105)
            result.addError(
                    "⚠ Чуть глубже!",
                    LEFT_KNEE, RIGHT_KNEE);

        } else if (angle < DEPTH_DEEP_ERR) {
            // angle = 50° → слишком глубоко (< 55)
            result.addError(
                    "⚠ Слишком глубокий присед — "
                            + "поднимитесь немного",
                    LEFT_KNEE, RIGHT_KNEE);

        } else if (angle < DEPTH_DEEP_WARN) {
            // angle = 65° → почти слишком (< 70)
            result.addError(
                    "⚠ Очень глубокий присед — "
                            + "следите за коленями",
                    LEFT_KNEE, RIGHT_KNEE);
        }
        // 70° - 105° → идеально ✅ — нет ошибки
    }

    // ════════════════════════════════════════════════
    //  СПИНА (только сбоку)
    //  Параллельность вектора спины и вектора голени
    // ════════════════════════════════════════════════
    private void checkBack(AnalysisResult result, Side s) {

        if (!isFinite(emaShX) || !isFinite(emaShY)
                || !isFinite(emaHiX) || !isFinite(emaHiY)
                || !isFinite(emaKnX) || !isFinite(emaKnY)
                || !isFinite(emaAnX) || !isFinite(emaAnY)) return;

        // Вектор спины: shoulder → hip
        double backDx = emaHiX - emaShX;
        double backDy = emaHiY - emaShY;

        // Вектор голени: knee → ankle
        double shinDx = emaAnX - emaKnX;
        double shinDy = emaAnY - emaKnY;

        // Угол к горизонтали
        double backAngle = Math.toDegrees(
                Math.atan2(backDy, backDx));
        double shinAngle = Math.toDegrees(
                Math.atan2(shinDy, shinDx));

        // Разница с нормализацией [0, 180]
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

    // ════════════════════════════════════════════════
    //  ПЯТКИ — heel.Y vs ankle.Y
    //
    //  Координатная система MediaPipe: Y растёт ВНИЗ
    //
    //  В норме пятка находится НИЖЕ лодыжки:
    //    heel.Y > ankle.Y
    //    ankle.Y - heel.Y < 0 → liftRatio < 0 → норма ✅
    //
    //  При отрыве пятки она ПОДНИМАЕТСЯ (Y уменьшается):
    //    heel.Y < ankle.Y
    //    ankle.Y - heel.Y > 0 → liftRatio > 0 → ошибка ❌
    //
    //  Нормализация на длину ноги (ankle.Y - hip.Y):
    //  Делает порог независимым от роста и расстояния до камеры
    // ════════════════════════════════════════════════
    private void checkHeel(AnalysisResult result,
                           float heelY,
                           float ankleY,
                           float hipY,
                           boolean isLeft,
                           int heelIdx,
                           int ankleIdx) {

        // Длина ноги (лодыжка ниже бедра → ankleY > hipY)
        float legLength = ankleY - hipY;

        // Аномальные данные
        if (legLength < 0.05f) {
            Log.w(TAG, "Heel: anomalous legLength=" + legLength);
            return;
        }

        // heelDiff > 0 → пятка ВЫШЕ лодыжки → оторвана
        // heelDiff < 0 → пятка НИЖЕ лодыжки → норма
        float heelDiff  = ankleY - heelY;
        float liftRatio = heelDiff / legLength;

        String name = isLeft ? "Левая" : "Правая";

        Log.d(TAG, String.format(
                "Heel[%s]: heelY=%.3f ankleY=%.3f hipY=%.3f "
                        + "legLen=%.3f diff=%.3f ratio=%.3f",
                name, heelY, ankleY, hipY,
                legLength, heelDiff, liftRatio));

        if (liftRatio > HEEL_ERROR) {
            result.addError(
                    "⚠ " + name + " пятка оторвана! "
                            + "Прижимайте пятку к полу",
                    heelIdx, ankleIdx);

        } else if (liftRatio > HEEL_WARN) {
            result.addError(
                    "⚠ " + name + " пятка начинает подниматься",
                    heelIdx);
        }
    }

    // ════════════════════════════════════════════════
    //  КОЛЕНО ЗА НОСКОМ (только сбоку)
    // ════════════════════════════════════════════════
    private void checkKneeOverToe(List<NormalizedLandmark> lm,
                                  AnalysisResult result,
                                  Side s) {
        float kneeX  = lm.get(s.kneeIdx).x();
        float toeX   = lm.get(s.toeIdx).x();
        float ankleX = lm.get(s.ankleIdx).x();

        float footDir = toeX - ankleX;
        if (Math.abs(footDir) < 0.01f) return;

        float kneeRel = (kneeX - ankleX) / Math.abs(footDir);
        float toeRel  = (toeX  - ankleX) / Math.abs(footDir);

        if (Math.abs(kneeRel) > Math.abs(toeRel) + KNEE_OVER_TOE) {
            result.addError(
                    "⚠ Колено выходит за носок — "
                            + "перенесите вес на пятки",
                    s.kneeIdx, s.toeIdx);
        }
    }

    // ════════════════════════════════════════════════
    //  КОЛЕНИ ВНУТРЬ (только спереди)
    // ════════════════════════════════════════════════
    private void checkKneesInward(List<NormalizedLandmark> lm,
                                  AnalysisResult result) {
        float kW = distX(lm, LEFT_KNEE,  RIGHT_KNEE);
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

    // ════════════════════════════════════════════════
    //  АСИММЕТРИЯ (только спереди)
    // ════════════════════════════════════════════════
    private void checkAsymmetry(List<NormalizedLandmark> lm,
                                AnalysisResult result) {
        float lA = getAngle(lm, LEFT_HIP,  LEFT_KNEE,  LEFT_ANKLE);
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

    // ════════════════════════════════════════════════
    //  Стабилизация вида съёмки
    // ════════════════════════════════════════════════
    private ViewMode updateView(List<NormalizedLandmark> lm) {
        ViewMode raw = detectRawView(lm);

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
            Log.d(TAG, "View: "
                    + currentView + " → " + candidateView);
            resetHeelEMA();
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

    // ════════════════════════════════════════════════
    //  EMA обновление
    // ════════════════════════════════════════════════
    private void updateEMA(List<NormalizedLandmark> lm) {

        emaLHeelY  = emaPoint(emaLHeelY,  lm, LEFT_HEEL,       true);
        emaRHeelY  = emaPoint(emaRHeelY,  lm, RIGHT_HEEL,      true);
        emaLAnkleY = emaPoint(emaLAnkleY, lm, LEFT_ANKLE,      true);
        emaRAnkleY = emaPoint(emaRAnkleY, lm, RIGHT_ANKLE,     true);
        emaLHipY   = emaPoint(emaLHipY,   lm, LEFT_HIP,        true);
        emaRHipY   = emaPoint(emaRHipY,   lm, RIGHT_HIP,       true);

        if (allVisible(lm, LEFT_SHOULDER, RIGHT_SHOULDER)) {
            float w = distX(lm, LEFT_SHOULDER, RIGHT_SHOULDER);
            emaShoulderWidth = emaVal(emaShoulderWidth, w);
        } else {
            emaShoulderWidth = -1f;
        }
    }

    // EMA для Y координаты одной точки
    private float emaPoint(float prev,
                           List<NormalizedLandmark> lm,
                           int idx,
                           boolean useY) {
        if (!isVisible(lm, idx)) return -1f; // сброс при потере
        float val = useY ? lm.get(idx).y() : lm.get(idx).x();
        return emaVal(prev, val);
    }

    private float emaVal(float prev, float newVal) {
        if (newVal < 0 || newVal > 1.0f
                || Float.isNaN(newVal)
                || Float.isInfinite(newVal)) return prev;
        if (prev < 0) return newVal; // первое значение
        return prev + EMA_ALPHA * (newVal - prev);
    }

    private void updateBackEMA(List<NormalizedLandmark> lm, Side s) {
        if (s.hasShoulder) {
            emaShX = emaVal(emaShX, lm.get(s.shoulderIdx).x());
            emaShY = emaVal(emaShY, lm.get(s.shoulderIdx).y());
        } else { emaShX = emaShY = -1f; }

        if (s.hasHip) {
            emaHiX = emaVal(emaHiX, lm.get(s.hipIdx).x());
            emaHiY = emaVal(emaHiY, lm.get(s.hipIdx).y());
        } else { emaHiX = emaHiY = -1f; }

        if (s.hasKnee) {
            emaKnX = emaVal(emaKnX, lm.get(s.kneeIdx).x());
            emaKnY = emaVal(emaKnY, lm.get(s.kneeIdx).y());
        } else { emaKnX = emaKnY = -1f; }

        if (s.hasAnkle) {
            emaAnX = emaVal(emaAnX, lm.get(s.ankleIdx).x());
            emaAnY = emaVal(emaAnY, lm.get(s.ankleIdx).y());
        } else { emaAnX = emaAnY = -1f; }
    }

    // ════════════════════════════════════════════════
    //  Вспомогательные методы
    // ════════════════════════════════════════════════

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
        Side s        = new Side();
        s.isLeft      = left;
        s.shoulderIdx = left ? LEFT_SHOULDER   : RIGHT_SHOULDER;
        s.hipIdx      = left ? LEFT_HIP        : RIGHT_HIP;
        s.kneeIdx     = left ? LEFT_KNEE       : RIGHT_KNEE;
        s.ankleIdx    = left ? LEFT_ANKLE      : RIGHT_ANKLE;
        s.heelIdx     = left ? LEFT_HEEL       : RIGHT_HEEL;
        s.toeIdx      = left ? LEFT_FOOT_INDEX : RIGHT_FOOT_INDEX;

        s.hasShoulder = isVisible(lm, s.shoulderIdx);
        s.hasHip      = isVisible(lm, s.hipIdx);
        s.hasKnee     = isVisible(lm, s.kneeIdx);
        s.hasAnkle    = isVisible(lm, s.ankleIdx);
        s.hasHeel     = isVisible(lm, s.heelIdx);
        s.hasToe      = isVisible(lm, s.toeIdx);

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
        if (s.hasShoulder)   sc += 3;
        if (s.hasAnkle)      sc += 2;
        if (s.hasHeel)       sc += 2;
        if (s.hasToe)        sc += 2;
        if (s.hasHip)        sc += 1;
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
            case "DOWN": return "✅ Хорошо! Держите спину" + hint;
            case "UP":   return "✅ Повторений: " + repCount + hint;
            default:     return "✅ Начните приседать" + hint;
        }
    }

    private void resetHeelEMA() {
        emaLHeelY  = emaRHeelY  = -1f;
        emaLAnkleY = emaRAnkleY = -1f;
        emaLHipY   = emaRHipY   = -1f;
        Log.d(TAG, "Heel EMA reset");
    }

    @Override
    public void reset() {
        super.reset();
        resetHeelEMA();
        emaShX = emaShY = emaHiX = emaHiY = -1f;
        emaKnX = emaKnY = emaAnX = emaAnY = -1f;
        emaShoulderWidth = -1f;
        currentView    = ViewMode.UNKNOWN;
        candidateView  = ViewMode.UNKNOWN;
        candidateCount = 0;
    }
}
