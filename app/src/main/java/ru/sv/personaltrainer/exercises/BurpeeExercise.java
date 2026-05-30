package ru.sv.personaltrainer.exercises;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;

import java.util.List;

import ru.sv.personaltrainer.R;

public class BurpeeExercise extends BaseExercise {

    @Override
    public String getName() {
        return getString(R.string.exercise_burpee_name);
    }

    private enum Stage {STAND, PLANK, PLANK_DONE, JUMP}

    private Stage stage = Stage.STAND;

    private final PushUpExercise pushUpDelegate = new PushUpExercise();

    private int plankConfirm = 0;
    private int standConfirm = 0;
    private static final int CONFIRM_PLANK = 6;
    private static final int CONFIRM_STAND = 6;

    private static final float PLANK_WR_SH_DIFF = 0.30f;
    private static final float STAND_WR_SH_DIFF = 0.05f;
    private static final float JUMP_WR_SH_DIFF = -0.05f;

    @Override
    public AnalysisResult analyze(List<NormalizedLandmark> lm) {
        AnalysisResult result = new AnalysisResult();

        if (!isValidData(lm)) {
            result.mainFeedback = getString(R.string.msg_full_frame);
            result.repCount = repCount;
            return result;
        }

        boolean hasShoulders = allVisible(lm, LEFT_SHOULDER, RIGHT_SHOULDER);
        boolean hasWrists = allVisible(lm, LEFT_WRIST, RIGHT_WRIST);

        if (!hasShoulders) {
            result.mainFeedback = getString(R.string.msg_camera_shoulders);
            result.repCount = repCount;
            return result;
        }

        if (!hasWrists) {
            result.mainFeedback = getString(R.string.msg_camera_shoulders_hands);
            result.repCount = repCount;
            return result;
        }

        float shY = avgY(lm, LEFT_SHOULDER, RIGHT_SHOULDER);
        float wrY = avgY(lm, LEFT_WRIST, RIGHT_WRIST);
        float wrShDiff = wrY - shY;

        boolean isPlankPos = wrShDiff > PLANK_WR_SH_DIFF;
        boolean isStandPos = wrShDiff < STAND_WR_SH_DIFF;
        boolean isJumpPos = wrShDiff < JUMP_WR_SH_DIFF;

        updateStage(lm, isPlankPos, isStandPos, isJumpPos, result);

        result.repCount = repCount;
        result.mainFeedback = result.errors.isEmpty() ? buildFeedback() : result.errors.get(0);
        return result;
    }

    private void updateStage(List<NormalizedLandmark> lm, boolean isPlankPos, boolean isStandPos, boolean isJumpPos, AnalysisResult result) {
        switch (stage) {

            case STAND:
                result.phase = "UP";
                if (isPlankPos) {
                    plankConfirm++;
                    if (plankConfirm >= CONFIRM_PLANK) {
                        stage = Stage.PLANK;
                        plankConfirm = 0;
                        standConfirm = 0;
                        pushUpDelegate.reset();
                    }
                } else {
                    plankConfirm = 0;
                }
                break;

            case PLANK:
                AnalysisResult pushResult = pushUpDelegate.analyze(lm);
                result.phase = pushResult.phase;
                result.errors = pushResult.errors;

                if (pushResult.repCount >= 1) {
                    stage = Stage.PLANK_DONE;
                    plankConfirm = 0;
                    standConfirm = 0;
                    pushUpDelegate.reset();
                }

                if (isStandPos) {
                    standConfirm++;
                    if (standConfirm >= CONFIRM_STAND) {
                        stage = Stage.STAND;
                        standConfirm = 0;
                        plankConfirm = 0;
                        pushUpDelegate.reset();
                    }
                } else {
                    standConfirm = 0;
                }
                break;

            case PLANK_DONE:
                result.phase = "UP";
                if (isJumpPos) {
                    stage = Stage.JUMP;
                    plankConfirm = 0;
                    standConfirm = 0;
                }
                break;

            case JUMP:
                result.phase = "UP";
                if (!isJumpPos) {
                    repCount++;
                    stage = Stage.STAND;
                    plankConfirm = 0;
                    standConfirm = 0;
                }
                break;
        }
    }

    private String buildFeedback() {
        switch (stage) {
            case STAND:
                return repCount == 0 ? getString(R.string.feedback_burpee_stand_start) : getString(R.string.feedback_burpee_stand_repeat, repCount);
            case PLANK:
                return getString(R.string.feedback_burpee_plank);
            case PLANK_DONE:
                return getString(R.string.feedback_burpee_plank_done);
            case JUMP:
                return getString(R.string.feedback_burpee_jump);
            default:
                return getString(R.string.feedback_burpee_default);
        }
    }

    @Override
    public void reset() {
        super.reset();
        stage = Stage.STAND;
        pushUpDelegate.reset();
        plankConfirm = 0;
        standConfirm = 0;
    }
}