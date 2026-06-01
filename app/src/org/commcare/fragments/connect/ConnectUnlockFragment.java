package org.commcare.fragments.connect;

import static org.commcare.connect.ConnectConstants.OPPORTUNITY_UUID;
import static org.commcare.connect.ConnectConstants.REDIRECT_ACTION;
import static org.commcare.connect.ConnectConstants.SHOW_LAUNCH_BUTTON;

import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigation;

import org.commcare.activities.CommCareActivity;
import org.commcare.activities.connect.ConnectActivity;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.ConnectJobHelper;
import org.commcare.connect.database.ConnectJobUtils;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.connect.network.connect.ConnectApiHandler;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.FragmentConnectUnlockBinding;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.personalId.PersonalIdUnlocker;
import org.commcare.personalId.UnlockPolicy;
import org.javarosa.core.services.Logger;

import java.util.List;

public class ConnectUnlockFragment extends Fragment {
    private FragmentConnectUnlockBinding binding;
    private String redirectionAction = "";
    private boolean buttons = false;
    private boolean fromOppInviteLink = false;
    private String requestedOpportunityUuid = null;

    public ConnectUnlockFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        requireActivity().setTitle(R.string.connect_title);

        if(getArguments() != null) {
            redirectionAction = getArguments().getString(REDIRECT_ACTION);
            buttons = getArguments().getBoolean(SHOW_LAUNCH_BUTTON, true);
            fromOppInviteLink = getArguments().getBoolean(
                    ConnectConstants.FROM_SMS_INVITE_LINK, false);
            requestedOpportunityUuid = getArguments().getString(OPPORTUNITY_UUID);
        }

        binding = FragmentConnectUnlockBinding.inflate(inflater, container, false);
        binding.getRoot().setBackgroundColor(getResources().getColor(R.color.white));

        return binding.getRoot();
    }

    private final Runnable unlockRunnable = new Runnable() {
        @Override
        public void run() {
            PersonalIdUnlocker.INSTANCE.unlock((CommCareActivity<?>) requireActivity(), UnlockPolicy.SESSION_WITH_TIME_THRESHOLD, success -> {
                if (success) {
                    retrieveOpportunities();
                } else {
                    requireActivity().finish();
                }
            });
        }
    };


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        new Handler().post(unlockRunnable);
    }

    private void retrieveOpportunities() {
        ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(getContext());
        new ConnectApiHandler<List<ConnectJobRecord>>() {

            @Override
            public void onFailure(@NonNull PersonalIdOrConnectApiErrorCodes errorCode,
                                  @androidx.annotation.Nullable Throwable t) {
                if (!isAdded()) { return; }
                if (fromOppInviteLink) {
                    handleOppInviteLinkFailure(AnalyticsParamValue.OPP_INVITE_LINK_NETWORK_FAILURE);
                }

                tryToLoadInvitedOpp();
                setFragmentRedirection();
            }

            @Override
            public void onSuccess(List<ConnectJobRecord> jobs) {
                if (!isAdded()) { return; }
                if (!jobs.isEmpty()) {
                    ConnectUserDatabaseUtil.turnOnConnectAccess(requireContext());
                }

                tryToLoadInvitedOpp();
                setFragmentRedirection();
            }
        }.getConnectOpportunities(requireContext(), user);
    }

    private void tryToLoadInvitedOpp() {
        if (fromOppInviteLink) {
            ConnectJobRecord requested = ConnectJobUtils.getCompositeJob(
                    requireContext(), requestedOpportunityUuid);
            if (requested == null) {
                handleOppInviteLinkFailure(AnalyticsParamValue.OPP_INVITE_LINK_OPPORTUNITY_NOT_FOUND);
            } else {
                FirebaseAnalyticsUtil.reportExternalAppLaunchEvent(
                        AnalyticsParamValue.OPP_INVITE_LINK, true, null);
                ((ConnectActivity) requireActivity()).setActiveJob(requested);
                redirectionAction = ConnectJobHelper.INSTANCE.resolveGenericOpportunityDestination(
                        redirectionAction, requested, null);
            }
        }
    }

    private void handleOppInviteLinkFailure(String analyticsOutcome) {
        FirebaseAnalyticsUtil.reportExternalAppLaunchEvent(
                AnalyticsParamValue.OPP_INVITE_LINK, false, analyticsOutcome);
        Toast.makeText(requireContext(),
                R.string.connect_sms_invite_opportunity_not_found,
                Toast.LENGTH_LONG).show();

        //Clear the redirection action so we navigate to the jobs list
        redirectionAction = "";
    }

    /**
     * Sets the fragment redirection based on the redirection action.
     * This method determines the fragment to be displayed using the getFragmentId() method,
     * prepares a bundle with additional data, and navigates to the appropriate fragment.
     */
    private void setFragmentRedirection() {
        Logger.log("ConnectUnlockFragment", "Redirecting after unlock fragment");
        Bundle bundle = new Bundle();
        bundle.putBoolean(SHOW_LAUNCH_BUTTON, buttons);

        int fragmentId;
        if (redirectionAction.equals(ConnectConstants.CCC_DEST_OPPORTUNITY_SUMMARY_PAGE)) {
            fragmentId = R.id.connect_job_intro_fragment;
        } else if (redirectionAction.equals(ConnectConstants.CCC_DEST_LEARN_PROGRESS)) {
            fragmentId = R.id.connect_job_learning_progress_fragment;
        } else if (redirectionAction.equals(ConnectConstants.CCC_DEST_DELIVERY_PROGRESS)) {
            fragmentId = R.id.connect_job_delivery_progress_fragment;
            // Set the tab position in the bundle based on the redirection action
            bundle.putInt(ConnectDeliveryProgressFragment.TAB_POSITION,
                    ConnectDeliveryProgressFragment.TAB_PROGRESS);
        } else if (redirectionAction.equals(ConnectConstants.CCC_DEST_PAYMENTS)) {
            fragmentId = R.id.connect_job_delivery_progress_fragment;
            // Set the tab position in the bundle based on the redirection action
            bundle.putInt(ConnectDeliveryProgressFragment.TAB_POSITION,
                    ConnectDeliveryProgressFragment.TAB_PAYMENT);
        } else {
            //Default case
            fragmentId = R.id.connect_jobs_list_fragment;
        }

        NavController navController = Navigation.findNavController(binding.getRoot());
        navController.popBackStack();
        NavOptions options = new NavOptions.Builder()
                .setPopUpTo(navController.getGraph().getStartDestinationId(), true, true)
                .build();
        navController.navigate(fragmentId, bundle, options);
    }
}
