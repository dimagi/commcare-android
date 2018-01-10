package org.commcare.activities;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.view.View.OnClickListener;

import org.commcare.activities.components.FormEntryConstants;
import org.commcare.dalvik.R;
import org.commcare.interfaces.TimerListener;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.utils.GeoUtils;
import org.commcare.utils.StringUtils;
import org.commcare.utils.TimeoutTimer;
import org.commcare.views.dialogs.GeoProgressDialog;

import java.text.DecimalFormat;
import java.util.Set;

/**
 * Activity that blocks user until the current GPS location is captured
 */
public class GeoPointActivity extends Activity implements LocationListener, TimerListener {
    private GeoProgressDialog locationDialog;
    private LocationManager locationManager;
    private Location location;
    private Set<String> providers;

    public final static int DEFAULT_MAX_WAIT_IN_SECS = 60;

    private TimeoutTimer mTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(StringUtils.getStringRobust(this, R.string.application_name) +
                " > " + StringUtils.getStringRobust(this, R.string.get_location));

        locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);

        providers = GeoUtils.evaluateProviders(locationManager);

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

        // stops the GPS. Note that this will turn off the GPS if the screen goes to sleep.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.removeUpdates(this);
        }

        // We're not using managed dialogs, so we have to dismiss the dialog to prevent it from
        // leaking memory.
        if (locationDialog != null && locationDialog.isShowing()) {
            locationDialog.dismiss();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        providers = GeoUtils.evaluateProviders(locationManager);
        if (providers.isEmpty()) {
            handleNoLocationProviders();
        } else {
            for (String provider : providers) {
                if ((provider.equals(LocationManager.GPS_PROVIDER) && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) ||
                        (provider.equals(LocationManager.NETWORK_PROVIDER) && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
                    locationManager.requestLocationUpdates(provider, 0, 0, this);
                }
            }
            // TODO PLM: warn user and ask for permissions if the user has disabled them
            locationDialog.show();
        }
    }

    private void handleNoLocationProviders() {
        DialogInterface.OnCancelListener onCancelListener = new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                location = null;
                GeoPointActivity.this.finish();
            }
        };

        DialogInterface.OnClickListener onChangeListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int i) {
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
            }
        };

        GeoUtils.showNoGpsDialog(this, onChangeListener, onCancelListener);
    }

    /**
     * Sets up the look and actions for the progress dialog while the GPS is searching.
     */
    private void setupLocationDialog() {
        // dialog displayed while fetching gps location

        OnClickListener cancelButtonListener = new OnClickListener() {
            @Override
            public void onClick(View v) {
                location = null;
                finish();
            }
        };

        OnClickListener okButtonListener = new OnClickListener() {
            @Override
            public void onClick(View v) {
                returnLocation();
            }
        };

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

    @Override
    public void onLocationChanged(Location location) {
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
    public void onProviderDisabled(String provider) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        switch (status) {
            case LocationProvider.AVAILABLE:
                if (location != null) {
                    locationDialog.setMessage(StringUtils.getStringRobust(this, R.string.location_accuracy,
                            "" + (int)location.getAccuracy()));
                }
                break;
            case LocationProvider.OUT_OF_SERVICE:
                break;
            case LocationProvider.TEMPORARILY_UNAVAILABLE:
                break;
        }
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
}
