/*
 * Copyright (C) 2009 University of Washington
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.commcare.android.preferences;

import org.odk.collect.android.R;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.PreferenceActivity;

public class ServerPreferences extends PreferenceActivity implements
        OnSharedPreferenceChangeListener {

    public static String KEY_DATA = "dataserver";
    public static String KEY_APP = "appserver";
    public static String KEY_SUBMIT = "submitserver";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.server_preferences);
        setTitle(getString(R.string.app_name) + " > " + "Server Preferences");
        updateValue(KEY_DATA);
        updateValue(KEY_APP);
        updateValue(KEY_SUBMIT);
    }


    @Override
    protected void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(
                this);
    }


    @Override
    protected void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }


    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    	updateValue(key);
    	
//        if (key.equals(KEY_DATA)) {
//            updateData();
//        } else if (key.equals(KEY_APP)) {
//            updateUsername();
//        } else if (key.equals(KEY_SUBMIT)) {
//            updatePassword();
//        }
    }


    private void updateValue(String key) {
        EditTextPreference etp = (EditTextPreference) this.getPreferenceScreen().findPreference(key);
        etp.setSummary(etp.getText());
    }

}
