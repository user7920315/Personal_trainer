package ru.sv.personaltrainer.exercises;

import android.util.Log;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import java.util.List;

public class PlankExercise extends BaseExercise {

    private static final String TAG = "PlankExercise";

    private static final float BODY_SAG_WARN   =  0.06f;
    private static final float BODY_SAG_ERROR  =  0.12f;
    private static final float HIP_SAG_WARN    =  0.15f; // таз ниже плеч
    private static final float HIP_SAG_ERROR   =  0.25f;
    private static final float HIP_HIGH_WARN   = -0.20f; // таз выше плеч
    private static final float HIP_HIGH_ERROR  = -0.35f;
    private static final float BODY_HIGH_WARN  = -0.12f; // было -0.06
    private static final float BODY_HIGH_ERROR = -0.20f; // было -0.12

    private static final float KNEE_T = 0.70f;

    private static final float HEAD_NEUTRAL    = 0.15f;
    private static final float HEAD_DROP_WARN  = 0.25f;
    private static final float HEAD_DROP_ERROR = 0.40f;
    private static final float HEAD_HIGH_WARN  = 0.25f;
    private static final float HEAD_HIGH_ERROR = 0.40f;

    private static final float ARM_BODY_ANGLE_WARN  = 15f;
    private static final float ARM_BODY_ANGLE_ERROR = 25f;

    private static final float SHOULDER_TILT_WARN  = 0.03f;
    private static final float SHOULDER_TILT_ERROR = 0.06f;

    private static final float KNEE_TILT_WARN  = 0.05f;
    private static final float KNEE_TILT_ERROR = 0.10f;

    private static final float KNEE_ONLY_SAG_WARN  =  0.08f;
    private static final float KNEE_ONLY_SAG_ERROR =  0.14f;

    private static final float KNEE_ONLY_HIGH_WARN  = -0.14f; // было -0.08
    private static final float KNEE_ONLY_HIGH_ERROR = -0.22f; // было -0.14

    private static final float SIDE_THRESHOLD = 0.12f;
    private static final int   STABLE_FRAMES  = 8;

    private static final float EMA_ALPHA = 0.15f;

    private float emaShoulderY  = -1f; // avg Y обоих плеч
    private float emaShoulderX  = -1f; // avg X обоих плеч
    private float emaLShoulderY = -1f; // левое плечо Y
    private float emaRShoulderY = -1f; // правое плечо Y
    private float emaHipY = -1f; // avg Y обоих бёдер
    private float   emaKneeY  = -1f; // avg или одно колено Y
    private float   emaKneeX  = -1f; // avg или одно колено X
    private float   emaLKneeY = -1f; // левое колено Y
    private float   emaRKneeY = -1f; // правое колено Y
    private boolean hasLKnee  = false;
    private boolean hasRKnee  = false;
    private float emaAnkleY = -1f; // avg Y нижней точки ног
    private float emaAnkleX = -1f; // avg X нижней точки ног

    private float emaNoseY = -1f;

    private float emaWristX = -1f; // avg X запястий
    private float emaWristY = -1f; // avg Y запястий

    private float emaShoulderWidth = -1f;

    private ViewMode currentView    = ViewMode.UNKNOWN;
    private ViewMode candidateView  = ViewMode.UNKNOWN;
    private int      candidateCount = 0;

    private enum ViewMode { SIDE, FRONT, UNKNOWN }


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

        // Минимум: плечи видны
        if (!allVisible(lm, LEFT_SHOULDER, RIGHT_SHOULDER)) {
            result.mainFeedback =
                    "Направьте камеру — должны быть видны плечи";
            result.phase = "";
            return result;
        }

        // Обновляем EMA
        updateEMA(lm);

        // Определяем вид
        ViewMode view = updateView();
        if (view == ViewMode.UNKNOWN) {
            result.mainFeedback =
                    "Лягте боком или лицом к камере";
            result.phase = "";
            return result;
        }

        Log.d(TAG, "View=" + view);

        if (view == ViewMode.SIDE) {
            analyzeSide(lm, result);
        } else {
            analyzeFront(lm, result);
        }

        result.repCount     = repCount;
        result.mainFeedback = result.errors.isEmpty()
                ? "✅ Отличная планка! Держите позицию"
                : result.errors.get(0);

        return result;
    }

    private void analyzeSide(List<NormalizedLandmark> lm,
                             AnalysisResult result) {

        // 1. Прямолинейность тела через таз
        if (emaHipY > 0 && emaShoulderY > 0) {
            checkBodyLineSide(result);
        } else {
            Log.d(TAG, "Side: таз не виден — пропускаем проверку тела");
        }

        // 2. Параллельность руки и тела
        boolean hasWrist = emaWristX > 0 && emaWristY > 0;
        boolean hasBody  = (emaAnkleX > 0 && emaAnkleY > 0)
                || (emaKneeX  > 0 && emaKneeY  > 0);

        if (emaShoulderX > 0 && emaShoulderY > 0
                && hasWrist && hasBody) {
            checkArmBodyAngle(result);
        }

        // 3. Голова
        if (emaNoseY > 0 && emaShoulderY > 0
                && emaShoulderWidth > 0) {
            checkHead(result);
        }
    }
    private void analyzeFront(List<NormalizedLandmark> lm,
                              AnalysisResult result) {

        // 1. Симметрия плеч
        if (emaLShoulderY > 0 && emaRShoulderY > 0) {
            checkShoulderTilt(result);
        }

        // 2. Симметрия колен
        // Проверяем флаги — оба колена должны быть актуальны
        if (hasLKnee && hasRKnee) {
            checkKneeTilt(result);
        }

        // 3. Голова по центру
        if (isVisible(lm, NOSE)
                && allVisible(lm, LEFT_SHOULDER, RIGHT_SHOULDER)) {
            checkHeadCenter(lm, result);
        }
    }

    private void checkBodyLineSide(AnalysisResult result) {

        // Разница Y: > 0 → таз ниже плеч → провисает
        //            < 0 → таз выше плеч → задран
        float diff = emaHipY - emaShoulderY;

        // Масштаб — используем расстояние плечо-колено
        // если колено видно, иначе фиксированный
        float scale = (emaKneeY > 0 && emaShoulderY > 0)
                ? Math.abs(emaKneeY - emaShoulderY)
                : 0.20f;
        if (scale < 0.05f) scale = 0.10f;

        float deviation = diff / scale;

        Log.d(TAG, String.format(
                "BodyLineSide: shY=%.3f hipY=%.3f "
                        + "diff=%.3f scale=%.3f dev=%.3f",
                emaShoulderY, emaHipY,
                diff, scale, deviation));

        if (deviation > HIP_SAG_ERROR) {
            result.addError(
                    "⚠ Тело провисает — напрягите пресс и ягодицы",
                    LEFT_HIP, RIGHT_HIP);

        } else if (deviation > HIP_SAG_WARN) {
            result.addError(
                    "⚠ Тело немного провисает — подтяните пресс",
                    LEFT_HIP, RIGHT_HIP);

        } else if (deviation < HIP_HIGH_ERROR) {
            result.addError(
                    "⚠ Таз слишком высоко — опустите бёдра",
                    LEFT_HIP, RIGHT_HIP);

        } else if (deviation < HIP_HIGH_WARN) {
            result.addError(
                    "⚠ Таз чуть высоковат — опустите немного",
                    LEFT_HIP, RIGHT_HIP);
        }
    }

    private void checkBodyLineKneeOnly(AnalysisResult result) {

        float diff = emaKneeY - emaShoulderY;

        Log.d(TAG, String.format(
                "BodyLineKneeOnly: shY=%.3f knY=%.3f diff=%.3f",
                emaShoulderY, emaKneeY, diff));

        if (diff > KNEE_ONLY_SAG_ERROR) {
            result.addError(
                    "⚠ Тело провисает — напрягите пресс и ягодицы",
                    LEFT_KNEE, RIGHT_KNEE);

        } else if (diff > KNEE_ONLY_SAG_WARN) {
            result.addError(
                    "⚠ Тело немного провисает — подтяните пресс",
                    LEFT_KNEE, RIGHT_KNEE);

        } else if (diff < KNEE_ONLY_HIGH_ERROR) {
            result.addError(
                    "⚠ Таз слишком высоко — опустите бёдра",
                    LEFT_KNEE, RIGHT_KNEE);

        } else if (diff < KNEE_ONLY_HIGH_WARN) {
            result.addError(
                    "⚠ Таз чуть высоковат — опустите немного",
                    LEFT_KNEE, RIGHT_KNEE);
        }
    }

    private void checkArmBodyAngle(AnalysisResult result) {

        // Нижняя точка тела: лодыжка если есть, иначе колено
        float bodyEndY = emaAnkleY > 0 ? emaAnkleY : emaKneeY;
        float bodyEndX = emaAnkleX > 0 ? emaAnkleX : emaKneeX;

        if (bodyEndY <= 0 || bodyEndX <= 0) return;

        // Вектор тела: плечо → лодыжка/колено
        double bodyDx = bodyEndX - emaShoulderX;
        double bodyDy = bodyEndY - emaShoulderY;

        // Вектор руки: запястье → плечо (снизу вверх)
        double armDx  = emaShoulderX - emaWristX;
        double armDy  = emaShoulderY - emaWristY;

        // Длины векторов — проверка на вырожденный случай
        double bodyLen = Math.sqrt(bodyDx * bodyDx + bodyDy * bodyDy);
        double armLen  = Math.sqrt(armDx  * armDx  + armDy  * armDy);
        if (bodyLen < 0.01 || armLen < 0.01) return;

        // Угол каждого вектора к горизонтали
        double bodyAngle = Math.toDegrees(Math.atan2(bodyDy, bodyDx));
        double armAngle  = Math.toDegrees(Math.atan2(armDy,  armDx));

        // Угол между векторами, нормализация в [0, 180]
        double angleBetween = Math.abs(bodyAngle - armAngle);
        if (angleBetween > 180.0) angleBetween = 360.0 - angleBetween;

        // Отклонение от идеальных 90°
        double diff = Math.abs(angleBetween - 90.0);

        Log.d(TAG, String.format(
                "ArmBody: bodyAngle=%.1f° armAngle=%.1f° "
                        + "between=%.1f° diff=%.1f°",
                bodyAngle, armAngle, angleBetween, diff));

        if (diff > ARM_BODY_ANGLE_ERROR) {
            result.addError(
                    angleBetween < 90.0
                            ? "⚠ Плечи ушли вперёд — "
                            + "поставьте руки строго под плечи"
                            : "⚠ Плечи ушли назад — "
                            + "поставьте руки строго под плечи",
                    LEFT_SHOULDER, RIGHT_SHOULDER,
                    LEFT_WRIST,    RIGHT_WRIST);

        } else if (diff > ARM_BODY_ANGLE_WARN) {
            result.addError(
                    "⚠ Поправьте положение рук — "
                            + "руки должны быть перпендикулярны телу",
                    LEFT_SHOULDER, RIGHT_SHOULDER);
        }
    }

    private void checkHead(AnalysisResult result) {

        float scale = emaShoulderWidth > 0.02f
                ? emaShoulderWidth : 0.15f;

        float headDiff  = emaNoseY - emaShoulderY;
        float deviation = headDiff / scale;

        Log.d(TAG, String.format(
                "Head: noseY=%.3f shY=%.3f scale=%.3f "
                        + "headDiff=%.3f deviation=%.3f",
                emaNoseY, emaShoulderY, scale,
                headDiff, deviation));

        // Нейтральная зона — нет ошибки
        if (Math.abs(deviation) <= HEAD_NEUTRAL) return;

        if (deviation > HEAD_DROP_ERROR) {
            result.addError(
                    "⚠ Голова сильно опущена — "
                            + "смотрите чуть вперёд",
                    NOSE);

        } else if (deviation > HEAD_DROP_WARN) {
            result.addError(
                    "⚠ Не опускайте голову вниз",
                    NOSE);

        } else if (deviation < -HEAD_HIGH_ERROR) {
            result.addError(
                    "⚠ Голова сильно задрана — "
                            + "смотрите в пол перед собой",
                    NOSE);

        } else if (deviation < -HEAD_HIGH_WARN) {
            result.addError(
                    "⚠ Не запрокидывайте голову",
                    NOSE);
        }
    }

    private void checkShoulderTilt(AnalysisResult result) {

        float diff = emaLShoulderY - emaRShoulderY;

        Log.d(TAG, String.format(
                "ShoulderTilt: L=%.3f R=%.3f diff=%.3f",
                emaLShoulderY, emaRShoulderY, diff));

        if (Math.abs(diff) > SHOULDER_TILT_ERROR) {
            result.addError(
                    diff > 0
                            ? "⚠ Левое плечо ниже — выровняйте корпус"
                            : "⚠ Правое плечо ниже — выровняйте корпус",
                    LEFT_SHOULDER, RIGHT_SHOULDER);

        } else if (Math.abs(diff) > SHOULDER_TILT_WARN) {
            result.addError(
                    "⚠ Плечи немного перекошены — выровняйте",
                    LEFT_SHOULDER, RIGHT_SHOULDER);
        }
    }

    private void checkKneeTilt(AnalysisResult result) {

        float diff = emaLKneeY - emaRKneeY;

        Log.d(TAG, String.format(
                "KneeTilt: L=%.3f R=%.3f diff=%.3f",
                emaLKneeY, emaRKneeY, diff));

        if (Math.abs(diff) > KNEE_TILT_ERROR) {
            result.addError(
                    diff > 0
                            ? "⚠ Левая нога ниже — выровняйте таз"
                            : "⚠ Правая нога ниже — выровняйте таз",
                    LEFT_KNEE, RIGHT_KNEE);

        } else if (Math.abs(diff) > KNEE_TILT_WARN) {
            result.addError(
                    "⚠ Таз немного перекошен — выровняйте",
                    LEFT_KNEE, RIGHT_KNEE);
        }
    }

    private void checkHeadCenter(List<NormalizedLandmark> lm,
                                 AnalysisResult result) {

        float noseX         = lm.get(NOSE).x();
        float lShX          = lm.get(LEFT_SHOULDER).x();
        float rShX          = lm.get(RIGHT_SHOULDER).x();
        float midShoulderX  = (lShX + rShX) / 2f;
        float shoulderWidth = Math.abs(lShX - rShX);

        if (shoulderWidth < 0.02f) return;

        float offset = (noseX - midShoulderX) / shoulderWidth;

        Log.d(TAG, String.format(
                "HeadCenter: noseX=%.3f midShX=%.3f offset=%.3f",
                noseX, midShoulderX, offset));

        if (Math.abs(offset) > 0.25f) {
            result.addError(
                    "⚠ Голова смещена в сторону — держите ровно",
                    NOSE);
        }
    }

    private ViewMode updateView() {
        ViewMode raw = detectRawView();

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
            resetEMA();
            currentView = candidateView;
        }

        return currentView != ViewMode.UNKNOWN
                ? currentView : raw;
    }

    private ViewMode detectRawView() {
        if (emaShoulderWidth > 0) {
            return emaShoulderWidth < SIDE_THRESHOLD
                    ? ViewMode.SIDE : ViewMode.FRONT;
        }
        return ViewMode.UNKNOWN;
    }

    private void updateEMA(List<NormalizedLandmark> lm) {
        if (allVisible(lm, LEFT_HIP, RIGHT_HIP)) {
            float lHipY = lm.get(LEFT_HIP).y();
            float rHipY = lm.get(RIGHT_HIP).y();
            emaHipY = emaVal(emaHipY, (lHipY + rHipY) / 2f);

        } else if (isVisible(lm, LEFT_HIP)) {
            emaHipY = emaVal(emaHipY, lm.get(LEFT_HIP).y());

        } else if (isVisible(lm, RIGHT_HIP)) {
            emaHipY = emaVal(emaHipY, lm.get(RIGHT_HIP).y());
        }

        // ── Плечи ────────────────────────────────────
        if (allVisible(lm, LEFT_SHOULDER, RIGHT_SHOULDER)) {
            float lShY = lm.get(LEFT_SHOULDER).y();
            float rShY = lm.get(RIGHT_SHOULDER).y();
            float lShX = lm.get(LEFT_SHOULDER).x();
            float rShX = lm.get(RIGHT_SHOULDER).x();

            emaShoulderY     = emaVal(emaShoulderY,
                    (lShY + rShY) / 2f);
            emaShoulderX     = emaVal(emaShoulderX,
                    (lShX + rShX) / 2f);
            emaLShoulderY    = emaVal(emaLShoulderY, lShY);
            emaRShoulderY    = emaVal(emaRShoulderY, rShY);
            emaShoulderWidth = emaVal(emaShoulderWidth,
                    Math.abs(lShX - rShX));
        }

        if (allVisible(lm, LEFT_KNEE, RIGHT_KNEE)) {
            float lKnY = lm.get(LEFT_KNEE).y();
            float rKnY = lm.get(RIGHT_KNEE).y();
            float lKnX = lm.get(LEFT_KNEE).x();
            float rKnX = lm.get(RIGHT_KNEE).x();
            emaKneeY  = emaVal(emaKneeY,  (lKnY + rKnY) / 2f);
            emaKneeX  = emaVal(emaKneeX,  (lKnX + rKnX) / 2f);
            emaLKneeY = emaVal(emaLKneeY, lKnY);
            emaRKneeY = emaVal(emaRKneeY, rKnY);
            hasLKnee  = true;
            hasRKnee  = true;

        } else if (isVisible(lm, LEFT_KNEE)) {
            emaKneeY  = emaVal(emaKneeY,  lm.get(LEFT_KNEE).y());
            emaKneeX  = emaVal(emaKneeX,  lm.get(LEFT_KNEE).x());
            emaLKneeY = emaVal(emaLKneeY, lm.get(LEFT_KNEE).y());
            hasLKnee  = true;
            // emaRKneeY и hasRKnee не трогаем

        } else if (isVisible(lm, RIGHT_KNEE)) {
            emaKneeY  = emaVal(emaKneeY,  lm.get(RIGHT_KNEE).y());
            emaKneeX  = emaVal(emaKneeX,  lm.get(RIGHT_KNEE).x());
            emaRKneeY = emaVal(emaRKneeY, lm.get(RIGHT_KNEE).y());
            hasRKnee  = true;
            // emaLKneeY и hasLKnee не трогаем
        }

        // ── Лодыжки / пятки ──────────────────────────
        if (allVisible(lm, LEFT_ANKLE, RIGHT_ANKLE)) {
            float lAnY = lm.get(LEFT_ANKLE).y();
            float rAnY = lm.get(RIGHT_ANKLE).y();
            float lAnX = lm.get(LEFT_ANKLE).x();
            float rAnX = lm.get(RIGHT_ANKLE).x();
            emaAnkleY = emaVal(emaAnkleY, (lAnY + rAnY) / 2f);
            emaAnkleX = emaVal(emaAnkleX, (lAnX + rAnX) / 2f);

        } else if (isVisible(lm, LEFT_ANKLE)) {
            emaAnkleY = emaVal(emaAnkleY, lm.get(LEFT_ANKLE).y());
            emaAnkleX = emaVal(emaAnkleX, lm.get(LEFT_ANKLE).x());

        } else if (isVisible(lm, RIGHT_ANKLE)) {
            emaAnkleY = emaVal(emaAnkleY, lm.get(RIGHT_ANKLE).y());
            emaAnkleX = emaVal(emaAnkleX, lm.get(RIGHT_ANKLE).x());

        } else if (allVisible(lm, LEFT_HEEL, RIGHT_HEEL)) {
            // Fallback на пятки если лодыжки не видны
            emaAnkleY = emaVal(emaAnkleY,
                    (lm.get(LEFT_HEEL).y()
                            + lm.get(RIGHT_HEEL).y()) / 2f);
            emaAnkleX = emaVal(emaAnkleX,
                    (lm.get(LEFT_HEEL).x()
                            + lm.get(RIGHT_HEEL).x()) / 2f);
        }

        // ── Голова ───────────────────────────────────
        if (isVisible(lm, NOSE)) {
            emaNoseY = emaVal(emaNoseY, lm.get(NOSE).y());
        }

        // ── Запястья ─────────────────────────────────
        if (allVisible(lm, LEFT_WRIST, RIGHT_WRIST)) {
            float lWrX = lm.get(LEFT_WRIST).x();
            float rWrX = lm.get(RIGHT_WRIST).x();
            float lWrY = lm.get(LEFT_WRIST).y();
            float rWrY = lm.get(RIGHT_WRIST).y();
            emaWristX = emaVal(emaWristX, (lWrX + rWrX) / 2f);
            emaWristY = emaVal(emaWristY, (lWrY + rWrY) / 2f);

        } else if (isVisible(lm, LEFT_WRIST)) {
            emaWristX = emaVal(emaWristX, lm.get(LEFT_WRIST).x());
            emaWristY = emaVal(emaWristY, lm.get(LEFT_WRIST).y());

        } else if (isVisible(lm, RIGHT_WRIST)) {
            emaWristX = emaVal(emaWristX, lm.get(RIGHT_WRIST).x());
            emaWristY = emaVal(emaWristY, lm.get(RIGHT_WRIST).y());
        }
    }

    private float emaVal(float prev, float newVal) {
        if (newVal < 0
                || newVal > 1.0f
                || Float.isNaN(newVal)
                || Float.isInfinite(newVal)) return prev;
        if (prev < 0) return newVal; // первое значение без сглаживания
        return prev + EMA_ALPHA * (newVal - prev);
    }

    private void resetEMA() {
        emaShoulderY     = emaShoulderX     = -1f;
        emaLShoulderY    = emaRShoulderY    = -1f;
        emaHipY          = -1f;              // ← новое поле
        emaKneeY         = emaKneeX         = -1f;
        emaLKneeY        = emaRKneeY        = -1f;
        emaAnkleY        = emaAnkleX        = -1f;
        emaNoseY         = -1f;
        emaWristX        = emaWristY        = -1f;
        hasLKnee         = hasRKnee         = false;
        Log.d(TAG, "EMA reset");
    }

    @Override
    public void reset() {
        super.reset();
        resetEMA();
        emaShoulderWidth = -1f;
        currentView      = ViewMode.UNKNOWN;
        candidateView    = ViewMode.UNKNOWN;
        candidateCount   = 0;
    }
}