package org.commcare.android.util;

import net.nightwhistler.htmlspanner.HtmlSpanner;
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
}
