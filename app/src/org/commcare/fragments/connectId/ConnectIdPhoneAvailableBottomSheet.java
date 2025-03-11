package org.commcare.fragments.connectId;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.commcare.connect.ConnectConstants;
import org.commcare.dalvik.R;
import org.commcare.views.connect.connecttextview.ConnectMediumTextView;

import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;


public class ConnectIdPhoneAvailableBottomSheet extends BottomSheetDialogFragment {

    ConnectMediumTextView phoneTextView;
    Button recover;
    Button back;
    String phoneNumber;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_phone_available_bottom_sheet, container, false);
        back = (Button) view.findViewById(R.id.back_button);
        recover = (Button) view.findViewById(R.id.recover_button);
        phoneTextView = (ConnectMediumTextView) view.findViewById(R.id.phone_number);
        if (getArguments() != null) {
            phoneNumber = ConnectIdPhoneAvailableBottomSheetArgs.fromBundle(getArguments()).getPhone();
        }

        phoneTextView.setText(phoneNumber != null ? phoneNumber : "");

        back.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());
        recover.setOnClickListener(v -> {
            NavDirections directions = org.commcare.fragments.connectId.ConnectIdPhoneAvailableBottomSheetDirections.actionConnectidPhoneNotAvailableToConnectidPhoneFragment().setCallingClass(ConnectConstants.CONNECT_RECOVERY_PRIMARY_PHONE).setPhone(phoneNumber);
            NavHostFragment.findNavController(this).navigate(directions);
        });
        return view;

    }
}