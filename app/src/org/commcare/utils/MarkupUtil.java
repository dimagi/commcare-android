package org.commcare.utils;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.UnderlineSpan;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import net.nightwhistler.htmlspanner.HtmlSpanner;
import net.nightwhistler.htmlspanner.SpanStack;
import net.nightwhistler.htmlspanner.TagNodeHandler;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.preferences.DeveloperPreferences;
import org.commonmark.node.Node;
import org.htmlcleaner.TagNode;
import org.javarosa.core.services.locale.Localization;

import java.io.File;

import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.LinkResolverDef;
import io.noties.markwon.Markwon;
import io.noties.markwon.MarkwonConfiguration;
import io.noties.markwon.MarkwonPlugin;

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

    /**
     *
     * @param textView textview we want to set Markdown to
     * @param markDownBuilder message containing the markdown we want to apply
     * @param nonMarkDownSuffix message to be applied as suffix to {@param markDownBuilder} as it is without any markdown formatting
     */
    public static void setMarkdown(TextView textView, SpannableStringBuilder markDownBuilder, SpannableStringBuilder nonMarkDownSuffix) {
        Markwon markwon = CommCareApplication.getMarkwonInstance();
        Node node = markwon.parse(markDownBuilder.toString());
        final Spanned markdownBuilder = markwon.render(node);
        if(markdownBuilder instanceof SpannableStringBuilder) {
            SpannableStringBuilder markdown = ((SpannableStringBuilder)markdownBuilder);
            markdown.append(nonMarkDownSuffix);
        }
        markwon.setParsedMarkdown(textView, markdownBuilder);
    }

    public static Spannable returnCSS(String message) {
        return htmlspanner.fromHtml(MarkupUtil.getStyleString() + message);
    }

    private static CharSequence generateMarkdown(Context c, String message) {
        return trimTrailingWhitespace(CommCareApplication.getMarkwonInstance()
                .toMarkdown(convertCharacterEncodings(message)));
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

    public static MarkwonPlugin getLinkResolverPlugin() {
        return new AbstractMarkwonPlugin() {
            @Override
            public void configureConfiguration(@NonNull MarkwonConfiguration.Builder builder) {
                builder.linkResolver(new LinkResolverDef() {
                    @Override
                    public void resolve(@NonNull View view, @NonNull String link) {
                        if (link.startsWith("file:///")) {
                            File file = new File(org.apache.commons.lang3.StringUtils.removeStart(link, "file://"));
                            Uri uri = FileUtil.getUriForExternalFile(view.getContext(), file);
                            String mimeType = FileUtil.getMimeType(file.getAbsolutePath());
                            Intent i = new Intent();
                            i.setAction(Intent.ACTION_VIEW);
                            i.setDataAndType(uri, mimeType);
                            i.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                            UriToFilePath.grantPermissionForUri(view.getContext(), i, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            try {
                                view.getContext().startActivity(i);
                            } catch (ActivityNotFoundException e) {
                                Toast.makeText(view.getContext(),
                                        org.commcare.utils.StringUtils.getStringSpannableRobust(view.getContext(),
                                                R.string.activity_not_found,
                                                " this file."),
                                        Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            super.resolve(view, link);
                        }
                    }
                });
            }
        };
    }
}
