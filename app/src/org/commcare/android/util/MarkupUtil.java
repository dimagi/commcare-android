package org.commcare.android.util;

import in.uncod.android.bypass.Bypass;
import net.nightwhistler.htmlspanner.HtmlSpanner;

import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.preferences.DeveloperPreferences;
import org.javarosa.core.services.locale.Localization;

import android.content.Context;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;

public class MarkupUtil {
    static HtmlSpanner htmlspanner = new HtmlSpanner();

    /*
     * Developer Preference helper classes
     */

    public static Spannable styleSpannable(Context c, String message){
        
        if(DeveloperPreferences.isCssEnabled()){
            
            return htmlspanner.fromHtml(MarkupUtil.getStyleString() + message);
        }

        if(DeveloperPreferences.isMarkdownEnabled()){
            
            return new SpannableString(generateMarkdown(c, message));
        }

        return Spannable.Factory.getInstance().newSpannable(MarkupUtil.stripHtml(message));
    }

    public static Spannable localizeStyleSpannable(Context c, String localizationKey){

        if(DeveloperPreferences.isCssEnabled()){
            return htmlspanner.fromHtml(MarkupUtil.getStyleString() + new String(Localization.get(localizationKey)));
        }

        if(DeveloperPreferences.isMarkdownEnabled()){
            return new SpannableString(localizeGenerateMarkdown(c, localizationKey));
        }

        return new SpannableString(Localization.get(localizationKey)); 
    }

    public static Spannable localizeStyleSpannable(Context c, String localizationKey, String localizationArg){

        if(DeveloperPreferences.isCssEnabled()){
            return htmlspanner.fromHtml(MarkupUtil.getStyleString() + Localization.get(localizationKey, localizationArg));
        }

        if(DeveloperPreferences.isMarkdownEnabled()){
            return new SpannableString(localizeGenerateMarkdown(c, localizationKey, new String[] {localizationArg}));
        }

        return new SpannableString(Localization.get(localizationKey, localizationArg));
    }



    public static Spannable localizeStyleSpannable(Context c, String localizationKey, String[] localizationArgs){
        if(DeveloperPreferences.isCssEnabled()){
            return htmlspanner.fromHtml(MarkupUtil.getStyleString() + Localization.get(localizationKey, localizationArgs));
        }

        if(DeveloperPreferences.isMarkdownEnabled()){
            return new SpannableString(localizeGenerateMarkdown(c, localizationKey, localizationArgs));
        }

        return Spannable.Factory.getInstance().newSpannable(MarkupUtil.stripHtml(Localization.get(localizationKey, localizationArgs)));
    }

    /*
     * Markdown styling utils from Bypass
     * https://github.com/Uncodin/bypass
     */

    public static CharSequence localizeGenerateMarkdown(Context c, String localizationKey){
        CharSequence mSequence = generateMarkdown(c, new String(Localization.get(localizationKey)));
        return mSequence;
    }

    public static CharSequence localizeGenerateMarkdown(Context c, String localizationKey, String[] localizationArgs){
        CharSequence mSequence = generateMarkdown(c, Localization.get(localizationKey, localizationArgs));
        return mSequence;
    }

    public static CharSequence generateMarkdown(Context c, String message){
        Bypass bypass = new Bypass(c);
        CharSequence mSequence = bypass.markdownToSpannable(convertCharacterEncodings(message));
        return mSequence;
    }
    
    /*
     * Perform conversions from encodings
     */
    
    public static String convertCharacterEncodings(String input){
        return convertNewlines(convertPoundSigns(input));
    }

    /*
     * Convert to newline
     */

    public static String convertNewlines(String input){
        
        return input.replace("%n", System.getProperty("line.separator"));
    }
    
    /*
     * Convert number signs
     */

    public static String convertPoundSigns(String input){
        
        return input.replace("%p", "#");
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


    public static String getStyleString(){
        
        if(CommCareApplication._() != null && CommCareApplication._().getCurrentApp() != null){
            return CommCareApplication._().getCurrentApp().getStylizer().getStyleString();
        } else{
            // fail silently? 
            return "";
        }
    }

    public static String formatKeyVal(String key, String val){
        return "<style> #" + key + " " + val + " </style>";
    }

    public static String stripHtml(String html) {
        return Html.fromHtml(html).toString();
    }
}