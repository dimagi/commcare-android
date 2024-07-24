package org.commcare.activities.connectId.fragments;

import android.content.Intent;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.commcare.views.UiElement;

public class ConsentFragment extends Fragment {

    private TextView messageText;
    private CheckBox checkbox;
    private Button button;

    public ConsentFragment() {
        // Required empty public constructor
    }
    public static ConsentFragment newInstance(String param1, String param2) {
        ConsentFragment fragment = new ConsentFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view= inflater.inflate(R.layout.screen_connect_consent, container, false);
        messageText= view.findViewById(R.id.connect_consent_message_1);
        checkbox= view.findViewById(R.id.connect_consent_check);
        button= view.findViewById(R.id.connect_consent_button);
        messageText.setMovementMethod(LinkMovementMethod.getInstance());
        checkbox.setOnClickListener(v -> updateState());
        requireActivity().setTitle(getString(R.string.connect_consent_title));
        button.setOnClickListener(v -> handleButtonPress());
        return view;
    }

    public void updateState() {
        button.setEnabled(checkbox.isChecked());
    }

    public void finish(boolean accepted) {
//        Intent intent = new Intent(getIntent());
//
//        setResult(accepted ? RESULT_OK : RESULT_CANCELED, intent);
//        finish();
    }

    public void handleButtonPress() {
        finish(true);
    }
}