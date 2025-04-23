package org.commcare.fragments.connectId;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.commcare.connect.ConnectConstants;
import org.commcare.dalvik.R;


import androidx.annotation.NonNull;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;


public class ConnectIdPhoneAvailableBottomSheet extends BottomSheetDialogFragment {

    TextView phoneTextView;
    Button recover;
    Button back;
    String phoneNumber;
    private static final String KEY_PHONE = "phone";


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_phone_available_bottom_sheet, container, false);
        back = (Button)view.findViewById(R.id.back_button);
        recover = view.findViewById(R.id.recover_button);
        phoneTextView = view.findViewById(R.id.phone_number);
        if (getArguments() != null) {
            phoneNumber = ConnectIdPhoneAvailableBottomSheetArgs.fromBundle(getArguments()).getPhone();
        }
        getLoadState(savedInstanceState);

        phoneTextView.setText(phoneNumber != null ? phoneNumber : "");

        back.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());
        recover.setOnClickListener(v -> {
            NavDirections directions = org.commcare.fragments.connectId.ConnectIdPhoneAvailableBottomSheetDirections.actionConnectidPhoneNotAvailableToConnectidPhoneFragment().setCallingClass(ConnectConstants.CONNECT_RECOVERY_PRIMARY_PHONE).setPhone(phoneNumber);
            NavHostFragment.findNavController(this).navigate(directions);
        });
        return view;
    }

    private void getLoadState(Bundle outState) {
        if (outState != null) {
            phoneNumber = outState.getString(KEY_PHONE);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_PHONE, phoneNumber);
    }
}