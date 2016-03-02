package org.commcare.fragments;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.commcare.views.SquareButtonWithText;
import org.javarosa.core.services.locale.Localization;

/**
 * Fragment to start, update or cancel an app installation.
 *
 * @author Daniel Luna (dcluna@dimagi.com)
 */
public class InstallConfirmFragment extends Fragment {
    private StartStopInstallCommands buttonCommands;

    public interface StartStopInstallCommands {
        void onStartInstallClicked();

        void onStopInstallClicked();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (!(context instanceof StartStopInstallCommands)) {
            throw new ClassCastException(context + " must implemement " + StartStopInstallCommands.class.getName());
        }

        this.buttonCommands = (StartStopInstallCommands)context;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.install_confirm_fragment, container, false);

        SquareButtonWithText btnStartInstall = (SquareButtonWithText)view.findViewById(R.id.btn_start_install);
        btnStartInstall.setText(Localization.get("install.button.start"));
        btnStartInstall.setEnabled(true);
        btnStartInstall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buttonCommands.onStartInstallClicked();
            }
        });

        SquareButtonWithText btnStopInstall = (SquareButtonWithText)view.findViewById(R.id.btn_stop_install);
        btnStopInstall.setText(Localization.get("install.button.startover"));
        btnStopInstall.setEnabled(true);
        btnStopInstall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buttonCommands.onStopInstallClicked();
            }
        });

        TextView setupMsg = (TextView)view.findViewById(R.id.str_setup_message);
        setupMsg.setText(Localization.get("install.ready.top"));

        TextView setupMsg2 = (TextView)view.findViewById(R.id.str_setup_message_2);
        setupMsg2.setText(Localization.get("install.ready.bottom"));

        TextView netWarn = (TextView)view.findViewById(R.id.net_warn);
        netWarn.setText(Localization.get("install.netwarn"));

        return view;
    }
}
