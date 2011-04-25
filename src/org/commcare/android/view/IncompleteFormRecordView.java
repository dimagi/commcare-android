/**
 * 
 */
package org.commcare.android.view;

import java.io.File;
import java.text.DateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.NoSuchElementException;

import org.commcare.android.R;
import org.commcare.android.application.CommCareApplication;
import org.commcare.android.database.SqlIndexedStorageUtility;
import org.commcare.android.models.Case;
import org.commcare.android.models.FormRecord;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.suite.model.Entry;
import org.commcare.suite.model.Suite;
import org.commcare.suite.model.Text;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * @author ctsims
 *
 */
public class IncompleteFormRecordView extends LinearLayout {
	
	public TextView mPrimaryTextView;
	public TextView mLowerTextView;
	public TextView mRightTextView;
	
	Hashtable<String,Text> names;
	Date start;

	public IncompleteFormRecordView(Context context, AndroidCommCarePlatform platform) {
		super(context);
		
		ViewGroup vg = (ViewGroup)View.inflate(context, R.layout.formrecordview, null);
		
		names = new Hashtable<String,Text>();
		for(Suite s : platform.getInstalledSuites()) {
			for(Enumeration en = s.getEntries().elements(); en.hasMoreElements() ;) {
				Entry entry = (Entry)en.nextElement();
				if(entry.getXFormNamespace() == null) {
					//This is a <view>, not an <entry>, so
					//it can't define a form
				} else {
					names.put(entry.getXFormNamespace(),entry.getText());
				}
			}
		}
		
        mPrimaryTextView = (TextView)vg.findViewById(R.id.formrecord_txt_main);
        mLowerTextView = (TextView)vg.findViewById(R.id.formrecord_txt_btm);
        mRightTextView = (TextView)vg.findViewById(R.id.formrecord_txt_right);
        
        mPrimaryTextView.setTextAppearance(context, android.R.style.TextAppearance_Large);
        
        
        LayoutParams l =new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
        addView(vg, l);

        start = new Date();
	}

	public void setParams(AndroidCommCarePlatform platform, FormRecord record) throws SessionUnavailableException{
		Text name = names.get(record.getFormNamespace());
		mPrimaryTextView.setText(name.evaluate());
		
		if(record.getEntityId() != null) {
			SqlIndexedStorageUtility<Case> storage =  CommCareApplication._().getStorage(Case.STORAGE_KEY, Case.class);
			try {
				Case c = storage.getRecordForValue(Case.META_CASE_ID, record.getCaseId());
				
				//TODO : I am a bad person, _Fix this_
				if(c.getProperty("initials") != null) {
					mLowerTextView.setText((String)c.getProperty("initials"));
				} else{
					mLowerTextView.setText(c.getName());
				}
				
			} catch(NoSuchElementException nsee) {
				//Not sure what to do about that one.
			}
		}
		
		//be careful here...
		File f = new File(record.getPath());
		if(f.exists()) {
			mRightTextView.setText(DateUtils.formatSameDayTime(f.lastModified(), start.getTime(), DateFormat.DEFAULT, DateFormat.DEFAULT));
		} else {
			mRightTextView.setText("Never");
		}
	}
}
