package ru.sv.personaltrainer.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import ru.sv.personaltrainer.repository.PermissionRepository;

public class PermissionViewModel extends AndroidViewModel {

    public enum PermissionState {
        CHECKING, GRANTED, REQUEST_DIALOG, SHOW_RATIONALE, GO_TO_SETTINGS, WAITING_RESULT
    }

    private final PermissionRepository repository;
    private final MutableLiveData<PermissionState> cameraPermissionState = new MutableLiveData<>(PermissionState.CHECKING);

    public PermissionViewModel(@NonNull Application application) {
        super(application);
        repository = new PermissionRepository(application);
    }

    public LiveData<PermissionState> getCameraPermissionState() {
        return cameraPermissionState;
    }

    public void checkCameraPermission(boolean canShowRationale) {
        PermissionState current = cameraPermissionState.getValue();

        if (current == PermissionState.WAITING_RESULT) {
            return;
        }

        if (repository.isCameraGranted()) {
            cameraPermissionState.setValue(PermissionState.GRANTED);
            return;
        }

        int denialCount = repository.getCameraDenialCount();

        if (denialCount == 0) {
            cameraPermissionState.setValue(PermissionState.REQUEST_DIALOG);
        } else if (denialCount == 1 && !repository.hasShownRationale() && canShowRationale) {
            cameraPermissionState.setValue(PermissionState.SHOW_RATIONALE);
        } else if (repository.shouldRedirectToSettings()) {
            cameraPermissionState.setValue(PermissionState.GO_TO_SETTINGS);
        } else {
            cameraPermissionState.setValue(PermissionState.REQUEST_DIALOG);
        }
    }

    public void markRequestLaunched() {
        cameraPermissionState.setValue(PermissionState.WAITING_RESULT);
    }

    public void onPermissionResult(boolean granted, boolean shouldShowRationale) {
        if (granted) {
            repository.resetCameraDenials();
            cameraPermissionState.setValue(PermissionState.GRANTED);
            return;
        }

        repository.recordCameraDenial();
        int denialCount = repository.getCameraDenialCount();

        if (denialCount == 1 && shouldShowRationale) {
            cameraPermissionState.setValue(PermissionState.SHOW_RATIONALE);
        } else if (denialCount >= PermissionRepository.MAX_DENIALS || !shouldShowRationale) {
            cameraPermissionState.setValue(PermissionState.GO_TO_SETTINGS);
        } else {
            cameraPermissionState.setValue(PermissionState.REQUEST_DIALOG);
        }
    }

    public void onRationaleShown() {
        repository.markRationaleShown();
        cameraPermissionState.setValue(PermissionState.REQUEST_DIALOG);
    }

    public void openSettings() {
        repository.openAppSettings();
    }

    public void resetDenials() {
        repository.resetCameraDenials();
    }
}