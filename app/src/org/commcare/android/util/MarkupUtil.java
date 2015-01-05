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
            return (Spannable) generateMarkdown(c, message);
        }

        return Spannable.Factory.getInstance().newSpannable(MarkupUtil.stripHtml(message));
    }

    public static Spannable localizeStyleSpannable(Context c, String localizationKey){

        if(DeveloperPreferences.isCssEnabled()){
            return htmlspanner.fromHtml(MarkupUtil.getStyleString() + Localization.get(localizationKey));
        }

        if(DeveloperPreferences.isMarkdownEnabled()){
            return (Spannable) localizeMarkdownSpannable(c, localizationKey);
        }

        return new SpannableString(Localization.get(localizationKey));
    }

    public static Spannable localizeStyleSpannable(Context c, String localizationKey, String localizationArg){

        if(DeveloperPreferences.isCssEnabled()){
            return htmlspanner.fromHtml(MarkupUtil.getStyleString() + Localization.get(localizationKey, localizationArg));
        }

        if(DeveloperPreferences.isMarkdownEnabled()){
            return (Spannable) localizeMarkdownSpannable(c, localizationKey, new String[] {localizationArg});
        }

        return new SpannableString(Localization.get(localizationKey, localizationArg));
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
        CharSequence mSequence = generateMarkdown(c, Localization.get(localizationKey));
        return mSequence;
    }

    public static CharSequence localizeMarkdownSpannable(Context c, String localizationKey, String[] localizationArgs){
        CharSequence mSequence = generateMarkdown(c, Localization.get(localizationKey, localizationArgs));
        return mSequence;
    }

    public static CharSequence generateMarkdown(Context c, String message){
        Bypass bypass = new Bypass(c);
        CharSequence mSequence = trimTrailingWhitespace(bypass.markdownToSpannable(convertNewlines(message)));
        return mSequence;
    }

    /*
     * Convert to newline
     */

    public static String convertNewlines(String input){
        return input.replace("%n", System.getProperty("line.separator"));
    }

    public static CharSequence trimTrailingWhitespace(CharSequence source) {

        if(source == null)
            return "";

        int i = source.length();

        // loop back to the first non-whitespace character
        while(--i >= 0 && Character.isWhitespace(source.charAt(i))) {
        }

        return source.subSequence(0, i+1);
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
        try{
            return CommCareApplication._().getCurrentApp().getStylizer().getStyle(key);
        }catch(Exception e){
            e.printStackTrace();
            return null;
        }
    }

    public static String getStyleString(){
        try{
            String styleString = CommCareApplication._().getCurrentApp().getStylizer().getStyleString();
            return CommCareApplication._().getCurrentApp().getStylizer().getStyleString();

        } catch(Exception e){
            e.printStackTrace();
            return null;
        }
    }

    public static String formatKeyVal(String key, String val){
        return "<style> #" + key + " " + val + " </style>";
    }

    public static String stripHtml(String html) {
        return Html.fromHtml(html).toString();
    }
}