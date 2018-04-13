package org.commcare.activities;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.widget.Button;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.commcare.utils.StringUtils;

public class TargetMismatchErrorActivity extends Activity {

    private static final String PACKAGE_LTS = "org.commcare.lts";
    private static final String PACKAGE_CC = "org.commcare.dalvik";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_target_mismatch_error);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setUpLayout();
    }

    private void setUpLayout() {
        boolean isLTS = getPackageName().contentEquals(PACKAGE_LTS);
        String requiredPackageName = isLTS ? PACKAGE_CC : PACKAGE_LTS;

        boolean appInstalled = checkAppInstall(requiredPackageName);

        //Set Title
        String title = isLTS ? getStringByResource(R.string.target_mismatch_commcare_required) : getStringByResource(R.string.target_mismatch_lts_required);
        ((TextView)findViewById(R.id.error_title)).setText(title);

        // Set Detail
        String errorInfo;
        if (appInstalled) {
            errorInfo = isLTS ? getStringByResource(R.string.target_mismatch_commcare_available_info) : getStringByResource(R.string.target_mismatch_lts_available_info);
        } else {
            errorInfo = isLTS ? getStringByResource(R.string.target_mismatch_commcare_required_info) : getStringByResource(R.string.target_mismatch_lts_required_info);
        }
        ((TextView)findViewById(R.id.error_info)).setText(errorInfo);

        // Set Action
        Button installButton = findViewById(R.id.install_app_button);
        String buttonText;
        if (appInstalled) {
            buttonText = isLTS ? getStringByResource(R.string.target_mismatch_commcare_open) : getStringByResource(R.string.target_mismatch_lts_open);
        } else {
            buttonText = isLTS ? getStringByResource(R.string.target_mismatch_commcare_required_install) : getStringByResource(R.string.target_mismatch_lts_required_install);
        }
        installButton.setText(buttonText);
        installButton.setOnClickListener(v -> {
            if (appInstalled) {
                openApp(requiredPackageName);
            } else {
                launchAppOnPlayStore(requiredPackageName);
            }
        });
    }

    private void openApp(String requiredPackageName) {
        startActivity(getPackageManager().getLaunchIntentForPackage(requiredPackageName));
    }

    private boolean checkAppInstall(String packageName) {
        PackageManager pm = getPackageManager();
        try {
            pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void launchAppOnPlayStore(String packageName) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + packageName));
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Intent intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + packageName));
            startActivity(intent);
        }
    }

    private String getStringByResource(int resId) {
        return StringUtils.getStringRobust(this, resId);
    }
}
