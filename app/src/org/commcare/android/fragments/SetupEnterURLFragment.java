package org.commcare.android.fragments;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.support.v4.app.Fragment;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import org.commcare.android.framework.ManagedUi;
import org.commcare.android.framework.UiElement;
import org.commcare.dalvik.R;
import org.javarosa.core.services.locale.Localization;

/**
 * Fragment for inputting app installation URL, "returned" through the URLInstaller interface.
 * Created by dancluna on 3/17/15.
 */
@ManagedUi(R.layout.setup_enter_url)
public class SetupEnterURLFragment extends Fragment {

    public interface URLInstaller {
        /**
         * Called when user fills in an URL and presses 'Start Install'.
         * The parent activity is responsible for implementing this interface and doing something with the URL.
         * @param url URL typed by the user
         */
        public void OnURLChosen(String url);
    }

    public static final String interfaceName = URLInstaller.class.getName();

    private URLInstaller listener;

    @UiElement(R.id.start_install)
    Button installButton;

    @UiElement(R.id.url_spinner)
    Spinner prefixURLSpinner;

    @UiElement(R.id.edit_profile_location)
    EditText profileLocation;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.setup_enter_url, container, false);
        installButton = (Button) view.findViewById(R.id.start_install);
        installButton.setText(Localization.get("install.button.start"));
        prefixURLSpinner = (Spinner) view.findViewById(R.id.url_spinner);
        profileLocation = (EditText) view.findViewById(R.id.edit_profile_location);
        TextView appProfile = (TextView) view.findViewById(R.id.TextView02);
        appProfile.setText(Localization.get("install.appprofile"));

        installButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getFragmentManager().popBackStack();
                if(listener != null) listener.OnURLChosen(getURL());
            }
        });
        return view;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if(!(activity instanceof URLInstaller)){
            throw new ClassCastException(activity + " must implemement " + interfaceName);
        } else {
            listener = (URLInstaller) activity;
        }
    }

    /**
     * Returns the chosen URL in the UI, prefixing it with http:// if not set.
     * @return The current URL
     */
    public String getURL(){
        int selectedPrefix = prefixURLSpinner.getSelectedItemPosition();
        String url = profileLocation.getText().toString();
        if (url == null || url.length() == 0) {
            return url;
        }
        // if it's not the last (which should be "Raw") choice, we'll use the prefix
        if(selectedPrefix < prefixURLSpinner.getCount() - 1) {
            url = prefixURLSpinner.getSelectedItem() + "/" + url;
        }
        if(!url.startsWith("http")){
            url = "http://" + url;
        }
        return url;
    }
}