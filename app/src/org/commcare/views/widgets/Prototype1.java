package org.commcare.views.widgets;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import org.commcare.dalvik.R;
import org.javarosa.core.model.data.DateData;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.form.api.FormEntryPrompt;


/**
 * Created by Saumya on 6/1/2016.
 */
public class Prototype1 extends QuestionWidget {

    private CalendarWidget myCal;
    private GregorianDateWidget myGreg;
    private boolean calendarMode;
    private Button calendarCloser;

    public Prototype1(Context con, FormEntryPrompt prompt){
        super(con, prompt);
        myGreg = new GregorianDateWidget(con, prompt);
        myCal = new CalendarWidget(con, prompt);

        myCal.setVisibility(GONE);
        calendarMode = false;

        LayoutInflater inflater = (LayoutInflater)con.getSystemService
                (Context.LAYOUT_INFLATER_SERVICE);
        RelativeLayout topbar =  (RelativeLayout) inflater.inflate(R.layout.prototype1, null);

        myGreg.removeQuestionText();
        myCal.removeQuestionText();

        addView(topbar);
        addView(myCal);
        addView(myGreg);

        initButtons();

        //TODO: Move open calendar button. Add it to relative view in GregWidget, to the right of the day of week
        //TODO: Move close calendar button. Overhaul existing approach, and add it to the relativelayout in CalendarWidget, to the right of the day of week.
    }

    private void initButtons() {
        ImageButton openCalButton = (ImageButton) findViewById(R.id.calendarbutton);

        openCalButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                openCalendar();
            }
        });

        calendarCloser = (Button) findViewById(R.id.closecalendar);
        calendarCloser.setVisibility(VISIBLE);

        calendarCloser.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                closeCalendar();
            }
        });
    }

    private void openCalendar() {
        myGreg.setFocus(getContext());
        myCal.setDate((DateData) myGreg.getAnswer());
        myCal.setVisibility(VISIBLE);
        calendarMode = true;
    }

    private void closeCalendar(){
        myGreg.setDate((DateData) myCal.getAnswer());
        myCal.setVisibility(GONE);
        calendarMode = false;
    }

    @Override
    public IAnswerData getAnswer() {
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
}
