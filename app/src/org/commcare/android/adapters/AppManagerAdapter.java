package org.commcare.android.adapters;

import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.dalvik.R;
import org.commcare.dalvik.activities.AppManagerActivity;
import org.commcare.dalvik.application.CommCareApplication;

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
        ApplicationRecord toDisplay = CommCareApplication._().getAppAtIndex(position);
        TextView appName = (TextView) v.findViewById(R.id.app_name);
        appName.setText(toDisplay.getDisplayName());
        v.setContentDescription(toDisplay.getUniqueId());
        return v;
	}

}
