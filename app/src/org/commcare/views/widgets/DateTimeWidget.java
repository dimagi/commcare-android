package org.commcare.views.widgets;

import android.content.Context;
import android.os.Build;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.inputmethod.InputMethodManager;
import android.widget.CalendarView;
import android.widget.DatePicker;
import android.widget.TimePicker;
import android.widget.TimePicker.OnTimeChangedListener;

import org.commcare.dalvik.R;
import org.javarosa.core.model.data.DateTimeData;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.form.api.FormEntryPrompt;
import org.joda.time.DateTime;

import java.util.Calendar;
import java.util.Date;

/**
 * Displays a DatePicker widget. DateWidget handles leap years and does not allow dates that do not
 * exist.
 *
 * @author Carl Hartung (carlhartung@gmail.com)
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */
public class DateTimeWidget extends QuestionWidget implements OnTimeChangedListener {

    private final DatePicker mDatePicker;
    private final TimePicker mTimePicker;
    private final DatePicker.OnDateChangedListener mDateListener;

    public DateTimeWidget(Context context, FormEntryPrompt prompt) {
        super(context, prompt);

        mDatePicker = (DatePicker)LayoutInflater.from(getContext()).inflate(R.layout.date_widget, this, false);
        mDatePicker.setFocusable(!prompt.isReadOnly());
        mDatePicker.setEnabled(!prompt.isReadOnly());
        mDatePicker.setCalendarViewShown(true);
        updateCalendarViewHeight();

        mTimePicker = (TimePicker)LayoutInflater.from(getContext()).inflate(R.layout.time_widget, this, false);
        mTimePicker.setFocusable(!prompt.isReadOnly());
        mTimePicker.setEnabled(!prompt.isReadOnly());
        mTimePicker.setPadding(0, 20, 0, 0);
        mTimePicker.setOnTimeChangedListener(this);
        mTimePicker.setSaveFromParentEnabled(false);
        mTimePicker.setSaveEnabled(true);

        String clockType =
                android.provider.Settings.System.getString(context.getContentResolver(),
                        android.provider.Settings.System.TIME_12_24);
        if (clockType == null || clockType.equalsIgnoreCase("24")) {
            mTimePicker.setIs24HourView(true);
        }

        mDateListener = (view, year, month, day) -> {
            if (mPrompt.isReadOnly()) {
                setAnswer();
            } else {
                // handle leap years and number of days in month
                // TODO
                // http://code.google.com/p/android/issues/detail?id=2081
                Calendar c = Calendar.getInstance();
                c.set(year, month, 1);
                int max = c.getActualMaximum(Calendar.DAY_OF_MONTH);
                if (day > max) {
                    //If the day has fallen out of spec, set it to the correct max
                    mDatePicker.updateDate(year, month, max);
                } else {
                    if (!(mDatePicker.getDayOfMonth() == day && mDatePicker.getMonth() == month && mDatePicker.getYear() == year)) {
                        //CTS: No reason to change the day if it's already correct?
                        mDatePicker.updateDate(year, month, day);
                    }
                }
            }
            widgetEntryChanged();
        };

        // If there's an answer, use it.
        setAnswer();

        setGravity(Gravity.LEFT);
        addView(mDatePicker);
        addView(mTimePicker);

    }

    /**
     * CalendarView bottom line gets cut off in appcompat theme because it's height is fixed here:
     * https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/res/res/layout/date_picker_legacy.xml#L78
     * This workaround updates the calendarview height to wrap content.
     */
    private void updateCalendarViewHeight() {
        CalendarView calendarView = mDatePicker.getCalendarView();
        LayoutParams params = (LayoutParams) calendarView.getLayoutParams();
        params.height = LayoutParams.WRAP_CONTENT;
        calendarView.setLayoutParams(params);
    }

    public void setAnswer() {

        if (mPrompt.getAnswerValue() != null) {

            DateTime ldt =
                    new DateTime(
                            ((Date)getCurrentAnswer().getValue()).getTime());
            mDatePicker.init(ldt.getYear(), ldt.getMonthOfYear() - 1, ldt.getDayOfMonth(),
                    mDateListener);

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

        widgetEntryChanged();
    }


    /**
     * Resets date to today.
     */
    @Override
    public void clearAnswer() {
        DateTime ldt = new DateTime();
        mDatePicker.init(ldt.getYear(), ldt.getMonthOfYear() - 1, ldt.getDayOfMonth(),
                mDateListener);
        mTimePicker.setCurrentHour(ldt.getHourOfDay());
        mTimePicker.setCurrentMinute(ldt.getMinuteOfHour());
    }


    @Override
    public IAnswerData getAnswer() {
        mDatePicker.clearFocus();
        mTimePicker.clearFocus();
        DateTime ldt =
                new DateTime(mDatePicker.getYear(), mDatePicker.getMonth() + 1,
                        mDatePicker.getDayOfMonth(), mTimePicker.getCurrentHour(),
                        mTimePicker.getCurrentMinute(), 0);
        //DateTime utc = ldt.withZone(DateTimeZone.forID("UTC"));
        return new DateTimeData(ldt.toDate());
    }


    @Override
    public void setFocus(Context context) {
        // Hide the soft keyboard if it's showing.
        InputMethodManager inputManager =
                (InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.hideSoftInputFromWindow(this.getWindowToken(), 0);
    }


    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        mDatePicker.setOnLongClickListener(l);
        mTimePicker.setOnLongClickListener(l);
    }

    @Override
    public void unsetListeners() {
        super.unsetListeners();

        mDatePicker.setOnLongClickListener(null);
        mTimePicker.setOnLongClickListener(null);
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        mDatePicker.cancelLongPress();
        mTimePicker.cancelLongPress();
    }

    @Override
    public void onTimeChanged(TimePicker view, int hourOfDay, int minute) {
        widgetEntryChanged();

    }

}
