package org.commcare.activities;

import android.content.SharedPreferences;
import android.widget.EditText;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.preferences.CommCarePreferences;

import java.util.ArrayList;

/**
 * Created by amstone326 on 4/28/16.
 */
public class BlankLoginActivityUIController extends LoginActivityUIController {

    private EditText username;
    private EditText password;

    public BlankLoginActivityUIController(LoginActivity activity) {
        super(activity);
    }

    @Override
    public void setupUI() {
        activity.setContentView(R.layout.blank_screen_login);
        username = (EditText)activity.findViewById(R.id.edit_username);
        password = (EditText)activity.findViewById(R.id.edit_password);
    }

    @Override
    public void refreshView() {
    }

    @Override
    protected void refreshForNewApp() {
    }

    @Override
    protected void setNormalPasswordMode() {

    }

    @Override
    protected void manualSwitchToPasswordMode() {
    }

    @Override
    protected void setErrorMessageUI(String message) {

    }

    @Override
    protected void clearErrorMessage() {
    }

    @Override
    protected void setMultipleAppsUIState(ArrayList<String> appNames, int position) {
    }

    @Override
    protected void setPermissionsGrantedState() {
    }

    @Override
    protected void setPermissionDeniedState() {
    }

    @Override
    protected void restoreLastUser() {
        SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
        String lastUser = prefs.getString(CommCarePreferences.LAST_LOGGED_IN_USER, null);
        if (lastUser != null) {
            username.setText(lastUser);
            password.requestFocus();
        }
    }

    @Override
    protected boolean isRestoreSessionChecked() {
        return false;
    }

    @Override
    protected String getEnteredUsername() {
        return username.getText().toString();
    }

    @Override
    protected String getEnteredPasswordOrPin() {
        return password.getText().toString();
    }

    @Override
    protected void setUsername(String s) {
        username.setText(s);
    }

    @Override
    protected void setPasswordOrPin(String s) {
        password.setText(s);
    }

}
