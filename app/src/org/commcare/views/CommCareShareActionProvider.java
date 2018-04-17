package org.commcare.views;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.view.View;
import android.widget.ShareActionProvider;

import org.commcare.dalvik.R;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

// We are only using this to override shareActionProvider icon and should remove
// this once we swtich to AppCompat and instead provide the icon from theme.
@RequiresApi(api = Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class CommCareShareActionProvider extends ShareActionProvider {

    private final Context mContext;

    public CommCareShareActionProvider(Context context) {
        super(context);
        mContext = context;
    }

    @Override
    public View onCreateActionView() {
        View chooserView = super.onCreateActionView();

        Drawable icon = mContext.getResources().getDrawable(R.drawable.ic_share_24dp);

        Class clazz = chooserView.getClass();

        //reflection
        try {
            Method method = clazz.getMethod("setExpandActivityOverflowButtonDrawable", Drawable.class);
            method.invoke(chooserView, icon);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        return chooserView;
    }
}
