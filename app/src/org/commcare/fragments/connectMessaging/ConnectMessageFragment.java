package org.commcare.fragments.connectMessaging;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import org.commcare.adapters.ConnectMessageAdapter;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.FragmentConnectMessageBinding;

import java.util.ArrayList;

public class ConnectMessageFragment extends Fragment {

    private FragmentConnectMessageBinding binding;

    public static ConnectMessageFragment newInstance(String param1, String param2) {
        ConnectMessageFragment fragment = new ConnectMessageFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentConnectMessageBinding.inflate(inflater, container, false);
        handleSendButtonListener();
        setChatAdapter();
        return binding.getRoot();
    }

    private void handleSendButtonListener() {
        binding.etMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0) {
                    binding.imgSendMessage.setVisibility(View.VISIBLE);
//                    binding.etMessage.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0); // Remove drawableEnd
                } else {
                    binding.imgSendMessage.setVisibility(View.GONE);
//                    binding.etMessage.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_connect_message_photo_camera, 0); // Add back drawableEnd
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void setChatAdapter() {
        ConnectMessageAdapter connectMessageAdapter = new ConnectMessageAdapter(getDummyData());
        binding.rvChat.setAdapter(connectMessageAdapter);
    }

    public static ArrayList<ConnectMessageChatData> getDummyData() {
        ArrayList<ConnectMessageChatData> dataList = new ArrayList<>();
        dataList.add(new ConnectMessageChatData(ConnectMessageAdapter.LEFTVIEW, "I commented on Figma, I want to add some fancy icons. Do you have any icon set?", "@Jane", false));
        dataList.add(new ConnectMessageChatData(ConnectMessageAdapter.RIGHTVIEW, "I am in a process of designing some. When do you need them?", "@Esther", true));
        dataList.add(new ConnectMessageChatData(ConnectMessageAdapter.RIGHTVIEW, "Hi! Are you there", "@Esther", true));
        dataList.add(new ConnectMessageChatData(ConnectMessageAdapter.LEFTVIEW, "Yes", "@Jane", false));
        dataList.add(new ConnectMessageChatData(ConnectMessageAdapter.RIGHTVIEW, "Do you want to go out", "@Esther", true));
        dataList.add(new ConnectMessageChatData(ConnectMessageAdapter.LEFTVIEW, "Sorry I am busy!!", "@Jane", false));
        dataList.add(new ConnectMessageChatData(ConnectMessageAdapter.LEFTVIEW, "I commented on Figma, I want to add some fancy icons. Do you have any icon set?", "@Jane", false));
        dataList.add(new ConnectMessageChatData(ConnectMessageAdapter.RIGHTVIEW, "I am in a process of designing some. When do you need them?", "@Esther", true));
        dataList.add(new ConnectMessageChatData(ConnectMessageAdapter.RIGHTVIEW, "Hi! Are you there", "@Esther", true));
        dataList.add(new ConnectMessageChatData(ConnectMessageAdapter.LEFTVIEW, "Yes", "@Jane", false));
        dataList.add(new ConnectMessageChatData(ConnectMessageAdapter.RIGHTVIEW, "Do you want to go out", "@Esther", true));
        dataList.add(new ConnectMessageChatData(ConnectMessageAdapter.LEFTVIEW, "Sorry I am busy!!", "@Jane", false));
        dataList.add(new ConnectMessageChatData(ConnectMessageAdapter.LEFTVIEW, "I commented on Figma, I want to add some fancy icons. Do you have any icon set?", "@Jane", false));
        dataList.add(new ConnectMessageChatData(ConnectMessageAdapter.RIGHTVIEW, "I am in a process of designing some. When do you need them?", "@Esther", true));
        dataList.add(new ConnectMessageChatData(ConnectMessageAdapter.RIGHTVIEW, "Hi! Are you there", "@Esther", true));
        dataList.add(new ConnectMessageChatData(ConnectMessageAdapter.LEFTVIEW, "Yes", "@Jane", false));
        dataList.add(new ConnectMessageChatData(ConnectMessageAdapter.RIGHTVIEW, "Do you want to go out", "@Esther", false));
        dataList.add(new ConnectMessageChatData(ConnectMessageAdapter.RIGHTVIEW, "Do you want to go out", "@Esther", false));
        dataList.add(new ConnectMessageChatData(ConnectMessageAdapter.RIGHTVIEW, "Do you want to go out", "@Esther", false));
        return dataList;
    }
}

