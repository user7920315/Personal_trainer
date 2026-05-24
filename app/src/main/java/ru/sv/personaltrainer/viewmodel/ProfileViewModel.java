package ru.sv.personaltrainer.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ru.sv.personaltrainer.model.BmiResult;
import ru.sv.personaltrainer.model.UserProfile;
import ru.sv.personaltrainer.model.WeightRecord;
import ru.sv.personaltrainer.model.WorkoutRecord;
import ru.sv.personaltrainer.repository.ProfileRepository;
import ru.sv.personaltrainer.repository.WeightRepository;
import ru.sv.personaltrainer.repository.WorkoutRepository;

public class ProfileViewModel extends AndroidViewModel {

    private final ProfileRepository profileRepo;
    private final WeightRepository weightRepo;
    private final WorkoutRepository workoutRepo;

    private final MutableLiveData<UserProfile> userProfile = new MutableLiveData<>();
    private final MutableLiveData<BmiResult> bmiResult = new MutableLiveData<>();
    private final MutableLiveData<List<WeightRecord>> weightLog = new MutableLiveData<>();
    private final MutableLiveData<List<WorkoutRecord>> workouts = new MutableLiveData<>();
    private final MutableLiveData<WorkoutRepository.Stats> stats = new MutableLiveData<>();

    public LiveData<UserProfile> getUserProfile() { return userProfile; }
    public LiveData<BmiResult> getBmiResult() { return bmiResult; }
    public LiveData<List<WeightRecord>> getWeightLog() { return weightLog; }
    public LiveData<List<WorkoutRecord>> getWorkouts() { return workouts; }
    public LiveData<WorkoutRepository.Stats> getStats() { return stats; }

    public ProfileViewModel(@NonNull Application application) {
        super(application);
        profileRepo = new ProfileRepository(application);
        weightRepo = new WeightRepository(application);
        workoutRepo = new WorkoutRepository(application);
        loadAll();
    }

    private void loadAll() {
        UserProfile p = new UserProfile();
        p.height = profileRepo.getHeight();
        p.weight = profileRepo.getWeight();
        p.age = profileRepo.getAge();
        p.gender = profileRepo.getGender();
        userProfile.setValue(p);

        weightLog.setValue(weightRepo.loadWeightLog());
        workouts.setValue(workoutRepo.loadWorkouts());
        stats.setValue(workoutRepo.getStats());

        if (p.height > 0 && p.weight > 0) {
            calculateBmi(p.height, p.weight, p.age, p.gender);
        }
    }

    public void saveProfile(float height, float weight, int age, String gender) {
        profileRepo.saveHeight(height);
        profileRepo.saveWeight(weight);
        profileRepo.saveAge(age);
        profileRepo.saveGender(gender);
        weightRepo.addWeightRecord(weight);
        loadAll();
        calculateBmi(height, weight, age, gender);
    }

    public void calculateBmi(float height, float weight, int age, String gender) {
        float heightM = height / 100f;
        float bmi = weight / (heightM * heightM);

        String category;
        int color;
        if (bmi < 16.0f) { category = "Выраженный\nдефицит"; color = 0xFFFF4444; }
        else if (bmi < 18.5f) { category = "Недостаточный\nвес"; color = 0xFFFF8800; }
        else if (bmi < 25.0f) { category = "Норма ✓"; color = 0xFF00FF88; }
        else if (bmi < 30.0f) { category = "Избыточный\nвес"; color = 0xFFFFFF00; }
        else if (bmi < 35.0f) { category = "Ожирение I"; color = 0xFFFF8800; }
        else if (bmi < 40.0f) { category = "Ожирение II"; color = 0xFFFF4444; }
        else { category = "Ожирение III"; color = 0xFFCC0000; }

        String norm = buildNormText(bmi, age, "male".equals(gender));
        bmiResult.setValue(new BmiResult(bmi, category, color, norm));
    }

    private String buildNormText(float bmi, int age, boolean isMale) {
        if (age <= 0) {
            return isMale ? "Норма для мужчин: 18.5 – 24.9" : "Норма для женщин: 18.5 – 24.9";
        }
        float normMin = 18.5f, normMax;
        if (isMale) {
            if (age < 45) normMax = 24.9f;
            else if (age < 55) normMax = 25.9f;
            else if (age < 65) normMax = 26.9f;
            else normMax = 27.9f;
        } else {
            if (age < 25) normMax = 24.9f;
            else if (age < 45) normMax = 25.9f;
            else if (age < 55) normMax = 26.9f;
            else if (age < 65) normMax = 27.9f;
            else normMax = 28.9f;
        }
        String genderStr = isMale ? "мужчин" : "женщин";
        String inNorm = (bmi >= normMin && bmi <= normMax) ? " ✓" : "";
        return String.format(java.util.Locale.US,
                "Норма для %s %d лет: %.1f – %.1f%s", genderStr, age, normMin, normMax, inNorm);
    }

    public void clearHistory() {
        workoutRepo.clearWorkouts();
        workouts.setValue(new ArrayList<>());
        stats.setValue(workoutRepo.getStats());
    }

    public void refreshData() {
        weightLog.setValue(weightRepo.loadWeightLog());
        workouts.setValue(workoutRepo.loadWorkouts());
        stats.setValue(workoutRepo.getStats());
    }
}