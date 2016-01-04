package org.commcare.dalvik.activities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.view.animation.ScaleAnimation;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import org.commcare.android.util.DotsData;
import org.commcare.android.util.DotsData.DotsBox;
import org.commcare.android.util.DotsData.DotsDay;
import org.commcare.android.util.DotsEditListener;
import org.commcare.android.util.GestureDetector;
import org.commcare.android.view.DotsDetailView;
import org.commcare.android.view.DotsHomeView;
import org.commcare.dalvik.R;
import org.javarosa.core.model.utils.DateUtils;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.Calendar;
import java.util.Date;

/**
 * @author ctsims
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
    private static final String DOTS_BOX = "dots_box";
    private static final String CURRENT_FOCUS = "dots_focus";

    private int curday = -1;
    private int curdose = -1;
    private DotsDay d;
    private DotsDetailView ddv;
    private GestureDetector mGestureDetector;
    
    int zX = -1;
    int zY = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        if(savedInstanceState == null) {
            String regimen = getIntent().getStringExtra("regimen");
            int[] regimens = new int[2];
            
            try {
                JSONArray array = new JSONArray(regimen);
                for(int i = 0; i < array.length(); ++i) {
                    regimens[i] = array.getInt(i);
                }
            }
            catch(JSONException e) {
                throw new RuntimeException(e);
            }
            
            String data = getIntent().getStringExtra(DOTS_DATA);
            boolean populateAnchor = false;
            
            Date anchorDate = new Date();
            String anchor = getIntent().getStringExtra("anchor");
            if(anchor != null) {
                anchorDate = DateUtils.parseDate(anchor);
            }
            
            if(data != null) {
                dotsData = DotsData.DeserializeDotsData(data);
                
                //If the regimen is new for today, or if the current values for the last day are all 
                //the default
                if(dotsData.recenter(regimens, anchorDate) != 0 || 
                   dotsData.days()[dotsData.days().length -1].isDefault()) {
                    populateAnchor = true;
                }
            } else {
                dotsData = DotsData.CreateDotsData(regimens, anchorDate);
                populateAnchor = true;
            }

            String currentDoseCheck = getIntent().getStringExtra("currentdose");
            if(populateAnchor && currentDoseCheck != null) {
                int box = Integer.parseInt(getIntent().getStringExtra("currentbox"));
                
                // now fill in the box specified
                DotsDay day = dotsData.days()[dotsData.days().length - 1];
                DotsBox[][] boxes = day.boxes();
                boxes[0][box] = boxes[0][box].update(DotsBox.deserialize(currentDoseCheck));
                
                dotsData.days()[dotsData.days().length - 1] = new DotsDay(boxes);
            }
            
            String currentDoseCheckTwo = getIntent().getStringExtra("currentdosetwo");
            if(populateAnchor && currentDoseCheckTwo != null && !"".equals(currentDoseCheckTwo)) {
                int box = Integer.parseInt(getIntent().getStringExtra("currentboxtwo"));
                
                // now fill in the box specified
                DotsDay day = dotsData.days()[dotsData.days().length - 1];
                DotsBox[][] boxes = day.boxes();
                boxes[1][box] = boxes[1][box].update(DotsBox.deserialize(currentDoseCheckTwo));
                
                dotsData.days()[dotsData.days().length - 1] = new DotsDay(boxes);
            }
            
            showView(home(), AnimationType.fade);
        }
        setTitle(getString(R.string.application_name) + " > " + " DOTS");
        mGestureDetector = new GestureDetector();
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(DOTS_DATA,dotsData.SerializeDotsData());
        outState.putInt(DOTS_EDITING, curday);
        outState.putInt(DOTS_BOX, curdose);
        if(curdose != -1) {
            outState.putString(DOTS_DAY, ddv.getDay().serialize().toString());
        }
    }
    
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        dotsData = DotsData.DeserializeDotsData(savedInstanceState.getString(DOTS_DATA));
        curday = savedInstanceState.getInt(DOTS_EDITING);
        curdose = savedInstanceState.getInt(DOTS_BOX);
        if(curdose != -1) {
            d = DotsDay.deserialize(savedInstanceState.getString(DOTS_DAY));
        }
    }
    
    private DotsHomeView home() {
        setTitle(getString(R.string.application_name) + " > " + " DOTS");
        return new DotsHomeView(this, dotsData, this);
    }
    
    
    public void dayEdited(int i, DotsDay day) {
        dotsData.days()[i] = day;
        curdose = -1;
        showView(curday(), AnimationType.fade);
    }
    
    public void cancelDoseEdit() {
        curdose = -1;
        showView(curday(), AnimationType.fade);
    }

    public void doneWithDOTS() {
        Intent i = new Intent(this.getIntent());
        i.putExtra(DOTS_DATA, dotsData.SerializeDotsData());
        setResult(RESULT_OK, i);
        finish();
    }

    public void editDotsDay(int i, Rect rect) {
        zX = rect.centerX();
        zY = rect.centerY();
        
        edit(i, AnimationType.zoomin);
    }
    
    private void edit(int i, AnimationType anim) {
        curday = i;
        //ddv = new DotsDetailView();
        Date date = DateUtils.dateAdd(dotsData.anchor(),  i - dotsData.days().length + 1);
        //View view = ddv.LoadDotsDetailView(this, day, i, date, boxes, this);
        View view = curday();
        
        //DateFormat df = DateFormat.getDateFormat(this);
        
        setTitle(getString(R.string.application_name) + " > " + "DOTS Details for " + DateFormat.format("MM/dd/yyyy", date));
        showView(view, anim);
    }
    
    Animation mInAnimation;
    Animation mOutAnimation;
    View mCurrentView;

    private void showView(View next, AnimationType anim) {
        showView(next,anim, null);
    }
    
    private void showView(View next, AnimationType anim, View target) {
        if(target != null) {
            next.buildDrawingCache();
            Rect targetRect = new Rect(0,0,target.getWidth(), target.getHeight());
            ((ViewGroup)next).offsetDescendantRectToMyCoords(target, targetRect);
            zX = targetRect.centerX();
            zY = targetRect.centerY();
            next.destroyDrawingCache();
        }
        
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
        if(curday == -1) {
            showView(home(), AnimationType.fade);
        } else if(curdose == -1) {
            edit(curday, AnimationType.fade);
        } else {
            editDose(curday, curdose, d, null);
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        //Make sure all data in UI's is in memory
        if(curdose != -1) {
            d = ddv.getDay();
        }
    }
    
    public void cancelDayEdit(int editing) {
        curday = -1;
        d= null;
        DotsHomeView home = home();
        
        showView(home, AnimationType.zoomout);
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                if(curday != -1) {
                    if(curdose != -1) {
                        cancelDoseEdit();
                        return true;
                    } else {
                        cancelDayEdit(curday);
                        return true;
                    }
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
                    handled = tryMove(-1);
                    break;
                case SWIPE_LEFT:
                    handled = tryMove(1);
                    break;
            }
        }
        return handled;
    }
    
    private boolean tryMove(int offsetChange) {
        if(curdose != -1 || curday == -1) {
            return false;
        }
        int maxOffset = this.dotsData.days().length - 1;
        if(curday + offsetChange < 0 || curday + offsetChange > maxOffset) {
            return false;
        }
        mBeenSwiped = true;
        curday = curday + offsetChange;
        edit(curday, offsetChange > 0 ? AnimationType.left : AnimationType.right);
        return true;
    }

    public void shiftDay(int delta) {
        tryMove(-delta);
    }
    

    private View curday() {
        final ViewGroup dayView = (ViewGroup)View.inflate(this, R.layout.dotsdoses, null);
        TableRow[] rows = new TableRow[4];
        rows[0] = (TableRow)dayView.findViewById(R.id.dots_dose_one);
        rows[1] = (TableRow)dayView.findViewById(R.id.dots_dose_two);
        //rows[2] = (TableRow)dayView.findViewById(R.id.dots_dose_three);
        //rows[3] = (TableRow)dayView.findViewById(R.id.dots_dose_four);
        
        DotsDay day = dotsData.days()[curday];
        
        int disLen = day.getMaxReg();
        for(int i = 0 ; i < disLen ; ++i ) {
            final int regIndex = i;
            View doseView = getDoseView(day, curday, i);
            rows[i % 2 == 0 ? 0 : 1].addView(doseView == null ? new ImageView(this): doseView);
            if(doseView == null) { 
                continue;
            }
            
            doseView.setOnClickListener(new OnClickListener() {

                public void onClick(View v) {
                    Rect hitRect = new Rect();
                    if(v.getParent() instanceof View) {
                        v.getHitRect(hitRect);
                        View parent = (View)v.getParent();
                        dayView.offsetDescendantRectToMyCoords(parent, hitRect);
                        DotsEntryActivity.this.editDose(curday, regIndex, dotsData.days()[curday], hitRect);
                    } else{
                        hitRect = new Rect(0,0,v.getWidth(), v.getHeight());
                        dayView.offsetDescendantRectToMyCoords(v, hitRect);
                        DotsEntryActivity.this.editDose(curday, regIndex, dotsData.days()[curday], hitRect);
                    }
                }
                
            });
        }
        
        View next = dayView.findViewById(R.id.btn_doses_next);
        if(curday == dotsData.days().length - 1) {
            next.setVisibility(View.INVISIBLE);
        } else{
            next.setOnClickListener(new OnClickListener() {

                public void onClick(View v) {
                    tryMove(1);
                }
                
            });
        }
        View prev = dayView.findViewById(R.id.btn_doses_prev);
        if(curday == 0) {
            prev.setVisibility(View.INVISIBLE);
        } else{
            prev.setOnClickListener(new OnClickListener() {

                public void onClick(View v) {
                    tryMove(-1);
                }
                
            });
        }
        
        Button done = (Button)dayView.findViewById(R.id.btn_dots_doses_done);
        done.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {
                DotsEntryActivity.this.cancelDayEdit(curday);
            }
            
        });
        return dayView;
    }
    
    private ViewGroup getDoseView(DotsDay d, int dayIndex, int regimenIndex) {
        
        int[] boxes = d.getRegIndexes(regimenIndex);
        boolean empty = true;
        for(int i : boxes ){
            if(i!= -1) {
                empty = false;
            }
        }
        if(empty) { return null; }

        Calendar c = Calendar.getInstance();
        c.setTime(dotsData.anchor());
        c.roll(Calendar.DATE, dotsData.days().length - dayIndex + 1);
        
        ViewGroup doseView = (ViewGroup)View.inflate(this, R.layout.dotsdose, null);
        TextView dosename = (TextView)doseView.findViewById(R.id.text_dosename);
        TableLayout table = (TableLayout)doseView.findViewById(R.id.dose_table);
        table.setPadding(0,0,2,0);
        table.setShrinkAllColumns(true);
        
        TableRow doses = (TableRow)table.findViewById(R.id.dose_status);
        TableRow selfReported = (TableRow)table.findViewById(R.id.self_report_row);
        
        dosename.setText(DotsDetailView.labels[d.getMaxReg() -1 ][regimenIndex]);
        
        doses.removeAllViews();
        
        for(int i = 0 ; i < boxes.length ; ++i) {
            if(boxes[i] == -1) {
                continue;
            }
            DotsBox box = d.boxes()[i][boxes[i]];
            ImageView status = new ImageView(this);
            status.setPadding(0,0,1,0);
            switch(box.status()) {
            case full:
                status.setImageResource(R.drawable.redx);
                break;
            case partial:
                status.setImageResource(R.drawable.blues);
                break;
            case empty:
                status.setImageResource(R.drawable.checkmark);
                break;
            case unchecked:
                status.setImageResource(R.drawable.blueq);
                break;
            }
                
            doses.addView(status);
            
            ImageView selfReport = new ImageView(this);
            selfReport.setPadding(0,3,1,0);
            switch(box.reportType()) {
            case direct:
                selfReport.setImageResource(R.drawable.eye);
                break;
            case pillbox:
                selfReport.setImageResource(R.drawable.pillbox);
                break;
            case self:
                selfReport.setImageResource(R.drawable.greencircle);
                break;
            }
            selfReported.addView(selfReport);
        }
        
        return doseView;
    }

    public void editDose(int dayIndex, int regimenIndex, DotsDay day, Rect hitRect) {
        curday = dayIndex;
        curdose = regimenIndex;
        Calendar c = Calendar.getInstance();
        c.setTime(dotsData.anchor());
        c.roll(Calendar.DATE, dotsData.days().length - dayIndex + 1);
        
        ddv = new DotsDetailView();
        showView(ddv.LoadDotsDetailView(this, day, dayIndex, c.getTime(), regimenIndex, this), AnimationType.fade);
    }
}
