package org.commcare.activities.connect

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import org.commcare.activities.connect.viewmodel.PersonalIdCredentialViewModel
import org.commcare.adapters.PersonalIdCredentialAdapter
import org.commcare.android.database.connect.models.PersonalIdCredential
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ActivityPersonalIdCredentialBinding
import org.javarosa.core.services.Logger

class PersonalIdCredentialActivity : AppCompatActivity() {
    private val binding: ActivityPersonalIdCredentialBinding by lazy {
        ActivityPersonalIdCredentialBinding.inflate(layoutInflater)
    }
    private lateinit var personalIdCredentialViewModel: PersonalIdCredentialViewModel
    private lateinit var earnedCredentialAdapter: PersonalIdCredentialAdapter
    private lateinit var yetToBeEarnedCredentialsAdapter: PersonalIdCredentialAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setUpUi()
        setupRecyclerViews()
        personalIdCredentialViewModel =
            ViewModelProvider(this)[PersonalIdCredentialViewModel::class.java]
        observeCredentialApiCall()
//        callCredentialApi()
    }

    private fun setUpUi() {
        supportActionBar?.apply {
            title = getString(R.string.my_worker_history)
            setDisplayShowHomeEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }
        binding.tvEarnedCredentials.text =
            getString(R.string.earned_credentials, 2)
        binding.tvCredentialsYetToBeEarned.text =
            getString(R.string.credentials_yet_to_be_earned, 0)
    }

    private fun setupRecyclerViews() {
        earnedCredentialAdapter = PersonalIdCredentialAdapter(listener = object :
            PersonalIdCredentialAdapter.OnCredentialClickListener {
            override fun onViewCredentialClick(credential: PersonalIdCredential) {
                startActivity(
                    Intent(
                        this@PersonalIdCredentialActivity,
                        SelectedPersonalIdCredentialActivity::class.java
                    )
                )
            }
        })
        binding.rvEarnedCredential.adapter = earnedCredentialAdapter

        yetToBeEarnedCredentialsAdapter = PersonalIdCredentialAdapter(listener = object :
            PersonalIdCredentialAdapter.OnCredentialClickListener {
            override fun onViewCredentialClick(credential: PersonalIdCredential) {
                startActivity(
                    Intent(
                        this@PersonalIdCredentialActivity,
                        SelectedPersonalIdCredentialActivity::class.java
                    )
                )
            }
        })
        binding.rvYetToBeEarnedCredentials.adapter = yetToBeEarnedCredentialsAdapter
    }

    private fun observeCredentialApiCall() {
        personalIdCredentialViewModel.credentialsLiveData.observe(this) { result ->
            updateCredentialLists(result.validCredentials)
        }

        personalIdCredentialViewModel.apiError.observe(this) { (code, throwable) ->
            Logger.log(
                "CREDENTIALS_API_ERROR",
                "Code: $code, Throwable: ${throwable?.localizedMessage ?: "null"}"
            )
        }
    }

    private fun updateCredentialLists(data: List<PersonalIdCredential>) {
        // filter data for earned credential and yetToBeEarned credential
        earnedCredentialAdapter.setData(data)
        yetToBeEarnedCredentialsAdapter.setData(data)
    }

    private fun callCredentialApi() {
        personalIdCredentialViewModel.retrieveCredentials(this)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_work_history, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                if (!isFinishing) {
                    this.onBackPressed()
                }
                true
            }

            R.id.cloud_sync -> {
//                callCredentialApi()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }
}
