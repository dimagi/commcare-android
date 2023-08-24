package org.commcare.activities.connect;

import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;

/**
 * UI Controller, handles UI interaction with the owning Activity
 *
 * @author dviggiano
 */
@ManagedUi(R.layout.screen_connect_pictures)
public class ConnectIdPicturesActivityUiController implements CommCareActivityUIController {
    @UiElement(value = R.id.connect_pictures_face_icon)
    private ImageView faceIcon;
    @UiElement(value = R.id.connect_pictures_face)
    private TextView faceTextView;
    @UiElement(value = R.id.connect_pictures_id_icon)
    private ImageView idIcon;
    @UiElement(value = R.id.connect_pictures_id)
    private TextView idTextView;
    @UiElement(value = R.id.connect_pictures_button)
    private Button continueButton;

    private boolean faceCompleted;
    private boolean idCompleted;

    protected final ConnectIdPicturesActivity activity;

    public ConnectIdPicturesActivityUiController(ConnectIdPicturesActivity activity) {
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
        continueButton.setText(activity.getString(faceCompleted && idCompleted ?
                R.string.connect_pictures_continue : R.string.connect_pictures_skip));
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
