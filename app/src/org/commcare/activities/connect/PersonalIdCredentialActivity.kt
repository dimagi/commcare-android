package org.commcare.activities.connect

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.tabs.TabLayoutMediator
import org.commcare.activities.connect.viewmodel.PersonalIdCredentialViewModel
import org.commcare.activities.connect.viewmodel.PersonalIdSessionDataViewModel
import org.commcare.adapters.CredentialsViewPagerAdapter
import org.commcare.android.database.connect.models.PersonalIdCredential
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.connect.network.connectId.PersonalIdApiErrorHandler
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ActivityPersonalIdCredentialBinding
import org.commcare.utils.MultipleAppsUtil

class PersonalIdCredentialActivity : AppCompatActivity() {
    private val binding: ActivityPersonalIdCredentialBinding by lazy {
        ActivityPersonalIdCredentialBinding.inflate(layoutInflater)
    }
    private lateinit var credentialsViewPagerAdapter: CredentialsViewPagerAdapter
    private lateinit var personalIdCredentialViewModel: PersonalIdCredentialViewModel
    private var personalIdSessionData: PersonalIdSessionData? = null
    private var userName: String? = null
    private var profilePic: String? = null
    private var installedAppRecords: List<PersonalIdCredential> = emptyList()
    private val titles = listOf(R.string.personalid_credential_earned, R.string.personalid_credential_pending)
    private val icons = listOf(R.drawable.ic_personalid_credential_earned, R.drawable.ic_personalid_credential_pending)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        personalIdSessionData =
            ViewModelProvider(this)[PersonalIdSessionDataViewModel::class.java].personalIdSessionData
        userName = personalIdSessionData!!.userName
        profilePic = personalIdSessionData?.photoBase64
        credentialsViewPagerAdapter = CredentialsViewPagerAdapter(this,userName!!,profilePic ?: "")
        personalIdCredentialViewModel = ViewModelProvider(
            this, ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[PersonalIdCredentialViewModel::class.java]
        initInstalledAppsList()
        observeCredentialApiCall()
        fetchCredentialsFromNetwork()
        setUpUi()
    }

    private fun initInstalledAppsList() {
        val usableApps = MultipleAppsUtil.getUsableAppRecords()

        installedAppRecords = usableApps.map { record ->
            PersonalIdCredential().apply {
                appId = record.applicationId
                title = record.displayName ?: ""
            }
        }
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
        personalIdCredentialViewModel.credentialsLiveData.observe(this) { result ->
            val earnedCredentials = result.validCredentials

            // Filter yet-to-be-earned by checking which installed app credentials are not in earned list
            val earnedAppIds = earnedCredentials.map { it.appId }.toSet()
            val yetToBeEarned = installedAppRecords.filter { it.appId !in earnedAppIds }

            personalIdCredentialViewModel.setFilteredCredentialLists(
                earned = earnedCredentials,
                pending = yetToBeEarned
            )
        }

        personalIdCredentialViewModel.apiError.observe(this) { (code, throwable) ->
            val errorMessage = PersonalIdApiErrorHandler.handle(this, code, throwable)
            Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchCredentialsFromNetwork() {
        personalIdCredentialViewModel.retrieveCredentials()
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