package org.odk.collect.android.widgets;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.view.Gravity;
import android.view.inputmethod.InputMethodManager;
import android.widget.TimePicker;
import android.widget.TimePicker.OnTimeChangedListener;

import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.TimeData;
import org.javarosa.form.api.FormEntryPrompt;
import org.joda.time.DateTime;

import java.util.Date;

/**
 * Displays a TimePicker widget.
 * 
 * @author Carl Hartung (carlhartung@gmail.com)
 */
public class TimeWidget extends QuestionWidget implements OnTimeChangedListener {

    private TimePicker mTimePicker;
    private static final String TAG = TimeWidget.class.getSimpleName();


    public TimeWidget(Context context, final FormEntryPrompt prompt) {
        super(context, prompt);

        mTimePicker = new TimePicker(getContext());
        mTimePicker.setFocusable(!prompt.isReadOnly());
        mTimePicker.setEnabled(!prompt.isReadOnly());
        mTimePicker.setOnTimeChangedListener(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            mTimePicker.setSaveFromParentEnabled(false);
            mTimePicker.setSaveEnabled(true);
        }

        String clockType =
            android.provider.Settings.System.getString(context.getContentResolver(),
                android.provider.Settings.System.TIME_12_24);
        if (clockType == null || clockType.equalsIgnoreCase("24")) {
            mTimePicker.setIs24HourView(true);
        }
        
        // If there's an answer, use it.
        setAnswer();

        setGravity(Gravity.LEFT);
        addView(mTimePicker);

    }
    
    public void setAnswer() {
        // If there's an answer, use it.
        if (mPrompt.getAnswerValue() != null) {

            // create a new date time from date object using default time zone
            DateTime ldt =
                new DateTime(((Date) ((TimeData) getCurrentAnswer()).getValue()).getTime());
            Log.d(TAG, "retrieving:" + ldt);

            int altVal = ldt.getHourOfDay() == 1 ? 2 : 1;
            mTimePicker.setCurrentHour(altVal);
            mTimePicker.setCurrentHour(ldt.getHourOfDay());
            
            altVal = ldt.getMinuteOfHour() == 1 ? 2 : 1;
            mTimePicker.setCurrentMinute(altVal);
            mTimePicker.setCurrentMinute(ldt.getMinuteOfHour());
        } else {
            // create time widget with current time as of right now
            clearAnswer();
        }
    }


    /**
     * Resets time to today.
     */
    @Override
    public void clearAnswer() {
        DateTime ldt = new DateTime();
        mTimePicker.setCurrentHour(ldt.getHourOfDay());
        mTimePicker.setCurrentMinute(ldt.getMinuteOfHour());
    }

    @Override
    public IAnswerData getAnswer() {
        mTimePicker.clearFocus();
        // use picker time, convert to epoch date (for TZ clarity), store as utc
        
        //CTS - 8/22/2021 : Adjusted this to store as the time past the Epoch, since the app otherwise can have conflicting
        //timezones with the JavaRosa Time storage, which is always stored against the epoch.
        DateTime ldt =
            (new DateTime(0)).withTime(mTimePicker.getCurrentHour(), mTimePicker.getCurrentMinute(),
                0, 0);
        //DateTime utc = ldt.withZone(DateTimeZone.forID("UTC"));
        Log.d(TAG, "storing:" + ldt);
        return new TimeData(ldt.toDate());
    }


    @Override
    public void setFocus(Context context) {
        // Hide the soft keyboard if it's showing.
        InputMethodManager inputManager =
            (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.hideSoftInputFromWindow(this.getWindowToken(), 0);
    }


    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        mTimePicker.setOnLongClickListener(l);
    }


    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        mTimePicker.cancelLongPress();
    }

    @Override
    public void onTimeChanged(TimePicker view, int hourOfDay, int minute) {
        this.widgetEntryChanged();
    }
}
