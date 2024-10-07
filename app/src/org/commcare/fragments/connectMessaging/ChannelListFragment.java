package org.commcare.fragments.connectMessaging;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.commcare.adapters.ChannelAdapter;
import org.commcare.dalvik.databinding.FragmentChannelListBinding;

public class ChannelListFragment extends Fragment {

    private FragmentChannelListBinding binding;

    public static ChannelListFragment newInstance() {
        ChannelListFragment fragment = new ChannelListFragment();
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
        ChannelAdapter channelAdapter = new ChannelAdapter();
        binding.rvChannel.setAdapter(channelAdapter);

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    public void onSearchQueryReceived(String query) {
    }
}
