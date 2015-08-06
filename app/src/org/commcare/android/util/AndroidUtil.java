package org.commcare.android.util;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;

import org.commcare.dalvik.BuildConfig;
import org.javarosa.core.util.DataUtil;
import org.javarosa.core.util.DataUtil.UnionLambda;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author ctsims
 */
public class AndroidUtil {
    private static final AtomicInteger sNextGeneratedId = new AtomicInteger(1);

    /**
     * Generate a value suitable for use in setId(int).
     * This value will not collide with ID values generated at build time by aapt for R.id.
     *
     * @return a generated ID value
     */
    @SuppressLint("NewApi")
    public static int generateViewId() {
        //raw implementation for < API 17
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
            for (;;) {
                final int result = sNextGeneratedId.get();
                // aapt-generated IDs have the high byte nonzero; clamp to the range under that.
                int newValue = result + 1;
                if (newValue > 0x00FFFFFF) newValue = 1; // Roll over to 1, not 0.
                if (sNextGeneratedId.compareAndSet(result, newValue)) {
                    return result;
                }
            }
        } else {
            //Whatever the current implementation is otherwise
            return View.generateViewId();
        }
    }
    
    /**
     * Initialize platform specific methods for common handlers
     */
    public static void initializeStaticHandlers() {
        DataUtil.setUnionLambda(new AndroidUnionLambda());
    }
    
    public static class AndroidUnionLambda extends UnionLambda {
        public <T> Vector<T> union(Vector<T> a, Vector<T> b) {
            //This is kind of (ok, so really) awkward looking, but we can't use sets in 
            //ccj2me (Thanks, Nokia!) also, there's no _collections_ interface in
            //j2me (thanks Sun!) so this is what we get.
            HashSet<T> joined = new HashSet<T>(a);
            joined.addAll(a);
            
            HashSet<T> other = new HashSet<T>();
            other.addAll(b);
            
            joined.retainAll(other);
            
            a.clear();
            a.addAll(joined);
            return a;
        }
    }

    public static void setClickListenersForEverything(Activity activity, ViewGroup v) {
        if (BuildConfig.DEBUG) {
            ViewGroup layout = v != null ? v : (ViewGroup) activity.findViewById(android.R.id.content);
            LinkedList<View> views = new LinkedList<View>();
            views.add(layout);
            for (int i = 0; !views.isEmpty(); i++) {
                View child = views.getFirst();
                views.removeFirst();
                Log.i("GetID", "Adding onClickListener to view " + child);
                child.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String vid;
                        try {
                            vid = "View id is: " + v.getResources().getResourceName(v.getId()) + " ( " + v.getId() + " )";
                        } catch (Resources.NotFoundException excp) {
                            vid = "View id is: " + v.getId();
                        }
                        Log.i("CLK", vid);
                    }
                });
                if(child instanceof ViewGroup) {
                    ViewGroup vg = (ViewGroup) child;
                    for (int j = 0; j < vg.getChildCount(); j++) {
                        View gchild = vg.getChildAt(j);
                        if (!views.contains(gchild)) views.add(gchild);
                    }
                }
            }
        }
    }

    public static void setClickListenersForEverything(Activity act){
        setClickListenersForEverything(act, (ViewGroup) act.findViewById(android.R.id.content));
    }

    /**
     * Returns an int array with the color values for the given attributes (R.attr).
     */
    public static int[] getThemeColorIDs(final Context context, final int[] attrs){
        int[] colors = new int[attrs.length];
        Resources.Theme theme = context.getTheme();
        for (int i = 0; i < attrs.length; i++) {
            TypedValue typedValue = new TypedValue();
            theme.resolveAttribute(attrs[i], typedValue, true);
            colors[i] = typedValue.data;
        }
        return colors;
    }

    public static boolean isNetworkAvailable(Context ctx) {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }
}
