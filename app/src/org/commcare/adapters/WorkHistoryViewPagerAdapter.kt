package org.commcare.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import org.commcare.fragments.personalId.WorkHistoryEarnedFragment
import org.commcare.fragments.personalId.WorkHistoryPendingFragment

class WorkHistoryViewPagerAdapter(
    fragmentActivity: FragmentActivity,
    private val username: String,
    private val profilePic: String
) : FragmentStateAdapter(fragmentActivity) {

    companion object {
        const val TOTAL_PAGES = 1 // temporary set to 1 until pending work history is rolled out
        const val EARNED_TAB_INDEX = 0
        const val PENDING_TAB_INDEX = 1
    }

    override fun getItemCount(): Int = TOTAL_PAGES
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            EARNED_TAB_INDEX -> WorkHistoryEarnedFragment.newInstance(username, profilePic)
            PENDING_TAB_INDEX -> WorkHistoryPendingFragment.newInstance(username)
            else -> throw IndexOutOfBoundsException("Invalid tab position")
        }
    }
}
