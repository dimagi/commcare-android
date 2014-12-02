package org.commcare.android.util;

import net.nightwhistler.htmlspanner.HtmlSpanner;

import org.javarosa.core.services.locale.Localization;

import android.text.Spannable;

public class MarkupUtil {
    static HtmlSpanner htmlspanner = new HtmlSpanner();

    public static Spannable getSpannable(String message){
        Spannable text = htmlspanner.fromHtml(message);
        return text;
    }
    
    public static Spannable getCustomSpannable(String style, String message){
        String mStyles = "<style> " + style + " </style>";
        Spannable text = htmlspanner.fromHtml(mStyles + message);
        return text;
    }
    
    public static Spannable getCustomSpannable(String message){
        Spannable text = htmlspanner.fromHtml(Stylizer.getStyleString() + message);
        return text;
    }
    
    public static Spannable localizeStyleSpannable(String localizationKey){
        Spannable text = htmlspanner.fromHtml(Stylizer.getStyleString() + Localization.get(localizationKey));
        return text;
    }
    
    public static Spannable localizeStyleSpannable(String localizationKey, String[] localizationArgs){
        Spannable text = htmlspanner.fromHtml(Stylizer.getStyleString() + Localization.get(localizationKey, localizationArgs));
        return text;
    }
    
    public static Spannable getCustomSpannableKey(String key, String message){
        String mStyles = formatKeyVal(key, getStyle(key));
        String mBody = "<body id=" + key + ">" + message + "</body>";
        Spannable text = htmlspanner.fromHtml(mStyles + mBody);
        return text;
    }
    
    
    
    public static String getStyle(String key){
       return Stylizer.getStyle(key);
    }
    
    public static Spannable localizeAndStyle(String localizationKey, String styleKey){
        return getCustomSpannableKey(styleKey, Localization.get(localizationKey));
    }
    
    public static String formatKeyVal(String key, String val){
        return "<style> #" + key + " " + getStyle(key) + " </style>";
    }
}