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
import org.commcare.android.util.CommCareUtil;
import org.commcare.dalvik.R;
import org.commcare.dalvik.activities.RecoveryActivity;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.util.NoLocalizedTextException;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
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
    
    public final static String ENABLE_SAVED_FORMS = "cc-show-saved";
    
    public final static String ENABLE_INCOMPLETE_FORMS = "cc-show-incomplete";
    
    public final static String LAST_UPDATE_ATTEMPT = "cc-last_up";
    public final static String LAST_SYNC_ATTEMPT = "last-ota-restore";
    
    public final static String LOG_WEEKLY_SUBMIT = "log_prop_weekly";
    public final static String LOG_DAILY_SUBMIT = "log_prop_daily";
    
    public final static String RESIZING_METHOD = "cc-resize-images";
    
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
    
    public final static String AUTO_TRIGGER_UPDATE = "auto-trigger-update";
    
    public static final String DUMP_FOLDER_PATH = "dump-folder-path";
    
    
    public final static String FUZZY_SEARCH = "cc-fuzzy-search-enabled";
    
    public final static String BRAND_BANNER_LOGIN = "brand-banner-login";
    public final static String BRAND_BANNER_HOME = "brand-banner-home";

    private static final int CLEAR_USER_DATA = Menu.FIRST;
    private static final int ABOUT_COMMCARE = Menu.FIRST + 1;
    private static final int FORCE_LOG_SUBMIT = Menu.FIRST + 2;
    private static final int RECOVERY_MODE = Menu.FIRST + 3;
    private static final int SUPERUSER_PREFS = Menu.FIRST + 4;

    /*
     * (non-Javadoc)
     * @see android.preference.PreferenceActivity#onCreate(android.os.Bundle)
     */
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
        updatePreferencesText();
        setTitle("CommCare" + " > " + "Application Preferences");
    }
    
    /*
     * (non-Javadoc)
     * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, CLEAR_USER_DATA, 0, "Clear User Data").setIcon(
                android.R.drawable.ic_menu_delete);
        menu.add(0, ABOUT_COMMCARE, 1, "About CommCare").setIcon(
                android.R.drawable.ic_menu_help);
        menu.add(0, FORCE_LOG_SUBMIT, 2, "Force Log Submission").setIcon(
                android.R.drawable.ic_menu_upload);
        
        menu.add(0, RECOVERY_MODE, 3, "Recovery Mode").setIcon(android.R.drawable.ic_menu_report_image);
        menu.add(0, SUPERUSER_PREFS, 4, "Developer Options").setIcon(android.R.drawable.ic_menu_edit);
        
        return true;
    }


    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(SUPERUSER_PREFS).setVisible(DeveloperPreferences.isSuperuserEnabled());
        return super.onPrepareOptionsMenu(menu);
    }


    int mDeveloperModeClicks = 0;
    
    /*
     * (non-Javadoc)
     * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case CLEAR_USER_DATA:
                CommCareApplication._().clearUserData();
                this.finish();
                return true;
            case ABOUT_COMMCARE:
                AlertDialog dialog = new AlertDialog.Builder(this).setMessage(R.string.aboutdialog).create();
                dialog.setOnCancelListener(new OnCancelListener() {

                    @Override
                    public void onCancel(DialogInterface dialog) {
                        mDeveloperModeClicks ++;
                        if(mDeveloperModeClicks == 4) {
                            CommCareApplication._().getCurrentApp().getAppPreferences().
                            edit().putString(DeveloperPreferences.SUPERUSER_ENABLED, YES).commit();
                            Toast.makeText(CommCarePreferences.this, "Developer Mode Enabled", Toast.LENGTH_SHORT).show();;
                        }
                    }
                    
                });
                dialog.show();
                return true;
            case FORCE_LOG_SUBMIT:
                CommCareUtil.triggerLogSubmission(this);
                return true;
            case RECOVERY_MODE:
                Intent i = new Intent(this,RecoveryActivity.class);
                this.startActivity(i);
                return true;
            case SUPERUSER_PREFS:
                Intent intent = new Intent(this,DeveloperPreferences.class);
                this.startActivity(intent);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    public static boolean isInSenseMode(){
        return CommCareApplication._().getCommCarePlatform().getCurrentProfile() != null && CommCareApplication._().getCommCarePlatform().getCurrentProfile().isFeatureActive("sense");
    }
   
    public static boolean isIncompleteFormsEnabled() {
        SharedPreferences properties = CommCareApplication._().getCurrentApp().getAppPreferences();
        //If there is a setting for form management it takes precedence
        if(properties.contains(ENABLE_INCOMPLETE_FORMS)) {
            
            return properties.getString(ENABLE_INCOMPLETE_FORMS, YES).equals(YES);
        }
        
        //otherwise, see if we're in sense mode
        return !isInSenseMode();
    }
    
    public static boolean isSavedFormsEnabled(){
        
        SharedPreferences properties = CommCareApplication._().getCurrentApp().getAppPreferences();
        //If there is a setting for form management it takes precedence
        if(properties.contains(ENABLE_SAVED_FORMS)) {
            return properties.getString(ENABLE_SAVED_FORMS, YES).equals(YES);
        }
        
        //otherwise, see if we're in sense mode
        return !isInSenseMode();
    }
    
    public static boolean isFuzzySearchEnabled(){
        SharedPreferences properties = CommCareApplication._().getCurrentApp().getAppPreferences();
        
        return properties.getString(FUZZY_SEARCH, NO).equals(YES);
    }
    
    /*
     * (non-Javadoc)
     * @see android.app.Activity#onResume()
     */
    @Override
    protected void onResume() {
        super.onResume();
        // Set up a listener whenever a key changes
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    /*
     * (non-Javadoc)
     * @see android.app.Activity#onPause()
     */
    @Override
    protected void onPause() {
        super.onPause();
        // Unregister the listener whenever a key changes
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }
    
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,String key) 
    {
        if(key.equals("cur_locale")) {
            Localization.setLocale(sharedPreferences.getString(key, "default"));
        }
    }

    public static String getResizeMethod() {
        SharedPreferences properties = CommCareApplication._().getCurrentApp().getAppPreferences();
        //If there is a setting for form management it takes precedence
        if(properties.contains(RESIZING_METHOD)) {
            return properties.getString(RESIZING_METHOD, "none");
        }
        
        //otherwise, see if we're in sense mode
        return "none";
    }
    
    public void updatePreferencesText(){
        PreferenceScreen screen = getPreferenceScreen();
        int i;
        for(i = 0; i < screen.getPreferenceCount(); i++) {
            try{
                String key = screen.getPreference(i).getKey();
                String prependedKey = "preferences.title."+key;
                String localizedString = Localization.get(prependedKey);
                screen.getPreference(i).setTitle(localizedString);
            } catch(NoLocalizedTextException nle){
                
            }
        }
    }

}
