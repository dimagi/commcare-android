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
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.view.Menu;

import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;

public class DeveloperPreferences extends PreferenceActivity {
    public final static String SUPERUSER_ENABLED = "cc-superuser-enabled";
    public final static String GRID_MENUS_ENABLED = "cc-grid-menus";
    public final static String NAV_UI_ENABLED = "cc-nav-ui-enabled";
    public final static String CSS_ENABLED = "cc-css-enabled";
	public final static String ACTION_BAR_ENABLED = "cc-action-nav-enabled";
	public final static String LIST_REFRESH_ENABLED = "cc-list-refresh";
    public final static String MARKDOWN_ENABLED = "cc-markdown-enabled";

    private static final int CLEAR_USER_DATA = Menu.FIRST;
    private static final int ABOUT_COMMCARE = Menu.FIRST + 1;
    private static final int FORCE_LOG_SUBMIT = Menu.FIRST + 2;
    private static final int RECOVERY_MODE = Menu.FIRST + 3;

    /*
     * (non-Javadoc)
     * @see android.preference.PreferenceActivity#onCreate(android.os.Bundle)
     */
    @Override    
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        PreferenceManager prefMgr = getPreferenceManager();
        
        prefMgr.setSharedPreferencesName((CommCareApplication._().getCurrentApp().getPreferencesFilename()));
        
        addPreferencesFromResource(R.xml.preferences_developer);
        setTitle("Developer Preferences");
    }
    
    private static boolean doesPropertyMatch(String key, String defaultValue, String matchingValue) {
        CommCareApp app = CommCareApplication._().getCurrentApp();
        if(app == null) { return defaultValue.equals(matchingValue); }
        SharedPreferences properties = app.getAppPreferences();
        return properties.getString(key, defaultValue).equals(matchingValue);
    }
    
    public static boolean isSuperuserEnabled(){
        return doesPropertyMatch(SUPERUSER_ENABLED, BuildConfig.DEBUG ? CommCarePreferences.YES : CommCarePreferences.NO, CommCarePreferences.YES);
    }
	
	public static boolean isActionBarEnabled(){
        return doesPropertyMatch(ACTION_BAR_ENABLED, CommCarePreferences.NO, CommCarePreferences.YES);
    }

    public static boolean isNewNavEnabled(){
        SharedPreferences properties = CommCareApplication._().getCurrentApp().getAppPreferences();
        return properties.getString(NAV_UI_ENABLED, CommCarePreferences.NO).equals(CommCarePreferences.YES);
    }
    
    public static boolean isCssEnabled(){
        try{
            SharedPreferences properties = CommCareApplication._().getCurrentApp().getAppPreferences();
            return properties.getString(CSS_ENABLED, BuildConfig.DEBUG ? CommCarePreferences.YES : CommCarePreferences.NO).equals(CommCarePreferences.YES);
        } catch(NullPointerException e){
            // currentApp() is not initialized
            return false;
        }
    }

    public static boolean isMarkdownEnabled(){
        try{
            SharedPreferences properties = CommCareApplication._().getCurrentApp().getAppPreferences();
            return properties.getString(MARKDOWN_ENABLED, BuildConfig.DEBUG ? CommCarePreferences.YES : CommCarePreferences.NO).equals(CommCarePreferences.YES);
        } catch(NullPointerException e){
            // currentApp() is not initialized
            return false;
        }
    }

    public static boolean isListRefreshEnabled(){
        SharedPreferences properties = CommCareApplication._().getCurrentApp().getAppPreferences();
        return properties.getString(LIST_REFRESH_ENABLED, CommCarePreferences.NO).equals(CommCarePreferences.YES);
    }
    
    public static boolean isGridMenuEnabled(){
        SharedPreferences properties = CommCareApplication._().getCurrentApp().getAppPreferences();
        return properties.getString(GRID_MENUS_ENABLED, CommCarePreferences.NO).equals(CommCarePreferences.YES);
    }

}
