package org.commcare.views.widgets;

import android.content.Context;

import org.javarosa.form.api.FormEntryPrompt;

/**
 * Factory returns different versions of the date widget prototype based on appearance attribute
 * Appearance attributes can be used as follows:
 * Add "gregorian" to get original gregorian date widget
 * Add "calendar_spinner" to get calendar popup with spinning wheel for month selection, OR "calendar_list" to get calendar popup with list for month selection
 * Add "cancel" to get cancel button
 * Add "widget_spinner" to get spinning wheel for month selection in main widget, OR "widget_list" to get list for month selection in main widget
 */
public class DatePrototypeFactory {

    public GregorianDateWidget getWidget(Context context, FormEntryPrompt fep, String appearance){
        String lowerCase = appearance.toLowerCase();
        boolean showCancelButton = false;
        String calendarPopupType = "arrows";

        if(lowerCase.contains("cancel")){
            showCancelButton = true;
        }

        if(lowerCase.contains("calendar_spinner")){
            calendarPopupType = "calspinner";
        }

        if(lowerCase.contains("calendar_list")){
            calendarPopupType = "callist";
        }

        if(lowerCase.contains("widget_spinner")){
            return new SpinnerGregorianWidget(context, fep, showCancelButton, calendarPopupType);
        }

        if(lowerCase.contains("widget_list")){
            return new ListGregorianWidget(context, fep, showCancelButton, calendarPopupType);
        }

        return new GregorianDateWidget(context, fep, showCancelButton, calendarPopupType);

    }
}
