package org.commcare.fragments.connect;

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

import org.commcare.android.database.connect.models.ConnectJobRecord;
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

    private static String TEST_APP_LINK =
            "https://www.commcarehq.org/a/shubhamgoyaltest/apps/download/b4e4c3b0331b400badf490d7c188b103"
                    + "/media_profile.ccpr";
    private ProgressBar progressBar;
    private TextView statusText;
    private ConnectJobRecord job;

    public ConnectDownloadingFragment() {
        // Required empty public constructor
    }

    public static ConnectDownloadingFragment newInstance() {
        return new ConnectDownloadingFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startAppDownload();
    }

    private void startAppDownload() {
        ResourceInstallUtils.startAppInstallAsync(false, APP_DOWNLOAD_TASK_ID, (CommCareTaskConnector)getActivity(), TEST_APP_LINK);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        ConnectDownloadingFragmentArgs args = ConnectDownloadingFragmentArgs.fromBundle(getArguments());
        job = args.getJob();
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
        ConnectJobRecord job = ConnectJobIntroFragmentArgs.fromBundle(getArguments()).getJob();
        NavDirections directions =
                ConnectDownloadingFragmentDirections.actionConnectDownloadingFragmentToConnectJobLearningProgressFragment(
                        job);
        View view = getView();
        if (view != null) {
            Navigation.findNavController(view).navigate(directions);
        }
    }

    @Override
    public void failMissingResource(UnresolvedResourceException ure, AppInstallStatus statusmissing) {
        showInstallFailError(statusmissing);
    }

    private void showInstallFailError(AppInstallStatus statusmissing) {
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
