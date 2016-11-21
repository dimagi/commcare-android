package org.commcare.views;

import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import org.commcare.preferences.CommCarePreferences;
import org.javarosa.core.services.locale.Localization;

/**
 * Allow password field to show/hide password text
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class PasswordShow {

    private boolean isPasswordVisible;
    private final Button showPasswordButton;
    private final EditText passwordField;
    private final String showText, hideText;

    public PasswordShow(Button showPasswordButton, EditText passwordField) {
        this.showPasswordButton = showPasswordButton;
        this.passwordField = passwordField;

        showText = Localization.get("login.show.password");
        hideText = Localization.get("login.hide.password");
    }

    public void setupPasswordVisibility() {
        switch (CommCarePreferences.getPasswordDisplayOption()) {
            case ALWAYS_HIDDEN:
                passwordAlwaysHiddenState();
                break;
            case DEFAULT_HIDE:
                passwordHiddenState();
                break;
            case DEFAULT_SHOW:
                passwordShownState();
                break;
        }
        showPasswordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePasswordVisibility();
            }
        });
    }

    private void passwordAlwaysHiddenState() {
        isPasswordVisible = false;
        showPasswordButton.setVisibility(View.INVISIBLE);
    }

    private void passwordHiddenState() {
        isPasswordVisible = false;
        showPasswordButton.setVisibility(View.VISIBLE);
        showPasswordButton.setText(showText);
        passwordField.setTransformationMethod(PasswordTransformationMethod.getInstance());
        passwordField.setSelection(passwordField.getText().length());
    }

    private void passwordShownState() {
        isPasswordVisible = true;
        showPasswordButton.setVisibility(View.VISIBLE);
        showPasswordButton.setText(hideText);
        passwordField.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
        passwordField.setSelection(passwordField.getText().length());
    }

    private void togglePasswordVisibility() {
        if (isPasswordVisible) {
            passwordHiddenState();
        } else {
            passwordShownState();
        }
    }

    public enum PasswordShowOption {
        ALWAYS_HIDDEN,
        DEFAULT_SHOW,
        DEFAULT_HIDE;

        public static PasswordShowOption fromString(String optionAsString) {
            switch (optionAsString) {
                case "always_hidden":
                    return ALWAYS_HIDDEN;
                case "default_show":
                    return DEFAULT_SHOW;
                case "default_hide":
                    return DEFAULT_HIDE;
                default:
                    return ALWAYS_HIDDEN;
            }
        }
    }

}
