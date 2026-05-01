package ru.sv.personaltrainer;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    public static final String PREFS_NAME = "PersonalTrainerPrefs";
    public static final String KEY_ONBOARDING_DONE = "onboarding_done";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        navigateNext();
    }

    private void navigateNext() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        if (!prefs.getBoolean(KEY_ONBOARDING_DONE, false)) {
            startActivity(new Intent(this, ru.sv.personaltrainer.onboarding.OnBoardingActivity.class));
        } else {
            startActivity(new Intent(this, ExerciseListActivity.class));
        }
        finish();
    }
}