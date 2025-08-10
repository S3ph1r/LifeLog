// Percorso: app/src/main/java/com/example/lifelog/ui/onboarding/OnboardingFragmentCallback.kt

package com.example.lifelog.ui.onboarding

/**
 * Interfaccia di callback che i fragment di onboarding possono usare per comunicare
 * con l'Activity che li ospita (OnboardingActivity).
 *
 * Ad esempio, un fragment può usare questo callback per dire all'Activity
 * di abilitare/disabilitare il pulsante "Prosegui" globale.
 */
interface OnboardingFragmentCallback {
    /**
     * Chiamato da un fragment per impostare lo stato di abilitazione
     * del pulsante "Prosegui" globale dell'Activity.
     */
    fun setNextButtonEnabled(isEnabled: Boolean)
}