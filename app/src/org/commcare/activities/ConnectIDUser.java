package org.commcare.activities;

import android.content.Intent;
import android.content.SharedPreferences;

public class ConnectIDUser {
    public String Username;
    public String Name;
    public String Password;
    public String DOB;
    public String Phone;
    public String AltPhone;

    public static ConnectIDUser getUserFromPreferences(SharedPreferences prefs) {
        ConnectIDUser user = null;
        if(prefs != null) {
            String username = prefs.getString(ConnectIDRegistrationActivity.USERNAME, "");
            if(username != null && username.length() > 0) {
                user = new ConnectIDUser();
                user.Username = username;
                user.Password = prefs.getString(ConnectIDRegistrationActivity.PASSWORD, "");
                user.Name = prefs.getString(ConnectIDRegistrationActivity.NAME, "");
                user.DOB = prefs.getString(ConnectIDRegistrationActivity.DOB, "");
                user.Phone = prefs.getString(ConnectIDRegistrationActivity.PHONE, "");
                user.AltPhone = prefs.getString(ConnectIDRegistrationActivity.ALTPHONE, "");
            }
        }

        return user;
    }

    public static void storeUserInPreferences(ConnectIDUser user, SharedPreferences prefs) {
        SharedPreferences.Editor editor = prefs.edit();
        boolean isNull = user == null;
        editor.putString(ConnectIDRegistrationActivity.USERNAME, isNull ? "" : user.Username);
        editor.putString(ConnectIDRegistrationActivity.PASSWORD, isNull ? "" : user.Password);
        editor.putString(ConnectIDRegistrationActivity.NAME, isNull ? "" : user.Name);
        editor.putString(ConnectIDRegistrationActivity.DOB, isNull ? "" : user.DOB);
        editor.putString(ConnectIDRegistrationActivity.PHONE, isNull ? "" : user.Phone);
        editor.putString(ConnectIDRegistrationActivity.ALTPHONE, isNull ? "" : user.AltPhone);

        editor.apply();
    }

    public static ConnectIDUser getUserFromIntent(Intent intent) {
        ConnectIDUser user = new ConnectIDUser();
        user.Username = intent.getStringExtra(ConnectIDRegistrationActivity.USERNAME);
        user.Password = intent.getStringExtra(ConnectIDRegistrationActivity.PASSWORD);
        user.Name = intent.getStringExtra(ConnectIDRegistrationActivity.NAME);
        user.DOB = intent.getStringExtra(ConnectIDRegistrationActivity.DOB);
        user.Phone = intent.getStringExtra(ConnectIDRegistrationActivity.PHONE);
        user.AltPhone = intent.getStringExtra(ConnectIDRegistrationActivity.ALTPHONE);

        return user;
    }

    public void putUserInIntent(Intent intent) {
        intent.putExtra(ConnectIDRegistrationActivity.USERNAME, Username);
        intent.putExtra(ConnectIDRegistrationActivity.PASSWORD, Password);
        intent.putExtra(ConnectIDRegistrationActivity.NAME, Name);
        intent.putExtra(ConnectIDRegistrationActivity.DOB, DOB);
        intent.putExtra(ConnectIDRegistrationActivity.PHONE, Phone);
        intent.putExtra(ConnectIDRegistrationActivity.ALTPHONE, AltPhone);
    }
}
