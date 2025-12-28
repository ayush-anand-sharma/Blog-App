package com.ayushcodes.blogapp.main.adapters // Defines the package name for this file

import androidx.fragment.app.Fragment // Imports Fragment class
import androidx.fragment.app.FragmentManager // Imports FragmentManager class
import androidx.lifecycle.Lifecycle // Imports Lifecycle class
import androidx.viewpager2.adapter.FragmentStateAdapter // Imports FragmentStateAdapter for ViewPager2
import com.ayushcodes.blogapp.main.fragments.LikedBlogsFragment // Imports LikedBlogsFragment
import com.ayushcodes.blogapp.main.fragments.MyBlogsFragment // Imports MyBlogsFragment

// This adapter provides the fragments for the ViewPager2 in the ProfilePage activity.
class ViewPagerAdapter(fragmentManager: FragmentManager, lifecycle: Lifecycle) : FragmentStateAdapter(fragmentManager, lifecycle) { // Defines ViewPagerAdapter class
    // Returns the total number of fragments.
    override fun getItemCount(): Int { // Overrides getItemCount method
        return 2 // Returns 2 as there are two tabs
    }

    // Creates the appropriate fragment based on the position.
    override fun createFragment(position: Int): Fragment { // Overrides createFragment method
        return when (position) { // Switches based on position
            0 -> MyBlogsFragment() // Returns MyBlogsFragment for position 0
            1 -> LikedBlogsFragment() // Returns LikedBlogsFragment for position 1
            else -> throw IllegalArgumentException("Invalid position") // Throws exception for invalid position
        }
    }
}
