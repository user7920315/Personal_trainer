package ru.sv.personaltrainer.repository;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import ru.sv.personaltrainer.model.WorkoutRecord;

public class WorkoutRepository {
    private static final String PREFS_NAME = "PersonalTrainerPrefs";
    private static final String KEY_WORKOUTS = "workouts";
    private final SharedPreferences prefs;
    private final Gson gson;

    public WorkoutRepository(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
    }

    public void saveWorkout(String exerciseId, String exerciseName, String icon, int reps) {
        if (reps <= 0) return;
        List<WorkoutRecord> history = loadWorkouts();
        history.add(new WorkoutRecord(exerciseId, exerciseName, icon, reps, System.currentTimeMillis()));
        if (history.size() > 100) history.remove(0);
        prefs.edit().putString(KEY_WORKOUTS, gson.toJson(history)).apply();
    }

    public List<WorkoutRecord> loadWorkouts() {
        String json = prefs.getString(KEY_WORKOUTS, "[]");
        Type type = new TypeToken<List<WorkoutRecord>>() {
        }.getType();
        List<WorkoutRecord> list = gson.fromJson(json, type);
        return list != null ? list : new ArrayList<>();
    }

    public void clearWorkouts() {
        prefs.edit().putString(KEY_WORKOUTS, "[]").apply();
    }

    public Stats getStats() {
        List<WorkoutRecord> list = loadWorkouts();
        Stats s = new Stats();
        s.total = list.size();
        s.streak = calcStreak(list);
        s.weekWorkouts = calcWeekWorkouts(list);
        return s;
    }

    private int calcStreak(List<WorkoutRecord> history) {
        if (history.isEmpty()) return 0;
        SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd", Locale.US);
        List<String> days = new ArrayList<>();
        for (WorkoutRecord r : history) {
            String d = fmt.format(new Date(r.timestamp));
            if (!days.contains(d)) days.add(d);
        }
        Collections.sort(days, Collections.reverseOrder());
        Calendar today = Calendar.getInstance();
        int streak = 0;
        for (String dayStr : days) {
            if (dayStr.equals(fmt.format(today.getTime()))) {
                streak++;
                today.add(Calendar.DAY_OF_YEAR, -1);
            } else {
                break;
            }
        }
        return streak;
    }

    private int calcWeekWorkouts(List<WorkoutRecord> history) {
        Calendar start = Calendar.getInstance();
        start.set(Calendar.DAY_OF_WEEK, start.getFirstDayOfWeek());
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        long weekStart = start.getTimeInMillis();
        int count = 0;
        for (WorkoutRecord r : history) {
            if (r.timestamp >= weekStart) count++;
        }
        return count;
    }

    public static class Stats {
        public int total;
        public int streak;
        public int weekWorkouts;
    }
}