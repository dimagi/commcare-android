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

package org.commcare.dalvik.preferences;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import org.commcare.android.framework.SessionAwarePreferenceActivity;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;

public class DeveloperPreferences extends SessionAwarePreferenceActivity {
    public final static String SUPERUSER_ENABLED = "cc-superuser-enabled";
    public final static String GRID_MENUS_ENABLED = "cc-grid-menus";
    public final static String NAV_UI_ENABLED = "cc-nav-ui-enabled";
    public final static String CSS_ENABLED = "cc-css-enabled";
    public final static String MARKDOWN_ENABLED = "cc-markdown-enabled";
    public final static String ACTION_BAR_ENABLED = "cc-action-nav-enabled";
    public final static String LIST_REFRESH_ENABLED = "cc-list-refresh";
    // Does the user want to download the latest app version deployed (built),
    // not just the latest app version released (starred)?
    public final static String NEWEST_APP_VERSION_ENABLED = "cc-newest-version-from-hq";
    public final static String QUESTION_TEXT_FORMAT = "cc-question-text-format";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PreferenceManager prefMgr = getPreferenceManager();

        prefMgr.setSharedPreferencesName((CommCareApplication._().getCurrentApp().getPreferencesFilename()));

        addPreferencesFromResource(R.xml.preferences_developer);
        setTitle("Developer Preferences");
    }

    /**
     * Try to lookup key in app preferences and test equality of the result to
     * matchingValue.  If either the app or preference key don't exist, just
     * compare defaultValue to matchingValue
     *
     * @param key is a potential entry in the app preferences
     * @param defaultValue use this value if key not found
     * @param matchingValue compare this to key lookup or defaultValue
     * @return boolean
     */
    private static boolean doesPropertyMatch(String key, String defaultValue, String matchingValue) {
        CommCareApp app = CommCareApplication._().getCurrentApp();
        if (app == null) {
            return defaultValue.equals(matchingValue);
        }
        SharedPreferences properties = app.getAppPreferences();
        return properties.getString(key, defaultValue).equals(matchingValue);
    }

    /**
     * Lookup superuser preference; if debug build, superuser is enabled by
     * default.
     *
     * @return is the superuser developer preference enabled?
     */
    public static boolean isSuperuserEnabled() {
        return doesPropertyMatch(SUPERUSER_ENABLED, BuildConfig.DEBUG ? CommCarePreferences.YES : CommCarePreferences.NO, CommCarePreferences.YES);
    }

    public static boolean isActionBarEnabled() {
        return doesPropertyMatch(ACTION_BAR_ENABLED, CommCarePreferences.YES, CommCarePreferences.YES);
    }

    public static boolean isGridMenuEnabled() {
        SharedPreferences properties = CommCareApplication._().getCurrentApp().getAppPreferences();
        return properties.getString(GRID_MENUS_ENABLED, CommCarePreferences.NO).equals(CommCarePreferences.YES);
    }

    public static boolean isNewNavEnabled() {
        SharedPreferences properties = CommCareApplication._().getCurrentApp().getAppPreferences();
        return properties.getString(NAV_UI_ENABLED, CommCarePreferences.YES).equals(CommCarePreferences.YES);
    }
    
    public static boolean isCssEnabled(){
        return doesPropertyMatch(CSS_ENABLED, CommCarePreferences.NO, CommCarePreferences.YES);
    }

    public static boolean isListRefreshEnabled() {
        SharedPreferences properties = CommCareApplication._().getCurrentApp().getAppPreferences();
        return properties.getString(LIST_REFRESH_ENABLED, CommCarePreferences.NO).equals(CommCarePreferences.YES);
    }

    /**
     * @return true if developer option to download the latest app version
     * deployed (built) is enabled.  Otherwise the latest released (starred)
     * app version will be downloaed on upgrade.
     */
    public static boolean isNewestAppVersionEnabled() {
        SharedPreferences properties = CommCareApplication._().getCurrentApp().getAppPreferences();
        return properties.getString(NEWEST_APP_VERSION_ENABLED, CommCarePreferences.NO).equals(CommCarePreferences.YES);
    }

    public static boolean isMarkdownEnabled(){
        return doesPropertyMatch(MARKDOWN_ENABLED, CommCarePreferences.NO, CommCarePreferences.YES);
    }

    public static String getQuestionTextFormat() {
        String defaultValue = "image-below-text";
        CommCareApp app = CommCareApplication._().getCurrentApp();
        if (app == null) {
            return defaultValue;
        }
        SharedPreferences properties = app.getAppPreferences();
        return properties.getString(QUESTION_TEXT_FORMAT, defaultValue);
    }

}
