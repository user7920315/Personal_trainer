package ru.sv.personaltrainer.exercises;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import java.util.List;

public class BurpeeExercise extends BaseExercise {

    @Override
    public String getName() { return "Берпи"; }

    private enum Stage {
        STAND,
        PLANK,
        PLANK_DONE,
        JUMP
    }

    private Stage stage    = Stage.STAND;
    private boolean isDown = false;

    // Порог горизонтальности тела
    // Используем плечи + бёдра (они видны лучше чем лодыжки)
    private static final float BODY_FLAT_HIP_THRESHOLD     = 0.10f;
    // Запасной вариант через лодыжки (если всё же видны)
    private static final float BODY_FLAT_ANKLE_THRESHOLD   = 0.35f;

    @Override
    public AnalysisResult analyze(List<NormalizedLandmark> lm) {
        AnalysisResult result = new AnalysisResult();

        if (!isValidData(lm)) {
            result.mainFeedback = "Встаньте полностью в кадр";
            result.repCount = repCount;
            return result;
        }

        boolean hasShoulders = allVisible(lm, LEFT_SHOULDER, RIGHT_SHOULDER);
        boolean hasElbows    = allVisible(lm, LEFT_ELBOW,    RIGHT_ELBOW);
        boolean hasWrists    = allVisible(lm, LEFT_WRIST,    RIGHT_WRIST);
        boolean hasHips      = allVisible(lm, LEFT_HIP,      RIGHT_HIP);
        boolean hasKnees     = allVisible(lm, LEFT_KNEE,     RIGHT_KNEE);
        boolean hasAnkles    = allVisible(lm, LEFT_ANKLE,    RIGHT_ANKLE);
        boolean hasNose      = isVisible(lm,  NOSE);

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

        // ── Определяем горизонтальность тела (планка) ────────────
        // Приоритет 1: плечи + бёдра (самые надёжные точки)
        // Приоритет 2: плечи + лодыжки (если видны)
        boolean bodyFlat = false;

        if (hasShoulders && hasHips) {
            float shY  = avgY(lm, LEFT_SHOULDER, RIGHT_SHOULDER);
            float hipY = avgY(lm, LEFT_HIP,      RIGHT_HIP);
            float diff = Math.abs(shY - hipY);

            bodyFlat = diff < BODY_FLAT_HIP_THRESHOLD;

            android.util.Log.d("BURPEE",
                    "shY=" + shY + " hipY=" + hipY +
                            " diff=" + diff + " bodyFlat=" + bodyFlat +
                            " stage=" + stage);

        } else if (hasShoulders && hasAnkles) {
            // Запасной вариант
            float shY  = avgY(lm, LEFT_SHOULDER, RIGHT_SHOULDER);
            float ankY = avgY(lm, LEFT_ANKLE,    RIGHT_ANKLE);
            float diff = Math.abs(shY - ankY);

            bodyFlat = diff < BODY_FLAT_ANKLE_THRESHOLD;

            android.util.Log.d("BURPEE",
                    "[ankle fallback] shY=" + shY + " ankY=" + ankY +
                            " diff=" + diff + " bodyFlat=" + bodyFlat);
        }

        // ── Запястья выше плеч (прыжок) ─────────────────────────
        boolean wristsUp = false;
        if (hasShoulders && hasWrists) {
            float shY = avgY(lm, LEFT_SHOULDER, RIGHT_SHOULDER);
            float wrY = avgY(lm, LEFT_WRIST,    RIGHT_WRIST);
            // В MediaPipe Y растёт вниз, поэтому
            // руки подняты = запястья имеют МЕНЬШИЙ Y чем плечи
            wristsUp  = (shY - wrY) > 0.06f;
        }

        // ── Машина состояний ─────────────────────────────────────
        updateStage(avgElbow, bodyFlat, wristsUp, hasElbows);

        // ── phase для UI ─────────────────────────────────────────
        result.phase = stageToPhaseLabel(stage, isDown);

        // ── Анализ ошибок ─────────────────────────────────────────
        switch (stage) {
            case PLANK:
            case PLANK_DONE:
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
                if (bodyFlat) {
                    stage  = Stage.PLANK;
                    isDown = false;
                    android.util.Log.d("BURPEE", "→ PLANK");
                }
                break;

            case PLANK:
                if (hasElbows && avgElbow > 0) {
                    // Опустились вниз
                    if (avgElbow < 90f && !isDown) {
                        isDown = true;
                        android.util.Log.d("BURPEE", "isDown = true, elbow=" + avgElbow);
                    }
                    // Выжались вверх — отжимание засчитано
                    if (isDown && avgElbow > 160f) {
                        isDown = false;
                        stage  = Stage.PLANK_DONE;
                        android.util.Log.d("BURPEE", "→ PLANK_DONE");
                    }
                }
                // Если встали (тело больше не горизонтально)
                // но отжимание ещё не сделали — сбрасываем в STAND
                if (!bodyFlat && !isDown) {
                    stage = Stage.STAND;
                    android.util.Log.d("BURPEE", "→ STAND (встали без отжимания)");
                }
                break;

            case PLANK_DONE:
                if (wristsUp) {
                    stage = Stage.JUMP;
                    android.util.Log.d("BURPEE", "→ JUMP");
                }
                // Если лёг снова — вернуть в планку для ещё одного отжимания
                if (bodyFlat && !wristsUp) {
                    stage  = Stage.PLANK;
                    isDown = false;
                }
                break;

            case JUMP:
                if (!wristsUp) {
                    repCount++;
                    stage  = Stage.STAND;
                    isDown = false;
                    android.util.Log.d("BURPEE", "→ STAND repCount=" + repCount);
                }
                break;
        }
    }

    // ═════════════════════════════════════════════════════════════
    //  Ошибки отжимания — та же логика что в PushUpExercise
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

        if (!isDown && avgElbow > 0 && avgElbow < 150f) {
            result.addError(
                    "⚠ Полностью разгибайте руки в верхней точке",
                    LEFT_ELBOW, RIGHT_ELBOW);
        }

        if (isDown && avgElbow > 0 && avgElbow > 110f) {
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
                        result.addError("⚠ Не запрокидывайте голову", NOSE);
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
    private String stageToPhaseLabel(Stage s, boolean down) {
        switch (s) {
            case PLANK: return down ? "DOWN" : "UP";
            case PLANK_DONE:
            case JUMP:  return "UP";
            default:    return "UP";
        }
    }

    private String buildFeedback() {
        switch (stage) {
            case STAND:
                return repCount == 0
                        ? "✅ Упадите в планку"
                        : "✅ Повторений: " + repCount + " — упадите в планку";
            case PLANK:
                return "✅ Сделайте одно отжимание";
            case PLANK_DONE:
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
        stage  = Stage.STAND;
        isDown = false;
    }
}