package org.commcare.activities;

import android.widget.Button;
import android.widget.TextView;

import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;

@ManagedUi("http_request_layout")
public abstract class QueryRequestUiControllerBase {

    @UiElement(value = "request_button", locale = "query.button")
    protected Button queryButton;

    @UiElement("error_message")
    protected TextView errorTextView;
}
