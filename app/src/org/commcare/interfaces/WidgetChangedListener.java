package org.commcare.interfaces;

import org.commcare.views.widgets.QuestionWidget;

public interface WidgetChangedListener {

    void widgetEntryChanged(QuestionWidget changedWidget);
}
