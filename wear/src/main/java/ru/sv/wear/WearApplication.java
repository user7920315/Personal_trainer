package ru.sv.wear;

import android.app.Application;

import androidx.lifecycle.ViewModelProvider;

import ru.sv.wear.viewmodel.WearViewModel;

public class WearApplication extends Application {

    private WearViewModel wearViewModel;

    @Override
    public void onCreate() {
        super.onCreate();
        wearViewModel = new ViewModelProvider.AndroidViewModelFactory(this)
                .create(WearViewModel.class);
    }

    public WearViewModel getWearViewModel() {
        return wearViewModel;
    }
}