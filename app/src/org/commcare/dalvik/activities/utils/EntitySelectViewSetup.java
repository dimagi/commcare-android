package org.commcare.dalvik.activities.utils;

import android.content.Context;
import android.graphics.drawable.LayerDrawable;
import android.util.TypedValue;
import android.widget.ListView;

import org.commcare.dalvik.R;

public class EntitySelectViewSetup {
    public static void setupDivider(Context context, ListView view, boolean useNewDivider) {
        if (useNewDivider) {
            int viewWidth = view.getWidth();
            // sometimes viewWidth is 0, and in this case we default to a reasonable value taken from dimens.xml
            int dividerWidth;
            if (viewWidth == 0) {
                dividerWidth = (int)context.getResources().getDimension(R.dimen.entity_select_divider_left_inset);
            } else {
                dividerWidth = (int)(viewWidth / 6.0);
            }
            dividerWidth += (int)context.getResources().getDimension(R.dimen.row_padding_horizontal);

            LayerDrawable dividerDrawable = (LayerDrawable)context.getResources().getDrawable(R.drawable.divider_case_list_modern);
            dividerDrawable.setLayerInset(0, dividerWidth, 0, 0, 0);

            view.setDivider(dividerDrawable);
        } else {
            view.setDivider(null);
        }

        view.setDividerHeight((int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1,
                context.getResources().getDisplayMetrics()));
    }
}
