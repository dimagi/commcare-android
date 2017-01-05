package org.commcare.android.shadows;

import android.util.Log;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowLog;

import java.util.ArrayList;

/**
 * Extend ShadowLog to allow hiding logs with a given tag.
 * Needed because robolectric doesn't provide a way to filter out non-app logs.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
@Implements(Log.class)
public class CommCareShadowLog extends ShadowLog {
    private static final ArrayList<String> tagsToIgnore;

    static {
        tagsToIgnore = new ArrayList<>();
        tagsToIgnore.add("CursorWindowStats");
        tagsToIgnore.add("SQLiteCursor");
        tagsToIgnore.add("SQLiteQueryBuilder");
        tagsToIgnore.add("Typeface");
        // TODO PLM: this shows a warning we should fix:
        // tagsToIgnore.add("SQLiteConnectionPool");
    }

    private static boolean shouldShowTag(String tag) {
        return !tagsToIgnore.contains(tag);
    }

    @Implementation
    public static void e(String tag, String msg) {
        e(tag, msg, null);
    }

    @Implementation
    public static void e(String tag, String msg, Throwable throwable) {
        if (shouldShowTag(tag)) {
            ShadowLog.e(tag, msg, throwable);
        }
    }

    @Implementation
    public static void d(String tag, String msg) {
        d(tag, msg, null);
    }

    @Implementation
    public static void d(String tag, String msg, Throwable throwable) {
        if (shouldShowTag(tag)) {
            ShadowLog.d(tag, msg, throwable);
        }
    }

    @Implementation
    public static void i(String tag, String msg) {
        i(tag, msg, null);
    }

    @Implementation
    public static void i(String tag, String msg, Throwable throwable) {
        if (shouldShowTag(tag)) {
            ShadowLog.i(tag, msg, throwable);
        }
    }

    @Implementation
    public static void v(String tag, String msg) {
        v(tag, msg, null);
    }

    @Implementation
    public static void v(String tag, String msg, Throwable throwable) {
        if (shouldShowTag(tag)) {
            ShadowLog.v(tag, msg, throwable);
        }
    }

    @Implementation
    public static void w(String tag, String msg) {
        w(tag, msg, null);
    }

    @Implementation
    public static void w(String tag, Throwable throwable) {
        w(tag, null, throwable);
    }


    @Implementation
    public static void w(String tag, String msg, Throwable throwable) {
        if (shouldShowTag(tag)) {
            ShadowLog.w(tag, msg, throwable);
        }
    }

    @Implementation
    public static void wtf(String tag, String msg) {
        wtf(tag, msg, null);
    }

    @Implementation
    public static void wtf(String tag, String msg, Throwable throwable) {
        if (shouldShowTag(tag)) {
            ShadowLog.wtf(tag, msg, throwable);
        }
    }
}
