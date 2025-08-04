package org.commcare.activities.connect

import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.tabs.TabLayoutMediator
import org.commcare.activities.connect.viewmodel.PersonalIdCredentialViewModel
import org.commcare.adapters.CredentialsViewPagerAdapter
import org.commcare.connect.network.connectId.PersonalIdApiErrorHandler
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ActivityPersonalIdCredentialBinding

class PersonalIdCredentialActivity : AppCompatActivity() {
    private val binding: ActivityPersonalIdCredentialBinding by lazy {
        ActivityPersonalIdCredentialBinding.inflate(layoutInflater)
    }
    private lateinit var credentialsViewPagerAdapter: CredentialsViewPagerAdapter
    private lateinit var personalIdCredentialViewModel: PersonalIdCredentialViewModel
    private var userName: String? = null
    private var profilePic: String? = null
    private val titles = listOf(R.string.personalid_credential_earned, R.string.personalid_credential_pending)
    private val icons = listOf(R.drawable.ic_personalid_credential_earned, R.drawable.ic_personalid_credential_pending)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        personalIdCredentialViewModel = ViewModelProvider(
            this, ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[PersonalIdCredentialViewModel::class.java]
        userName = personalIdCredentialViewModel.userName
        profilePic = personalIdCredentialViewModel.profilePhoto
        credentialsViewPagerAdapter = CredentialsViewPagerAdapter(this,userName!!,profilePic ?: "")
        observeCredentialApiCall()
        fetchCredentialsFromNetwork()
        setUpUi()
    }

    private fun setUpUi() {
        supportActionBar!!.apply {
            title = getString(R.string.personalid_credential_my_worker_history)
            setDisplayShowHomeEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }
        binding.vpCredentials.adapter = credentialsViewPagerAdapter
        TabLayoutMediator(binding.tabCredentials, binding.vpCredentials) { tab, position ->
            tab.text = getString(titles[position])
            tab.setIcon(icons[position])
        }.attach()
    }

    private fun observeCredentialApiCall() {
        personalIdCredentialViewModel.apiError.observe(this) { (code, throwable) ->
            val errorMessage = PersonalIdApiErrorHandler.handle(this, code, throwable)
            Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchCredentialsFromNetwork() {
        personalIdCredentialViewModel.retrieveAndProcessCredentials()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_work_history, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                if (!isFinishing) {
                    finish()
                }
                true
            }

            R.id.cloud_sync -> {
                fetchCredentialsFromNetwork()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }
}