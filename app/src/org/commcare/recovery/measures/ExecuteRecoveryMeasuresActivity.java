package org.commcare.recovery.measures;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.activities.CommCareActivity;
import org.commcare.activities.InstallArchiveActivity;
import org.commcare.dalvik.R;
import org.commcare.engine.resource.AppInstallStatus;
import org.commcare.resources.model.InvalidResourceException;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.tasks.ResourceEngineListener;
import org.javarosa.core.services.locale.Localization;

import static org.commcare.recovery.measures.ExecuteRecoveryMeasuresPresenter.OFFLINE_INSTALL_REQUEST;

/**
 * Created by amstone326 on 5/22/18.
 */
public class ExecuteRecoveryMeasuresActivity extends CommCareActivity<ExecuteRecoveryMeasuresActivity> implements ResourceEngineListener {

    protected static final int PROMPT_APK_UPDATE = 1;
    protected static final int PROMPT_APK_REINSTALL = 2;

    private static final String CURRENTLY_EXECUTING_ID = "currently-executing-id";
    private ExecuteRecoveryMeasuresPresenter mPresenter;

    private TextView progressTv;
    private TextView detailTv;

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
        setContentView(R.layout.blocking_process_screen);
        detailTv = findViewById(R.id.detail);
        detailTv.setText(Localization.get(getDisplayTextKey()));
        progressTv = findViewById(R.id.progress_text);
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
                mPresenter.onAsyncExecutionSuccess("CommCare update prompt");
                break;
            case PROMPT_APK_REINSTALL:
                mPresenter.onAsyncExecutionSuccess("CommCare reinstall prompt");
                break;
            case OFFLINE_INSTALL_REQUEST:
                if (resultCode == Activity.RESULT_OK) {
                    mPresenter.doOfflineAppInstall(intent.getStringExtra(InstallArchiveActivity.ARCHIVE_JR_REFERENCE));
                }
                break;
        }
    }

    protected String getDisplayTextKey() {
        return "executing.recovery.measures";
    }


    public void displayError(String message) {
        // todo show a sticking error instead
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    public void updateStatus(String msg) {
        progressTv.setVisibility(View.VISIBLE);
        progressTv.setText(msg);
    }

    public void hideProgress() {
        progressTv.setVisibility(View.GONE);
    }

    @Override
    public void reportSuccess(boolean b) {
        mPresenter.onAsyncExecutionSuccess("App install");
    }

    @Override
    public void failMissingResource(UnresolvedResourceException ure, AppInstallStatus statusmissing) {
        mPresenter.appInstallExecutionFailed("missing resource");
    }

    @Override
    public void failInvalidResource(InvalidResourceException e, AppInstallStatus statusmissing) {
        mPresenter.appInstallExecutionFailed("invalid resource");
    }

    @Override
    public void failBadReqs(String vReq, String vAvail, boolean majorIsProblem) {
        mPresenter.appInstallExecutionFailed("bad reqs");
    }

    @Override
    public void failUnknown(AppInstallStatus statusfailunknown) {
        mPresenter.appInstallExecutionFailed("unknown reason");
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
    public void failWithNotification(AppInstallStatus statusfailstate) {
        mPresenter.appInstallExecutionFailed("notification");
    }

    @Override
    public void failTargetMismatch() {
        mPresenter.appInstallExecutionFailed("target mismatch");
    }

    public void runFinish() {
        setResult(RESULT_OK, getIntent());
        finish();
    }
}
