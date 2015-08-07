package org.commcare.android.fragments;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import org.commcare.android.framework.ManagedUi;
import org.commcare.android.framework.UiElement;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.javarosa.core.services.locale.Localization;

/**
 * Fragment for inputting app installation URL, "returned" through the URLInstaller interface.
 * Created by dancluna on 3/17/15.
 */
@ManagedUi(R.layout.fragment_setup_enter_url)
public class SetupEnterURLFragment extends Fragment {
    public static final String TAG = SetupEnterURLFragment.class.getSimpleName();

    public interface URLInstaller {
        /**
         * Called when user fills in an URL and presses 'Start Install'.
         * The parent activity is responsible for implementing this interface and doing something with the URL.
         * @param url URL typed by the user
         */
        public void onURLChosen(String url);
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
        View view = inflater.inflate(R.layout.fragment_setup_enter_url, container, false);
        installButton = (Button) view.findViewById(R.id.start_install);
        installButton.setText(Localization.get("install.button.start"));
        prefixURLSpinner = (Spinner) view.findViewById(R.id.url_spinner);
        prefixURLSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedText = profileLocation.getText().toString();
                if((position == prefixURLSpinner.getCount() -1) &&
                        (selectedText == null || selectedText.length() == 0)) {
                    // automatically sets text to the default location for offline installs
                    profileLocation.setText(R.string.default_app_server);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        profileLocation = (EditText) view.findViewById(R.id.edit_profile_location);
        TextView appProfile = (TextView) view.findViewById(R.id.app_profile_txt_view);
        appProfile.setText(Localization.get("install.appprofile"));

        installButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                getFragmentManager().popBackStack(); // equivalent to pressing the "back" button
                // no need for a null check because onAttach is called before onCreateView
                listener.onURLChosen(getURL()); // returns the chosen URL to the parent Activity
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
        if (BuildConfig.DEBUG) {
            Log.v(TAG, "Selected prefix: " + selectedPrefix + ", selected item is: " + prefixURLSpinner.getSelectedItem());
        }
        if(selectedPrefix < prefixURLSpinner.getCount() - 1) {
            url = prefixURLSpinner.getSelectedItem() + "/" + url;
        }
        if(!url.contains("://")){ // if there is no (http|jr):// prefix, we'll assume it's a http:// URL
            url = "http://" + url;
        }
        return url;
    }
}
