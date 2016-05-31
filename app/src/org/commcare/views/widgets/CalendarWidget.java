package org.commcare.views.widgets;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
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
import org.joda.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;

/**
 * Created by Saumya on 5/29/2016.
 */
public class CalendarWidget extends QuestionWidget{

    private int selectedYear;
    private int selectedMonth;
    private int selectedYDay;

    private GridView myGrid;
    private Button decMonth;
    private Button incMonth;
    private Button decYear;
    private Button incYear;
    private TextView myMonth;
    private TextView myYear;

    private Calendar myCal;
    private LinearLayout myLayout;

    private final String[] monthNames = new String[]{"January", "February", "March", "April", "May", "June", "July",
            "August","September","October","November","December"};

    //TODO: Find out a way to make this thing not default to 42 days for every month!
    private final int DAYS_IN_MONTH = 42;

    private Collection<String> months = Collections.unmodifiableCollection(Arrays.asList(monthNames));

    public CalendarWidget(Context context, FormEntryPrompt prompt){
        super(context, prompt);
        LayoutInflater inflater = (LayoutInflater)context.getSystemService
                (Context.LAYOUT_INFLATER_SERVICE);
        myLayout = (LinearLayout) inflater.inflate(R.layout.calendar_widget, null);
        addView(myLayout);

        initDisplay();
        myCal = Calendar.getInstance();
        updateCalendar();

        initOnClick();
        //TODO: Slight tweaks to spacing

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
                updateCalendar();
            }
        });

        incMonth.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                myCal.add(Calendar.MONTH, 1);
                updateCalendar();
            }
        });

        decYear.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                myCal.add(Calendar.YEAR, -1);
                updateCalendar();
            }
        });

        incYear.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                myCal.add(Calendar.YEAR, 1);
                updateCalendar();
            }
        });

        myGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Date date = (Date) parent.getItemAtPosition(position);
                myCal.setTime(date);
                //selectItem(position);
                updateCalendar();
            }
        });
    }

    private void updateCalendar(){
        ArrayList<Date> dateList = new ArrayList<>();
        Calendar populator = (Calendar) myCal.clone();

        populator.set(Calendar.DAY_OF_MONTH, 1);

        //Day of week for the first of the month
        int monthStartWeekDay = populator.get(Calendar.DAY_OF_WEEK) - 1;

        //Backtracking calendar to the most recent Sunday
        populator.add(Calendar.DAY_OF_MONTH, -monthStartWeekDay);

        while(dateList.size() < DAYS_IN_MONTH){
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

            //TODO: Tweak blue color to make it lighter
            if(date.equals(current)){
                ((TextView) view).setBackgroundColor(Color.BLUE);
            }

            return view;
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
        updateCalendar();
    }

    @Override
    public void setFocus(Context context) {

    }

    @Override
    public void setOnLongClickListener(OnLongClickListener l) {

    }
}
