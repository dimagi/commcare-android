package org.commcare.utils;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Browser;
import android.support.annotation.NonNull;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.view.View;

import net.nightwhistler.htmlspanner.HtmlSpanner;
import net.nightwhistler.htmlspanner.SpanStack;
import net.nightwhistler.htmlspanner.TagNodeHandler;

import org.commcare.CommCareApplication;
import org.commcare.preferences.DeveloperPreferences;
import org.htmlcleaner.TagNode;
import org.javarosa.core.services.locale.Localization;

import java.io.File;

import in.uncod.android.bypass.Bypass;
import ru.noties.markwon.LinkResolverDef;
import ru.noties.markwon.Markwon;
import ru.noties.markwon.SpannableBuilder;
import ru.noties.markwon.SpannableConfiguration;
import ru.noties.markwon.renderer.SpannableRenderer;

public class MarkupUtil {

    public static class UnderlineHandler extends TagNodeHandler {

        @Override
        public void handleTagNode(TagNode node, SpannableStringBuilder builder,
                                  int start, int end, SpanStack spanStack) {
            spanStack.pushSpan(new UnderlineSpan(), start, end);
        }
    }

    private static final HtmlSpanner htmlspanner = new HtmlSpanner() {
        {
            this.registerHandler("u", new UnderlineHandler());
        }
    };

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
        return htmlspanner.fromHtml(MarkupUtil.getStyleString() + message);
    }

    private static CharSequence generateMarkdown(Context context, String message) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            return trimTrailingWhitespace(
                    new Bypass(context).markdownToSpannable(convertCharacterEncodings(message)));
        }
        return trimTrailingWhitespace(
                Markwon.markdown(markwonConfiguration(context), convertCharacterEncodings(message)));
    }

    private static SpannableConfiguration markwonConfiguration;

    private static SpannableConfiguration markwonConfiguration(Context context) {
        if (markwonConfiguration == null) {
            SpannableConfiguration.Builder builder = SpannableConfiguration.builder(context);
            builder.linkResolver(new LinkResolver(context));
            markwonConfiguration = builder.build();
        }
        return markwonConfiguration;
    }

    static class LinkResolver extends LinkResolverDef {

        Context context;

        LinkResolver(Context context) {
            this.context = context;
        }

        @Override
        public void resolve(View view, @NonNull String link) {
            if (!link.startsWith("/")) {
                // If not an absolute path, assume in app data
                link = CommCareApplication.instance().getAndroidFsRoot() + link;
            }
            final Uri uri = FileUtil.getUriForExternalFile(context, link);
            final Context context = view.getContext();
            final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.putExtra(Browser.EXTRA_APPLICATION_ID, context.getPackageName());
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                context.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Log.w("LinkResolverDef", "Actvity was not found for intent, " + intent.toString());
            }
        }
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
        if (CommCareApplication.instance() != null && CommCareApplication.instance().getCurrentApp() != null) {
            return CommCareApplication.instance().getCurrentApp().getStylizer().getStyleString();
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
