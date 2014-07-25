package org.commcare.android.adapters;

import java.util.List;

import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.dalvik.R;
import org.commcare.dalvik.activities.AppManagerActivity;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class AppManagerAdapter extends ArrayAdapter<ApplicationRecord> {
	
	private AppManagerActivity context;

	public AppManagerAdapter(Context context, int resource, List<ApplicationRecord> objects) {
		super(context, resource, objects);
		this.context = (AppManagerActivity) context;
		// TODO Auto-generated constructor stub
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		View v = convertView;
		if (v == null) {
			v = View.inflate(context, R.layout.single_app_view, null);
		} 
		//Set all attributes of this view according to the app being displayed here
		ApplicationRecord toDisplay = context.getAppAtIndex(position);
		TextView appName = (TextView) v.findViewById(R.id.app_name);
		appName.setText(toDisplay.getDisplayName());
		ImageView flag = (ImageView) v.findViewById(R.id.resources_flag);
		if (toDisplay.resourcesValidated()) {
			flag.setVisibility(View.INVISIBLE);
		}
		else {
			flag.setVisibility(View.VISIBLE);
		}
		return v;
		
	}

}
