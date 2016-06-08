package org.commcare.views.widgets;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import org.commcare.dalvik.R;
import org.javarosa.core.model.data.DateData;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.form.api.FormEntryPrompt;

import java.util.Calendar;
import java.util.Date;


/**
 * Created by Saumya on 6/1/2016.
 */
public class Prototype1 extends QuestionWidget {

    private CalendarWidget myCal;
    private GregorianDateWidget myGreg;
    private ImageButton openCalButton;

    public Prototype1(Context con, FormEntryPrompt prompt){
        super(con, prompt);
        myGreg = new GregorianDateWidget(con, prompt);
        myCal = new CalendarWidget(con, prompt);

        myCal.setVisibility(GONE);

        myGreg.removeQuestionText();
        myCal.removeQuestionText();

        addView(myCal);
        addView(myGreg);

        initButtons();
    }

    private void initButtons() {

        openCalButton = (ImageButton) findViewById(R.id.opencalendar);
        openCalButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                openCalendar();
            }
        });

        ImageButton calendarCloser = new ImageButton(getContext());
        calendarCloser.setMaxWidth(75);
        calendarCloser.setMaxHeight(75);

        calendarCloser.setBackgroundResource(R.drawable.green_check_mark);

        LinearLayout calendarinfo = (LinearLayout) findViewById(R.id.calendarinfo);
        calendarinfo.addView(calendarCloser);
//        LinearLayout.LayoutParams calendarParams = (LinearLayout.LayoutParams) calendarCloser.getLayoutParams();

        calendarCloser.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                closeCalendar();
            }
        });
    }

    protected void openCalendar() {

        if(myGreg.getAnswer() != null){
            myCal.setDate((DateData) myGreg.getAnswer());
        }else{
            myCal.setDate(new DateData(new Date()));
        }

        myGreg.setFocus(getContext());
        myGreg.setVisibility(GONE);
        myCal.setVisibility(VISIBLE);
    }

    protected void closeCalendar(){
        myGreg.setDate((DateData) myCal.getAnswer());
        myCal.setVisibility(GONE);
        myGreg.setVisibility(VISIBLE);
    }

    @Override
    public IAnswerData getAnswer() {
        if(myCal.getVisibility() != GONE){
            closeCalendar();
        }
        return myGreg.getAnswer();
    }

    @Override
    public void clearAnswer() {
        myGreg.clearAnswer();
    }

    @Override
    public void setFocus(Context context) {}

    @Override
    public void setOnLongClickListener(OnLongClickListener l) {}

    public GregorianDateWidget getMyGreg(){
        return myGreg;
    }

    protected void addCalendarButton(ImageButton b){
        b.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                openCalendar();
            }
        });
    }

}
