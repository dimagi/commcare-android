package org.commcare.activities.connect;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import android.os.Bundle;

import org.commcare.dalvik.R;

public class ConnectIdActivity extends AppCompatActivity {
    NavController controller;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect_id);
        NavHostFragment host2 = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_connectid);
        assert host2 != null;
        controller = host2.getNavController();
        controller.navigate(R.id.connectid_recovery_decision);
    }
}