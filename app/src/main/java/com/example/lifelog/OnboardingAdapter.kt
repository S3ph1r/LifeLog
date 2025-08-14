package com.example.lifelog

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class OnboardingAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {

    /**
     * La lista dei fragment che compongono l'onboarding, nel loro ordine di apparizione.
     * Abbiamo inserito il PermissionsFragment al secondo posto.
     */
    private val fragments: List<Fragment> = listOf(
        WelcomeFragment(),          // Posizione 0
        PermissionsFragment(),      // Posizione 1 (NUOVO)
        ConfigurationFragment(),    // Posizione 2
        VoiceprintFragment()        // Posizione 3
    )

    /**
     * Restituisce il numero totale di pagine nell'onboarding.
     */
    override fun getItemCount(): Int = fragments.size

    /**
     * Crea e restituisce il fragment per la posizione data.
     */
    override fun createFragment(position: Int): Fragment {
        return fragments[position]
    }
}