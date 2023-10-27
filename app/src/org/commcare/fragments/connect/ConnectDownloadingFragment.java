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
import org.commcare.android.database.connect.models.ConnectAppRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.commcaresupportlibrary.CommCareLauncher;
import org.commcare.dalvik.R;
import org.commcare.engine.resource.AppInstallStatus;
import org.commcare.engine.resource.ResourceInstallUtils;
import org.commcare.resources.model.InvalidResourceException;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.tasks.ResourceEngineListener;
import org.commcare.tasks.templates.CommCareTaskConnector;
import org.commcare.views.notifications.NotificationActionButtonInfo;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.services.locale.LocaleTextException;
import org.javarosa.core.services.locale.Localization;

public class ConnectDownloadingFragment extends Fragment implements ResourceEngineListener {

    private static final int APP_DOWNLOAD_TASK_ID = 4;

    private ProgressBar progressBar;
    private TextView statusText;
    private ConnectJobRecord job;
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
        job = args.getJob();
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
        ConnectAppRecord record = getLearnApp ? job.getLearnAppInfo() : job.getDeliveryAppInfo();
        String url = record.getInstallUrl();
//String.format("https://staging.commcarehq.org/a/%s/apps/download/%s/media_profile.ccpr",
//record.getDomain(), record.getAppId());
        ResourceInstallUtils.startAppInstallAsync(false, APP_DOWNLOAD_TASK_ID, (CommCareTaskConnector)getActivity(), url);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        ConnectDownloadingFragmentArgs args = ConnectDownloadingFragmentArgs.fromBundle(getArguments());
        getActivity().setTitle(job.getTitle());

        View view = inflater.inflate(R.layout.fragment_connect_downloading, container, false);

        TextView titleTv = view.findViewById(R.id.connect_downloading_title);
        titleTv.setText(args.getTitle());

        statusText = view.findViewById(R.id.connect_downloading_status);
        statusText.setText(getString(R.string.connect_downloading_status, 0));

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
                ConnectAppRecord appToLaunch = getLearnApp ? job.getLearnAppInfo() : job.getDeliveryAppInfo();
                CommCareLauncher.launchCommCareForAppIdFromConnect(getContext(), appToLaunch.getAppId());
            }
            else {
                //Go to learn/deliver progress
                NavDirections directions;
                if(getLearnApp) {
                    directions = ConnectDownloadingFragmentDirections.actionConnectDownloadingFragmentToConnectJobLearningProgressFragment(job);
                }
                else {
                    directions = ConnectDownloadingFragmentDirections.actionConnectDownloadingFragmentToConnectJobDeliveryProgressFragment(job);
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
        showInstallFailError(installError);
    }

    private void showInstallFailError(String error) {
        statusText.setText(error);
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
    }

    @Override
    public void failUnknown(AppInstallStatus statusfailunknown) {
        showInstallFailError(statusfailunknown);
    }

    @Override
    public void updateResourceProgress(int done, int pending, int phase) {
        progressBar.setMax(pending);
        progressBar.setProgress(done);
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
    }
}
