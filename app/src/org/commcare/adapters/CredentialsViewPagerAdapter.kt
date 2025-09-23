package org.commcare.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import org.commcare.fragments.personalId.EarnedCredentialFragment
import org.commcare.fragments.personalId.PendingCredentialFragment

class CredentialsViewPagerAdapter(fragmentActivity: FragmentActivity,private val username: String,
                                  private val profilePic: String) : FragmentStateAdapter(fragmentActivity) {
    
    companion object {
        const val TOTAL_PAGES = 2
        const val EARNED_TAB_INDEX = 0
        const val PENDING_TAB_INDEX = 1
    }

    override fun getItemCount(): Int = TOTAL_PAGES
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            EARNED_TAB_INDEX -> EarnedCredentialFragment.newInstance(username, profilePic)
            PENDING_TAB_INDEX -> PendingCredentialFragment.newInstance(username)
            else -> throw IndexOutOfBoundsException("Invalid tab position")
        }
    }
}
