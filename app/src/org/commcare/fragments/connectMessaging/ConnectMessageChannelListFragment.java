package org.commcare.fragments.connectMessaging;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.commcare.adapters.ChannelAdapter;
import org.commcare.connect.network.ApiConnectId;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.FragmentChannelListBinding;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

public class ConnectMessageChannelListFragment extends Fragment {

    private FragmentChannelListBinding binding;

    public static ConnectMessageChannelListFragment newInstance() {
        ConnectMessageChannelListFragment fragment = new ConnectMessageChannelListFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentChannelListBinding.inflate(inflater, container, false);
        View view = binding.getRoot();

        binding.rvChannel.setLayoutManager(new LinearLayoutManager(getContext()));
        ChannelAdapter channelAdapter = new ChannelAdapter(position -> {
            NavDirections directions;
            directions = ConnectMessageChannelListFragmentDirections.actionChannelListFragmentToConnectMessageFragment();
            Navigation.findNavController(binding.rvChannel).navigate(directions);
        });
        binding.rvChannel.setAdapter(channelAdapter);
//        retrieveChannel();
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    public void onSearchQueryReceived(String query) {
    }

    public void retrieveChannel() {
        IApiCallback callback = new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                Log.e("DEBUG_TESTING", "processSuccess: ");
            }

            @Override
            public void processFailure(int responseCode, IOException e) {
                Log.e("DEBUG_TESTING", "processFailure: " + responseCode);
                String message = "";
                if (responseCode > 0) {
                    message = String.format(Locale.getDefault(), "(%d)", responseCode);
                } else if (e != null) {
                    message = e.toString();
                }
            }

            @Override
            public void processNetworkFailure() {
                Log.e("DEBUG_TESTING", "processNetworkFailure: ");
            }

            @Override
            public void processOldApiError() {
                Log.e("DEBUG_TESTING", "processOldApiError: ");
            }
        };

        boolean isBusy;

        isBusy = !ApiConnectId.retrieveChannel(requireActivity(), callback);

        if (isBusy) {
            Toast.makeText(requireActivity(), R.string.busy_message, Toast.LENGTH_SHORT).show();
        }
    }
}
