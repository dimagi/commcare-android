package org.commcare.android.adapters;


import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import org.commcare.android.view.GridMediaView;
import org.commcare.suite.model.MenuDisplayable;
import org.commcare.util.CommCarePlatform;
import org.javarosa.core.services.locale.Localizer;

/**
 * Overrides MenuAdapter to provide a different tile (MenuGridEntryView)
 * instead of the normal row view
 *
 * @author wspride
 */

public class GridMenuAdapter extends MenuAdapter {

    public GridMenuAdapter(Context context, CommCarePlatform platform,
                           String menuID) {
        super(context, platform, menuID);
    }

    @Override
    public View getView(int i, View v, ViewGroup vg) {

        MenuDisplayable mDisplayable = displayableData[i];

        GridMediaView emv = (GridMediaView) v;
        String mQuestionText = textViewHelper(mDisplayable);
        if (emv == null) {
            emv = new GridMediaView(context);
        }

        //Final change, remove any numeric context requests. J2ME uses these to 
        //help with numeric navigation.
        if (mQuestionText != null) {
            mQuestionText = Localizer.processArguments(mQuestionText, new String[]{""}).trim();
        }
        emv.setAVT(mQuestionText, mDisplayable.getImageURI());
        return emv;

    }
}
