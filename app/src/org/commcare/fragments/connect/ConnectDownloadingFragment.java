package org.commcare.fragments.connect;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

import org.commcare.activities.connect.ConnectActivity;
import org.commcare.activities.connect.ConnectManager;
import org.commcare.android.database.connect.models.ConnectAppRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.commcaresupportlibrary.CommCareLauncher;
import org.commcare.dalvik.R;
import org.commcare.engine.resource.AppInstallStatus;
import org.commcare.engine.resource.ResourceInstallUtils;
import org.commcare.resources.model.InvalidResourceException;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.tasks.ResourceEngineListener;
import org.commcare.views.notifications.NotificationActionButtonInfo;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.services.locale.LocaleTextException;
import org.javarosa.core.services.locale.Localization;

public class ConnectDownloadingFragment extends Fragment implements ResourceEngineListener {

    private ProgressBar progressBar;
    private TextView statusText;
    private boolean getLearnApp;
    private boolean goToApp;

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
        goToApp = args.getGoToApp();

        //Disable back button during install (done by providing empty callback)
        setBackButtonEnabled(false);

        //Disable the default wait dialog during this fragment since it displays progress on its own
        setWaitDialogEnabled(false);

        startAppDownload();
    }

    private void setBackButtonEnabled(boolean enabled) {
        Activity activity = getActivity();
        if(activity instanceof ConnectActivity connectActivity) {
            connectActivity.setBackButtonEnabled(enabled);
        }
    }

    private void setWaitDialogEnabled(boolean enabled) {
        Activity activity = getActivity();
        if(activity instanceof ConnectActivity connectActivity) {
            connectActivity.setWaitDialogEnabled(enabled);
        }
    }

    private void startAppDownload() {
        ConnectJobRecord job = ConnectManager.getActiveJob();
        ConnectAppRecord record = getLearnApp ? job.getLearnAppInfo() : job.getDeliveryAppInfo();
        ConnectManager.downloadAppOrResumeUpdates(record.getInstallUrl(), this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        ConnectJobRecord job = ConnectManager.getActiveJob();
        ConnectDownloadingFragmentArgs args = ConnectDownloadingFragmentArgs.fromBundle(getArguments());
        getActivity().setTitle(job.getTitle());

        View view = inflater.inflate(R.layout.fragment_connect_downloading, container, false);

        TextView titleTv = view.findViewById(R.id.connect_downloading_title);
        titleTv.setText(args.getTitle());

        statusText = view.findViewById(R.id.connect_downloading_status);
        updateInstallStatus(null);

        progressBar = view.findViewById(R.id.connect_downloading_progress);

        return view;
    }

    @Override
    public void reportSuccess(boolean isNewInstall) {
        Toast.makeText(getActivity(), R.string.connect_app_installed, Toast.LENGTH_SHORT).show();
        onSuccessfulInstall();
    }

    private void onSuccessfulInstall() {
        setWaitDialogEnabled(true);
        ((ConnectActivity)getActivity()).startAppValidation();
    }

    public void onSuccessfulVerification() {
        setBackButtonEnabled(true);
        View view = getView();
        if (view != null) {
            if(goToApp) {
                Navigation.findNavController(view).popBackStack();

                //Launch the learn/deliver app
                ConnectJobRecord job = ConnectManager.getActiveJob();
                ConnectAppRecord appToLaunch = getLearnApp ? job.getLearnAppInfo() : job.getDeliveryAppInfo();
                ConnectManager.launchApp(getContext(), getLearnApp, appToLaunch.getAppId());
            }
            else {
                //Go to learn/deliver progress
                NavDirections directions;
                if(getLearnApp) {
                    directions = ConnectDownloadingFragmentDirections.actionConnectDownloadingFragmentToConnectJobLearningProgressFragment();
                }
                else {
                    directions = ConnectDownloadingFragmentDirections.actionConnectDownloadingFragmentToConnectJobDeliveryProgressFragment();
                }
                Navigation.findNavController(statusText).navigate(directions);
            }
        }
    }

    @Override
    public void failMissingResource(UnresolvedResourceException ure, AppInstallStatus statusmissing) {
        showInstallFailError(statusmissing);
    }

    private void showInstallFailError(AppInstallStatus statusmissing) {
        setBackButtonEnabled(true);
        setWaitDialogEnabled(true);
        String installError = getString(R.string.connect_app_install_unknown_error);
        try {
            installError = Localization.get(statusmissing.getLocaleKeyBase() + ".title");
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
    public void failInvalidResource(InvalidResourceException e, AppInstallStatus statusmissing) {
        showInstallFailError(statusmissing);
    }

    @Override
    public void failInvalidReference(InvalidReferenceException e, AppInstallStatus status) {
        showInstallFailError(status);
    }

    @Override
    public void failBadReqs(String vReq, String vAvail, boolean majorIsProblem) {
        ResourceInstallUtils.showApkUpdatePrompt(getActivity(), vReq, vAvail);
        showInstallFailError(AppInstallStatus.UnknownFailure);
    }

    @Override
    public void failUnknown(AppInstallStatus statusfailunknown) {
        showInstallFailError(statusfailunknown);
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
    public void failWithNotification(AppInstallStatus statusfailstate) {
        if (statusfailstate == AppInstallStatus.DuplicateApp) {
            onSuccessfulInstall();
        } else {
            showInstallFailError(statusfailstate);
        }
    }

    @Override
    public void failWithNotification(AppInstallStatus statusfailstate,
            NotificationActionButtonInfo.ButtonAction buttonAction) {
        showInstallFailError(statusfailstate);
    }

    @Override
    public void failTargetMismatch() {
        ResourceInstallUtils.showTargetMismatchError(getActivity());
        showInstallFailError(AppInstallStatus.UnknownFailure);
    }
}
