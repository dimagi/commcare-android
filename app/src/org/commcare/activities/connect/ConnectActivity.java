package org.commcare.activities.connect;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import org.commcare.activities.CommCareActivity;
import org.commcare.activities.CommCareVerificationActivity;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.connect.ConnectManager;
import org.commcare.connect.network.ApiConnect;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.commcare.fragments.connect.ConnectDownloadingFragment;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.tasks.ResourceEngineListener;
import org.commcare.util.LogTypes;
import org.commcare.views.dialogs.CustomProgressDialog;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

public class ConnectActivity extends CommCareActivity<ResourceEngineListener> {
    private boolean backButtonEnabled = true;
    private boolean waitDialogEnabled = true;
    String redirectionAction = "";
    String opportunityId = "";
    NavController navController;

    NavController.OnDestinationChangedListener destinationListener = null;

    final ActivityResultLauncher<Intent> verificationLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    ConnectDownloadingFragment connectDownloadFragment = getConnectDownloadFragment();
                    if (connectDownloadFragment != null) {
                        connectDownloadFragment.onSuccessfulVerification();
                    }
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_connect);
        setTitle(getString(R.string.connect_title));
        getIntentData();
        updateBackButton();

        destinationListener = FirebaseAnalyticsUtil.getDestinationChangeListener();

        NavHostFragment host = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_connect);
        navController = host.getNavController();
        navController.addOnDestinationChangedListener(destinationListener);

        if (getIntent().getBooleanExtra("info", false)) {
            ConnectJobRecord job = ConnectManager.getActiveJob();
            int fragmentId = job.getStatus() == ConnectJobRecord.STATUS_DELIVERING ?
                    R.id.connect_job_delivery_progress_fragment :
                    R.id.connect_job_learning_progress_fragment;

            boolean buttons = getIntent().getBooleanExtra("buttons", true);

            Bundle bundle = new Bundle();
            bundle.putBoolean("showLaunch", buttons);

            NavOptions options = new NavOptions.Builder()
                    .setPopUpTo(navController.getGraph().getStartDestinationId(), true)
                    .build();
            navController.navigate(fragmentId, bundle, options);
        } else {
            if (redirectionAction != null) {
                ConnectManager.unlockConnect(this, success -> {
                    if (success) {
                        getJobDetails();
                    }
                });
            }
        }
    }

    // Function for
    private int getFragmentId() {
        int fragmentId;
        if (redirectionAction.equals("ccc_opportunity_summary_page")) {
            fragmentId = R.id.connect_job_intro_fragment;
        } else if (redirectionAction.equals("ccc_learn_progress")) {
            fragmentId = R.id.connect_job_learning_progress_fragment;
        } else {
            fragmentId = R.id.connect_job_delivery_progress_fragment; // ccc_delivery_progress , ccc_payments
        }
        return fragmentId;
    }

    private void getIntentData() {
        redirectionAction = getIntent().getStringExtra("action");
        opportunityId = getIntent().getStringExtra("opportunity_id");
        Logger.log(LogTypes.TYPE_FCM, "CommCareFirebaseMessagingService Payload data3: " + redirectionAction);
        Logger.log(LogTypes.TYPE_FCM, "CommCareFirebaseMessagingService Payload data4: " + opportunityId);
    }

    private void setFragmentRedirection() {
        int fragmentId = getFragmentId();

        boolean buttons = getIntent().getBooleanExtra("buttons", true);
        Bundle bundle = new Bundle();
        bundle.putBoolean("showLaunch", buttons);

        NavOptions options = new NavOptions.Builder()
                .setPopUpTo(navController.getGraph().getStartDestinationId(), true)
                .build();
        navController.navigate(fragmentId, bundle, options);
    }

    @Override
    public void onBackPressed() {
        if (backButtonEnabled) {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (destinationListener != null) {
            NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.nav_host_fragment_connect);
            if (navHostFragment != null) {
                NavController navController = navHostFragment.getNavController();
                navController.removeOnDestinationChangedListener(destinationListener);
            }
            destinationListener = null;
        }

        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        ConnectManager.handleFinishedActivity(requestCode, resultCode, intent);
        super.onActivityResult(requestCode, resultCode, intent);
    }

    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        if (waitDialogEnabled) {
            return CustomProgressDialog.newInstance(null, getString(R.string.please_wait), taskId);
        }

        return null;
    }

    public void setBackButtonEnabled(boolean enabled) {
        backButtonEnabled = enabled;
    }

    public void setWaitDialogEnabled(boolean enabled) {
        waitDialogEnabled = enabled;
    }

    private void updateBackButton() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowHomeEnabled(isBackEnabled());
            actionBar.setDisplayHomeAsUpEnabled(isBackEnabled());
        }
    }

    @Override
    protected boolean shouldShowBreadcrumbBar() {
        return false;
    }

    @Override
    public ResourceEngineListener getReceiver() {
        return getConnectDownloadFragment();
    }

    @Nullable
    private ConnectDownloadingFragment getConnectDownloadFragment() {
        NavHostFragment navHostFragment =
                (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_connect);
        Fragment currentFragment =
                navHostFragment.getChildFragmentManager().getPrimaryNavigationFragment();
        if (currentFragment instanceof ConnectDownloadingFragment) {
            return (ConnectDownloadingFragment) currentFragment;
        }
        return null;
    }

    public void startAppValidation() {
        Intent i = new Intent(this, CommCareVerificationActivity.class);
        i.putExtra(CommCareVerificationActivity.KEY_LAUNCH_FROM_SETTINGS, true);
        verificationLauncher.launch(i);
    }

    public void getJobDetails() {
        ApiConnect.getConnectOpportunities(ConnectActivity.this, new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                try {
                    String responseAsString = new String(StreamsUtil.inputStreamToByteArray(responseData));
                    if (responseAsString.length() > 0) {
                        //Parse the JSON
                        JSONArray json = new JSONArray(responseAsString);
                        List<ConnectJobRecord> jobs = new ArrayList<>(json.length());
                        for (int i = 0; i < json.length(); i++) {
                            JSONObject obj = (JSONObject) json.get(i);
                            ConnectJobRecord job = ConnectJobRecord.fromJson(obj);
                            jobs.add(job);
                            if (job.getJobId() == Integer.parseInt(opportunityId)) {
                                ConnectManager.setActiveJob(job);
                            }
                        }

                        setFragmentRedirection();
                    }
                } catch (IOException | JSONException | ParseException e) {
                    Logger.exception("Parsing return from Opportunities request", e);
                }
            }

            @Override
            public void processFailure(int responseCode, IOException e) {
                Logger.log("ERROR", String.format(Locale.getDefault(), "Opportunities call failed: %d", responseCode));
            }

            @Override
            public void processNetworkFailure() {
                Logger.log("ERROR", "Failed (network)");
            }

            @Override
            public void processOldApiError() {
                ConnectNetworkHelper.showOutdatedApiError(ConnectActivity.this);
            }
        });
    }
}
