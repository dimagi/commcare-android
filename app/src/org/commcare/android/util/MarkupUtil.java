package org.commcare.android.util;

import android.content.Context;
import android.os.Build;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;

import net.nightwhistler.htmlspanner.HtmlSpanner;

import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.preferences.DeveloperPreferences;
import org.javarosa.core.services.locale.Localization;

import in.uncod.android.bypass.Bypass;

public class MarkupUtil {
    static final HtmlSpanner htmlspanner = new HtmlSpanner();

    /*
     * Developer Preference helper classes
     */

    public static Spannable styleSpannable(Context c, String message){

        if(DeveloperPreferences.isMarkdownEnabled()){
            returnMarkdown(c, message);
        }

        if(DeveloperPreferences.isCssEnabled()){
            returnCSS(message);
        }

        return new SpannableString(message);
    }

    public static Spannable localizeStyleSpannable(Context c, String localizationKey){
        return styleSpannable(c, Localization.get(localizationKey));
    }

    public static Spannable localizeStyleSpannable(Context c, String localizationKey, String localizationArg){
        return styleSpannable(c, Localization.get(localizationKey, localizationArg));
    }

    public static Spannable localizeStyleSpannable(Context c, String localizationKey, String[] localizationArgs){
        return styleSpannable( c, Localization.get(localizationKey, localizationArgs));
    }

    public static Spannable returnMarkdown(Context c, String message){
        return new SpannableString(generateMarkdown(c, message));
    }

    public static Spannable returnCSS(String message){
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD) {
            return new SpannableString(Html.fromHtml(message));
        } else {
            return htmlspanner.fromHtml(MarkupUtil.getStyleString() + message);
        }
    }


    /*
     * Markdown styling utils from Bypass
     * https://github.com/Uncodin/bypass
     */

    public static CharSequence localizeGenerateMarkdown(Context c, String localizationKey){
        CharSequence mSequence = generateMarkdown(c, Localization.get(localizationKey));
        return mSequence;
    }

    public static CharSequence localizeGenerateMarkdown(Context c, String localizationKey, String[] localizationArgs){
        CharSequence mSequence = generateMarkdown(c, Localization.get(localizationKey, localizationArgs));
        return mSequence;
    }

    public static CharSequence generateMarkdown(Context c, String message){
        Bypass bypass = new Bypass(c);
        CharSequence mSequence = trimTrailingWhitespace(bypass.markdownToSpannable(convertCharacterEncodings(message)));
        return mSequence;
    }
    
    /** Trims trailing whitespace. Removes any of these characters:
     * 0009, HORIZONTAL TABULATION
     * 000A, LINE FEED
     * 000B, VERTICAL TABULATION
     * 000C, FORM FEED
     * 000D, CARRIAGE RETURN
     * 001C, FILE SEPARATOR
     * 001D, GROUP SEPARATOR
     * 001E, RECORD SEPARATOR
     * 001F, UNIT SEPARATOR
     * @return "" if source is null, otherwise string with all trailing whitespace removed
     * soruce: http://stackoverflow.com/questions/9589381/remove-extra-line-breaks-after-html-fromhtml
     */
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
     * Perform conversions from encodings
     */

    public static String convertCharacterEncodings(String input){
        return convertNewlines(convertPoundSigns(input));
    }

    /*
     * Convert to newline
     */

    public static String convertNewlines(String input){
        return input.replace("\\n", System.getProperty("line.separator"));
    }

    /*
     * Convert number signs
     */

    public static String convertPoundSigns(String input){

        return input.replace("\\#", "#");
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