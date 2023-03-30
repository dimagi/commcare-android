package org.commcare.activities;

import android.content.Intent;
import android.os.Bundle;
import android.provider.MediaStore;

import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.WithUIController;

public class ConnectIDPicturesActivity extends CommCareActivity<ConnectIDPicturesActivity>
implements WithUIController {
    private static final int FACE_REQUEST = 1;
    private static final int ID_REQUEST = 2;

    private ConnectIDPicturesActivityUIController uiController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        uiController.setupUI();
    }

    @Override
    public CommCareActivityUIController getUIController() { return uiController; }

    @Override
    public void initUIController() { uiController = new ConnectIDPicturesActivityUIController(this); }

    public void getFacePhoto() {
        getPhoto(FACE_REQUEST);
    }

    public void getIdPhoto() {
        getPhoto(ID_REQUEST);
    }

    private void getPhoto(int requestCod) {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(intent, requestCod);
    }

    public void handleContinueButton() {
        finish(true);
    }

    public void finish(boolean success) {
        Intent intent = new Intent(getIntent());
        setResult(success ? RESULT_OK : RESULT_CANCELED, intent);
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if(resultCode == RESULT_OK) {
            switch (requestCode) {
                case FACE_REQUEST -> uiController.setFaceStatus(true);
                case ID_REQUEST -> uiController.setIdStatus(true);
            }
        }

        super.onActivityResult(requestCode, resultCode, intent);
    }
}
