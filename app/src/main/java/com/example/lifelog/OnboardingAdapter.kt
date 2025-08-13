package com.example.lifelog

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class OnboardingAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    // La lista ora contiene tutti e tre i passaggi
    val fragments: List<Fragment> = listOf(
        WelcomeFragment(),
        ConfigurationFragment(),
        VoiceprintFragment()
    )

    override fun getItemCount(): Int {
        return fragments.size
    }

    override fun createFragment(position: Int): Fragment {
        return fragments[position]
    }
}