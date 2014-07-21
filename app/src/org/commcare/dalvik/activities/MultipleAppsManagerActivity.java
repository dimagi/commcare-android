package org.commcare.dalvik.activities;

import org.commcare.dalvik.R;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

public class MultipleAppsManagerActivity extends Activity {
	
	public static final String KEY_LAUNCH_FROM_MANAGER = "from_manager";
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.app_manager);
	}
	
	public void installAppClicked(View v) {
		Intent i = new Intent(getApplicationContext(), CommCareSetupActivity.class);
		i.putExtra(KEY_LAUNCH_FROM_MANAGER, true);
	    this.startActivityForResult(i, CommCareHomeActivity.INIT_APP);
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		System.out.println("RETURNED to MultipleAppsManagerActivity");
	}
	
	public void uninstallAppClicked(View v) {
		
	}
	
	

}
