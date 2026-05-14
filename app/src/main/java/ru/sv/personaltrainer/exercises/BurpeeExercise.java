package ru.sv.personaltrainer.exercises;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import java.util.List;

public class BurpeeExercise extends BaseExercise {

    @Override
    public String getName() { return "Берпи"; }

    private enum Stage { STAND, PLANK, PLANK_DONE, JUMP }

    private Stage stage = Stage.STAND;
    private boolean isDown = false;

    private int plankConfirm = 0;
    private int standConfirm = 0;
    private static final int CONFIRM_PLANK = 6;
    private static final int CONFIRM_STAND = 6;

    // ─── Пороги, откалиброванные по реальным логам ────────────────────────────
    // Стоя:   wrY - shY ≈ -0.23 (запястья выше плеч)
    // Планка: wrY - shY ≈ +0.54 (запястья сильно ниже плеч)
    // Порог между ними — берём +0.30 с запасом
    private static final float PLANK_WR_SH_DIFF  = 0.30f;  // > этого = планка
    private static final float STAND_WR_SH_DIFF  = 0.05f;  // < этого = стойка

    // Прыжок: запястья выше плеч
    private static final float JUMP_WR_SH_DIFF   = -0.05f; // < этого = руки вверх

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
        boolean hasNose      = isVisible(lm,  NOSE);

        if (!hasShoulders) {
            result.mainFeedback = "Направьте камеру — должны быть видны плечи";
            result.repCount = repCount;
            return result;
        }

        if (!hasWrists) {
            result.mainFeedback = "Направьте камеру — должны быть видны руки";
            result.repCount = repCount;
            return result;
        }

        // ── Главный признак: разница Y запястья–плечи ─────────────────────────
        float shY   = avgY(lm, LEFT_SHOULDER, RIGHT_SHOULDER);
        float wrY   = avgY(lm, LEFT_WRIST,    RIGHT_WRIST);
        float wrShDiff = wrY - shY; // >0 = запястья ниже плеч

        android.util.Log.d("BURPEE_MAIN",
                "wrShDiff=" + wrShDiff +
                        " shY=" + shY + " wrY=" + wrY +
                        " stage=" + stage);

        boolean isPlankPos = wrShDiff > PLANK_WR_SH_DIFF;
        boolean isStandPos = wrShDiff < STAND_WR_SH_DIFF;
        boolean isJumpPos  = wrShDiff < JUMP_WR_SH_DIFF;

        float avgElbow = computeAvgElbow(lm, hasElbows);

        updateStage(avgElbow, isPlankPos, isStandPos, isJumpPos, hasElbows);

        result.phase = stageToPhaseLabel(stage, isDown);

        switch (stage) {
            case PLANK:
            case PLANK_DONE:
                analyzePushUp(lm, result, avgElbow,
                        hasShoulders, hasElbows, hasHips, hasKnees, hasNose);
                break;
            case JUMP:
                analyzeJump(lm, result, hasShoulders, hasWrists);
                break;
            default:
                break;
        }

        result.repCount = repCount;
        result.mainFeedback = result.errors.isEmpty()
                ? buildFeedback() : result.errors.get(0);
        return result;
    }

    // ─── Машина состояний ─────────────────────────────────────────────────────

    private void updateStage(float avgElbow,
                             boolean isPlankPos,
                             boolean isStandPos,
                             boolean isJumpPos,
                             boolean hasElbows) {
        switch (stage) {

            case STAND:
                if (isPlankPos) {
                    plankConfirm++;
                    android.util.Log.d("BURPEE",
                            "STAND→PLANK confirm=" + plankConfirm + "/" + CONFIRM_PLANK);
                    if (plankConfirm >= CONFIRM_PLANK) {
                        stage = Stage.PLANK;
                        isDown = false;
                        plankConfirm = 0;
                        standConfirm = 0;
                        android.util.Log.d("BURPEE", "→ PLANK ✓");
                    }
                } else {
                    plankConfirm = 0;
                }
                break;

            case PLANK:
                // Засчитываем отжимание
                if (hasElbows && avgElbow > 0) {
                    if (avgElbow < 90f && !isDown) {
                        isDown = true;
                        android.util.Log.d("BURPEE", "isDown=true elbow=" + avgElbow);
                    }
                    if (isDown && avgElbow > 160f) {
                        isDown = false;
                        stage = Stage.PLANK_DONE;
                        plankConfirm = 0;
                        standConfirm = 0;
                        android.util.Log.d("BURPEE", "→ PLANK_DONE ✓");
                        break;
                    }
                }
                // Встали без отжимания
                if (isStandPos && !isDown) {
                    standConfirm++;
                    android.util.Log.d("BURPEE",
                            "PLANK→STAND confirm=" + standConfirm + "/" + CONFIRM_STAND);
                    if (standConfirm >= CONFIRM_STAND) {
                        stage = Stage.STAND;
                        standConfirm = 0;
                        plankConfirm = 0;
                        android.util.Log.d("BURPEE", "→ STAND (без отжимания)");
                    }
                } else if (!isStandPos) {
                    standConfirm = 0;
                }
                break;

            case PLANK_DONE:
                // Руки вверх = прыжок
                if (isJumpPos) {
                    stage = Stage.JUMP;
                    plankConfirm = 0;
                    standConfirm = 0;
                    android.util.Log.d("BURPEE", "→ JUMP ✓");
                    break;
                }
                // Лёг снова = новое отжимание
                if (isPlankPos) {
                    stage = Stage.PLANK;
                    isDown = false;
                    android.util.Log.d("BURPEE", "→ PLANK (повторное)");
                }
                break;

            case JUMP:
                // Руки опустил = повторение засчитано
                if (!isJumpPos) {
                    repCount++;
                    stage = Stage.STAND;
                    isDown = false;
                    plankConfirm = 0;
                    standConfirm = 0;
                    android.util.Log.d("BURPEE", "→ STAND repCount=" + repCount);
                }
                break;
        }
    }

    // ─── Вычисления ───────────────────────────────────────────────────────────

    private float computeAvgElbow(List<NormalizedLandmark> lm, boolean hasElbows) {
        if (!hasElbows) return -1f;
        float l = getAngle(lm, LEFT_SHOULDER,  LEFT_ELBOW,  LEFT_WRIST);
        float r = getAngle(lm, RIGHT_SHOULDER, RIGHT_ELBOW, RIGHT_WRIST);
        if (l < 0 && r < 0) return -1f;
        if (l < 0) return r;
        if (r < 0) return l;
        return (l + r) / 2f;
    }

    // ─── Анализ ошибок ────────────────────────────────────────────────────────

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
            if (shoulderW > 0 && elbowW > shoulderW * 1.5f) {
                result.addError("⚠ Локти слишком широко — держите ближе к телу",
                        LEFT_ELBOW, RIGHT_ELBOW);
            }
        }

        if (!isDown && avgElbow > 0 && avgElbow < 150f) {
            result.addError("⚠ Полностью разгибайте руки в верхней точке",
                    LEFT_ELBOW, RIGHT_ELBOW);
        }

        if (isDown && avgElbow > 0 && avgElbow > 110f) {
            result.addError("⚠ Опускайтесь ниже — грудь ближе к полу",
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
            float diff   = hipY - elbowY;
            if (diff > 0.04f) {
                result.addError("⚠ Таз провисает — напрягите пресс и ягодицы",
                        LEFT_HIP, RIGHT_HIP);
            } else if (diff < -0.04f) {
                result.addError("⚠ Таз задран вверх — опустите его",
                        LEFT_HIP, RIGHT_HIP);
            }
            if (hasKnees) {
                float backAngle = getAngle(lm, LEFT_SHOULDER, LEFT_HIP, LEFT_KNEE);
                if (backAngle > 0 && backAngle < 150f) {
                    result.addError("⚠ Выпрямите спину — не прогибайтесь в пояснице",
                            LEFT_HIP, RIGHT_HIP);
                }
            }
            return;
        }
        if (hasShoulders) {
            float tilt = Math.abs(
                    lm.get(LEFT_SHOULDER).y() - lm.get(RIGHT_SHOULDER).y());
            if (tilt > 0.05f) {
                result.addError("⚠ Плечи перекошены — держите их ровно",
                        LEFT_SHOULDER, RIGHT_SHOULDER);
            }
            if (hasNose) {
                float shY   = avgY(lm, LEFT_SHOULDER, RIGHT_SHOULDER);
                float noseY = lm.get(NOSE).y();
                if (noseY > shY + 0.08f) {
                    result.addError(
                            "⚠ Держите голову прямо — не опускайте подбородок",
                            NOSE);
                } else if (noseY < shY - 0.08f) {
                    result.addError("⚠ Не запрокидывайте голову", NOSE);
                }
            }
        }
    }

    private void analyzeJump(List<NormalizedLandmark> lm,
                             AnalysisResult result,
                             boolean hasShoulders,
                             boolean hasWrists) {
        if (!hasShoulders || !hasWrists) return;
        float shY = avgY(lm, LEFT_SHOULDER, RIGHT_SHOULDER);
        float wrY = avgY(lm, LEFT_WRIST,    RIGHT_WRIST);
        if ((wrY - shY) > JUMP_WR_SH_DIFF) {
            result.addError("⚠ Поднимите руки выше при прыжке!",
                    LEFT_WRIST, RIGHT_WRIST);
        }
    }

    // ─── UI ───────────────────────────────────────────────────────────────────

    private String stageToPhaseLabel(Stage s, boolean down) {
        if (s == Stage.PLANK) return down ? "DOWN" : "UP";
        return "UP";
    }

    private String buildFeedback() {
        switch (stage) {
            case STAND:
                return repCount == 0
                        ? "✅ Упадите в планку"
                        : "✅ Повторений: " + repCount + " — упадите в планку";
            case PLANK:      return "✅ Сделайте отжимание";
            case PLANK_DONE: return "✅ Вставайте и прыгайте!";
            case JUMP:       return "✅ Прыжок! Руки вверх!";
            default:         return "✅ Выполняйте упражнение";
        }
    }

    @Override
    public void reset() {
        super.reset();
        stage = Stage.STAND;
        isDown = false;
        plankConfirm = 0;
        standConfirm = 0;
    }
}