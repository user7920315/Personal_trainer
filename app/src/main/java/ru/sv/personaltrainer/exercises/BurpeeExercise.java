package ru.sv.personaltrainer.exercises;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import java.util.List;

public class BurpeeExercise extends BaseExercise {

    @Override
    public String getName() { return "Берпи"; }

    private enum Stage {
        STAND,         // стоим
        PLANK_UP,      // в планке, руки прямые — нужно опуститься
        PLANK_DOWN,    // внизу — нужно выжаться
        PLANK_UP_DONE, // выжались — нужно прыгнуть
        JUMP           // в прыжке — нужно приземлиться
    }

    private Stage stage = Stage.STAND;

    // Порог: тело горизонтально (в планке)
    // Разница Y между плечами и лодыжками
    private static final float BODY_FLAT_THRESHOLD = 0.20f;

    @Override
    public AnalysisResult analyze(List<NormalizedLandmark> lm) {
        AnalysisResult result = new AnalysisResult();

        if (!isValidData(lm)) {
            result.mainFeedback = "Встаньте полностью в кадр";
            result.repCount = repCount;
            return result;
        }

        boolean hasShoulders = allVisible(lm, LEFT_SHOULDER, RIGHT_SHOULDER);
        boolean hasElbows    = allVisible(lm, LEFT_ELBOW, RIGHT_ELBOW);
        boolean hasWrists    = allVisible(lm, LEFT_WRIST,  RIGHT_WRIST);
        boolean hasHips      = allVisible(lm, LEFT_HIP,    RIGHT_HIP);
        boolean hasKnees     = allVisible(lm, LEFT_KNEE,   RIGHT_KNEE);
        boolean hasAnkles    = allVisible(lm, LEFT_ANKLE,  RIGHT_ANKLE);
        boolean hasNose      = isVisible(lm, NOSE);

        if (!hasShoulders) {
            result.mainFeedback = "Направьте камеру — должны быть видны плечи";
            result.repCount = repCount;
            return result;
        }

        // ── Углы локтей ──────────────────────────────────────────
        float leftElbow  = hasElbows
                ? getAngle(lm, LEFT_SHOULDER,  LEFT_ELBOW,  LEFT_WRIST)  : -1f;
        float rightElbow = hasElbows
                ? getAngle(lm, RIGHT_SHOULDER, RIGHT_ELBOW, RIGHT_WRIST) : -1f;

        float avgElbow;
        if      (leftElbow < 0 && rightElbow < 0) avgElbow = -1f;
        else if (leftElbow  < 0)                  avgElbow = rightElbow;
        else if (rightElbow < 0)                  avgElbow = leftElbow;
        else                                      avgElbow = (leftElbow + rightElbow) / 2f;

        // ── Тело горизонтально (лежит в планке) ─────────────────
        // Используем Y плеч и Y лодыжек — если близко, человек лежит
        boolean bodyFlat = false;
        if (hasShoulders && hasAnkles) {
            float shY  = avgY(lm, LEFT_SHOULDER, RIGHT_SHOULDER);
            float ankY = avgY(lm, LEFT_ANKLE,    RIGHT_ANKLE);
            bodyFlat = Math.abs(shY - ankY) < BODY_FLAT_THRESHOLD;
        }

        // ── Запястья выше плеч (прыжок) ─────────────────────────
        boolean wristsUp = false;
        if (hasShoulders && hasWrists) {
            float shY = avgY(lm, LEFT_SHOULDER, RIGHT_SHOULDER);
            float wrY = avgY(lm, LEFT_WRIST,    RIGHT_WRIST);
            wristsUp = (shY - wrY) > 0.06f;
        }

        // ── Машина состояний ─────────────────────────────────────
        updateStage(avgElbow, bodyFlat, wristsUp, hasElbows);

        // ── phase для UI ─────────────────────────────────────────
        result.phase = stageToPhaseLabel(stage);

        // ── Ошибки ───────────────────────────────────────────────
        switch (stage) {
            case PLANK_UP:
            case PLANK_DOWN:
            case PLANK_UP_DONE:
                analyzePushUp(lm, result, avgElbow,
                        hasShoulders, hasElbows,
                        hasHips, hasKnees, hasNose);
                break;
            case JUMP:
                analyzeJump(lm, result, hasShoulders, hasWrists);
                break;
            default:
                break;
        }

        result.repCount     = repCount;
        result.mainFeedback = result.errors.isEmpty()
                ? buildFeedback()
                : result.errors.get(0);

        return result;
    }

    // ═════════════════════════════════════════════════════════════
    //  Машина состояний
    // ═════════════════════════════════════════════════════════════
    private void updateStage(float avgElbow, boolean bodyFlat,
                             boolean wristsUp, boolean hasElbows) {
        switch (stage) {

            case STAND:
                // Переходим в планку только если тело горизонтально
                // Это надёжнее чем угол локтя — стоя локти тоже прямые
                if (bodyFlat) {
                    stage = Stage.PLANK_UP;
                }
                break;

            case PLANK_UP:
                // Ждём опускания: локоть < 90°
                if (hasElbows && avgElbow > 0 && avgElbow < 90f) {
                    stage = Stage.PLANK_DOWN;
                }
                break;

            case PLANK_DOWN:
                // Ждём подъёма: локоть > 160°
                if (hasElbows && avgElbow > 160f) {
                    stage = Stage.PLANK_UP_DONE;
                }
                break;

            case PLANK_UP_DONE:
                // Два варианта выхода:
                // 1. Прыжок — руки вверх
                if (wristsUp) {
                    stage = Stage.JUMP;
                }
                // 2. Ещё одно отжимание — снова вниз
                else if (hasElbows && avgElbow > 0 && avgElbow < 90f) {
                    stage = Stage.PLANK_DOWN;
                }
                break;

            case JUMP:
                // Приземлились — руки опустили
                if (!wristsUp) {
                    repCount++;
                    stage = Stage.STAND;
                }
                break;
        }
    }

    // ═════════════════════════════════════════════════════════════
    //  Ошибки отжимания (из PushUpExercise)
    // ═════════════════════════════════════════════════════════════
    private void analyzePushUp(List<NormalizedLandmark> lm,
                               AnalysisResult result,
                               float avgElbow,
                               boolean hasShoulders,
                               boolean hasElbows,
                               boolean hasHips,
                               boolean hasKnees,
                               boolean hasNose) {

        if (hasShoulders && hasElbows) {
            float shoulderW = distX(lm, LEFT_SHOULDER, RIGHT_SHOULDER);
            float elbowW    = distX(lm, LEFT_ELBOW,    RIGHT_ELBOW);
            if (shoulderW > 0 && elbowW > 0 && elbowW > shoulderW * 1.5f) {
                result.addError(
                        "⚠ Локти слишком широко — держите ближе к телу",
                        LEFT_ELBOW, RIGHT_ELBOW);
            }
        }

        if ((stage == Stage.PLANK_UP || stage == Stage.PLANK_UP_DONE)
                && avgElbow > 0 && avgElbow < 150f) {
            result.addError(
                    "⚠ Полностью разгибайте руки в верхней точке",
                    LEFT_ELBOW, RIGHT_ELBOW);
        }

        if (stage == Stage.PLANK_DOWN && avgElbow > 0 && avgElbow > 110f) {
            result.addError(
                    "⚠ Опускайтесь ниже — грудь ближе к полу",
                    LEFT_ELBOW, RIGHT_ELBOW);
        }

        analyzeBackAndHips(lm, result,
                hasShoulders, hasElbows, hasHips, hasKnees, hasNose);
    }

    private void analyzeBackAndHips(List<NormalizedLandmark> lm,
                                    AnalysisResult result,
                                    boolean hasShoulders,
                                    boolean hasElbows,
                                    boolean hasHips,
                                    boolean hasKnees,
                                    boolean hasNose) {
        if (hasElbows && hasHips) {
            float elbowY = avgY(lm, LEFT_ELBOW, RIGHT_ELBOW);
            float hipY   = avgY(lm, LEFT_HIP,   RIGHT_HIP);
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
            float tilt = Math.abs(
                    lm.get(LEFT_SHOULDER).y() - lm.get(RIGHT_SHOULDER).y());
            if (tilt > 0.05f) {
                result.addError(
                        "⚠ Плечи перекошены — держите их ровно",
                        LEFT_SHOULDER, RIGHT_SHOULDER);
            }
            if (hasNose) {
                float shoulderY = avgY(lm, LEFT_SHOULDER, RIGHT_SHOULDER);
                float noseY     = lm.get(NOSE).y();
                if (shoulderY >= 0) {
                    if (noseY > shoulderY + 0.08f) {
                        result.addError(
                                "⚠ Держите голову прямо — не опускайте подбородок",
                                NOSE);
                    } else if (noseY < shoulderY - 0.08f) {
                        result.addError(
                                "⚠ Не запрокидывайте голову", NOSE);
                    }
                }
            }
            return;
        }

        if (hasShoulders && hasNose) {
            float shoulderY = avgY(lm, LEFT_SHOULDER, RIGHT_SHOULDER);
            float tilt      = Math.abs(
                    lm.get(LEFT_SHOULDER).y() - lm.get(RIGHT_SHOULDER).y());
            float noseY     = lm.get(NOSE).y();
            if (tilt > 0.05f) {
                result.addError(
                        "⚠ Плечи перекошены — держите их ровно",
                        LEFT_SHOULDER, RIGHT_SHOULDER);
            }
            if (noseY > shoulderY + 0.08f) {
                result.addError(
                        "⚠ Держите голову прямо — не опускайте подбородок",
                        NOSE);
            } else if (noseY < shoulderY - 0.08f) {
                result.addError("⚠ Не запрокидывайте голову", NOSE);
            }
        }
    }

    // ═════════════════════════════════════════════════════════════
    //  Ошибки прыжка
    // ═════════════════════════════════════════════════════════════
    private void analyzeJump(List<NormalizedLandmark> lm,
                             AnalysisResult result,
                             boolean hasShoulders,
                             boolean hasWrists) {
        if (!hasShoulders || !hasWrists) return;

        float shY = avgY(lm, LEFT_SHOULDER, RIGHT_SHOULDER);
        float wrY = avgY(lm, LEFT_WRIST,    RIGHT_WRIST);
        if (shY < 0 || wrY < 0) return;

        if ((shY - wrY) < 0.06f) {
            result.addError(
                    "⚠ Поднимите руки выше при прыжке!",
                    LEFT_WRIST, RIGHT_WRIST);
        }

        float tilt = Math.abs(
                lm.get(LEFT_SHOULDER).y() - lm.get(RIGHT_SHOULDER).y());
        if (tilt > 0.06f) {
            result.addError(
                    "⚠ Держите плечи ровно при прыжке",
                    LEFT_SHOULDER, RIGHT_SHOULDER);
        }
    }

    // ═════════════════════════════════════════════════════════════
    //  Вспомогательные
    // ═════════════════════════════════════════════════════════════
    private String stageToPhaseLabel(Stage s) {
        switch (s) {
            case PLANK_DOWN: return "DOWN";
            case PLANK_UP:
            case PLANK_UP_DONE:
            case JUMP:       return "UP";
            default:         return "UP";
        }
    }

    // Подсказка — что нужно сделать СЕЙЧАС в текущем состоянии
    private String buildFeedback() {
        switch (stage) {
            case STAND:
                return repCount == 0
                        ? "✅ Упадите в планку"
                        : "✅ Повторений: " + repCount + " — упадите в планку";
            case PLANK_UP:
                // Попали в планку — просим опуститься
                return "✅ Опуститесь грудью к полу";
            case PLANK_DOWN:
                // Внизу — просим выжаться
                return "✅ Выжимайтесь вверх";
            case PLANK_UP_DONE:
                // Выжались — просим прыгнуть
                return "✅ Вставайте и прыгайте!";
            case JUMP:
                return "✅ Прыжок! Руки вверх!";
            default:
                return "✅ Выполняйте упражнение";
        }
    }

    @Override
    public void reset() {
        super.reset();
        stage = Stage.STAND;
    }
}