package org.commcare.views.widgets;

import android.content.Context;
import android.view.LayoutInflater;
import android.widget.Button;

import org.commcare.dalvik.R;
import org.javarosa.form.api.FormEntryPrompt;

/**
 * A utility for all the widgets that want to show a clear button.
 * This class will provide the code for creation of clearButton using {@link ClearableWidget#setupClearButton(Context, String)}.
 * Subclasses will need to add the button to the view by themselves.
 *
 * See {@link SelectOneWidget} for example.
 * @author $|-|!˅@M
 */
public abstract class ClearableWidget extends QuestionWidget {

    protected Button clearButton;

    public ClearableWidget(Context context, FormEntryPrompt p) {
        super(context, p);
    }

    public ClearableWidget(Context context, FormEntryPrompt p, boolean inCompactGroup) {
        super(context, p, inCompactGroup);
    }

    protected void setupClearButton(Context context, String text, int visibility) {
        clearButton = (Button) LayoutInflater.from(context).inflate(R.layout.blue_outlined_button, this, false);
        clearButton.setText(text);
        clearButton.setVisibility(visibility);
        clearButton.setOnClickListener(view -> {
            clearAnswer();
            widgetEntryChanged();
        });
    }
}
