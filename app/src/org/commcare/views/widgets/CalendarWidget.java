package org.commcare.views.widgets;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.javarosa.core.model.data.DateData;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.form.api.FormEntryPrompt;
import org.commcare.dalvik.R;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Created by Saumya on 5/29/2016.
 */
public class CalendarWidget extends QuestionWidget{

    private GridView myGrid;
    private Button decMonth;
    private Button incMonth;
    private Button decYear;
    private Button incYear;
    private TextView myMonth;
    private TextView myYear;

    private Calendar myCal;
    private LinearLayout myLayout;

    private String[] monthNames;

    //TODO: Find out a way to make this thing not default to 42 days for every month!
    private final int DAYS_IN_MONTH = 42;

    public CalendarWidget(Context context, FormEntryPrompt prompt, Calendar cal){
        super(context, prompt);
        LayoutInflater inflater = (LayoutInflater)context.getSystemService
                (Context.LAYOUT_INFLATER_SERVICE);
        myLayout = (LinearLayout) inflater.inflate(R.layout.calendar_widget, null);
        addView(myLayout);

        myCal = cal;
        initDisplay();
        initMonths();
        initWeekDays();
        refresh();
        initOnClick();
    }

    private void initWeekDays(){
        final Map<String, Integer> weekDays = myCal.getDisplayNames(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault());

        ArrayList<String> weekDayList = new ArrayList<>(weekDays.keySet());

        Collections.sort(weekDayList, new Comparator<String>(){
            @Override
            public int compare(String a, String b){
                return weekDays.get(a) - weekDays.get(b);
            }
        });

        ((TextView) findViewById(R.id.day1)).setText(weekDayList.get(0));
        ((TextView) findViewById(R.id.day2)).setText(weekDayList.get(1));
        ((TextView) findViewById(R.id.day3)).setText(weekDayList.get(2));
        ((TextView) findViewById(R.id.day4)).setText(weekDayList.get(3));
        ((TextView) findViewById(R.id.day5)).setText(weekDayList.get(4));
        ((TextView) findViewById(R.id.day6)).setText(weekDayList.get(5));
        ((TextView) findViewById(R.id.day7)).setText(weekDayList.get(6));

    }

    private void initMonths(){
        monthNames = new String[12];

        final Map<String, Integer> monthMap = myCal.getDisplayNames(Calendar.MONTH, Calendar.LONG, Locale.getDefault());
        List<String> monthList = new ArrayList<>(monthMap.keySet());

        Collections.sort(monthList, new Comparator<String>(){
            @Override
            public int compare(String a, String b){
                return monthMap.get(a) - monthMap.get(b);
            }
        });

        monthNames = monthList.toArray(monthNames);
    }

    private void initDisplay(){
        myGrid = (GridView) myLayout.findViewById(R.id.calendar_grid);

        decMonth = (Button) myLayout.findViewById(R.id.prevmonthbutton);
        incMonth = (Button) myLayout.findViewById(R.id.nextmonthbutton);
        myMonth = (TextView) myLayout.findViewById(R.id.currentmonth);

        decYear = (Button) myLayout.findViewById(R.id.prevyearbutton);
        incYear = (Button) myLayout.findViewById(R.id.nextyearbutton);
        myYear = (TextView) myLayout.findViewById(R.id.currentyear);

    }

    private void initOnClick(){

        decMonth.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                myCal.add(Calendar.MONTH, -1);
                refresh();
            }
        });

        incMonth.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                myCal.add(Calendar.MONTH, 1);
                refresh();
            }
        });

        decYear.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                myCal.add(Calendar.YEAR, -1);
                refresh();
            }
        });

        incYear.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                myCal.add(Calendar.YEAR, 1);
                refresh();
            }
        });

        myGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Date date = (Date) parent.getItemAtPosition(position);
                myCal.setTime(date);
                //selectItem(position);
                refresh();
            }
        });
    }

    public void refresh(){

        ArrayList<Date> dateList = new ArrayList<>();
        Calendar populator = (Calendar) myCal.clone();

        int totalDays = populator.getActualMaximum(Calendar.DAY_OF_MONTH);

        populator.set(Calendar.DAY_OF_MONTH, 1);

        //Day of week for the first of the month
        int monthStartWeekDay = populator.get(Calendar.DAY_OF_WEEK) - 1;

        totalDays += monthStartWeekDay;

        //Backtracking calendar to the most recent Sunday
        populator.add(Calendar.DAY_OF_MONTH, -monthStartWeekDay);

        while(dateList.size() < totalDays){
            dateList.add(populator.getTime());
            populator.add(Calendar.DAY_OF_MONTH, 1);
        }

        int remainingDays = 8-populator.get(Calendar.DAY_OF_WEEK);

        for(int i = 0; i < remainingDays; i ++){
            dateList.add(populator.getTime());
            populator.add(Calendar.DAY_OF_MONTH, 1);
        }

        myYear.setText(String.valueOf(myCal.get(Calendar.YEAR)));
        myMonth.setText(monthNames[myCal.get(Calendar.MONTH)]);
        myGrid.setAdapter(new CalendarAdapter(getContext(), dateList));
    }

    private class CalendarAdapter extends ArrayAdapter<Date>{

        private LayoutInflater mInflater;

        public CalendarAdapter(Context context, ArrayList<Date> dates){
            super(context, R.layout.calendar_date, dates);
            mInflater = LayoutInflater.from(context);
        }

        @Override
        public View getView(int position, View view, ViewGroup parent){
            if(view == null){
                view = mInflater.inflate(R.layout.calendar_date, null);
            }

            TextView text = (TextView) view;

            Date date = getItem(position);
            text.setText(String.valueOf(date.getDate()));

            Date current = myCal.getTime();

            if(date.equals(current)){
                text.setBackgroundColor(Color.rgb(105, 217, 255));
            }

            if(date.getMonth() != current.getMonth()){
                text.setTextColor(Color.rgb(150, 150, 150));
            }

            text.setHeight(120);
            return text;
        }
    }

    @Override
    public IAnswerData getAnswer() {
        return new DateData(myCal.getTime());
    }

    /*
    Resets date to today
     */
    @Override
    public void clearAnswer() {
        myCal = Calendar.getInstance();
        refresh();
    }

    @Override
    public void setFocus(Context context) {}

    @Override
    public void setOnLongClickListener(OnLongClickListener l) {}

    public void removeQuestionText(){
        mQuestionText.setVisibility(GONE);
    }

    public void setDate(DateData newDate){
        Date nextDate = (Date) newDate.getValue();
        myCal.setTimeInMillis(nextDate.getTime());
        refresh();
    }
}
