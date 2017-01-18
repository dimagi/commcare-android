package org.commcare.views.widgets;

import android.content.Context;

import org.javarosa.form.api.FormEntryPrompt;

public class DatePrototypeFactory {

    public GregorianDateWidget getWidget(Context context, FormEntryPrompt fep, String appearance){
        String lowerCase = appearance.toLowerCase();
        boolean showCancelButton = false;

        if(lowerCase.contains("cancel")){
            showCancelButton = true;
        }

        return new GregorianDateWidget(context, fep, showCancelButton);
    }
}
