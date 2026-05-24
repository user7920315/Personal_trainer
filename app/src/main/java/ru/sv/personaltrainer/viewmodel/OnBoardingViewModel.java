package ru.sv.personaltrainer.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import ru.sv.personaltrainer.repository.ProfileRepository;

public class OnBoardingViewModel extends AndroidViewModel {

    private final ProfileRepository profileRepo;
    private final MutableLiveData<Boolean> onboardingComplete = new MutableLiveData<>();

    public OnBoardingViewModel(@NonNull Application application) {
        super(application);
        profileRepo = new ProfileRepository(application);
    }

    public void finishOnboarding() {
        profileRepo.setOnboardingDone(true);
        onboardingComplete.setValue(true);
    }

    public LiveData<Boolean> getOnboardingComplete() {
        return onboardingComplete;
    }
}