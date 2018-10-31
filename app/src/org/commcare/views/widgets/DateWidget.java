package org.commcare.views.widgets;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.view.Gravity;
import android.view.inputmethod.InputMethodManager;
import android.widget.DatePicker;

import org.javarosa.core.model.data.DateData;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.form.api.FormEntryPrompt;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;

import java.util.Calendar;
import java.util.Date;

/**
 * Displays a DatePicker widget. DateWidget handles leap years and does not allow dates that do not
 * exist.
 *
 * Dates returned from the date widget should be considered uncontextualized by the current timezone.
 * This means that the date "2014-05-01" _always_ refers to "May 1st, 2014", not the specific point
 * in time "May 1st, 2014 in Boston, MA" which could refer to April 30th, 2014, or May 2, 2014
 *
 * @author Carl Hartung (carlhartung@gmail.com)
 * @author Yaw Anokwa (yanokwa@gmail.com)
 * @author csims@dimagi.com
 */
public class DateWidget extends QuestionWidget {

    private final DatePicker mDatePicker;
    private final DatePicker.OnDateChangedListener mDateListener;
    private boolean isFilled = false;

    @SuppressLint("NewApi")
    public DateWidget(Context context, FormEntryPrompt prompt) {
        super(context, prompt);

        resetIsFilled();

        mDatePicker = buildDatePicker(!prompt.isReadOnly());
        mDateListener = buildDateListener();

        setAnswer();

        setGravity(Gravity.LEFT);
        addView(mDatePicker);
    }

    private DatePicker buildDatePicker(boolean isQuestionWriteable) {
        DatePicker datePicker = new DatePicker(getContext());
        datePicker.setFocusable(isQuestionWriteable);
        datePicker.setEnabled(isQuestionWriteable);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            datePicker.setCalendarViewShown(false);
        }
        return datePicker;
    }

    private DatePicker.OnDateChangedListener buildDateListener() {
        return (view, year, month, day) -> {
            if (mPrompt.isReadOnly()) {
                setAnswer();
            } else {
                // TODO support dates <1900 >2100
                // handle leap years and number of days in month
                // http://code.google.com/p/android/issues/detail?id=2081
                Calendar c = Calendar.getInstance();
                c.set(year, month, 1);
                int max = c.getActualMaximum(Calendar.DAY_OF_MONTH);
                if (day > max) {
                    //If the day has fallen out of spec, set it to the correct max
                    mDatePicker.updateDate(year, month, max);
                } else {
                    if (!(mDatePicker.getDayOfMonth() == day && mDatePicker.getMonth() == month && mDatePicker.getYear() == year)) {
                        //CTS: No reason to change the day if it's already correct
                        mDatePicker.updateDate(year, month, day);
                    }
                }
            }

            isFilled = true;
            widgetEntryChangedDelayed();
        };
    }

    private void setAnswer() {
        if (getCurrentAnswer() != null) {

            //The incoming date is in Java Format, parsed from an ISO-8601 date.
            DateTime isoAnchoredDate =
                    new DateTime(((Date)getCurrentAnswer().getValue()).getTime());

            //The java date we loaded doesn't know how to communicate its timezone offsets to
            //Joda if the offset for the datetime represented differs from what it is currently.
            //This is the case, for instance, in historical dates where timezones offsets were
            //different. This method identifies what offset the Java date actually was using.
            DateTimeZone correctedAbsoluteOffset =
                    DateTimeZone.forOffsetMillis(-isoAnchoredDate.toDate().getTimezoneOffset() * 60 * 1000);

            //Then manually loads it back into a Joda datetime for manipulation
            DateTime adjustedDate = isoAnchoredDate.toDateTime(correctedAbsoluteOffset);


            mDatePicker.init(adjustedDate.getYear(), adjustedDate.getMonthOfYear() - 1, adjustedDate.getDayOfMonth(),
                    mDateListener);
            isFilled = true;
        } else {
            // create date widget with current time as of right now
            clearAnswer();
        }
    }

    /**
     * Resets date to today.
     */
    @Override
    public void clearAnswer() {
        DateTime ldt = new DateTime();
        mDatePicker.init(ldt.getYear(), ldt.getMonthOfYear() - 1, ldt.getDayOfMonth(),
                mDateListener);
        resetIsFilled();
    }

    private void resetIsFilled() {
        // if it's not editable, assume it to be filled.
        if (mPrompt.isReadOnly()) {
            isFilled = true;
        } else {
            isFilled = false;
        }
    }

    @Override
    public IAnswerData getAnswer() {
        if (isFilled) {
            mDatePicker.clearFocus();

            LocalDate ldt = new LocalDate(mDatePicker.getYear(), mDatePicker.getMonth() + 1,
                    mDatePicker.getDayOfMonth());
            return new DateData(ldt.toDate());
        }
        return null;
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
    }

    @Override
    public void unsetListeners() {
        super.unsetListeners();

        mDatePicker.setOnLongClickListener(null);
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        mDatePicker.cancelLongPress();
    }
}
