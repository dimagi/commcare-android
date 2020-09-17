package org.commcare.views.widgets;

import android.content.Context;
import android.view.LayoutInflater;
import android.widget.Button;

import org.commcare.dalvik.R;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.form.api.FormEntryPrompt;

/**
 * @author $|-|!˅@M
 */
public abstract class SelectQuestionWidget extends QuestionWidget {

    protected Button clearButton;

    public SelectQuestionWidget(Context context, FormEntryPrompt p) {
        super(context, p);
    }

    public SelectQuestionWidget(Context context, FormEntryPrompt p, boolean inCompactGroup) {
        super(context, p, inCompactGroup);
    }

    protected void addClearButton(Context context, boolean show) {
        clearButton = (Button) LayoutInflater.from(context).inflate(R.layout.blue_outlined_button, this, false);
        clearButton.setText(Localization.get("button.clear.title"));
        clearButton.setVisibility(show ? VISIBLE : GONE);
        clearButton.setOnClickListener(view -> {
            clearAnswer();
            widgetEntryChanged();
        });
        addView(clearButton);
    }
}
