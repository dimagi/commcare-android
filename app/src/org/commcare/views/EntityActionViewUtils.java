package org.commcare.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.v7.widget.CardView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.commcare.activities.CommCareActivity;
import org.commcare.activities.EntitySelectActivity;
import org.commcare.dalvik.R;
import org.commcare.suite.model.Action;
import org.commcare.suite.model.DisplayData;
import org.commcare.utils.FileUtil;
import org.commcare.utils.MediaUtil;
import org.commcare.views.media.AudioButton;

/**
 * Setup view for entity list action ('register', 'claim case', etc)
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class EntityActionViewUtils {
    public static void buildActionView(FrameLayout actionCardView,
                                       Action action,
                                       CommCareActivity commCareActivity) {
        DisplayData displayData = action.getDisplay().evaluate();

        setupActionAudio(displayData.getAudioURI(), actionCardView);
        setupActionImage(displayData.getImageURI(), actionCardView, commCareActivity);

        TextView text = (TextView)actionCardView.findViewById(R.id.text);
        text.setText(displayData.getName().toUpperCase());

        setupActionClickListener(actionCardView, action, commCareActivity);
    }

    private static void setupActionAudio(String audioURI, FrameLayout actionCardView) {

        if (audioURI != null) {
            AudioButton audioButton = (AudioButton)actionCardView.findViewById(R.id.audio);
            if (FileUtil.referenceFileExists(audioURI)) {
                audioButton.setVisibility(View.VISIBLE);
                audioButton.resetButton(audioURI, true);
            }
        }
    }

    private static void setupActionImage(String imageURI, FrameLayout actionCardView,
                                         Context context) {
        if (imageURI != null) {
            ImageView icon = (ImageView)actionCardView.findViewById(R.id.icon);
            int iconDimension = (int)context.getResources().getDimension(R.dimen.menu_icon_size);
            Bitmap b = MediaUtil.inflateDisplayImage(context, imageURI, iconDimension, iconDimension);
            if (b != null) {
                icon.setVisibility(View.VISIBLE);
                icon.setImageBitmap(b);
            }
        }
    }

    private static void setupActionClickListener(FrameLayout actionCardView,
                                                 final Action action,
                                                 final CommCareActivity commCareActivity) {
        CardView cardView = (CardView)actionCardView.findViewById(R.id.card_body);
        cardView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EntitySelectActivity.triggerDetailAction(action, commCareActivity);
            }
        });
    }
}
