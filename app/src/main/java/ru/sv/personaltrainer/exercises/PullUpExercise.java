package ru.sv.personaltrainer.exercises;

import android.util.Log;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import java.util.List;

public class PullUpExercise extends BaseExercise {

    private static final String TAG = "PullUpExercise";

    // ── Пороги фаз ───────────────────────────────────

    // Верхняя точка: подбородок выше запястий
    // (chin Y < wrist Y в системе MediaPipe, где Y растёт вниз)
    private static final float CHIN_ABOVE_WRIST_MIN = 0.02f;

    // Нижняя точка: руки почти прямые (угол в локте)
    private static final float ELBOW_EXTENDED_ANGLE = 155f;
    private static final float ELBOW_DOWN_ANGLE     = 135f;

    // ── Пороги ошибок ────────────────────────────────

    // Не полное разгибание рук внизу
    private static final float ELBOW_FULL_EXTEND_WARN  = 150f;
    private static final float ELBOW_FULL_EXTEND_ERROR = 140f;

    // Не подтянулся достаточно (подбородок не выше перекладины)
    // Косвенно: запястья должны быть значительно ниже плеч
    private static final float CHIN_HEIGHT_WARN = 0.0f;   // подбородок на уровне запястий
    private static final float CHIN_HEIGHT_ERROR = -0.03f; // подбородок ниже запястий

    // Раскачка (горизонтальное смещение плеч)
    private static final float SWING_WARN  = 0.05f;
    private static final float SWING_ERROR = 0.10f;

    // Локти не разводятся слишком широко
    private static final float ELBOW_WIDTH_RATIO_MAX = 1.8f;

    // Скрещенные ноги (бёдра не отклоняются назад)
    private static final float LEG_SWING_WARN  = 0.06f;
    private static final float LEG_SWING_ERROR = 0.12f;

    // Симметрия рук
    private static final float ELBOW_ASYMMETRY_WARN  = 15f;
    private static final float ELBOW_ASYMMETRY_ERROR = 25f;

    // ViewMode
    private static final float SIDE_THRESHOLD = 0.12f;
    private static final int   STABLE_FRAMES  = 8;

    // EMA
    private static final float EMA_ALPHA = 0.15f;

    // ── EMA переменные ───────────────────────────────
    private float emaNoseY = -1f, emaNoseX = -1f;
    private float emaLShoulderY = -1f, emaLShoulderX = -1f;
    private float emaRShoulderY = -1f, emaRShoulderX = -1f;
    private float emaLElbowY = -1f, emaLElbowX = -1f;
    private float emaRElbowY = -1f, emaRElbowX = -1f;
    private float emaLWristY = -1f, emaLWristX = -1f;
    private float emaRWristY = -1f, emaRWristX = -1f;
    private float emaLHipY = -1f, emaLHipX = -1f;
    private float emaRHipY = -1f, emaRHipX = -1f;
    private float emaLKneeY = -1f;
    private float emaRKneeY = -1f;

    private float emaShoulderWidth = -1f;

    // Позиция перекладины (фиксируем по запястьям в начале)
    private float barY = -1f;
    private int   barFrameCount = 0;
    private static final int BAR_CAPTURE_FRAMES = 15;

    // Предыдущий X плеч для определения раскачки
    private float prevShoulderX = -1f;
    private float emaSwingX     = -1f;

    // ViewMode
    private ViewMode currentView   = ViewMode.UNKNOWN;
    private ViewMode candidateView = ViewMode.UNKNOWN;
    private int candidateCount     = 0;

    private enum ViewMode { SIDE, FRONT, UNKNOWN }

    @Override
    public String getName() { return "🏅 Подтягивания"; }

    // ═════════════════════════════════════════════════
    @Override
    public AnalysisResult analyze(List<NormalizedLandmark> lm) {
        AnalysisResult result = new AnalysisResult();

        if (!isValidData(lm)) {
            result.mainFeedback = "Встаньте полностью в кадр";
            return result;
        }

        if (!anyVisible(lm, LEFT_ELBOW, RIGHT_ELBOW)) {
            result.mainFeedback = "Не видно рук — отойдите от камеры";
            result.phase = "";
            return result;
        }

        updateEMA(lm);
        captureBarPosition();
        updateSwing();

        ViewMode view = updateView();

        // Угол в локте — основной показатель фазы
        float avgElbowAngle = getAvgElbowAngle(lm);

        if (avgElbowAngle < 0) {
            result.mainFeedback = "Не видно суставов — отойдите от камеры";
            result.phase = "";
            return result;
        }

        updatePhase(result, lm, avgElbowAngle);

        Log.d(TAG, "View=" + view
                + " elbow=" + String.format("%.1f°", avgElbowAngle)
                + " phase=" + result.phase);

        // Проверки
        checkFullExtension(result, avgElbowAngle);
        checkChinAboveBar(result, lm);
        checkElbowSymmetry(result, lm);
        checkSwing(result);

        if (view == ViewMode.FRONT) {
            checkElbowWidth(result, lm);
        }

        checkLegSwing(result, lm);

        result.repCount     = repCount;
        result.mainFeedback = result.errors.isEmpty()
                ? buildFeedback(result.phase)
                : result.errors.get(0);

        return result;
    }

    // ═════════════════════════════════════════════════
    //  Фаза
    // ═════════════════════════════════════════════════
    private void updatePhase(AnalysisResult r,
                             List<NormalizedLandmark> lm,
                             float elbowAngle) {
        // DOWN = руки прямые (висим на перекладине)
        // UP   = подтянулись

        if (elbowAngle > ELBOW_EXTENDED_ANGLE) {
            // Руки прямые — нижняя точка
            if (isDown) {
                // Только что опустились — повторение не засчитываем здесь
            }
            isDown  = true;
            r.phase = "DOWN";

        } else if (elbowAngle < ELBOW_DOWN_ANGLE && isDown) {
            // Подтянулись
            // Проверяем: подбородок выше запястий?
            boolean chinOver = checkChinOverBar(lm);
            if (chinOver) {
                isDown = false;
                repCount++;
            }
            r.phase = "UP";
        } else {
            r.phase = elbowAngle > 135f ? "DOWN" : "UP";
        }
    }

    // ═════════════════════════════════════════════════
    //  Проверки
    // ═════════════════════════════════════════════════

    /** Полное разгибание рук в нижней точке */
    private void checkFullExtension(AnalysisResult result,
                                    float angle) {
        if (!result.phase.equals("DOWN")) return;

        if (angle < ELBOW_FULL_EXTEND_ERROR) {
            result.addError(
                    "⚠ Полностью разогните руки внизу — "
                            + "до полного виса",
                    LEFT_ELBOW, RIGHT_ELBOW);
        } else if (angle < ELBOW_FULL_EXTEND_WARN) {
            result.addError(
                    "⚠ Чуть разогните руки в нижней точке",
                    LEFT_ELBOW, RIGHT_ELBOW);
        }
    }

    /** Подбородок выше перекладины */
    private void checkChinAboveBar(AnalysisResult result,
                                   List<NormalizedLandmark> lm) {
        if (!result.phase.equals("UP")) return;
        if (!checkChinOverBar(lm)) {
            result.addError(
                    "⚠ Подтянитесь выше — подбородок должен быть над перекладиной",
                    NOSE,
                    LEFT_WRIST, RIGHT_WRIST);
        }
    }

    /** Проверка: подбородок над запястьями */
    private boolean checkChinOverBar(List<NormalizedLandmark> lm) {
        if (emaNoseY < 0) return false;
        float wristY = getAvgWristY();
        if (wristY < 0) return false;
        // noseY < wristY → нос выше запястий (Y растёт вниз)
        return (wristY - emaNoseY) > CHIN_ABOVE_WRIST_MIN;
    }

    /** Симметрия сгибания рук */
    private void checkElbowSymmetry(AnalysisResult result,
                                    List<NormalizedLandmark> lm) {
        float lA = getAngle(lm, LEFT_SHOULDER,  LEFT_ELBOW,  LEFT_WRIST);
        float rA = getAngle(lm, RIGHT_SHOULDER, RIGHT_ELBOW, RIGHT_WRIST);

        if (lA < 0 || rA < 0) return;

        float diff = Math.abs(lA - rA);

        if (diff > ELBOW_ASYMMETRY_ERROR) {
            result.addError(
                    lA > rA
                            ? "⚠ Левая рука слабее — тянете больше правой"
                            : "⚠ Правая рука слабее — тянете больше левой",
                    lA > rA ? LEFT_ELBOW : RIGHT_ELBOW);
        } else if (diff > ELBOW_ASYMMETRY_WARN) {
            result.addError(
                    "⚠ Небольшая асимметрия рук — тяните равномерно",
                    LEFT_ELBOW, RIGHT_ELBOW);
        }
    }

    /** Раскачка корпуса */
    private void checkSwing(AnalysisResult result) {
        if (emaSwingX < 0) return;

        Log.d(TAG, String.format("Swing: emaSwingX=%.4f", emaSwingX));

        if (emaSwingX > SWING_ERROR) {
            result.addError(
                    "⚠ Сильная раскачка — тяните без маятника",
                    LEFT_SHOULDER, RIGHT_SHOULDER);
        } else if (emaSwingX > SWING_WARN) {
            result.addError(
                    "⚠ Небольшая раскачка — держите корпус стабильно",
                    LEFT_SHOULDER, RIGHT_SHOULDER);
        }
    }

    /** Ширина локтей */
    private void checkElbowWidth(AnalysisResult result,
                                 List<NormalizedLandmark> lm) {
        if (!allVisible(lm, LEFT_ELBOW, RIGHT_ELBOW,
                LEFT_SHOULDER, RIGHT_SHOULDER)) return;

        float elbowW   = distX(lm, LEFT_ELBOW,   RIGHT_ELBOW);
        float shoulderW = distX(lm, LEFT_SHOULDER, RIGHT_SHOULDER);

        if (elbowW < 0 || shoulderW <= 0) return;

        float ratio = elbowW / shoulderW;

        if (ratio > ELBOW_WIDTH_RATIO_MAX) {
            result.addError(
                    "⚠ Локти слишком широко — возьмитесь чуть уже",
                    LEFT_ELBOW, RIGHT_ELBOW);
        }
    }

    /** Ноги не раскачиваются */
    private void checkLegSwing(AnalysisResult result,
                               List<NormalizedLandmark> lm) {
        float shX  = getAvgShoulderX();
        float hipX = getAvgHipX();

        if (shX < 0 || hipX < 0) return;

        float offset = Math.abs(hipX - shX);

        if (offset > LEG_SWING_ERROR) {
            result.addError(
                    "⚠ Ноги отклоняются — скрестите и держите неподвижно",
                    LEFT_HIP, RIGHT_HIP);
        } else if (offset > LEG_SWING_WARN) {
            result.addError(
                    "⚠ Небольшое отклонение ног — держите их прямо",
                    LEFT_HIP, RIGHT_HIP);
        }
    }

    // ═════════════════════════════════════════════════
    //  Позиция перекладины
    // ═════════════════════════════════════════════════
    private void captureBarPosition() {
        if (barFrameCount >= BAR_CAPTURE_FRAMES) return;
        float wY = getAvgWristY();
        if (wY < 0) return;

        barY = barFrameCount == 0 ? wY : (barY + wY) / 2f;
        barFrameCount++;
        Log.d(TAG, "Bar Y captured: " + barY
                + " frame=" + barFrameCount);
    }

    // ═════════════════════════════════════════════════
    //  Раскачка
    // ═════════════════════════════════════════════════
    private void updateSwing() {
        float shX = getAvgShoulderX();
        if (shX < 0) return;

        if (prevShoulderX > 0) {
            float delta = Math.abs(shX - prevShoulderX);
            emaSwingX = emaVal(emaSwingX < 0 ? 0 : emaSwingX, delta);
        }
        prevShoulderX = shX;
    }

    // ═════════════════════════════════════════════════
    //  Вспомогательные
    // ═════════════════════════════════════════════════
    private float getAvgElbowAngle(List<NormalizedLandmark> lm) {
        float lA = getAngle(lm, LEFT_SHOULDER,  LEFT_ELBOW,  LEFT_WRIST);
        float rA = getAngle(lm, RIGHT_SHOULDER, RIGHT_ELBOW, RIGHT_WRIST);
        if (lA < 0 && rA < 0) return -1f;
        if (lA < 0) return rA;
        if (rA < 0) return lA;
        return (lA + rA) / 2f;
    }

    private float getAvgShoulderX() {
        if (emaLShoulderX > 0 && emaRShoulderX > 0)
            return (emaLShoulderX + emaRShoulderX) / 2f;
        if (emaLShoulderX > 0) return emaLShoulderX;
        if (emaRShoulderX > 0) return emaRShoulderX;
        return -1f;
    }

    private float getAvgHipX() {
        if (emaLHipX > 0 && emaRHipX > 0)
            return (emaLHipX + emaRHipX) / 2f;
        if (emaLHipX > 0) return emaLHipX;
        if (emaRHipX > 0) return emaRHipX;
        return -1f;
    }

    private float getAvgWristY() {
        if (emaLWristY > 0 && emaRWristY > 0)
            return (emaLWristY + emaRWristY) / 2f;
        if (emaLWristY > 0) return emaLWristY;
        if (emaRWristY > 0) return emaRWristY;
        return -1f;
    }

    private String buildFeedback(String phase) {
        switch (phase) {
            case "UP":   return "✅ Подтянулись! Повторений: " + repCount;
            case "DOWN": return "✅ Полный вис — начните тянуться";
            default:     return "✅ Повисните на перекладине";
        }
    }

    // ═════════════════════════════════════════════════
    //  EMA и ViewMode
    // ═════════════════════════════════════════════════
    private void updateEMA(List<NormalizedLandmark> lm) {
        if (isVisible(lm, NOSE)) {
            emaNoseY = emaVal(emaNoseY, lm.get(NOSE).y());
            emaNoseX = emaVal(emaNoseX, lm.get(NOSE).x());
        }
        if (isVisible(lm, LEFT_SHOULDER)) {
            emaLShoulderY = emaVal(emaLShoulderY, lm.get(LEFT_SHOULDER).y());
            emaLShoulderX = emaVal(emaLShoulderX, lm.get(LEFT_SHOULDER).x());
        }
        if (isVisible(lm, RIGHT_SHOULDER)) {
            emaRShoulderY = emaVal(emaRShoulderY, lm.get(RIGHT_SHOULDER).y());
            emaRShoulderX = emaVal(emaRShoulderX, lm.get(RIGHT_SHOULDER).x());
        }
        if (isVisible(lm, LEFT_ELBOW)) {
            emaLElbowY = emaVal(emaLElbowY, lm.get(LEFT_ELBOW).y());
            emaLElbowX = emaVal(emaLElbowX, lm.get(LEFT_ELBOW).x());
        }
        if (isVisible(lm, RIGHT_ELBOW)) {
            emaRElbowY = emaVal(emaRElbowY, lm.get(RIGHT_ELBOW).y());
            emaRElbowX = emaVal(emaRElbowX, lm.get(RIGHT_ELBOW).x());
        }
        if (isVisible(lm, LEFT_WRIST)) {
            emaLWristY = emaVal(emaLWristY, lm.get(LEFT_WRIST).y());
            emaLWristX = emaVal(emaLWristX, lm.get(LEFT_WRIST).x());
        }
        if (isVisible(lm, RIGHT_WRIST)) {
            emaRWristY = emaVal(emaRWristY, lm.get(RIGHT_WRIST).y());
            emaRWristX = emaVal(emaRWristX, lm.get(RIGHT_WRIST).x());
        }
        if (isVisible(lm, LEFT_HIP)) {
            emaLHipY = emaVal(emaLHipY, lm.get(LEFT_HIP).y());
            emaLHipX = emaVal(emaLHipX, lm.get(LEFT_HIP).x());
        }
        if (isVisible(lm, RIGHT_HIP)) {
            emaRHipY = emaVal(emaRHipY, lm.get(RIGHT_HIP).y());
            emaRHipX = emaVal(emaRHipX, lm.get(RIGHT_HIP).x());
        }
        if (isVisible(lm, LEFT_KNEE))  emaLKneeY = emaVal(emaLKneeY, lm.get(LEFT_KNEE).y());
        if (isVisible(lm, RIGHT_KNEE)) emaRKneeY = emaVal(emaRKneeY, lm.get(RIGHT_KNEE).y());

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

    @Override
    public void reset() {
        super.reset();
        emaNoseY = emaNoseX = -1f;
        emaLShoulderY = emaLShoulderX = emaRShoulderY = emaRShoulderX = -1f;
        emaLElbowY = emaLElbowX = emaRElbowY = emaRElbowX = -1f;
        emaLWristY = emaLWristX = emaRWristY = emaRWristX = -1f;
        emaLHipY = emaLHipX = emaRHipY = emaRHipX = -1f;
        emaLKneeY = emaRKneeY = -1f;
        emaShoulderWidth = -1f;
        barY          = -1f;
        barFrameCount = 0;
        prevShoulderX = -1f;
        emaSwingX     = -1f;
        currentView    = ViewMode.UNKNOWN;
        candidateView  = ViewMode.UNKNOWN;
        candidateCount = 0;
    }
}