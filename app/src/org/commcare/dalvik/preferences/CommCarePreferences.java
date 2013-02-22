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

import org.commcare.android.tasks.LogSubmissionTask;
import org.commcare.android.util.ChangeLocaleUtil;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.services.locale.Localization;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

public class CommCarePreferences extends PreferenceActivity implements OnSharedPreferenceChangeListener{

	//So these are stored in the R files, but I dont' seem to be able to figure out how to pull them
	//out cleanly?
	public final static String AUTO_SYNC_FREQUENCY = "cc-autosync-freq";
    public final static String AUTO_UPDATE_FREQUENCY = "cc-autoup-freq";
    public final static String FREQUENCY_NEVER = "freq-never";
    public final static String FREQUENCY_DAILY = "freq-daily";
    public final static String FREQUENCY_WEEKLY = "freq-weekly";

    public final static String LAST_UPDATE_ATTEMPT = "cc-last_up";
    public final static String LAST_SYNC_ATTEMPT = "last-ota-restore";
    
	public final static String LOG_WEEKLY_SUBMIT = "log_prop_weekly";
	public final static String LOG_DAILY_SUBMIT = "log_prop_daily";
	
	public final static String NEVER = "log_never";
	public final static String SHORT = "log_short";
	public final static String FULL = "log_full";
	
	public final static String LOG_LAST_DAILY_SUBMIT = "log_prop_last_daily";
	public final static String LOG_NEXT_WEEKLY_SUBMIT = "log_prop_next_weekly";
	
	public final static String FORM_MANAGEMENT = "cc-form-management";
	public final static String PROPERTY_ENABLED = "enabled";
	public final static String PROPERTY_DISABLED = "disabled";
	
	
	public final static String LAST_LOGGED_IN_USER = "last_logged_in_user";
    public final static String CONTENT_VALIDATED = "cc-content-valid";
    
    public final static String YES = "yes";
    public final static String NO = "no";

	private static final int CLEAR_USER_DATA = Menu.FIRST;
	private static final int ABOUT_COMMCARE = Menu.FIRST + 1;
	private static final int FORCE_LOG_SUBMIT = Menu.FIRST + 2;

    @Override	
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        PreferenceManager prefMgr = getPreferenceManager();
        
        prefMgr.setSharedPreferencesName((CommCareApplication._().getCurrentApp().getPreferencesFilename()));
        
        addPreferencesFromResource(R.xml.server_preferences);
        
        ListPreference lp = new ListPreference(this);
        lp.setEntries(ChangeLocaleUtil.getLocaleNames());
        lp.setEntryValues(ChangeLocaleUtil.getLocaleCodes());
        lp.setTitle("Change Locale");
        lp.setKey("cur_locale");
        lp.setDialogTitle("Choose your Locale");
        this.getPreferenceScreen().addPreference(lp);
        
        setTitle("CommCare" + " > " + "Application Preferences");
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, CLEAR_USER_DATA, 0, "Clear User Data").setIcon(
                android.R.drawable.ic_menu_delete);
        menu.add(0, ABOUT_COMMCARE, 1, "About CommCare").setIcon(
                android.R.drawable.ic_menu_help);
        menu.add(0, FORCE_LOG_SUBMIT, 2, "Force Log Submission").setIcon(
                android.R.drawable.ic_menu_upload);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case CLEAR_USER_DATA:
                CommCareApplication._().clearUserData();
                this.finish();
                return true;
            case ABOUT_COMMCARE:
            	AlertDialog dialog = new AlertDialog.Builder(this).setMessage(R.string.aboutdialog).create();
            	dialog.show();
            	return true;
            case FORCE_LOG_SUBMIT:
        		SharedPreferences settings = CommCareApplication._().getCurrentApp().getAppPreferences();
        		String url = settings.getString("PostURL", null);
        		
        		if(url == null) {
	        		//This is mostly for dev purposes
	        		Toast.makeText(this, "Couldn't submit logs! Invalid submission URL...", Toast.LENGTH_LONG);
        		} else {
	            	LogSubmissionTask reportSubmitter = new LogSubmissionTask(CommCareApplication._(), true, CommCareApplication._().getSession().startDataSubmissionListener(R.string.submission_logs_title), url);
	            	reportSubmitter.execute();
        		}
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static boolean isFormManagementEnabled() {
    	SharedPreferences properties = CommCareApplication._().getCurrentApp().getAppPreferences();
    	//If there is a setting for form management it takes precedence
    	if(properties.contains(FORM_MANAGEMENT)) {
    		return !properties.getString(FORM_MANAGEMENT, PROPERTY_ENABLED).equals(PROPERTY_DISABLED);
    	}
    	
    	//otherwise, see if we're in sense mode
    	if(CommCareApplication._().getCommCarePlatform().getCurrentProfile() != null && CommCareApplication._().getCommCarePlatform().getCurrentProfile().isFeatureActive("sense")) {
    		return false;
    	} 
    	
    	//if not, form management is a go
    	return true;
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Set up a listener whenever a key changes
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Unregister the listener whenever a key changes
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }
    
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,String key) 
    {
      Localization.setLocale(sharedPreferences.getString(key, "default"));
    }
}
