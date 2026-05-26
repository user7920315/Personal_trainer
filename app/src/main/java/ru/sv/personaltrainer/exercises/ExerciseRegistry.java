package ru.sv.personaltrainer.exercises;

import android.content.Context;

import ru.sv.personaltrainer.R;
import ru.sv.personaltrainer.model.ExerciseInfo;

import java.util.Arrays;
import java.util.List;

public class ExerciseRegistry {

    public static List<ExerciseInfo> getAll(Context context) {
        return Arrays.asList(

                new ExerciseInfo(
                        "PUSH_UP",
                        context.getString(R.string.exercise_push_up_name),
                        context.getString(R.string.exercise_push_up_emoji),
                        context.getString(R.string.exercise_push_up_muscles),
                        context.getString(R.string.exercise_push_up_difficulty),
                        R.color.exercise_push_up,
                        context.getString(R.string.exercise_push_up_description),
                        context.getString(R.string.exercise_push_up_video)
                ),

                new ExerciseInfo(
                        "SQUAT",
                        context.getString(R.string.exercise_squat_name),
                        context.getString(R.string.exercise_squat_emoji),
                        context.getString(R.string.exercise_squat_muscles),
                        context.getString(R.string.exercise_squat_difficulty),
                        R.color.exercise_squat,
                        context.getString(R.string.exercise_squat_description),
                        context.getString(R.string.exercise_squat_video)
                ),

                new ExerciseInfo(
                        "PLANK",
                        context.getString(R.string.exercise_plank_name),
                        context.getString(R.string.exercise_plank_emoji),
                        context.getString(R.string.exercise_plank_muscles),
                        context.getString(R.string.exercise_plank_difficulty),
                        R.color.exercise_plank,
                        context.getString(R.string.exercise_plank_description),
                        context.getString(R.string.exercise_plank_video)
                ),

                new ExerciseInfo(
                        "LUNGE",
                        context.getString(R.string.exercise_lunge_name),
                        context.getString(R.string.exercise_lunge_emoji),
                        context.getString(R.string.exercise_lunge_muscles),
                        context.getString(R.string.exercise_lunge_difficulty),
                        R.color.exercise_lunge,
                        context.getString(R.string.exercise_lunge_description),
                        context.getString(R.string.exercise_lunge_video)
                ),

                new ExerciseInfo(
                        "GLUTE_BRIDGE",
                        context.getString(R.string.exercise_glute_bridge_name),
                        context.getString(R.string.exercise_glute_bridge_emoji),
                        context.getString(R.string.exercise_glute_bridge_muscles),
                        context.getString(R.string.exercise_glute_bridge_difficulty),
                        R.color.exercise_glute_bridge,
                        context.getString(R.string.exercise_glute_bridge_description),
                        context.getString(R.string.exercise_glute_bridge_video)
                ),

                new ExerciseInfo(
                        "BURPEE",
                        context.getString(R.string.exercise_burpee_name),
                        context.getString(R.string.exercise_burpee_emoji),
                        context.getString(R.string.exercise_burpee_muscles),
                        context.getString(R.string.exercise_burpee_difficulty),
                        R.color.exercise_burpee,
                        context.getString(R.string.exercise_burpee_description),
                        context.getString(R.string.exercise_burpee_video)
                ),

                new ExerciseInfo(
                        "PULL_UP",
                        context.getString(R.string.exercise_pull_up_name),
                        context.getString(R.string.exercise_pull_up_emoji),
                        context.getString(R.string.exercise_pull_up_muscles),
                        context.getString(R.string.exercise_pull_up_difficulty),
                        R.color.exercise_pull_up,
                        context.getString(R.string.exercise_pull_up_description),
                        context.getString(R.string.exercise_pull_up_video)
                )
        );
    }


    public static BaseExercise createExercise(String id, Context context) {
        BaseExercise exercise;
        switch (id) {
            case "PUSH_UP":
                exercise = new PushUpExercise();
                break;
            case "SQUAT":
                exercise = new SquatExercise();
                break;
            case "PLANK":
                exercise = new PlankExercise();
                break;
            case "LUNGE":
                exercise = new LungeExercise();
                break;
            case "GLUTE_BRIDGE":
                exercise = new GluteBridgeExercise();
                break;
            case "BURPEE":
                exercise = new BurpeeExercise();
                break;
            case "PULL_UP":
                exercise = new PullUpExercise();
                break;
            default:
                throw new IllegalArgumentException(
                        String.format(context.getString(R.string.msg_unknown_exercise), id));
        }
        exercise.setContext(context);
        return exercise;
    }
}