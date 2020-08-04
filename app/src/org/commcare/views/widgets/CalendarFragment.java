package org.commcare.views.widgets;

import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import org.commcare.dalvik.R;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import androidx.fragment.app.DialogFragment;

/**
 * Created by Saumya on 5/29/2016.
 * DialogFragment for a popup calendar icon
 * Uses support library for compatibility with pre-honeycomb devices
 * Layout and logic inspired by Ahmed Al-Amir at https://www.toptal.com/android/android-customization-how-to-build-a-ui-component-that-does-what-you-want
 */
public class CalendarFragment extends DialogFragment {

    private GridView calendarGrid;
    private ImageButton cancel;
    private Button today;
    private Spinner monthSpinner;
    private Spinner yearSpinner;
    private LinearLayout layout;

    private Calendar calendar;
    private CalendarCloseListener calendarCloseListener;

    private long todaysDateInMillis;
    private static final int DAYSINWEEK = 7;
    private static final String KEY_SELECTED_DATE = "selected-date";

    public static final String KEY_STARTING_SELECTION = "starting-selection";
    private long startingSelectedDate;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        this.calendar = Calendar.getInstance();
        this.todaysDateInMillis = calendar.getTimeInMillis();
        // Set the starting selection passed in from outside.
        Bundle input = getArguments();
        if (input != null && input.containsKey(KEY_STARTING_SELECTION)) {
            startingSelectedDate = input.getLong(KEY_STARTING_SELECTION);
            calendar.setTimeInMillis(startingSelectedDate);
        }
        inflateView(inflater, container);

        initDisplay();
        initWeekDays();
        initOnClick();
        setWindowSize();

        // Set the calendar date to what the user selected before rotation.
        if (savedInstanceState != null && savedInstanceState.containsKey(KEY_SELECTED_DATE)) {
            calendar.setTimeInMillis(savedInstanceState.getLong(KEY_SELECTED_DATE));
        }
        return layout;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        long millis = calendar.getTimeInMillis();
        outState.putLong(KEY_SELECTED_DATE, millis);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        refresh();
    }

    private void initWeekDays() {
        final Map<String, Integer> weekDays = calendar.getDisplayNames(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault());

        ArrayList<String> weekDayList = new ArrayList<>(weekDays.keySet());

        DateListHelper.sortCalendarItems(weekDays, weekDayList);

        ((TextView)layout.findViewById(R.id.day1)).setText(weekDayList.get(0));
        ((TextView)layout.findViewById(R.id.day2)).setText(weekDayList.get(1));
        ((TextView)layout.findViewById(R.id.day3)).setText(weekDayList.get(2));
        ((TextView)layout.findViewById(R.id.day4)).setText(weekDayList.get(3));
        ((TextView)layout.findViewById(R.id.day5)).setText(weekDayList.get(4));
        ((TextView)layout.findViewById(R.id.day6)).setText(weekDayList.get(5));
        ((TextView)layout.findViewById(R.id.day7)).setText(weekDayList.get(6));
    }

    private void initOnClick() {

        calendarGrid.setOnItemClickListener((parent, view, position, id) -> {
            Date date = (Date) parent.getItemAtPosition(position);
            calendar.setTime(date);
            refresh();
        });

        cancel.setOnClickListener(v -> {
            dismiss();
            if (calendarCloseListener != null) {
                calendarCloseListener.onDateSelected(startingSelectedDate);
            }
        });

        ImageButton closer = layout.findViewById(R.id.close_calendar);
        closer.setOnClickListener(v -> {
            dismiss();
            if (calendarCloseListener != null) {
                calendarCloseListener.onDateSelected(calendar.getTimeInMillis());
            }
        });

        today.setOnClickListener(v -> {
            calendar.setTimeInMillis(todaysDateInMillis);
            refresh();
        });
    }

    private void setWindowSize() {
        Rect displayRectangle = new Rect();
        Window window = getActivity().getWindow();
        window.getDecorView().getWindowVisibleDisplayFrame(displayRectangle);
        layout.setMinimumWidth((int)(displayRectangle.width() * 0.9f));
    }

    private void initDisplay() {
        calendarGrid = layout.findViewById(R.id.calendar_grid);
        setupMonthComponents();
        setupYearComponents();
        cancel = layout.findViewById(R.id.cancel_calendar);
        today = layout.findViewById(R.id.today);
    }

    private void setupYearComponents() {
        yearSpinner = layout.findViewById(R.id.year_spinner);

        ArrayList<String> years = new ArrayList<>();

        for (int i = GregorianDateWidget.MIN_YEAR; i <= GregorianDateWidget.MAX_YEAR; i++) {
            years.add(String.valueOf(i));
        }

        ArrayAdapter<String> yearAdapter = new ArrayAdapter<>(getContext(), R.layout.calendar_date, years);
        yearSpinner.setAdapter(yearAdapter);

        yearSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                calendar.set(Calendar.YEAR, position+GregorianDateWidget.MIN_YEAR);
                refresh();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    //Have to sort month names because Calendar can't return them in order
    private void setupMonthComponents() {
        monthSpinner = layout.findViewById(R.id.calendar_spinner);

        final Map<String, Integer> monthMap = calendar.getDisplayNames(Calendar.MONTH, Calendar.LONG, Locale.getDefault());
        ArrayList<String> monthList = new ArrayList<>(monthMap.keySet());
        DateListHelper.sortCalendarItems(monthMap, monthList);
        monthSpinner.setAdapter(new ArrayAdapter<>(getContext(), R.layout.calendar_date, monthList));

        monthSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                calendar.set(Calendar.MONTH, position);
                refresh();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    // Redraws the calendar display
    private void refresh() {
        ArrayList<Date> dateList = new ArrayList<>();
        Calendar populator = (Calendar)calendar.clone();

        int totalDays = getNumDaysInMonth(populator);
        populateListOfDates(dateList, populator, totalDays);

        yearSpinner.setSelection(calendar.get(Calendar.YEAR)-GregorianDateWidget.MIN_YEAR);
        monthSpinner.setSelection(calendar.get(Calendar.MONTH));
        calendarGrid.setAdapter(new CalendarAdapter(getContext(), dateList));
    }

    //Populates an arraylist with dates
    private static void populateListOfDates(ArrayList<Date> dateList, Calendar populator, int totalDays) {
        while(dateList.size() < totalDays) {
            dateList.add(populator.getTime());
            populator.add(Calendar.DAY_OF_MONTH, 1);
        }

        int remainingDays = ((DAYSINWEEK + 1)-(populator.get(Calendar.DAY_OF_WEEK)))%DAYSINWEEK;

        for (int i = 0; i < remainingDays; i++) {
            dateList.add(populator.getTime());
            populator.add(Calendar.DAY_OF_MONTH, 1);
        }
    }

    //Calculates the total number of days from most recent Sunday to the first Saturday following the end of the current month to fill the calendar view
    private static int getNumDaysInMonth(Calendar cal) {
        int totalDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        cal.set(Calendar.DAY_OF_MONTH, 1);

        //Day of week for the first of the month
        int monthStartWeekDay = cal.get(Calendar.DAY_OF_WEEK) - 1;
        int daysSincePreviousSunday = -monthStartWeekDay;

        totalDays += monthStartWeekDay;

        //Backtracking calendar to the most recent Sunday
        cal.add(Calendar.DAY_OF_MONTH, daysSincePreviousSunday);

        return totalDays;
    }

    private class CalendarAdapter extends ArrayAdapter<Date>{

        private final LayoutInflater mInflater;

        public CalendarAdapter(Context context, ArrayList<Date> dates) {
            super(context, R.layout.calendar_date, dates);
            mInflater = LayoutInflater.from(context);
        }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            if (view == null) {
                view = mInflater.inflate(R.layout.calendar_date, null);
            }

            TextView text = (TextView)view;

            Date date = getItem(position);
            Calendar gridPopulator = (Calendar)calendar.clone();
            gridPopulator.setTime(date);

            text.setText(String.valueOf(gridPopulator.get(Calendar.DAY_OF_MONTH)));

            highlightCalendarGridCell(text, gridPopulator, calendar);
            return text;
        }

        //Current month has white background, previous/next months have gray background, today's date has green background
        protected void highlightCalendarGridCell(TextView text, Calendar calendarDate, Calendar currentDate) {
            if (calendarDate.equals(currentDate)) {
                text.setTextColor(getResources().getColor(R.color.white));
                text.setBackgroundColor(getResources().getColor(R.color.cc_attention_positive_color));
            }
            else if (calendarDate.get(Calendar.MONTH) != currentDate.get(Calendar.MONTH)) {
                text.setTextColor(getResources().getColor(R.color.grey_dark));
                text.setBackgroundColor(getResources().getColor(R.color.grey_lighter));
            }
            else {
                text.setTextColor(getResources().getColor(R.color.black));
                text.setBackgroundColor(getResources().getColor(R.color.transparent));
            }
        }
    }

    private void inflateView(LayoutInflater inflater, ViewGroup container) {
        layout = (LinearLayout)inflater.inflate(R.layout.scrolling_calendar_widget, container);
    }

    public interface CalendarCloseListener {
        void onDateSelected(long millis);
    }

    public void setListener(CalendarCloseListener listener) {
        calendarCloseListener = listener;
    }

}
