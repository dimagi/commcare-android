package org.commcare.activities;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.View.OnClickListener;
import android.widget.Toast;

import com.google.android.gms.common.api.ResolvableApiException;

import org.commcare.activities.components.FormEntryConstants;
import org.commcare.dalvik.R;
import org.commcare.interfaces.RuntimePermissionRequester;
import org.commcare.interfaces.TimerListener;
import org.commcare.location.CommCareLocationController;
import org.commcare.location.CommCareLocationControllerFactory;
import org.commcare.location.CommCareLocationListener;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.utils.GeoUtils;
import org.commcare.utils.Permissions;
import org.commcare.utils.StringUtils;
import org.commcare.utils.TimeoutTimer;
import org.commcare.views.dialogs.CommCareAlertDialog;
import org.commcare.views.dialogs.DialogCreationHelpers;
import org.commcare.views.dialogs.GeoProgressDialog;
import org.javarosa.core.services.locale.Localization;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

/**
 * Activity that blocks user until the current GPS location is captured
 */
public class GeoPointActivity extends AppCompatActivity implements TimerListener, CommCareLocationListener, RuntimePermissionRequester {

    private GeoProgressDialog locationDialog;
    private Location location;
    private CommCareLocationController locationController;

    public final static int DEFAULT_MAX_WAIT_IN_SECS = 60;
    private final static int LOCATION_PERMISSION_REQ = 101;
    private final static int LOCATION_SETTING_REQ = 102;
    private final static String[] requiredPermissions = new String[]{
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION};

    private TimeoutTimer mTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(StringUtils.getStringRobust(this, R.string.application_name) +
                " > " + StringUtils.getStringRobust(this, R.string.get_location));

        locationController = CommCareLocationControllerFactory.getLocationController(this, this);

        setupLocationDialog();
        long mLong = -1;
        if (savedInstanceState != null) {
            mLong = savedInstanceState.getLong("millisRemaining", -1);
        }
        if (mLong > 0) {
            mTimer = new TimeoutTimer(mLong, this);
        } else {
            mTimer = new TimeoutTimer(HiddenPreferences.getGpsWidgetTimeoutInMilliseconds(), this);
        }
        mTimer.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        locationController.stop();
        // We're not using managed dialogs, so we have to dismiss the dialog to prevent it from
        // leaking memory.
        if (locationDialog != null && locationDialog.isShowing()) {
            locationDialog.dismiss();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        requestLocation();
    }

    @Override
    protected void onDestroy() {
        locationController.destroy();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case LOCATION_PERMISSION_REQ:
                boolean granted = grantResults.length > 0;
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        granted = false;
                    }
                }
                if (granted) {
                    locationController.start();
                } else {
                    Toast.makeText(this,
                            Localization.get("permission.location.denial.message"),
                            Toast.LENGTH_LONG).show();
                    returnLocation();
                }
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case LOCATION_SETTING_REQ:
                if (resultCode == RESULT_OK) {
                    locationController.start();
                } else {
                    returnLocation();
                }
        }
    }

    private void handleNoLocationProviders() {
        DialogInterface.OnCancelListener onCancelListener = dialog -> {
            location = null;
            GeoPointActivity.this.finish();
        };

        DialogInterface.OnClickListener onChangeListener = (dialog, i) -> {
            switch (i) {
                case DialogInterface.BUTTON_POSITIVE:
                    GeoUtils.goToProperLocationSettingsScreen(GeoPointActivity.this);
                    break;
                case DialogInterface.BUTTON_NEGATIVE:
                    location = null;
                    GeoPointActivity.this.finish();
                    break;
            }
            dialog.dismiss();
        };

        GeoUtils.showNoGpsDialog(this, onChangeListener, onCancelListener);
    }

    /**
     * Sets up the look and actions for the progress dialog while the GPS is searching.
     */
    private void setupLocationDialog() {
        // dialog displayed while fetching gps location

        OnClickListener cancelButtonListener = v -> {
            location = null;
            finish();
        };

        OnClickListener okButtonListener = v -> returnLocation();

        locationDialog = new GeoProgressDialog(this, StringUtils.getStringRobust(this, R.string.found_location),
                StringUtils.getStringRobust(this, R.string.finding_location));
        locationDialog.setImage(getResources().getDrawable(R.drawable.green_check_mark));
        locationDialog.setMessage(StringUtils.getStringRobust(this, R.string.please_wait_long));
        locationDialog.setOKButton(StringUtils.getStringRobust(this, R.string.accept_location),
                okButtonListener);
        locationDialog.setCancelButton(StringUtils.getStringRobust(this, R.string.cancel_location),
                cancelButtonListener);
    }

    private void returnLocation() {
        if (location != null) {
            Intent i = new Intent();
            i.putExtra(FormEntryConstants.LOCATION_RESULT, GeoUtils.locationToString(location));
            setResult(RESULT_OK, i);
        }
        finish();
    }

    private void onLocationChanged(Location location) {
        this.location = location;
        if (this.location != null) {
            String accuracy = truncateDouble(this.location.getAccuracy());
            locationDialog.setMessage(StringUtils.getStringRobust(this,
                    R.string.location_provider_accuracy, accuracy));

            // If location is accurate, we're done
            if (this.location.getAccuracy() <= HiddenPreferences.getGpsWidgetGoodAccuracy()) {
                returnLocation();
            }

            // If location isn't great but might be acceptable, notify
            // the user and let them decide whether or not to record it
            locationDialog.setLocationFound(
                    this.location.getAccuracy() < HiddenPreferences.getGpsWidgetAcceptableAccuracy()
                            || mTimer.getMillisUntilFinished() == 0
            );
        }
    }

    private String truncateDouble(float number) {
        DecimalFormat df = new DecimalFormat("#.##");
        return df.format(number);
    }

    @Override
    public void notifyTimerFinished() {
        onLocationChanged(location);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putLong("millisRemaining", mTimer.getMillisUntilFinished());
        super.onSaveInstanceState(savedInstanceState);
    }

    private void requestLocation() {
        if (Permissions.missingAppPermission(this, requiredPermissions)) {
            if (Permissions.shouldShowPermissionRationale(this, requiredPermissions)) {
                CommCareAlertDialog dialog =
                        DialogCreationHelpers.buildPermissionRequestDialog(this, this,
                                LOCATION_PERMISSION_REQ,
                                Localization.get("permission.location.title"),
                                Localization.get("permission.location.message"));
                dialog.showNonPersistentDialog();
            } else {
                missingPermissions();
            }
        } else {
            locationController.start();
        }
    }

    @Override
    public void onLocationRequestStart() {
        locationDialog.show();
    }

    @Override
    public void onLocationResult(@NotNull Location result) {
        onLocationChanged(result);
    }

    @Override
    public void missingPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION},
                LOCATION_PERMISSION_REQ);
    }

    @Override
    public void onLocationRequestFailure(@NotNull CommCareLocationListener.Failure failure) {
        if (failure instanceof CommCareLocationListener.Failure.ApiException) {
            Exception exception = ((CommCareLocationListener.Failure.ApiException)failure).getException();
            if (exception instanceof ResolvableApiException) {
                try {
                    ((ResolvableApiException)exception).startResolutionForResult(this, LOCATION_SETTING_REQ);
                } catch (IntentSender.SendIntentException e) {
                    e.printStackTrace();
                }
            } else {
                // ignore and return, we can't do anything.
                returnLocation();
            }
        } else {
            handleNoLocationProviders();
        }
    }

    @Override
    public void requestNeededPermissions(int requestCode) {
        missingPermissions();
    }
}
