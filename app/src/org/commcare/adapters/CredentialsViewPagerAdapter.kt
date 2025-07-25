package org.commcare.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import org.commcare.fragments.personalId.EarnedCredentialFragment
import org.commcare.fragments.personalId.PendingCredentialFragment

class CredentialsViewPagerAdapter(fragmentActivity: FragmentActivity,private val username: String,
                                  private val profilePic: String) : FragmentStateAdapter(fragmentActivity) {

    private val fragments = listOf(EarnedCredentialFragment(), PendingCredentialFragment())

    override fun getItemCount(): Int = fragments.size
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> EarnedCredentialFragment.newInstance(username, profilePic)
            1 -> PendingCredentialFragment.newInstance(username)
            else -> throw IndexOutOfBoundsException("Invalid tab position")
        }
    }
}