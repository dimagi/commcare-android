package org.commcare.activities;

import android.widget.Button;
import android.widget.ImageView;
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
    @UiElement(value = R.id.connect_pictures_face_icon)
    private ImageView faceIcon;
    @UiElement(value = R.id.connect_pictures_face, locale = "connect.pictures.face")
    private TextView faceTextView;

    @UiElement(value = R.id.connect_pictures_id_icon)
    private ImageView idIcon;
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
        setStatus(faceTextView, faceIcon, completed);

        updateButtonText();
    }

    public void setIdStatus(boolean completed) {
        idCompleted = completed;
        setStatus(idTextView, idIcon, completed);

        updateButtonText();
    }

    private void setStatus(TextView textView, ImageView iconView, boolean completed) {
        int image = completed ? R.drawable.checkmark : R.drawable.eye;

        iconView.setImageResource(image);
        textView.setEnabled(!completed);
    }
}
