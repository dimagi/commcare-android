package org.commcare.appupdate;

import android.app.Activity;

import androidx.annotation.NonNull;

import com.google.android.play.core.install.InstallState;
import com.google.android.play.core.install.model.InstallErrorCode;

import javax.annotation.Nullable;

/**
 * @author $|-|!˅@M
 */
public class DummyFlexibleAppUpdateManager implements FlexibleAppUpdateController {
    @Override
    public void register() {

    }

    @Override
    public void unregister() {

    }

    @Override
    public void onStateUpdate(InstallState state) {

    }

    @Override
    public void startUpdate(Activity activity) {

    }

    @NonNull
    @Override
    public AppUpdateState getStatus() {
        return AppUpdateState.UNAVAILABLE;
    }

    @Override
    public void completeUpdate() {

    }

    @Nullable
    @Override
    public Integer getProgress() {
        return null;
    }

    @Override
    public int getErrorCode() {
        return InstallErrorCode.NO_ERROR;
    }

    @Override
    public void skipVersion() {

    }
}
