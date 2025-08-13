package com.example.lifelog

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class OnboardingAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    // Lista dei fragment che l'adapter gestirà.
    // L'ordine in questa lista determina l'ordine delle pagine.
    val fragments: List<Fragment> = listOf(
        WelcomeFragment(),
        ConfigurationFragment()
        // In futuro, potremmo aggiungere qui un VoiceprintFragment, etc.
    )

    /**
     * Restituisce il numero totale di pagine.
     */
    override fun getItemCount(): Int {
        return fragments.size
    }

    /**
     * Crea e restituisce il Fragment per una data posizione.
     */
    override fun createFragment(position: Int): Fragment {
        return fragments[position]
    }
}