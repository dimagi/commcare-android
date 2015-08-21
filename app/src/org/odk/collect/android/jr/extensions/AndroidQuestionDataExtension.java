package org.odk.collect.android.jr.extensions;

import org.javarosa.core.model.QuestionDataExtension;
import org.odk.collect.android.widgets.QuestionWidget;

/**
 * Created by amstone326 on 8/21/15.
 */
public abstract class AndroidQuestionDataExtension implements QuestionDataExtension {

    private QuestionWidget widget;

    public AndroidQuestionDataExtension() {}

    public void setWidget(QuestionWidget w) {
        this.widget = w;
    }

    public void applyExtension() {
        if (this.widget == null) {

        }
        applyExtensionToWidget(this.widget);
    }

    public abstract void applyExtensionToWidget(QuestionWidget w);
}
