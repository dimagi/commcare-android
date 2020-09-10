package org.commcare.appupdate;

import android.app.Activity;
import android.content.IntentSender;
import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.UpdateAvailability;
import org.commcare.activities.CommCareActivity;
import org.commcare.util.LogTypes;
import org.javarosa.core.services.Logger;
import javax.annotation.Nullable;

/**
 * @author $|-|!˅@M
 */
public class CommcareImmediateAppUpdateManager implements AppUpdateController, DefaultLifecycleObserver {

    private AppUpdateManager mAppUpdateManager;
    private AppUpdateInfo mAppUpdateInfo;
    private Activity activity;

    CommcareImmediateAppUpdateManager(CommCareActivity activity) {
        this.activity = activity;
        mAppUpdateManager = AppUpdateManagerFactory.create(activity.getApplicationContext());
        activity.getLifecycle().addObserver(this);
    }

    @Override
    public void onResume(@NonNull LifecycleOwner owner) {
        fetchAppUpdateInfo(() -> {
            if (mAppUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                startImmediateUpdate();
            }
        });
    }

    private void fetchAppUpdateInfo(Runnable callback) {
        mAppUpdateManager.getAppUpdateInfo()
                .addOnSuccessListener(info -> {
                    mAppUpdateInfo = info;
                    callback.run();
                })
                .addOnFailureListener(exception -> {
                    mAppUpdateInfo = null;
                    Logger.log(LogTypes.TYPE_CC_UPDATE, "fetchAppUpdateInfo|failed with : " + exception.getMessage());
                });
    }

    private void startImmediateUpdate() {
        try {
            boolean success = mAppUpdateManager.startUpdateFlowForResult(
                    mAppUpdateInfo,
                    AppUpdateType.IMMEDIATE,
                    activity,
                    IN_APP_UPDATE_REQUEST_CODE);
            if (!success) {
                Logger.log(LogTypes.TYPE_CC_UPDATE, "startUpdate|requested update cannot be started");
            }
        } catch (IntentSender.SendIntentException exception) {
            Logger.log(LogTypes.TYPE_CC_UPDATE, "startUpdate|failed with : " + exception.getMessage());
        }
    }

    //region AppUpdateController implementation
    @Override
    public void startUpdate(Activity activity) {
        fetchAppUpdateInfo(() -> {
            if (mAppUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                startImmediateUpdate();
            }
        });
    }

    @Nullable
    @Override
    public Integer availableVersionCode() {
        if (mAppUpdateInfo != null) {
            return mAppUpdateInfo.availableVersionCode();
        }
        return null;
    }
    //endregion
}
