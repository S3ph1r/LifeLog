// Percorso: app/src/main/java/com/example/lifelog/ui/onboarding/UserInfoFragment.kt

package com.example.lifelog.ui.onboarding

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.lifelog.OnboardingActivity
import com.example.lifelog.R
import com.example.lifelog.data.SettingsManager
import com.google.android.material.textfield.TextInputEditText

/**
 * Il primo frammento nel flusso di onboarding.
 * Raccoglie le informazioni base dell'utente (nome, cognome, alias).
 */
class UserInfoFragment : Fragment() {

    private lateinit var firstNameEditText: TextInputEditText
    private lateinit var lastNameEditText: TextInputEditText
    private lateinit var aliasEditText: TextInputEditText

    // MODIFICA: Rimosso il riferimento a nextButton qui, sarà gestito dall'Activity

    // Riferimento al callback per comunicare con l'Activity host
    private var callback: OnboardingFragmentCallback? = null

    /**
     * Chiamato quando il fragment viene attaccato per la prima volta a un'Activity.
     * Qui stabiliamo il riferimento al callback.
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
        return inflater.inflate(R.layout.fragment_user_info, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews(view)
        setupTextWatchers() // MODIFICA: Nuovo metodo per i TextWatcher

        // Carica i dati salvati se l'utente torna indietro
        loadSavedData()
    }

    /**
     * Chiamato quando il fragment viene staccato dall'Activity.
     * Rimuoviamo il riferimento al callback per evitare memory leaks.
     */
    override fun onDetach() {
        super.onDetach()
        callback = null
    }

    private fun initializeViews(view: View) {
        firstNameEditText = view.findViewById(R.id.firstNameEditText)
        lastNameEditText = view.findViewById(R.id.lastNameEditText)
        aliasEditText = view.findViewById(R.id.aliasEditText)
        // MODIFICA: nextButton non è più qui, è gestito dall'Activity
    }

    // MODIFICA: Metodo per caricare i dati salvati
    private fun loadSavedData() {
        firstNameEditText.setText(SettingsManager.userFirstName)
        lastNameEditText.setText(SettingsManager.userLastName)
        aliasEditText.setText(SettingsManager.userAlias)
        // Aggiorna lo stato del pulsante al caricamento dei dati
        updateNextButtonState()
    }

    // MODIFICA: Nuovo metodo per i TextWatcher
    private fun setupTextWatchers() {
        // Aggiungiamo un TextWatcher a ogni campo per abilitare/disabilitare il pulsante "Prosegui"
        // in tempo reale.
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateNextButtonState()
            }
        }
        firstNameEditText.addTextChangedListener(textWatcher)
        lastNameEditText.addTextChangedListener(textWatcher)
        aliasEditText.addTextChangedListener(textWatcher)
    }

    // MODIFICA: La logica di click è ora sull'Activity, questo metodo verifica solo la validazione.
    // Viene chiamato dall'Activity quando il pulsante "Prosegui" globale viene cliccato.
    // Non è più privato per essere accessibile dall'Activity.
    fun handleNextButtonClick(): Boolean {
        val firstName = firstNameEditText.text.toString().trim()
        val lastName = lastNameEditText.text.toString().trim()
        val alias = aliasEditText.text.toString().trim()

        if (firstName.isBlank() || lastName.isBlank() || alias.isBlank()) {
            Toast.makeText(requireContext(), "Per favore, compila tutti i campi.", Toast.LENGTH_SHORT).show()
            // Se la validazione fallisce, l'Activity non deve avanzare.
            return false
        }

        SettingsManager.userFirstName = firstName
        SettingsManager.userLastName = lastName
        SettingsManager.userAlias = alias

        // Non mostriamo più il Toast qui, lo farà la Activity quando la navigazione avrà successo.
        // Non navighiamo più direttamente.

        return true // Indica che il fragment è valido e l'Activity può avanzare
    }

    // MODIFICA: Nuovo metodo per aggiornare lo stato del pulsante dell'Activity
    private fun updateNextButtonState() {
        // Il pulsante è abilitato solo se tutti i campi sono non vuoti.
        val isFormValid = !firstNameEditText.text.isNullOrBlank() &&
                !lastNameEditText.text.isNullOrBlank() &&
                !aliasEditText.text.isNullOrBlank()
        callback?.setNextButtonEnabled(isFormValid)
    }
}