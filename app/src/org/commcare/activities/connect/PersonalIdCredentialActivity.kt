package org.commcare.activities.connect

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ActivityPersonalIdCredentialBinding


class PersonalIdCredentialActivity : AppCompatActivity() {
    private var binding: ActivityPersonalIdCredentialBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPersonalIdCredentialBinding.inflate(layoutInflater)
        setContentView(binding?.root)
        supportActionBar?.title = getString(R.string.my_credentials)
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
    }
}