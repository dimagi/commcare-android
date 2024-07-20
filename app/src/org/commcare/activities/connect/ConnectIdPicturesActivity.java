package org.commcare.activities.connect;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Toast;

import org.commcare.activities.CommCareActivity;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.RuntimePermissionRequester;
import org.commcare.interfaces.WithUIController;
import org.commcare.utils.Permissions;
import org.commcare.views.dialogs.CommCareAlertDialog;
import org.commcare.views.dialogs.DialogCreationHelpers;
import org.commcare.views.widgets.ImageWidget;
import org.javarosa.core.services.locale.Localization;

import androidx.core.app.ActivityCompat;

/**
 * NOTE: Not currently in use
 * Shows a page that attempts to get the user to take a picture of their face and photo ID
 *
 * @author dviggiano
 */
public class ConnectIdPicturesActivity extends CommCareActivity<ConnectIdPicturesActivity>
        implements WithUIController, RuntimePermissionRequester {
    private static final int FACE_REQUEST = 1;
    private static final int ID_REQUEST = 2;

    private ConnectIdPicturesActivityUiController uiController;

    private int requestedPhoto;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        uiController.setupUI();
    }

    @Override
    public CommCareActivityUIController getUIController() {
        return uiController;
    }

    @Override
    public void initUIController() {
        uiController = new ConnectIdPicturesActivityUiController(this);
    }

    public void getFacePhoto() {
        getPhoto(FACE_REQUEST);
    }

    public void getIdPhoto() {
        getPhoto(ID_REQUEST);
    }

    private void getPhoto(int requestCode) {
        requestedPhoto = requestCode;

        if (Permissions.missingAppPermission(this, Manifest.permission.CAMERA)) {
            if (Permissions.shouldShowPermissionRationale(this, Manifest.permission.CAMERA)) {
                CommCareAlertDialog dialog =
                        DialogCreationHelpers.buildPermissionRequestDialog(this, this,
                                ImageWidget.REQUEST_CAMERA_PERMISSION,
                                Localization.get("permission.camera.title"),
                                Localization.get("permission.camera.message"));
                dialog.showNonPersistentDialog();
            } else {
                this.requestNeededPermissions(ImageWidget.REQUEST_CAMERA_PERMISSION);
            }
        } else {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            startActivityForResult(intent, requestCode);
        }
    }

    @Override
    public void requestNeededPermissions(int requestCode) {
        if (requestCode == ImageWidget.REQUEST_CAMERA_PERMISSION) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    requestCode);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // If request is cancelled, the result arrays are empty.
        boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (permissions[0].contentEquals(Manifest.permission.CAMERA)) {
            if (granted) {
                getPhoto(requestedPhoto);
            } else {
                Toast.makeText(this,
                        Localization.get("permission.camera.denial.message"),
                        Toast.LENGTH_SHORT).show();
            }
        }
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
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case FACE_REQUEST -> uiController.setFaceStatus(true);
                case ID_REQUEST -> uiController.setIdStatus(true);
            }
        }

        super.onActivityResult(requestCode, resultCode, intent);
    }
}
