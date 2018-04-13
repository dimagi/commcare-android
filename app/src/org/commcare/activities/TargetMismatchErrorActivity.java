package org.commcare.activities;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.widget.Button;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.commcare.utils.StringUtils;

public class TargetMismatchErrorActivity extends Activity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_target_mismatch_error);
        setUpLayout();
    }

    private void setUpLayout() {
        boolean isLTS = getPackageName().contentEquals("org.commcare.lts");
        String title = isLTS ? getStringByResource(R.string.target_mismatch_commcare_required) : getStringByResource(R.string.target_mismatch_lts_required);
        ((TextView)findViewById(R.id.error_title)).setText(title);
        String error_info = isLTS ? getStringByResource(R.string.target_mismatch_commcare_required_info) : getStringByResource(R.string.target_mismatch_lts_required_info);
        ((TextView)findViewById(R.id.error_info)).setText(error_info);
        String buttonText = isLTS ? getStringByResource(R.string.target_mismatch_commcare_required_install) : getStringByResource(R.string.target_mismatch_lts_required_install);
        ((Button)findViewById(R.id.install_app_button)).setText(buttonText);
    }

    private String getStringByResource(int resId) {
        return StringUtils.getStringRobust(this, resId);
    }
}
