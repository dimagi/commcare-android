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

import org.commcare.android.R;
import org.commcare.android.activities.LoginActivity;
import org.commcare.android.application.CommCareApplication;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.view.Menu;
import android.view.MenuItem;

public class CommCarePreferences extends PreferenceActivity {
	
	private static final int CLEAR_USER_DATA = Menu.FIRST;
	private static final int ABOUT_COMMCARE = Menu.FIRST + 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.server_preferences);
        setTitle("CommCare" + " > " + "Application Preferences");
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, CLEAR_USER_DATA, 0, "Clear User Data").setIcon(
                android.R.drawable.ic_menu_delete);
        menu.add(0, ABOUT_COMMCARE, 1, "About CommCare").setIcon(
                android.R.drawable.ic_menu_help);
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
        }
        return super.onOptionsItemSelected(item);
    }

}
