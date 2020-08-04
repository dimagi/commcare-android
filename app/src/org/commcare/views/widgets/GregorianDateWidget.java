package org.commcare.views.widgets;

import android.content.Context;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.javarosa.core.model.data.InvalidDateData;
import org.javarosa.xform.util.UniversalDate;
import org.javarosa.core.model.data.DateData;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.form.api.FormEntryPrompt;
import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GregorianDateWidget extends AbstractUniversalDateWidget
        implements CalendarFragment.CalendarCloseListener {

    private EditText dayText;
    private EditText yearText;
    private TextView dayOfWeek;
    private Calendar calendar;
    private LinearLayout gregorianView;
    private Spinner monthSpinner;
    private long dateOfLastWidgetUpdateNotice = -1;

    private final ImageButton openCalButton;
    private static final int EMPTY_MONTH_ENTRY_INDEX = 12;

    private List<String> monthList;
    private final long todaysDateInMillis;
    private long timeBeforeCalendarOpened;

    private CalendarFragment myCalendarFragment;
    private final FragmentManager fm;

    public static final int MIN_YEAR = 1900;
    public static final int MAX_YEAR = 2100;

    private static final String DAYFORMAT = "%02d";
    private static final String YEARFORMAT = "%04d";
    private static final String CALENDAR_FRAGMENT_TAG = "calendar-popup";

    public GregorianDateWidget(Context context, FormEntryPrompt prompt, boolean closeButton) {
        super(context, prompt);
        todaysDateInMillis = calendar.getTimeInMillis();
        ImageButton clearAll = findViewById(R.id.clear_all);

        if (closeButton) {
            clearAll.setOnClickListener(v -> clearAll());
        } else {
            clearAll.setVisibility(View.GONE);
        }

        fm = ((FragmentActivity)getContext()).getSupportFragmentManager();
        myCalendarFragment = (CalendarFragment) fm.findFragmentByTag(CALENDAR_FRAGMENT_TAG);
        if (myCalendarFragment == null) {
            myCalendarFragment = new CalendarFragment();
        }
        myCalendarFragment.setListener(this);
        myCalendarFragment.setCancelable(false);

        openCalButton = findViewById(R.id.open_calendar_bottom);
        openCalButton.setOnClickListener(v -> openCalendar());

        setAnswer();
    }

    @Override
    protected void initText() {
        dayOfWeek = findViewById(R.id.greg_day_of_week);
        dayText = findViewById(R.id.day_txt_field);
        yearText = findViewById(R.id.year_txt_field);

        dayText.setOnClickListener(v -> {
            dayText.clearFocus();
            dayText.requestFocus();
        });

        yearText.setOnClickListener(v -> {
            yearText.clearFocus();
            yearText.requestFocus();
        });

        setupMonthComponents();
    }

    private void setupMonthComponents() {
        monthSpinner = gregorianView.findViewById(R.id.month_spinner);
        monthList.add("");
        monthSpinner.setAdapter(new ArrayAdapter<>(getContext(), R.layout.calendar_date, monthList));
        monthSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position != EMPTY_MONTH_ENTRY_INDEX) {
                    validateDayText();
                    int previouslySelectedMonth = monthArrayPointer;
                    // Need to have a valid monthArrayPointer if they pick the empty option, so mod everything by 12
                    monthArrayPointer = position % 12;
                    int monthDifference = monthArrayPointer - previouslySelectedMonth;
                    DateTime dt = new DateTime(calendar.getTimeInMillis()).plusMonths(monthDifference);
                    calendar.setTimeInMillis(dt.getMillis());
                    refreshDisplay();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    @Override
    protected void inflateView(Context context) {
        gregorianView = (LinearLayout)LayoutInflater.from(context).inflate(R.layout.list_gregorian_widget, null);
        addView(gregorianView);
    }

    @Override
    protected void updateDateDisplay(long millisFromJavaEpoch) {
        UniversalDate dateUniv = fromMillis(millisFromJavaEpoch);
        monthArrayPointer = dateUniv.month - 1;
        dayText.setText(String.format(DAYFORMAT, dateUniv.day));
        monthSpinner.setSelection(monthArrayPointer);
        yearText.setText(String.format(YEARFORMAT, dateUniv.year));
        calendar.setTimeInMillis(millisFromJavaEpoch);
        dayOfWeek.setText(calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault()));

        //The setSelection response above is delayed in its execution, which means that on a given
        //ui loop where the widget is created, we're guaranteed to get this execution path
        //on the next loop. If something changes, we should fire the event, but otherwise
        //this can end up supressing QuestionWidget state like validation messages
        if(dateOfLastWidgetUpdateNotice != millisFromJavaEpoch) {
            dateOfLastWidgetUpdateNotice = millisFromJavaEpoch;
            widgetEntryChanged();
        }
    }

    //Used to calculate new time when a button is pressed
    @Override
    protected long getCurrentMillis() {
        autoFillEmptyTextFields();
        validateDayText();
        updateCalendar();
        int day = Integer.parseInt(dayText.getText().toString());
        // monthArray and Java calendar assume january = 0, millis from java epoch assumes january = 1
        int month = monthArrayPointer + 1;
        int year = Integer.parseInt(yearText.getText().toString());
        return toMillisFromJavaEpoch(year, month, day);
    }

    //Autofills any empty text fields whenever a button is pressed
    private void autoFillEmptyTextFields() {
        if (dayText.getText().toString().isEmpty()) {
            dayText.setText(String.valueOf(calendar.get(Calendar.DAY_OF_MONTH)));
        }

        if (((String)monthSpinner.getSelectedItem()).isEmpty()) {
            monthSpinner.setSelection(monthArrayPointer);
        }

        if (yearText.getText().toString().isEmpty()) {
            yearText.setText(String.valueOf(calendar.get(Calendar.YEAR)));
        }
    }

    private void updateCalendar() {
        monthArrayPointer = monthSpinner.getSelectedItemPosition();
        calendar.set(Calendar.MONTH, monthArrayPointer);

        String yearTextValue = yearText.getText().toString();
        calendar.set(Calendar.YEAR, Integer.parseInt(yearTextValue));
    }

    //Makes sure that value of day text field is valid given values of month and year fields
    private void validateDayText() {
        String dayTextString = dayText.getText().toString();
        if(dayTextString.isEmpty()){
            return;
        }
        int dayTextValue = Integer.parseInt(dayTextString);
        int maxDayOfMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);

        if (dayTextValue >= maxDayOfMonth) {
            dayTextValue = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
            dayText.setText(String.valueOf(dayTextValue));
        } else if (dayTextValue < 1) {
            dayTextValue = calendar.getActualMinimum(Calendar.DAY_OF_MONTH);
            dayText.setText(String.valueOf(dayTextValue));
        }
        calendar.set(Calendar.DAY_OF_MONTH, dayTextValue);
    }

    @Override
    protected UniversalDate decrementMonth(long millisFromJavaEpoch) {
        DateTime dt = new DateTime(millisFromJavaEpoch).minusMonths(1);
        return constructUniversalDate(dt);
    }

    @Override
    protected UniversalDate decrementYear(long millisFromJavaEpoch) {
        DateTime dt = new DateTime(millisFromJavaEpoch).minusYears(1);
        return constructUniversalDate(dt);
    }

    @Override
    protected UniversalDate fromMillis(long millisFromJavaEpoch) {
        return constructUniversalDate(new DateTime(millisFromJavaEpoch));
    }

    @Override
    protected UniversalDate incrementMonth(long millisFromJavaEpoch) {
        DateTime dt = new DateTime(millisFromJavaEpoch).plusMonths(1);
        return constructUniversalDate(dt);
    }

    @Override
    protected UniversalDate incrementYear(long millisFromJavaEpoch) {
        DateTime dt = new DateTime(millisFromJavaEpoch).plusYears(1);
        return constructUniversalDate(dt);
    }

    @Override
    protected String[] getMonthsArray() {
        calendar = Calendar.getInstance();

        String[] monthNames = new String[12];
        final Map<String, Integer> monthMap =
                calendar.getDisplayNames(Calendar.MONTH, Calendar.LONG, Locale.getDefault());
        monthList = new ArrayList<>(monthMap.keySet());
        Collections.sort(monthList, (a, b) -> monthMap.get(a) - monthMap.get(b));

        monthNames = monthList.toArray(monthNames);
        return monthNames;
    }

    @Override
    protected long toMillisFromJavaEpoch(int year, int month, int day) {
        DateTime dt = new DateTime()
                .withYear(year)
                .withMonthOfYear(month)
                .withDayOfMonth(day);
        return dt.getMillis();
    }

    @Override
    protected void updateGregorianDateHelperDisplay() {
    }

    @Override
    protected void setupTouchListeners() {
    }

    @Override
    protected void setupKeyListeners() {
    }

    @Override
    public IAnswerData getAnswer() {
        String month = (String)monthSpinner.getSelectedItem();
        String day = dayText.getText().toString();
        String year = yearText.getText().toString();

        //All fields are empty - Like submitting a blank date
        if (month.isEmpty() && day.isEmpty() && year.isEmpty()) {
            setFocus(getContext());
            return null;
        }

        //Some but not all fields are empty
        if (month.isEmpty() || day.isEmpty() || year.isEmpty()) {
            setFocus(getContext());
            return new InvalidDateData(Localization.get("calendar.empty.fields"), new DateData(calendar.getTime()), day, month, year);
        }

        //Invalid year (too low)
        if (Integer.parseInt(year) < MIN_YEAR) {
            setFocus(getContext());
            return new InvalidDateData(Localization.get("calendar.low.year", "" + MIN_YEAR), new DateData(calendar.getTime()), day, month, year);
        }

        //Invalid day (too high)
        if (Integer.parseInt(day) > calendar.getActualMaximum(Calendar.DAY_OF_MONTH)) {
            setFocus(getContext());
            return new InvalidDateData(Localization.get("calendar.high.day", "" + calendar.getActualMaximum(Calendar.DAY_OF_MONTH)), new DateData(calendar.getTime()), day, month, year);
        }

        //Invalid day (too low)
        if (Integer.parseInt(day) < 1) {
            setFocus(getContext());
            return new InvalidDateData(Localization.get("calendar.low.day"), new DateData(calendar.getTime()), day, month, year);
        }

        //Invalid year (too high)
        if (Integer.parseInt(year) > MAX_YEAR) {
            setFocus(getContext());
            return new InvalidDateData(Localization.get("calendar.high.year", "" + MAX_YEAR), new DateData(calendar.getTime()), day, month, year);
        }

        return super.getAnswer();
    }

    private UniversalDate constructUniversalDate(DateTime dt) {
        return new UniversalDate(
                dt.getYear(),
                dt.getMonthOfYear(),
                dt.getDayOfMonth(),
                dt.getMillis()
        );
    }

    private void clearAll() {
        dayText.setText("");
        yearText.setText("");
        monthSpinner.setSelection(EMPTY_MONTH_ENTRY_INDEX);
        setFocus(getContext());
    }

    @Override
    public void setFocus(Context context) {
        super.setFocus(context);
        dayText.setCursorVisible(false);
        yearText.setCursorVisible(false);
    }

    private void refreshDisplay() {
        updateDateDisplay(calendar.getTimeInMillis());
    }

    private void openCalendar() {
        setFocus(getContext());
        timeBeforeCalendarOpened = getCurrentMillis();
        calendar.setTimeInMillis(timeBeforeCalendarOpened);
        Bundle args = new Bundle();
        args.putLong(CalendarFragment.KEY_STARTING_SELECTION, timeBeforeCalendarOpened);
        myCalendarFragment.setArguments(args);
        myCalendarFragment.show(fm, CALENDAR_FRAGMENT_TAG);
    }

    @Override
    public void onDateSelected(long millis) {
        updateDateDisplay(millis);
        setFocus(getContext());
    }

    @Override
    public void setAnswer() {
        if (mPrompt.getAnswerValue() != null) {
            if (mPrompt.getAnswerValue() instanceof InvalidDateData) {
                InvalidDateData previousDate = (InvalidDateData)mPrompt.getAnswerValue();

                String day = previousDate.getDayText();
                String month = previousDate.getMonthText();
                String year = previousDate.getYearText();

                monthSpinner.setSelection(monthList.indexOf(month)); //Update month first because selecting a month item will call refreshDisplay().
                dayText.setText(day);
                yearText.setText(year);
            }else{
                Date date = (Date)mPrompt.getAnswerValue().getValue();
                updateDateDisplay(date.getTime());
            }
        } else {
            super.clearAnswer();
        }
    }

}
