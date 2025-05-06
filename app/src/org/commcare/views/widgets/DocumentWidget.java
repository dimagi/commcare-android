package org.commcare.views.widgets;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.view.View;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;

import org.commcare.activities.components.FormEntryConstants;
import org.commcare.dalvik.R;
import org.commcare.logic.PendingCalloutInterface;
import org.commcare.utils.StringUtils;
import org.javarosa.form.api.FormEntryPrompt;

import androidx.appcompat.app.AppCompatActivity;

public class DocumentWidget extends MediaWidget {

    public DocumentWidget(Context context, final FormEntryPrompt prompt, PendingCalloutInterface pic) {
        super(context, prompt, pic);
    }

    @Override
    protected void initializeButtons() {
        mChooseButton = new MaterialButton(getContext());
        WidgetUtils.setupButton(mChooseButton,
                StringUtils.getStringSpannableRobust(getContext(), R.string.choose_document),
                !mPrompt.isReadOnly());

        mChooseButton.setOnClickListener(v -> {
            try {
                ((AppCompatActivity)getContext())
                        .startActivityForResult(WidgetUtils.createPickMediaIntent(getContext(), "application/*,text/*"),
                                FormEntryConstants.AUDIO_VIDEO_DOCUMENT_FETCH);
                pendingCalloutInterface.setPendingCalloutFormIndex(mPrompt.getIndex());
            } catch (ActivityNotFoundException e) {
                Toast.makeText(getContext(),
                        StringUtils.getStringSpannableRobust(getContext(),
                                R.string.activity_not_found, "choose document"),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void setupLayout() {
        addView(mChooseButton);
    }
    
    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        mChooseButton.setOnLongClickListener(l);
    }
}
