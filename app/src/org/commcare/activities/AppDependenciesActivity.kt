package org.commcare.activities;

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import org.commcare.CommCareApplication
import org.commcare.adapters.AppDependenciesAdapter
import org.commcare.dalvik.R
import org.commcare.suite.model.AppDependency
import org.commcare.utils.StringUtils


class AppDependenciesActivity : CommCareActivity<AppDependenciesActivity>() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.app_dependencies)
        val dependenciesAdapter = AppDependenciesAdapter { appId -> installDependency(appId) }
        dependenciesAdapter.submitList(getDependencies())
        val recyclerView = findViewById<RecyclerView>(R.id.dependencies_recycler_view)
        recyclerView.adapter = dependenciesAdapter
    }

    private fun getDependencies(): MutableList<AppDependency> {
        val dependencies = CommCareApplication.instance().commCarePlatform.currentProfile.dependencies
        for (dependency in dependencies) {
            setInstallStatus(dependency)
            if(dependency.isForce && !dependency.isInstalled){
                blockUser()
            }
        }
        return dependencies
    }

    private fun blockUser() {
        TODO("Disable Continue Button and Show Error Text")
    }

    private fun setInstallStatus(dependency: AppDependency) {
        try {
            val packageManager = applicationContext.packageManager
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(
                    dependency.id,
                    PackageManager.GET_META_DATA
                )
            )
            dependency.isInstalled = true
        } catch (e: PackageManager.NameNotFoundException) {
            dependency.isInstalled = false
        }
    }

    private fun installDependency(appId: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$appId")))
        } catch (e: ActivityNotFoundException) {
            showNoPlaystoreFoundError()
        }
    }

    private fun showNoPlaystoreFoundError() {
        val error = StringUtils.getStringRobust(this, R.string.app_dependency_no_playstore_found)
        Toast.makeText(this, error, Toast.LENGTH_LONG).show()
    }
}


