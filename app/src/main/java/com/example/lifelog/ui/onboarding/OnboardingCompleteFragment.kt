// Percorso: app/src/main/java/com/example/lifelog/ui/onboarding/OnboardingCompleteFragment.kt

package com.example.lifelog.ui.onboarding

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.lifelog.R

/**
 * L'ultimo frammento nel flusso di onboarding, che indica il completamento della configurazione.
 * Abilita il pulsante "Prosegui" per permettere all'utente di passare all'app principale.
 */
class OnboardingCompleteFragment : Fragment() {

    // Riferimento al callback per comunicare con l'Activity host
    private var callback: OnboardingFragmentCallback? = null

    /**
     * Chiamato quando il fragment viene attaccato per la prima volta a un'Activity.
     */
    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnboardingFragmentCallback) {
            callback = context
        } else {
            throw RuntimeException("$context deve implementare OnboardingFragmentCallback")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_onboarding_complete, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Abilita il pulsante "Prosegui" dell'Activity.
        // Questo fragment è sempre "completato" non appena viene mostrato.
        callback?.setNextButtonEnabled(true)
    }

    /**
     * Chiamato quando il fragment viene staccato dall'Activity.
     */
    override fun onDetach() {
        super.onDetach()
        callback = null
    }

    // Questo metodo sarà chiamato dall'Activity quando il pulsante "Prosegui" globale è cliccato.
    fun handleNextButtonClick(): Boolean {
        // Non ci sono validazioni in questo fragment, si può sempre avanzare.
        return true
    }
}