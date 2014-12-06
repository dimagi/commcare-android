package org.commcare.android.util;

import net.nightwhistler.htmlspanner.HtmlSpanner;

import org.commcare.dalvik.preferences.DeveloperPreferences;
import org.javarosa.core.services.locale.Localization;

import android.text.Html;
import android.text.Spannable;

public class MarkupUtil {
    static HtmlSpanner htmlspanner = new HtmlSpanner();

    public static Spannable getSpannable(String message){
        if(!DeveloperPreferences.isMarkupEnabled()){
            return Spannable.Factory.getInstance().newSpannable(MarkupUtil.stripHtml(message));
        }
        Spannable text = htmlspanner.fromHtml(message);
        return text;
    }
    
    public static Spannable getCustomSpannable(String style, String message){
        if(!DeveloperPreferences.isMarkupEnabled()){
            return Spannable.Factory.getInstance().newSpannable(MarkupUtil.stripHtml(message));
        }
        String mStyles = "<style> " + style + " </style>";
        Spannable text = htmlspanner.fromHtml(mStyles + message);
        return text;
    }
    
    public static Spannable getCustomSpannable(String message){
        if(!DeveloperPreferences.isMarkupEnabled()){
            return Spannable.Factory.getInstance().newSpannable(MarkupUtil.stripHtml(message));
        }
        
        Spannable text = htmlspanner.fromHtml(Stylizer.getStyleString() + message);
        return text;
    }
    
    public static Spannable localizeStyleSpannable(String localizationKey){
        if(!DeveloperPreferences.isMarkupEnabled()){
            return Spannable.Factory.getInstance().newSpannable(MarkupUtil.stripHtml(Localization.get(localizationKey)));
        }
        Spannable text = htmlspanner.fromHtml(Stylizer.getStyleString() + Localization.get(localizationKey));
        return text;
    }
    
    public static Spannable localizeStyleSpannable(String localizationKey, String[] localizationArgs){
        if(!DeveloperPreferences.isMarkupEnabled()){
            return Spannable.Factory.getInstance().newSpannable(MarkupUtil.stripHtml(Localization.get(localizationKey, localizationArgs)));
        }
        Spannable text = htmlspanner.fromHtml(Stylizer.getStyleString() + Localization.get(localizationKey, localizationArgs));
        return text;
    }
    
    public static Spannable getCustomSpannableKey(String key, String message){
        if(!DeveloperPreferences.isMarkupEnabled()){
            return Spannable.Factory.getInstance().newSpannable(MarkupUtil.stripHtml(message));
        }
        String mStyles = formatKeyVal(key, getStyle(key));
        String mBody = "<body id=" + key + ">" + message + "</body>";
        Spannable text = htmlspanner.fromHtml(mStyles + mBody);
        return text;
    }
    
    
    
    public static String getStyle(String key){
       return Stylizer.getStyle(key);
    }
    
    public static Spannable localizeAndStyle(String localizationKey, String styleKey){
        if(!DeveloperPreferences.isMarkupEnabled()){
            return Spannable.Factory.getInstance().newSpannable(MarkupUtil.stripHtml(Localization.get(localizationKey)));
        }
        return getCustomSpannableKey(styleKey, Localization.get(localizationKey));
    }
    
    public static String formatKeyVal(String key, String val){
        return "<style> #" + key + " " + getStyle(key) + " </style>";
    }
    
    public static String stripHtml(String html) {
        return Html.fromHtml(html).toString();
    }
}