package org.commcare.views.widgets;

import android.content.Context;
import android.view.View;
import android.widget.ImageButton;
import org.commcare.dalvik.R;
import org.javarosa.form.api.FormEntryPrompt;

/**
 * Created by Saumya on 6/2/2016.
 * Prototype 2 of 3
 */
public class Prototype2 extends Prototype1 {

    public Prototype2(Context context, FormEntryPrompt prompt){
        super(context, prompt);
        initView();
    }

    private void initView(){
        findViewById(R.id.dayupbtn).setVisibility(GONE);
        findViewById(R.id.daydownbtn).setVisibility(GONE);
        findViewById(R.id.monthupbtn).setVisibility(GONE);
        findViewById(R.id.monthdownbtn).setVisibility(GONE);
        findViewById(R.id.yearupbtn).setVisibility(GONE);
        findViewById(R.id.yeardownbtn).setVisibility(GONE);
        findViewById(R.id.widgetinfo).setVisibility(GONE);

        ImageButton openCalendar = (ImageButton) findViewById(R.id.opencalendarbottom);
        openCalendar.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                openCalendar();
            }
        });
        openCalendar.setVisibility(VISIBLE);

    }
}
