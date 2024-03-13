package org.commcare.activities.connect;

import org.commcare.activities.CommCareActivity;
import org.commcare.dalvik.R;

import androidx.lifecycle.ViewModel;

public class ConnectUpgradeViewModel extends ViewModel {
    private static final int UPGRADE_STATE_INTRO = 1;
    private static final int UPGRADE_STATE_FAILED_UNLOCK = 2;
    private static final int UPGRADE_STATE_UPGRADING = 3;
    private static final int UPGRADE_STATE_FAILED = 4;
    private static final int UPGRADE_STATE_COMPLETED = 5;
    private int state = UPGRADE_STATE_INTRO;
    private Runnable callback = null;

    public void setCallback(Runnable callback) {
        this.callback = callback;

        callback.run();
    }

    public String getMessage(CommCareActivity<?> activity) {
        int textId;
        switch(state) {
            case UPGRADE_STATE_INTRO -> { textId = R.string.connect_upgrade_message_intro; }
            case UPGRADE_STATE_FAILED_UNLOCK -> { textId = R.string.connect_upgrade_message_failed_unlock; }
            case UPGRADE_STATE_UPGRADING -> { textId = R.string.connect_upgrade_message_upgrading; }
            case UPGRADE_STATE_FAILED -> { textId = R.string.connect_upgrade_message_failed; }
            case UPGRADE_STATE_COMPLETED -> { textId = R.string.connect_upgrade_message_completed; }
            default -> {
                throw new RuntimeException("Unhandled switch case: " + state);
            }
        }

        return activity.getString(textId);
    }

    public boolean getButtonEnabled() {
        return state != UPGRADE_STATE_UPGRADING;
    }

    public String getButtonText(CommCareActivity<?> activity) {
        int textId;
        switch(state) {
            case UPGRADE_STATE_INTRO -> { textId = R.string.connect_upgrade_button_intro; }
            case UPGRADE_STATE_FAILED_UNLOCK, UPGRADE_STATE_FAILED, UPGRADE_STATE_COMPLETED -> {
                textId = R.string.connect_upgrade_button_proceed; }
            case UPGRADE_STATE_UPGRADING -> { textId = R.string.connect_upgrade_button_upgrading; }
            default -> {
                throw new RuntimeException("Unhandled switch case: " + state);
            }
        }

        return activity.getString(textId);
    }

    public void handleButtonPress(CommCareActivity<?> activity) {
        switch(state) {
            case UPGRADE_STATE_INTRO -> {
                ConnectManager.unlockConnect(activity, true, unlocked -> {
                    state = unlocked ? UPGRADE_STATE_UPGRADING : UPGRADE_STATE_FAILED_UNLOCK;
                    if(callback != null) {
                        callback.run();
                    }

                    if(unlocked) {
                        ConnectUpgrader.startUpgrade(activity, success -> {
                            state = success ? UPGRADE_STATE_COMPLETED : UPGRADE_STATE_FAILED;
                            if(callback != null) {
                                callback.run();
                            }
                        });
                    }
                });
            }
            case UPGRADE_STATE_FAILED_UNLOCK, UPGRADE_STATE_FAILED, UPGRADE_STATE_COMPLETED -> {
                activity.finish();
            }
            default -> {
                throw new RuntimeException("Unhandled switch case: " + state);
            }
        }
    }
}
