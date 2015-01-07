package org.commcare.android.adapters;

import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.dalvik.R;
import org.commcare.dalvik.activities.AppManagerActivity;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;

public class AppManagerAdapter extends ArrayAdapter<ApplicationRecord> {
	
	private AppManagerActivity context;

	public AppManagerAdapter(Context context, int resource, ApplicationRecord[] objects) {
		super(context, resource, objects);
		this.context = (AppManagerActivity) context;
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView; 
        if (v == null) {
            v = View.inflate(context, R.layout.app_title_view, null);
        }
        ApplicationRecord toDisplay = context.getAppAtIndex(position);
        TextView appName = (TextView) v.findViewById(R.id.app_name);
        appName.setText(toDisplay.getDisplayName());
        v.setContentDescription(toDisplay.getUniqueId());
        return v;
	}

	/*@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		System.out.println("getView called");
		View v = convertView;
		if (v == null) {
			v = View.inflate(context, R.layout.single_app_view, null);
		} 
		//Set all attributes of this view according to the app being displayed here
		ApplicationRecord toDisplay = context.getAppAtIndex(position);
		TextView appName = (TextView) v.findViewById(R.id.app_name);
		String appUniqueId = toDisplay.getUniqueId();
		appName.setText(toDisplay.getDisplayName());
		
		//set content description field of all buttons to the unique id of the app they refer to
		Button uninstallButton = (Button) v.findViewById(R.id.uninstall_button);
		Button archiveButton = (Button) v.findViewById(R.id.archive_button);
		Button updateButton = (Button) v.findViewById(R.id.update_button);
		Button validateButton = (Button) v.findViewById(R.id.verify_button);
		uninstallButton.setContentDescription(appUniqueId);
		archiveButton.setContentDescription(appUniqueId);
		updateButton.setContentDescription(appUniqueId);
		validateButton.setContentDescription(appUniqueId);
		
		//validate button only clickable if resources are not yet validated
		if (toDisplay.resourcesValidated()) {
			validateButton.setEnabled(false);
		} else {
			validateButton.setEnabled(true);
		}
		//change text for archive button depending on archive status
		if (toDisplay.isArchived()) {
			System.out.println("AppManagerAdapter setting button to 'Unarchive'");
			archiveButton.setText("Unarchive");
		} else {
			archiveButton.setText("Archive");
			System.out.println("AppManagerAdapter setting button to 'Archive'");
		}
		return v;
	}*/

}
