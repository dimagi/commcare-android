package org.commcare.activities;

import android.os.Bundle;

import org.commcare.dalvik.R;

/**
 * UIController for running UpdateActivity without showing anything on the screen
 */
public class BlankUpdateUIController extends UpdateUIController {

    public BlankUpdateUIController(UpdateActivity updateActivity, boolean startedByAppManager) {
        super(updateActivity, startedByAppManager);
    }

    @Override
    public void setupUI() {
        activity.setContentView(R.layout.blank_activity);
    }

    @Override
    public void refreshView() {
    }


    @Override
    protected void upToDateUiState() {
    }

    @Override
    protected void idleUiState() {
    }

    @Override
    protected void checkFailedUiState() {
    }

    @Override
    protected void downloadingUiState() {
    }

    @Override
    protected void unappliedUpdateAvailableUiState() {
    }

    @Override
    protected void cancellingUiState() {
    }

    @Override
    protected void errorUiState() {
    }

    @Override
    protected void noConnectivityUiState() {
    }

    @Override
    protected void applyingUpdateUiState() {
    }

    @Override
    protected void updateProgressText(String msg) {
    }

    @Override
    protected void updateProgressBar(int currentProgress, int max) {
    }

    @Override
    public void saveCurrentUIState(Bundle outState) {
    }

    @Override
    public void loadSavedUIState(Bundle savedInstanceState) {
    }

}
