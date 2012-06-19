/**
 * 
 */
package org.commcare.android.view;

import java.text.DateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;

import org.commcare.android.R;
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

	public void setParams(FormRecord record, String dataTitle, Long timestamp) throws SessionUnavailableException{
		Text name = names.get(record.getFormNamespace());
		mPrimaryTextView.setText(name.evaluate());
		
		if(dataTitle != null) {
			mLowerTextView.setText(dataTitle); 
		}
				
		//be careful here...
		if(timestamp != 0) {
			mRightTextView.setText(DateUtils.formatSameDayTime(timestamp, start.getTime(), DateFormat.DEFAULT, DateFormat.DEFAULT));
		} else {
			mRightTextView.setText("Never");
		}
	}
}
