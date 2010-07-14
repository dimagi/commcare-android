/**
 * 
 */
package org.commcare.android.activities;

import java.util.Date;

import org.commcare.android.R;
import org.commcare.android.util.DotsData;
import org.commcare.android.util.DotsEditListener;
import org.commcare.android.util.GestureDetector;
import org.commcare.android.util.DotsData.DotsDay;
import org.commcare.android.view.DotsDetailView;
import org.commcare.android.view.DotsHomeView;
import org.javarosa.core.model.utils.DateUtils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.ScaleAnimation;
import android.view.animation.Animation.AnimationListener;
import android.view.inputmethod.InputMethodManager;

/**
 * @author ctsims
 *
 */
public class DotsEntryActivity extends Activity implements DotsEditListener, AnimationListener {
	
	private DotsData dotsData;
	
	enum AnimationType {
		zoomin,
		zoomout,
		fade,
		right,
		left
	}
	
	private static final String DOTS_DATA = "odk_intent_data";
	private static final String DOTS_EDITING = "dots_editing";
	private static final String DOTS_DAY = "dots_day";
	private static final String DOTS_OFFSET = "dots_offset";
	private static final String CURRENT_FOCUS = "dots_focus";
	private static final String DOTS_BOX_INDICES = "dots_indices";
	
	private int editing = -1;
	private int[] editingBoxes = null;
	private int offset;
	private DotsDay d;
	private DotsDetailView ddv;
	private GestureDetector mGestureDetector;
	
	int zX = -1;
	int zY = -1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        if(savedInstanceState != null) {
        	dotsData = DotsData.DeserializeDotsData(savedInstanceState.getString(DOTS_DATA));
        	editing = savedInstanceState.getInt(DOTS_EDITING);
        	offset = savedInstanceState.getInt(DOTS_OFFSET);
        	if(editing != -1) {
        		editingBoxes = savedInstanceState.getIntArray(DOTS_BOX_INDICES);
        		d = DotsDay.deserialize(savedInstanceState.getString(DOTS_DAY));
        	}
        } else {
	        String data = getIntent().getStringExtra(DOTS_DATA);
	        
	        Date anchorDate = new Date();
        	String anchor = getIntent().getStringExtra("anchor");
        	if(anchor != null) {
        		anchorDate = DateUtils.parseDate(anchor);
        	}
	        
	        if(data != null) {
	        	dotsData = DotsData.DeserializeDotsData(data);
	        	dotsData.recenter(anchorDate);
	        } else {
	        	String regimen = getIntent().getStringExtra("regimen");
	        	int regType = Integer.parseInt(regimen);
	        	dotsData = DotsData.CreateDotsData(regType, anchorDate);
	        }
	
	        offset = 0;
	        
	        showView(home(), AnimationType.fade);
        }
        setTitle(getString(R.string.app_name) + " > " + " DOTS");
        mGestureDetector = new GestureDetector();
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
        outState.putInt(DOTS_OFFSET, offset);
        if(editing != -1) {
        	outState.putIntArray(DOTS_BOX_INDICES, editingBoxes);
        	outState.putString(DOTS_DAY, ddv.getDay().serialize());
        }
    }
    
    private View home() {
    	return new DotsHomeView(this, dotsData, this, offset);
    }
    
    
	public void dayEdited(int i, DotsDay day) {
		dotsData.days()[i] = day;
		editing = -1;
		showView(home(), AnimationType.zoomout);
		zX = -1;
		zY = -1;
	}
	
	public void cancelDayEdit() {
		editing = -1;
		showView(home(), AnimationType.zoomout);
		zX = -1;
		zY = -1;
	}

	public void doneWithWeek() {
		Intent i = new Intent(this.getIntent());
        i.putExtra(DOTS_DATA, dotsData.SerializeDotsData());
        setResult(RESULT_OK, i);
        finish();
	}

	public void editDotsDay(int i, Rect rect, int[] boxes) {
		zX = rect.centerX();
		zY = rect.centerY();
		
		edit(i, dotsData.days()[i], AnimationType.zoomin, boxes);
	}
	
	private void edit(int i, DotsDay day, AnimationType anim, int[] boxes) {
		editing = i;
		ddv = new DotsDetailView();
		View view = ddv.LoadDotsDetailView(this, day, i, DateUtils.dateAdd(dotsData.anchor(),  i - dotsData.days().length + 1), boxes, this);
		showView(view, anim);
	}
	
	Animation mInAnimation;
	Animation mOutAnimation;
	View mCurrentView;
	View mNextView;
	
	private void showView(View next, AnimationType anim) {
		
		
        switch (anim) {
	        case zoomin:
	            mInAnimation = AnimationUtils.loadAnimation(this, R.anim.zoom_in);
	            if(zX != -1 && zY != -1) {
	            	long duration = mInAnimation.getDuration();
	            	mInAnimation = new ScaleAnimation(0.0f, 1.0f, 0.0f, 1.0f, zX, zY);
	            	mInAnimation.setDuration(duration);
	            }
	            mOutAnimation= AnimationUtils.loadAnimation(this, R.anim.fade_out);
	            break;
	        case zoomout:
	        	mInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in_delay);
	            mOutAnimation = AnimationUtils.loadAnimation(this, R.anim.zoom_out);
	            if(zX != -1 && zY != -1) {
	            	long duration = mOutAnimation.getDuration();
	            	mOutAnimation = new ScaleAnimation(1.0f, 0.0f, 1.0f, 0.0f, zX, zY);
	            	mOutAnimation.setDuration(duration);
	            }
	            break;
	        case fade:
	            mInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in);
	            mOutAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_out);
	            break;
	        case left:
	            mInAnimation = AnimationUtils.loadAnimation(this, R.anim.push_left_in);
	            mOutAnimation = AnimationUtils.loadAnimation(this, R.anim.push_left_out);
	            break;
	        case right:
	            mInAnimation = AnimationUtils.loadAnimation(this, R.anim.push_right_in);
	            mOutAnimation = AnimationUtils.loadAnimation(this, R.anim.push_right_out);
	            break;

	    }
	
	    if (mCurrentView != null && mOutAnimation != null) {
	        mCurrentView.startAnimation(mOutAnimation);
	        //mRelativeLayout.removeView(mCurrentView);
	    }
	
	    if(mInAnimation != null) {
	    	mInAnimation.setAnimationListener(this);
	    }
	
	    mCurrentView = next;
	    this.setContentView(next);
	
	    if(mInAnimation != null) {
		    mCurrentView.startAnimation(mInAnimation);
		        InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
		        inputManager.hideSoftInputFromWindow(mCurrentView.getWindowToken(), 0);
	    }
	}

	@Override
	protected void onResume() {
		super.onResume();
    	if(editing == -1) {
    		showView(home(), AnimationType.fade);
    	} else {
    		edit(editing, d, AnimationType.fade, editingBoxes);
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

	public void onAnimationEnd(Animation animation) {
		mBeenSwiped = false;
	}

	public void onAnimationRepeat(Animation animation) {
		// TODO Auto-generated method stub
		
	}

	public void onAnimationStart(Animation animation) {
		// TODO Auto-generated method stub
		
	}

	boolean mBeenSwiped;
	
    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onTouchEvent(android.view.MotionEvent)
     */
    @Override
    public boolean onTouchEvent(MotionEvent motionEvent) {
        /*
         * constrain the user to only be able to swipe (that causes a view transition) once per
         * screen with the mBeenSwiped variable.
         */
        boolean handled = false;
        if (!mBeenSwiped) {
            switch (mGestureDetector.getGesture(motionEvent)) {
                case SWIPE_RIGHT:
                    handled = tryMove(1);
                    break;
                case SWIPE_LEFT:
                    handled = tryMove(-1);
                    break;
            }
        }
        return handled;
    }
    
    private boolean tryMove(int offsetChange) {
    	if(editing != -1) {
    		return false;
    	}
    	int maxOffset = (int)Math.ceil(this.dotsData.days().length / DotsHomeView.TABLE_LENGTH) - 1;
    	if(offset + offsetChange < 0 || offset + offsetChange > maxOffset) {
    		return false;
    	}
		mBeenSwiped = true;
		offset = offset + offsetChange;
		showView(home(), offsetChange > 0 ? AnimationType.right : AnimationType.left);
		return true;
    }

	public void shiftWeek(int delta) {
		tryMove(-delta);
	}
}
