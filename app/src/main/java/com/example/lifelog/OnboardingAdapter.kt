package com.example.lifelog

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class OnboardingAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {

    // Mappa per tenere traccia dei fragment istanziati dal ViewPager
    private val fragmentMap = mutableMapOf<Int, Fragment>()

    /**
     * La lista delle classi dei fragment da creare.
     * Creare una nuova istanza ogni volta in createFragment è più sicuro.
     */
    private val fragmentClasses = listOf(
        WelcomeFragment::class.java,
        PermissionsFragment::class.java,
        ConfigurationFragment::class.java,
        VoiceprintFragment::class.java,
        OnboardingCompleteFragment::class.java
    )

    override fun getItemCount(): Int = fragmentClasses.size

    /**
     * Crea una nuova istanza del fragment per la posizione data.
     * Questo è il metodo preferito per garantire che ogni fragment sia nuovo.
     */
    override fun createFragment(position: Int): Fragment {
        // Usa il costruttore della classe per creare una nuova istanza
        val fragment = fragmentClasses[position].constructors.first().newInstance() as Fragment
        fragmentMap[position] = fragment
        return fragment
    }

    /**
     * Funzione per recuperare un fragment già creato dalla nostra mappa.
     * Sarà usata dall'OnboardingActivity.
     */
    fun getFragment(position: Int): Fragment? {
        return fragmentMap[position]
    }
}