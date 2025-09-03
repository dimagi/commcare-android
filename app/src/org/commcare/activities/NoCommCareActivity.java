package org.commcare.activities;

import android.os.Build;
import android.os.Bundle;

import org.commcare.utils.AndroidUtil;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsControllerCompat;

public class NoCommCareActivity extends AppCompatActivity {
    @Override
    public void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
            controller.setAppearanceLightStatusBars(true);

            AndroidUtil.attachWindowInsetsListener(this, android.R.id.content);
        }
    }
}
