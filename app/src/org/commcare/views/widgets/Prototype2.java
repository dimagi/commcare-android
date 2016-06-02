package org.commcare.views.widgets;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.commcare.utils.UniversalDate;
import org.javarosa.core.model.data.DateData;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.form.api.FormEntryPrompt;
import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.commcare.utils.UniversalDate.MILLIS_IN_DAY;

/**
 * Created by Saumya on 6/2/2016.
 */
public class Prototype2 extends QuestionWidget {

    private GregorianDateWidget myGreg;
    private CalendarWidget myCal;

    public Prototype2(Context context, FormEntryPrompt prompt){
        super(context, prompt);

        myGreg = new GregorianDateWidget(context, prompt);
        myCal = new CalendarWidget(context, prompt);

        myCal.setVisibility(GONE);

        myGreg.removeQuestionText();
        myCal.removeQuestionText();
        initView();

        addView(myCal);
        addView(myGreg);

        final ImageButton openCalendar = new ImageButton(context);
        openCalendar.setImageResource(R.drawable.avatar_vellum_date);
        myGreg.addView(openCalendar);

        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) openCalendar.getLayoutParams();
        params.width = 60;
        params.height = 60;

        openCalendar.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                openCalendar();
            }
        });

        ImageButton calendarCloser = new ImageButton(getContext());
        calendarCloser.setImageResource(R.drawable.close_cross_icon);

        RelativeLayout layout = (RelativeLayout) findViewById(R.id.calendarinfo);
        layout.addView(calendarCloser);
        RelativeLayout.LayoutParams relativeParams = (RelativeLayout.LayoutParams) calendarCloser.getLayoutParams();
        relativeParams.addRule(RelativeLayout.RIGHT_OF, R.id.calendarweekday);
        relativeParams.width = 90;
        relativeParams.height = 90;

        calendarCloser.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                closeCalendar();
            }
        });

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

    private void initView(){
        (myGreg.findViewById(R.id.dayupbtn)).setVisibility(GONE);
        (myGreg.findViewById(R.id.daydownbtn)).setVisibility(GONE);
        (myGreg.findViewById(R.id.monthupbtn)).setVisibility(GONE);
        (myGreg.findViewById(R.id.monthdownbtn)).setVisibility(GONE);
        (myGreg.findViewById(R.id.yearupbtn)).setVisibility(GONE);
        (myGreg.findViewById(R.id.yeardownbtn)).setVisibility(GONE);
        (myGreg.findViewById(R.id.clearall)).setVisibility(GONE);
        (myGreg.findViewById(R.id.gregdayofweek)).setVisibility(GONE);
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
}
