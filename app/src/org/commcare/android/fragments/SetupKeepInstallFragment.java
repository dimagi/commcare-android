package org.commcare.android.fragments;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.commcare.android.framework.UiElement;
import org.commcare.android.view.SquareButtonWithText;
import org.commcare.dalvik.R;

/**
 * Created by dancluna on 4/10/15.
 */
public class SetupKeepInstallFragment extends Fragment {
    @UiElement(R.id.btn_start_install)
    SquareButtonWithText btnStartInstall;

    @UiElement(R.id.btn_stop_install)
    SquareButtonWithText btnStopInstall;

    public interface StartStopInstallCommands {
        void onStartInstallClicked();
        void onStopInstallClicked();
    }

    StartStopInstallCommands buttonCommands;

    public void setButtonCommands(StartStopInstallCommands buttonCommands) {
        this.buttonCommands = buttonCommands;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.setup_keepinstall, container, false);
        btnStartInstall = (SquareButtonWithText) view.findViewById(R.id.btn_start_install);
        btnStopInstall = (SquareButtonWithText) view.findViewById(R.id.btn_stop_install);
        btnStartInstall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(buttonCommands != null) buttonCommands.onStartInstallClicked();
            }
        });
        btnStopInstall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(buttonCommands != null) buttonCommands.onStopInstallClicked();
            }
        });
        return view;
    }
}
