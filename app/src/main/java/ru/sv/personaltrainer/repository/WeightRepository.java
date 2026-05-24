package ru.sv.personaltrainer.repository;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import ru.sv.personaltrainer.model.WeightRecord;

public class WeightRepository {
    private static final String PREFS_NAME = "PersonalTrainerPrefs";
    private static final String KEY_WEIGHT_LOG = "weight_log";
    private final SharedPreferences prefs;
    private final Gson gson;

    public WeightRepository(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
    }

    public void addWeightRecord(float weight) {
        List<WeightRecord> log = loadWeightLog();
        long now = System.currentTimeMillis();
        Calendar cal = Calendar.getInstance();
        int todayDay = cal.get(Calendar.DAY_OF_YEAR);
        int todayYear = cal.get(Calendar.YEAR);

        boolean updated = false;
        for (int i = 0; i < log.size(); i++) {
            cal.setTimeInMillis(log.get(i).timestamp);
            if (cal.get(Calendar.DAY_OF_YEAR) == todayDay && cal.get(Calendar.YEAR) == todayYear) {
                log.get(i).weight = weight;
                updated = true;
                break;
            }
        }
        if (!updated) {
            log.add(new WeightRecord(weight, now));
        }
        if (log.size() > 30) log.remove(0);
        saveWeightLog(log);
    }

    public List<WeightRecord> loadWeightLog() {
        String json = prefs.getString(KEY_WEIGHT_LOG, "[]");
        Type type = new TypeToken<List<WeightRecord>>(){}.getType();
        List<WeightRecord> list = gson.fromJson(json, type);
        return list != null ? list : new ArrayList<>();
    }

    private void saveWeightLog(List<WeightRecord> log) {
        prefs.edit().putString(KEY_WEIGHT_LOG, gson.toJson(log)).apply();
    }
}