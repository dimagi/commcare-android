/**
 * 
 */
package org.commcare.android.activities;

import java.util.Date;

import org.commcare.android.R;
import org.commcare.android.util.DotsData;
import org.commcare.android.util.DotsEditListener;
import org.commcare.android.util.DotsData.DotsDay;
import org.commcare.android.view.DotsDetailView;
import org.commcare.android.view.DotsHomeView;
import org.javarosa.core.model.utils.DateUtils;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;

/**
 * @author ctsims
 *
 */
public class DotsEntryActivity extends Activity implements DotsEditListener {
	
	private DotsData dotsData;
	
	private static final String DOTS_DATA = "odk_intent_data";
	private static final String DOTS_EDITING = "dots_editing";
	private static final String DOTS_DAY = "dots_day";
	private static final String CURRENT_FOCUS = "dots_focus";
	
	private int editing = -1;
	private DotsDay d;
	private DotsDetailView ddv;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        if(savedInstanceState != null) {
        	dotsData = DotsData.DeserializeDotsData(savedInstanceState.getString(DOTS_DATA));
        	editing = savedInstanceState.getInt(DOTS_EDITING);
        	if(editing != -1) {
        		d = DotsDay.deserialize(savedInstanceState.getString(DOTS_DAY));
        	}
        } else {
	        String data = getIntent().getStringExtra(DOTS_DATA);
	        
	        if(data != null) {
	        	dotsData = DotsData.DeserializeDotsData(data);
	        } else {
	        	String regimen = getIntent().getStringExtra("regimen");
	        	int regType = Integer.parseInt(regimen);
	        	dotsData = DotsData.CreateDotsData(regType, new Date());
	        }
	
	        setTitle(getString(R.string.app_name) + " > " + " DOTS");
	        
	        this.setContentView(new DotsHomeView(this, dotsData, this));
        }
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onSaveInstanceState(android.os.Bundle)
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(DOTS_DATA,dotsData.SerializeDotsData());
        outState.putInt(DOTS_EDITING, editing);
        if(editing != -1) {
        	outState.putString(DOTS_DAY, ddv.getDay().serialize());
        }
    }
    
    
	public void dayEdited(int i, DotsDay day) {
		dotsData.days()[i] = day;
		editing = -1;
		this.setContentView(new DotsHomeView(this, dotsData, this));
	}
	
	public void cancelDayEdit() {
		editing = -1;
		this.setContentView(new DotsHomeView(this, dotsData, this));
	}

	public void doneWithWeek() {
		Intent i = new Intent(this.getIntent());
        i.putExtra(DOTS_DATA, dotsData.SerializeDotsData());
        setResult(RESULT_OK, i);
        finish();
	}

	public void editDotsDay(int i) {
		edit(i, dotsData.days()[i]);
	}
	
	private void edit(int i, DotsDay day) {
		editing = i;
		ddv = new DotsDetailView();
		View view = ddv.LoadDotsDetailView(this, day, i, DateUtils.dateAdd(dotsData.anchor(),  i - dotsData.days().length), this);
		this.setContentView(view);
	}

	@Override
	protected void onResume() {
		super.onResume();
    	if(editing == -1) {
    		this.setContentView(new DotsHomeView(this, dotsData, this));
    	} else {
    		edit(editing, d);
    	}
	}
	
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                if(editing != -1) {
                	cancelDayEdit();
                	return true;
                }
                
        }
        return super.onKeyDown(keyCode, event);
    }

}
