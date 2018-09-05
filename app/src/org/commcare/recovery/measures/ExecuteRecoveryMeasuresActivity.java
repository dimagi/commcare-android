package org.commcare.recovery.measures;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.activities.CommCareActivity;
import org.commcare.activities.InstallArchiveActivity;
import org.commcare.dalvik.R;
import org.commcare.engine.resource.AppInstallStatus;
import org.commcare.resources.model.InvalidResourceException;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.tasks.ResourceEngineListener;
import org.commcare.utils.StringUtils;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;
import org.javarosa.core.services.locale.Localization;

import static org.commcare.recovery.measures.ExecuteRecoveryMeasuresPresenter.OFFLINE_INSTALL_REQUEST;

/**
 * Created by amstone326 on 5/22/18.
 */

@ManagedUi(R.layout.execute_recovery_measures)
public class ExecuteRecoveryMeasuresActivity extends CommCareActivity<ExecuteRecoveryMeasuresActivity> implements ResourceEngineListener {

    protected static final int PROMPT_APK_UPDATE = 1;
    protected static final int PROMPT_APK_REINSTALL = 2;

    private static final String CURRENTLY_EXECUTING_ID = "currently-executing-id";
    private ExecuteRecoveryMeasuresPresenter mPresenter;

    @UiElement(value = R.id.status_tv)
    private TextView statusTv;

    @UiElement(value = R.id.detail)
    private TextView detailTv;

    @UiElement(value = R.id.retry_button)
    private Button retryBt;

    @UiElement(value = R.id.progress_bar)
    private ProgressBar progressBar;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPresenter = new ExecuteRecoveryMeasuresPresenter(this);
        if (savedInstanceState != null) {
            mPresenter.setMeasureFromId(savedInstanceState.getInt(CURRENTLY_EXECUTING_ID));
        }
        setUpUI();
        mPresenter.executePendingMeasures();
    }

    private void setUpUI() {
        detailTv.setText(StringUtils.getStringRobust(this, R.string.recovery_measure_detail));
        retryBt.setOnClickListener(v -> {
            retryBt.setVisibility(View.GONE);
            mPresenter.executePendingMeasures();
        });
    }


    @Override
    public void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(CURRENTLY_EXECUTING_ID, mPresenter.getCurrentMeasure().getID());
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch (requestCode) {
            //todo check if we are on latest version ? If a reinstall has happened we should be on latest version
            case PROMPT_APK_UPDATE:
                mPresenter.onAsyncExecutionSuccess();
                break;
            case PROMPT_APK_REINSTALL:
                mPresenter.onAsyncExecutionSuccess();
                break;
            case OFFLINE_INSTALL_REQUEST:
                if (resultCode == Activity.RESULT_OK) {
                    mPresenter.doOfflineAppInstall(intent.getStringExtra(InstallArchiveActivity.ARCHIVE_JR_REFERENCE));
                }
                break;
        }
    }

    public void displayError(String message) {
        // todo show a sticking error instead
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    public void updateStatus(String msg) {
        statusTv.setVisibility(View.VISIBLE);
        statusTv.setText(msg);
    }

    public void hideStatus() {
        statusTv.setVisibility(View.GONE);
    }

    @Override
    public void reportSuccess(boolean b) {
        mPresenter.onAppReinstallSuccess();
    }

    @Override
    public void failMissingResource(UnresolvedResourceException ure, AppInstallStatus status) {
        mPresenter.appInstallExecutionFailed(status, "missing resource");
    }

    @Override
    public void failInvalidResource(InvalidResourceException e, AppInstallStatus status) {
        mPresenter.appInstallExecutionFailed(status, "invalid resource");
    }

    @Override
    public void failBadReqs(String vReq, String vAvail, boolean majorIsProblem) {
        mPresenter.appInstallExecutionFailed(AppInstallStatus.IncompatibleReqs, "bad reqs");
    }

    @Override
    public void failUnknown(AppInstallStatus status) {
        mPresenter.appInstallExecutionFailed(status, "unknown reason");
    }

    @Override
    public void updateResourceProgress(int done, int total, int phase) {
        String installProgressText =
                Localization.getWithDefault("profile.found",
                        new String[]{"" + done, "" + total},
                        "Setting up app...");
        updateStatus(installProgressText);
    }

    @Override
    public void failWithNotification(AppInstallStatus status) {
        mPresenter.appInstallExecutionFailed(status, "notification");
    }

    @Override
    public void failTargetMismatch() {
        mPresenter.appInstallExecutionFailed(AppInstallStatus.IncorrectTargetPackage, "target mismatch");
    }

    public void runFinish() {
        setResult(RESULT_OK, getIntent());
        finish();
    }

    public void enableRetry() {
        retryBt.setVisibility(View.VISIBLE);
    }

    public void disableLoadingIndicator() {
        progressBar.setVisibility(View.GONE);
    }

    public void enableLoadingIndicator() {
        progressBar.setVisibility(View.VISIBLE);
    }

    @Override
    public void onBackPressed() {
        if(mPresenter.shouldAllowBackPress()) {
            super.onBackPressed();
        }
    }
}
