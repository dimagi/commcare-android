package org.commcare.dalvik.activities;

import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;


public class AppManagerActivity extends Activity {
	
	public static final String KEY_LAUNCH_FROM_MANAGER = "from_manager";
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.app_manager);
		ListView lv = (ListView) findViewById(R.id.apps_list_view);
		lv.setAdapter(new ArrayAdapter<ApplicationRecord>(this, 
				android.R.layout.simple_list_item_1, appRecordArray()));
	}
	
	private ApplicationRecord[] appRecordArray() {
		SqlStorage<ApplicationRecord> appList = CommCareApplication._().getInstalledAppRecords();
		ApplicationRecord[] appArray = new ApplicationRecord[appList.getNumRecords()];
		int index = 0;
		for (ApplicationRecord r : appList) {
			appArray[index++] = r;
		}
		return appArray;
	}
	
	public ApplicationRecord getAppAtIndex(int index) {
		ApplicationRecord[] currentApps = appRecordArray();
		if (index < 0 || index >= currentApps.length) {
			System.out.println("WARNING: attempting to get ApplicationRecord from ManagerActivity at invalid index");
			return null;
		} else return currentApps[index];
	}
	
	public void installAppClicked(View v) {
		Intent i = new Intent(getApplicationContext(), CommCareSetupActivity.class);
		i.putExtra(KEY_LAUNCH_FROM_MANAGER, true);
	    this.startActivityForResult(i, CommCareHomeActivity.INIT_APP);
	}
	
	public void uninstallSelectedApp(View v) {
		
	}
	
	public void archiveSelectedApp(View v) {
		
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		switch(requestCode) {
		case CommCareHomeActivity.INIT_APP:
			if (resultCode == RESULT_OK) {
				Toast.makeText(this, "App installed successfully", Toast.LENGTH_LONG).show();
			}
		}
	}

}
