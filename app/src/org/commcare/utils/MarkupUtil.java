package org.commcare.utils;

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
    private static final HtmlSpanner htmlspanner = new HtmlSpanner();

    public static Spannable styleSpannable(Context c, String message) {
        if (DeveloperPreferences.isMarkdownEnabled()) {
            returnMarkdown(c, message);
        }

        if (DeveloperPreferences.isCssEnabled()) {
            returnCSS(message);
        }

        return new SpannableString(message);
    }

    public static Spannable localizeStyleSpannable(Context c, String localizationKey) {
        return styleSpannable(c, Localization.get(localizationKey));
    }

    public static Spannable localizeStyleSpannable(Context c, String localizationKey, String localizationArg) {
        return styleSpannable(c, Localization.get(localizationKey, localizationArg));
    }

    public static Spannable localizeStyleSpannable(Context c, String localizationKey, String[] localizationArgs) {
        return styleSpannable(c, Localization.get(localizationKey, localizationArgs));
    }

    public static Spannable returnMarkdown(Context c, String message) {
        return new SpannableString(generateMarkdown(c, message));
    }

    public static Spannable returnCSS(String message) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD) {
            return new SpannableString(Html.fromHtml(message));
        } else {
            return htmlspanner.fromHtml(MarkupUtil.getStyleString() + message);
        }
    }

    private static CharSequence generateMarkdown(Context c, String message) {
        Bypass bypass = new Bypass(c);
        return trimTrailingWhitespace(bypass.markdownToSpannable(convertCharacterEncodings(message)));
    }

    /**
     * Trims trailing whitespace. Removes any of these characters:
     * 0009, HORIZONTAL TABULATION
     * 000A, LINE FEED
     * 000B, VERTICAL TABULATION
     * 000C, FORM FEED
     * 000D, CARRIAGE RETURN
     * 001C, FILE SEPARATOR
     * 001D, GROUP SEPARATOR
     * 001E, RECORD SEPARATOR
     * 001F, UNIT SEPARATOR
     *
     * @return "" if source is null, otherwise string with all trailing whitespace removed
     * soruce: http://stackoverflow.com/questions/9589381/remove-extra-line-breaks-after-html-fromhtml
     */
    private static CharSequence trimTrailingWhitespace(CharSequence source) {
        if (source == null) {
            return "";
        }

        int i = source.length();

        // loop back to the first non-whitespace character
        while (--i >= 0 && Character.isWhitespace(source.charAt(i))) {
        }

        return source.subSequence(0, i + 1);
    }

    private static String convertCharacterEncodings(String input) {
        return convertNewlines(convertPoundSigns(input));
    }

    private static String convertNewlines(String input) {
        return input.replace("\\n", System.getProperty("line.separator"));
    }

    private static String convertPoundSigns(String input) {
        return input.replace("\\#", "#");
    }
    
    /*
     * CSS style classes used by GridEntityView which has its own pattern (probably silly)
     */

    public static Spannable getSpannable(String message) {
        if (!DeveloperPreferences.isCssEnabled()) {
            return Spannable.Factory.getInstance().newSpannable(MarkupUtil.stripHtml(message));
        }
        return htmlspanner.fromHtml(message);
    }

    public static Spannable getCustomSpannable(String style, String message) {
        if (!DeveloperPreferences.isCssEnabled()) {
            return Spannable.Factory.getInstance().newSpannable(MarkupUtil.stripHtml(message));
        }
        String mStyles = "<style> " + style + " </style>";
        return htmlspanner.fromHtml(mStyles + message);
    }


    private static String getStyleString() {
        if (CommCareApplication._() != null && CommCareApplication._().getCurrentApp() != null) {
            return CommCareApplication._().getCurrentApp().getStylizer().getStyleString();
        } else {
            // fail silently? 
            return "";
        }
    }

    public static String formatKeyVal(String key, String val) {
        return "<style> #" + key + " " + val + " </style>";
    }

    private static String stripHtml(String html) {
        return Html.fromHtml(html).toString();
    }
}
