package org.commcare.android.util;

import in.uncod.android.bypass.Bypass;
import net.nightwhistler.htmlspanner.HtmlSpanner;

import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.preferences.DeveloperPreferences;
import org.javarosa.core.services.locale.Localization;

import android.content.Context;
import android.text.Html;
import android.text.Spannable;

public class MarkupUtil {
    static HtmlSpanner htmlspanner = new HtmlSpanner();
    
    /*
     * Developer Preference helper classes
     */
    
    public static Spannable localizeStyleSpannable(Context c, String localizationKey){
        
        if(DeveloperPreferences.isCssEnabled()){
            return htmlspanner.fromHtml(MarkupUtil.getStyleString() + Localization.get(localizationKey));
        }
        
        if(DeveloperPreferences.isMarkdownEnabled()){
            return (Spannable) localizeMarkdownSpannable(c, localizationKey);
        }
        
        return Spannable.Factory.getInstance().newSpannable(MarkupUtil.stripHtml(Localization.get(localizationKey)));
    }
    
    public static Spannable localizeStyleSpannable(Context c, String localizationKey, String[] localizationArgs){
        if(DeveloperPreferences.isCssEnabled()){
            return htmlspanner.fromHtml(MarkupUtil.getStyleString() + Localization.get(localizationKey, localizationArgs));
        }
        
        if(DeveloperPreferences.isMarkdownEnabled()){
            return (Spannable) localizeMarkdownSpannable(c, localizationKey, localizationArgs);
        }
        
        return Spannable.Factory.getInstance().newSpannable(MarkupUtil.stripHtml(Localization.get(localizationKey, localizationArgs)));
    }
    
    /*
     * Markdown styling utils from Bypass
     * https://github.com/Uncodin/bypass
     */
    
    public static CharSequence localizeMarkdownSpannable(Context c, String localizationKey){
        Bypass bypass = new Bypass(c);
        CharSequence mSequence = bypass.markdownToSpannable(Localization.get(localizationKey));
        return mSequence;
    }
   
    public static CharSequence localizeMarkdownSpannable(Context c, String localizationKey, String[] localizationArgs){
        Bypass bypass = new Bypass(c);
        CharSequence mSequence = bypass.markdownToSpannable(Localization.get(localizationKey, localizationArgs));
        return mSequence;
    }
    
    /*
     * CSS styling utils from NightWhistler's HtmlSpanner 
     * https://github.com/NightWhistler/HtmlSpanner
     */
    
    public static Spannable localizeCssSpannable(String localizationKey){
        if(!DeveloperPreferences.isCssEnabled()){
            return Spannable.Factory.getInstance().newSpannable(MarkupUtil.stripHtml(Localization.get(localizationKey)));
        }
        Spannable text = htmlspanner.fromHtml(MarkupUtil.getStyleString() + Localization.get(localizationKey));
        return text;
    }
    
    public static Spannable localizeCssSpannable(String localizationKey, String[] localizationArgs){
        if(!DeveloperPreferences.isCssEnabled()){
            return Spannable.Factory.getInstance().newSpannable(MarkupUtil.stripHtml(Localization.get(localizationKey, localizationArgs)));
        }
        Spannable text = htmlspanner.fromHtml(MarkupUtil.getStyleString() + Localization.get(localizationKey, localizationArgs));
        return text;
    }
    
    
    /*
     * CSS style classes used by GridEntityView which has its own pattern (probably silly)
     */
    
    public static Spannable getSpannable(String message){
        if(!DeveloperPreferences.isCssEnabled()){
            return Spannable.Factory.getInstance().newSpannable(MarkupUtil.stripHtml(message));
        }
        Spannable text = htmlspanner.fromHtml(message);
        return text;
    }
    
    public static Spannable getCustomSpannable(String style, String message){
        if(!DeveloperPreferences.isCssEnabled()){
            return Spannable.Factory.getInstance().newSpannable(MarkupUtil.stripHtml(message));
        }
        String mStyles = "<style> " + style + " </style>";
        Spannable text = htmlspanner.fromHtml(mStyles + message);
        return text;
    }
    
    /*
     * Util methods to help this class
     */
    
    public static String getStyle(String key){
       return CommCareApplication._().getCurrentApp().getStylizer().getStyle(key);
    }
    
    public static String getStyleString(){
        return CommCareApplication._().getCurrentApp().getStylizer().getStyleString();
    }
    
    public static String formatKeyVal(String key, String val){
        return "<style> #" + key + " " + getStyle(key) + " </style>";
    }
    
    public static String stripHtml(String html) {
        return Html.fromHtml(html).toString();
    }
}