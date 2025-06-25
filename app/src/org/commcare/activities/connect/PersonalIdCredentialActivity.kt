package org.commcare.activities.connect

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.commcare.android.database.connect.models.PersonalIdValidAndCorruptCredential
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.network.connectId.PersonalIdApiHandler
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ActivityPersonalIdCredentialBinding


class PersonalIdCredentialActivity : AppCompatActivity() {
    private val binding: ActivityPersonalIdCredentialBinding by lazy {
        ActivityPersonalIdCredentialBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        supportActionBar?.title = getString(R.string.my_credentials)
        retrieveCredentials()
    }

    fun retrieveCredentials() {

        val user = ConnectUserDatabaseUtil.getUser(this)

        object : PersonalIdApiHandler<PersonalIdValidAndCorruptCredential>() {
            override fun onSuccess(personalIdValidAndCorruptCredential: PersonalIdValidAndCorruptCredential) {

            }

            override fun onFailure(failureCode: PersonalIdOrConnectApiErrorCodes, t: Throwable?) {

            }
        }.retrieveCredentials(this, user.name, user.password)
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}