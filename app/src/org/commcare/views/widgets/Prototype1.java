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
        openCalButton = new ImageButton(getContext());

        openCalButton.setImageResource(R.drawable.avatar_vellum_date);

        RelativeLayout layout = (RelativeLayout) findViewById(R.id.widgetinfo);
        layout.addView(openCalButton);
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) openCalButton.getLayoutParams();
        params.addRule(RelativeLayout.RIGHT_OF, R.id.gregdayofweek);
        params.width = 60;
        params.height = 60;

        openCalButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                openCalendar();
            }
        });

        ImageButton calendarCloser = new ImageButton(getContext());
        calendarCloser.setImageResource(R.drawable.close_cross_icon);

        layout = (RelativeLayout) findViewById(R.id.calendarinfo);
        layout.addView(calendarCloser);
        params = (RelativeLayout.LayoutParams) calendarCloser.getLayoutParams();
        params.addRule(RelativeLayout.RIGHT_OF, R.id.calendarweekday);
        params.width = 90;
        params.height = 90;

        calendarCloser.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                closeCalendar();
            }
        });

        //TODO: Add green "Submit" button. Not done so far because it's unclear what its point is. Navigation to next question is handled already.
    }

    public ImageButton getCalendarButton(){
        return openCalButton;
    }

    private void openCalendar() {

        if(myGreg.getAnswer() != null){
            myCal.setDate((DateData) myGreg.getAnswer());
        }else{
            myCal.setDate(new DateData(new Date()));
        }

        myGreg.setFocus(getContext());
        myGreg.setVisibility(GONE);
        myCal.setVisibility(VISIBLE);
    }

    private void closeCalendar(){
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
    public void setFocus(Context context) {

    }

    @Override
    public void setOnLongClickListener(OnLongClickListener l) {

    }

    public void removeQuestionText(){
        mQuestionText.setVisibility(GONE);
    }

    public GregorianDateWidget getMyGreg(){
        return myGreg;
    }

    public void addCalendarButton(ImageButton b){
        b.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                openCalendar();
            }
        });
    }

}
