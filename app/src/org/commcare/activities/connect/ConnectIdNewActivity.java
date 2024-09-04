package org.commcare.activities.connect;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.os.Bundle;

import org.commcare.dalvik.R;
import org.commcare.fragments.newConnectId.PhoneFragment;

public class ConnectIdNewActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect_id_new);
        PhoneFragment frag = new PhoneFragment();
        FragmentManager manager = getSupportFragmentManager();
        FragmentTransaction transaction = manager.beginTransaction();
        transaction.add(R.id.PhoneFragment,frag,"Test Fragment");
        transaction.commit();
    }
}