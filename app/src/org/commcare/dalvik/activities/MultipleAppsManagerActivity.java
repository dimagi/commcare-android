package org.commcare.dalvik.activities;

import java.util.ArrayList;

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


public class MultipleAppsManagerActivity extends Activity {
	
	public static final String KEY_LAUNCH_FROM_MANAGER = "from_manager";
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.app_manager);
		ListView lv = (ListView) findViewById(R.id.apps_list_view);
		lv.setAdapter(new ArrayAdapter<ApplicationRecord>(this, 
				android.R.layout.simple_list_item_1, appRecordList()));
	}
	
	private ArrayList<ApplicationRecord> appRecordList() {
		SqlStorage<ApplicationRecord> storageList = CommCareApplication._().getInstalledAppRecords();
		ArrayList<ApplicationRecord> toReturn = new ArrayList<ApplicationRecord>();
		for (ApplicationRecord r : storageList) {
			toReturn.add(r);
		}
		return toReturn;
	}
	
	public void installAppClicked(View v) {
		Intent i = new Intent(getApplicationContext(), CommCareSetupActivity.class);
		i.putExtra(KEY_LAUNCH_FROM_MANAGER, true);
	    this.startActivityForResult(i, CommCareHomeActivity.INIT_APP);
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
