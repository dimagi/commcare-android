package org.commcare.fragments.connect;

import static org.commcare.connect.ConnectConstants.REDIRECT_ACTION;
import static org.commcare.connect.ConnectConstants.SHOW_LAUNCH_BUTTON;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.commcare.activities.CommCareActivity;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.PersonalIdManager;
import org.commcare.connect.database.ConnectJobUtils;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.connect.network.connect.ConnectApiHandler;
import org.commcare.connect.network.connect.models.ConnectOpportunitiesResponseModel;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.FragmentConnectUnlockBinding;
import org.javarosa.core.services.Logger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigation;

public class ConnectUnlockFragment extends Fragment {
    private FragmentConnectUnlockBinding binding;
    private String redirectionAction = "";
    private boolean buttons = false;

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
        }

        binding = FragmentConnectUnlockBinding.inflate(inflater, container, false);
        binding.getRoot().setBackgroundColor(getResources().getColor(R.color.white));

        PersonalIdManager.getInstance().unlockConnect((CommCareActivity<?>)requireActivity(), success -> {
            if (success) {
                retrieveOpportunities();
            } else {
                requireActivity().finish();
            }
        });

        return binding.getRoot();
    }

    private void retrieveOpportunities() {
        ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(getContext());
        new ConnectApiHandler<ConnectOpportunitiesResponseModel>() {

            @Override
            public void onFailure(@NonNull PersonalIdOrConnectApiErrorCodes errorCode, @androidx.annotation.Nullable Throwable t) {
                setFragmentRedirection();
            }

            @Override
            public void onSuccess(ConnectOpportunitiesResponseModel data) {
                if (!data.getValidJobs().isEmpty()) {
                    ConnectJobUtils.storeJobs(requireContext(), data.getValidJobs(), true);
                    ConnectUserDatabaseUtil.turnOnConnectAccess(requireContext());
                }
                setFragmentRedirection();

            }
        }.getConnectOpportunities(requireContext(), user);
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
