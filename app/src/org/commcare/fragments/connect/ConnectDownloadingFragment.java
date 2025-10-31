package org.commcare.fragments.connect;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.activities.CommCareVerificationActivity;
import org.commcare.activities.connect.ConnectActivity;
import org.commcare.android.database.connect.models.ConnectAppRecord;
import org.commcare.connect.ConnectAppUtils;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.FragmentConnectDownloadingBinding;
import org.commcare.engine.resource.AppInstallStatus;
import org.commcare.engine.resource.ResourceInstallUtils;
import org.commcare.resources.model.InvalidResourceException;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.tasks.ResourceEngineListener;
import org.commcare.views.notifications.NotificationActionButtonInfo;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.services.locale.LocaleTextException;
import org.javarosa.core.services.locale.Localization;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.navigation.Navigation;

public class ConnectDownloadingFragment extends ConnectJobFragment<FragmentConnectDownloadingBinding> implements ResourceEngineListener {

    private ProgressBar progressBar;
    private TextView statusText;
    private boolean getLearnApp;

    final ActivityResultLauncher<Intent> verificationLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    onSuccessfulVerification();
                }
            });

    public ConnectDownloadingFragment() {
        // Required empty public constructor
    }

    public static ConnectDownloadingFragment newInstance() {
        return new ConnectDownloadingFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ConnectDownloadingFragmentArgs args = ConnectDownloadingFragmentArgs.fromBundle(getArguments());
        getLearnApp = args.getLearning();

        //Disable back button during install (done by providing empty callback)
        setBackButtonAndActionBarState(false);

        //Disable the default wait dialog during this fragment since it displays progress on its own
        setWaitDialogEnabled(false);

        startAppDownload();
    }

    private void setBackButtonAndActionBarState(boolean enabled) {
        Activity activity = getActivity();
        if(activity instanceof ConnectActivity connectActivity) {
            connectActivity.setBackButtonAndActionBarState(enabled);
        }
    }

    private void setWaitDialogEnabled(boolean enabled) {
        Activity activity = getActivity();
        if(activity instanceof ConnectActivity connectActivity) {
            connectActivity.setWaitDialogEnabled(enabled);
        }
    }

    private void startAppDownload() {
        ConnectAppRecord record = getLearnApp ? job.getLearnAppInfo() : job.getDeliveryAppInfo();
        ConnectAppUtils.INSTANCE.downloadApp(record.getInstallUrl(), this);
    }

    @Override
    public void reportSuccess(boolean isNewInstall) {
        Toast.makeText(getActivity(), R.string.connect_app_installed, Toast.LENGTH_SHORT).show();
        startAppValidation();
    }

    public void startAppValidation() {
        setWaitDialogEnabled(true);

        Intent i = new Intent(requireContext(), CommCareVerificationActivity.class);
        i.putExtra(CommCareVerificationActivity.KEY_LAUNCH_FROM_CONNECT, true);
        verificationLauncher.launch(i);
    }

    public void onSuccessfulVerification() {
        setBackButtonAndActionBarState(true);
        View view = getView();
        if (view != null) {

            //Launch the learn/deliver app
            ConnectAppRecord appToLaunch = getLearnApp ? job.getLearnAppInfo() : job.getDeliveryAppInfo();
            ConnectAppUtils.INSTANCE.launchApp(requireActivity(), getLearnApp, appToLaunch.getAppId());
        }
    }

    @Override
    public void failMissingResource(UnresolvedResourceException ure, AppInstallStatus statusMissing) {
        showInstallFailError(statusMissing);
    }

    private void showInstallFailError(AppInstallStatus statusMissing) {
        setBackButtonAndActionBarState(true);
        setWaitDialogEnabled(true);
        String installError = getString(R.string.connect_app_install_unknown_error);
        try {
            installError = Localization.get(statusMissing.getLocaleKeyBase() + ".title");
        } catch (LocaleTextException e) {
            // do nothing
        }
        updateInstallStatus(installError);
    }

    private void updateInstallStatus(String status) {
        statusText.setVisibility(status != null ? View.VISIBLE : View.GONE);
        statusText.setText(status);
    }

    @Override
    public void failInvalidResource(InvalidResourceException e, AppInstallStatus statusMissing) {
        showInstallFailError(statusMissing);
    }

    @Override
    public void failInvalidReference(InvalidReferenceException e, AppInstallStatus status) {
        showInstallFailError(status);
    }

    @Override
    public void failBadReqs(String vReq, String vAvail, boolean majorIsProblem) {
        ResourceInstallUtils.showApkUpdatePrompt(getActivity(), vReq, vAvail);
        showInstallFailError(AppInstallStatus.IncompatibleReqs);
    }

    @Override
    public void failUnknown(AppInstallStatus statusFailUnknown) {
        showInstallFailError(statusFailUnknown);
    }

    @Override
    public void updateResourceProgress(int done, int pending, int phase) {
        progressBar.setMax(pending);
        progressBar.setProgress(done);

        // Don't change the text on the progress dialog if we are showing the generic consumer
        // apps startup dialog
        String installProgressText =
                Localization.getWithDefault("profile.found",
                        new String[]{"" + done, "" + pending},
                        "Setting up app...");

        updateInstallStatus(installProgressText);
    }

    @Override
    public void failWithNotification(AppInstallStatus statusFailState) {
        if (statusFailState == AppInstallStatus.DuplicateApp) {
            startAppValidation();
        } else {
            showInstallFailError(statusFailState);
        }
    }

    @Override
    public void failWithNotification(AppInstallStatus statusFailState,
            NotificationActionButtonInfo.ButtonAction buttonAction) {
        showInstallFailError(statusFailState);
    }

    @Override
    public void failTargetMismatch() {
        ResourceInstallUtils.showTargetMismatchError(getActivity());
        showInstallFailError(AppInstallStatus.IncorrectTargetPackage);
    }

    @Override
    protected @NotNull FragmentConnectDownloadingBinding inflateBinding(@NotNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return FragmentConnectDownloadingBinding.inflate(inflater, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @androidx.annotation.Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ConnectDownloadingFragmentArgs args = ConnectDownloadingFragmentArgs.fromBundle(getArguments());
        requireActivity().setTitle(job.getTitle());

        TextView titleTv = view.findViewById(R.id.connect_downloading_title);
        titleTv.setText(args.getTitle());

        statusText = view.findViewById(R.id.connect_downloading_status);
        updateInstallStatus(null);

        progressBar = view.findViewById(R.id.connect_downloading_progress);
    }
}
