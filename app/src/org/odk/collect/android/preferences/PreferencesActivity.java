/*
 * Copyright (C) 2011 University of Washington
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.odk.collect.android.preferences;

import android.content.CursorLoader;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.provider.MediaStore.Images;
import android.text.InputFilter;
import android.text.Spanned;
import android.widget.Toast;

import org.commcare.android.framework.SessionAwarePreferenceActivity;
import org.commcare.dalvik.R;
import org.odk.collect.android.utilities.UrlUtils;

/**
 * @author yanokwa
 */
public class PreferencesActivity extends SessionAwarePreferenceActivity implements
        OnSharedPreferenceChangeListener {

    protected static final int IMAGE_CHOOSER = 0;

    public static final String KEY_SPLASH_PATH = "splashPath";
    public static final String KEY_FONT_SIZE = "font_size";

    public static final String KEY_SERVER_URL = "server_url";

    public static final String KEY_FORMLIST_URL = "formlist_url";
    public static final String KEY_SUBMISSION_URL = "submission_url";

    public static final String KEY_COMPLETED_DEFAULT = "default_completed";
    public static final String KEY_HELP_MODE_TRAY = "help_mode_tray";

    public static final String KEY_SERVER_PREFS = "serverprefs";

    private PreferenceScreen mSplashPathPreference;
    private EditTextPreference mSubmissionUrlPreference;
    private EditTextPreference mFormListUrlPreference;
    private EditTextPreference mServerUrlPreference;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        setTitle(getString(R.string.application_name) + " > " + getString(R.string.general_preferences));

        //there's no ODK collect instance, so we should
        //hide everything that's irrelevant
        this.getPreferenceScreen().removePreference(this.findPreference(KEY_SERVER_PREFS));
        updateFontSize();
    }

    private void setSplashPath(String path) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        Editor editor = sharedPreferences.edit();
        editor.putString(KEY_SPLASH_PATH, path);
        editor.commit();
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
        
        updateFontSize();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (resultCode == RESULT_CANCELED) {
            // request was canceled, so do nothing
            return;
        }

        switch (requestCode) {
            case IMAGE_CHOOSER:
                String sourceImagePath = null;

                // get gp of chosen file
                Uri uri = intent.getData();
                if (uri.toString().startsWith("file")) {
                    sourceImagePath = uri.toString().substring(6);
                } else {
                    String[] projection = {
                        Images.Media.DATA
                    };

                    Cursor c;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                        c = new CursorLoader(this, uri, projection, null, null, null).loadInBackground();
                    } else {
                        c = managedQuery(uri, projection, null, null, null);
                        startManagingCursor(c);
                    }
                    int i = c.getColumnIndexOrThrow(Images.Media.DATA);
                    c.moveToFirst();
                    sourceImagePath = c.getString(i);
                }

                // setting image path
                setSplashPath(sourceImagePath);
                updateSplashPath();
                break;
        }
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key) {
        switch (key) {
            case KEY_SERVER_URL:
                updateServerUrl();
                break;
            case KEY_FORMLIST_URL:
                updateFormListUrl();
                break;
            case KEY_SUBMISSION_URL:
                updateSubmissionUrl();
                break;
            case KEY_SPLASH_PATH:
                updateSplashPath();
                break;
            case KEY_FONT_SIZE:
                updateFontSize();
                break;
        }
    }

    private void validateUrl(final EditTextPreference preference) {
        if (preference != null) {
            final String url = preference.getText();
            if (UrlUtils.isValidUrl(url)) {
                preference.setText(url);
                preference.setSummary(url);
            } else {
                // preference.setText((String) preference.getSummary());
                Toast.makeText(getApplicationContext(), getString(R.string.url_error),
                    Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateServerUrl() {
        mServerUrlPreference = (EditTextPreference) findPreference(KEY_SERVER_URL);

        // remove all trailing "/"s
        while (mServerUrlPreference.getText().endsWith("/")) {
            mServerUrlPreference.setText(mServerUrlPreference.getText().substring(0,
                mServerUrlPreference.getText().length() - 1));
        }
        validateUrl(mServerUrlPreference);
        mServerUrlPreference.setSummary(mServerUrlPreference.getText());

        mServerUrlPreference.getEditText().setFilters(new InputFilter[]{
                getReturnFilter()
        });
    }

    private void updateSplashPath() {
        mSplashPathPreference = (PreferenceScreen) findPreference(KEY_SPLASH_PATH);
        mSplashPathPreference.setSummary(mSplashPathPreference.getSharedPreferences().getString(
                KEY_SPLASH_PATH, getString(R.string.default_splash_path)));
    }

    private void updateFormListUrl() {
        mFormListUrlPreference = (EditTextPreference) findPreference(KEY_FORMLIST_URL);
        mFormListUrlPreference.setSummary(mFormListUrlPreference.getText());

        mFormListUrlPreference.getEditText().setFilters(new InputFilter[] {
            getReturnFilter()
        });
    }

    private void updateSubmissionUrl() {
        mSubmissionUrlPreference = (EditTextPreference) findPreference(KEY_SUBMISSION_URL);
        mSubmissionUrlPreference.setSummary(mSubmissionUrlPreference.getText());

        mSubmissionUrlPreference.getEditText().setFilters(new InputFilter[]{
                getReturnFilter()
        });
    }

    private void updateFontSize() {
        ListPreference lp = (ListPreference) findPreference(KEY_FONT_SIZE);
        lp.setSummary(lp.getEntry());
    }

    private InputFilter getReturnFilter() {
        InputFilter returnFilter = new InputFilter() {
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest,
                    int dstart, int dend) {
                for (int i = start; i < end; i++) {
                    if (Character.getType((source.charAt(i))) == Character.CONTROL) {
                        return "";
                    }
                }
                return null;
            }
        };
        return returnFilter;
    }
}
