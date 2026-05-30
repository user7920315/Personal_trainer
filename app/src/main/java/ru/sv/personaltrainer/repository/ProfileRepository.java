package ru.sv.personaltrainer.repository;

import android.content.Context;
import android.content.SharedPreferences;

public class ProfileRepository {
    private static final String PREFS_NAME = "PersonalTrainerPrefs";
    public static final String KEY_HEIGHT = "height";
    public static final String KEY_WEIGHT = "weight";
    public static final String KEY_AGE = "age";
    public static final String KEY_GENDER = "gender";
    public static final String KEY_ONBOARDING_DONE = "onboarding_done";

    private final SharedPreferences prefs;

    public ProfileRepository(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveHeight(float height) {
        prefs.edit().putFloat(KEY_HEIGHT, height).apply();
    }

    public float getHeight() {
        return prefs.getFloat(KEY_HEIGHT, 0f);
    }

    public void saveWeight(float weight) {
        prefs.edit().putFloat(KEY_WEIGHT, weight).apply();
    }

    public float getWeight() {
        return prefs.getFloat(KEY_WEIGHT, 0f);
    }

    public void saveAge(int age) {
        prefs.edit().putInt(KEY_AGE, age).apply();
    }

    public int getAge() {
        return prefs.getInt(KEY_AGE, 0);
    }

    public void saveGender(String gender) {
        prefs.edit().putString(KEY_GENDER, gender).apply();
    }

    public String getGender() {
        return prefs.getString(KEY_GENDER, "male");
    }

    public void setOnboardingDone(boolean done) {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, done).apply();
    }

    public boolean isOnboardingDone() {
        return prefs.getBoolean(KEY_ONBOARDING_DONE, false);
    }
}