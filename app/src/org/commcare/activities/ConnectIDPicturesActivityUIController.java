package org.commcare.activities;

import android.graphics.Color;
import android.widget.Button;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;
import org.javarosa.core.services.locale.Localization;

@ManagedUi(R.layout.screen_connect_pictures)
public class ConnectIDPicturesActivityUIController implements CommCareActivityUIController {

    @UiElement(value = R.id.connect_pictures_title, locale = "connect.pictures.title")
    private TextView titleTextView;

    @UiElement(value = R.id.connect_pictures_label, locale = "connect.pictures.label")
    private TextView labelTextView;
    @UiElement(value = R.id.connect_pictures_face, locale = "connect.pictures.face")
    private TextView faceTextView;
    @UiElement(value = R.id.connect_pictures_id, locale = "connect.pictures.id")
    private TextView idTextView;

    @UiElement(value = R.id.connect_pictures_button)
    private Button continueButton;

    private boolean faceCompleted;
    private boolean idCompleted;

    protected final ConnectIDPicturesActivity activity;

    public ConnectIDPicturesActivityUIController(ConnectIDPicturesActivity activity) {
        this.activity = activity;
    }

    @Override
    public void setupUI() {
        faceTextView.setOnClickListener(arg0 -> activity.getFacePhoto());
        idTextView.setOnClickListener(arg0 -> activity.getIdPhoto());
        continueButton.setOnClickListener(arg0 -> activity.handleContinueButton());

        setFaceStatus(false);
        setIdStatus(false);
    }

    @Override
    public void refreshView() {

    }

    private void updateButtonText() {
        continueButton.setText(Localization.get(faceCompleted && idCompleted ?
                "connect.pictures.continue" : "connect.pictures.skip"));
    }

    public void setFaceStatus(boolean completed) {
        faceCompleted = completed;
        setStatus(faceTextView, completed);

        updateButtonText();
    }

    public void setIdStatus(boolean completed) {
        idCompleted = completed;
        setStatus(idTextView, completed);

        updateButtonText();
    }

    private void setStatus(TextView textView, boolean completed) {
        int color = completed ? Color.GREEN : Color.RED;

        textView.setTextColor(color);
        textView.setEnabled(!completed);
    }
}
