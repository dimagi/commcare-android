package org.commcare.fragments.connect

import android.os.Build
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.dalvik.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * Robolectric UI tests for [ConnectLearnModulesBottomSheet]: verifies the sheet title and that
 * one row is rendered per learn module with its name and estimated time.
 */
@Config(application = CommCareTestApplication::class, sdk = [Build.VERSION_CODES.Q])
@RunWith(AndroidJUnit4::class)
class ConnectLearnModulesBottomSheetTest : BaseConnectJobIntroTest() {
    private fun showSheet(): ConnectLearnModulesBottomSheet {
        val sheet = ConnectLearnModulesBottomSheet()
        showBottomSheet(sheet)
        return sheet
    }

    private fun recyclerView(sheet: ConnectLearnModulesBottomSheet): RecyclerView {
        val rv = sheet.requireView().findViewById<RecyclerView>(R.id.rv_modules)
        rv.measure(0, 0)
        rv.layout(0, 0, 1000, 4000)
        return rv
    }

    @Test
    fun `sheet title includes the job title`() {
        val sheet = showSheet()
        assertEquals(
            activity.getString(R.string.connect_opportunity_modules_sheet_title, "Infant Vaccination"),
            sheet
                .requireView()
                .findViewById<TextView>(R.id.tv_modules_title)
                .text
                .toString(),
        )
    }

    @Test
    fun `one row is rendered per learn module`() {
        val sheet = showSheet()
        assertEquals(2, recyclerView(sheet).adapter?.itemCount)
    }

    @Test
    fun `first module row shows its index name and estimated time`() {
        val sheet = showSheet()
        val rv = recyclerView(sheet)
        ShadowLooper.idleMainLooper()

        val firstRow = rv.findViewHolderForAdapterPosition(0)?.itemView
        assertNotNull("First module row should be laid out", firstRow)
        assertEquals(
            "1. Infant Vaccination",
            firstRow!!.findViewById<TextView>(R.id.tv_module_name).text.toString(),
        )
        assertEquals(
            activity.getString(R.string.connect_opportunity_module_estimated_time, 1),
            firstRow.findViewById<TextView>(R.id.tv_module_estimate).text.toString(),
        )
    }
}
