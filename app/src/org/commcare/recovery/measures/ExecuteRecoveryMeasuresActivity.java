package org.commcare.recovery.measures;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

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

import static org.commcare.recovery.measures.ExecuteRecoveryMeasuresPresenter.OFFLINE_INSTALL_REQUEST;

/**
 * Created by amstone326 on 5/22/18.
 */

@ManagedUi(R.layout.execute_recovery_measures)
public class ExecuteRecoveryMeasuresActivity extends CommCareActivity<ExecuteRecoveryMeasuresActivity> implements ResourceEngineListener {

    protected static final int PROMPT_APK_UPDATE = 1;
    protected static final int PROMPT_APK_REINSTALL = 2;

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
        mPresenter.loadSaveInstanceState(savedInstanceState);
        setUpUI();
        mPresenter.start();
    }

    private void setUpUI() {
        detailTv.setText(StringUtils.getStringRobust(this, R.string.recovery_measure_detail));
        retryBt.setOnClickListener(v -> {
            retryBt.setVisibility(View.GONE);
            statusTv.setVisibility(View.GONE);
            mPresenter.executePendingMeasures();
        });
    }


    @Override
    public void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        mPresenter.saveInstanceState(out);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch (requestCode) {
            case PROMPT_APK_UPDATE:
            case PROMPT_APK_REINSTALL:
                mPresenter.onReturnFromPlaystorePrompts();
                break;
            case OFFLINE_INSTALL_REQUEST:
                if (resultCode == Activity.RESULT_OK) {
                    mPresenter.doOfflineAppInstall(intent.getStringExtra(InstallArchiveActivity.ARCHIVE_JR_REFERENCE));
                }
                break;
        }
    }

    public void updateStatus(String msg) {
        statusTv.setVisibility(View.VISIBLE);
        statusTv.setText(msg);
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
        String installProgressText = StringUtils.getStringRobust(
                this,
                R.string.recovery_measure_app_reinstall_progress,
                new String[]{"" + done, "" + total});
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
        if (mPresenter.shouldAllowBackPress()) {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mPresenter.onActivityDestroy();
    }
}