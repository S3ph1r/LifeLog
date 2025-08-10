// Percorso: app/src/main/java/com/example/lifelog/ui/onboarding/BackendSetupFragment.kt

package com.example.lifelog.ui.onboarding

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.lifelog.R
import com.example.lifelog.data.SettingsManager
import com.google.android.material.textfield.TextInputEditText

/**
 * Fragment per la configurazione dell'indirizzo del server backend e della password di crittografia.
 */
class BackendSetupFragment : Fragment() {

    private val TAG = "BackendSetupFragment"

    private lateinit var serverAddressEditText: TextInputEditText
    private lateinit var encryptionPasswordEditText: TextInputEditText

    private var callback: OnboardingFragmentCallback? = null

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
        return inflater.inflate(R.layout.fragment_backend_setup, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews(view)
        setupTextWatchers()
        loadSavedData()
    }

    override fun onDetach() {
        super.onDetach()
        callback = null
    }

    override fun onResume() {
        super.onResume()
        updateNextButtonState()
    }

    private fun initializeViews(view: View) {
        serverAddressEditText = view.findViewById(R.id.serverAddressEditText)
        encryptionPasswordEditText = view.findViewById(R.id.encryptionPasswordEditText)
    }

    private fun loadSavedData() {
        serverAddressEditText.setText(SettingsManager.serverAddress)
        encryptionPasswordEditText.setText(SettingsManager.encryptionPassword)
    }

    private fun setupTextWatchers() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateNextButtonState()
            }
        }
        serverAddressEditText.addTextChangedListener(textWatcher)
        encryptionPasswordEditText.addTextChangedListener(textWatcher)
    }

    /**
     * Chiamato dall'Activity host per validare i campi del fragment prima di avanzare.
     */
    fun handleNextButtonClick(): Boolean {
        val serverAddress = serverAddressEditText.text.toString().trim()
        val encryptionPassword = encryptionPasswordEditText.text.toString().trim()

        if (serverAddress.isBlank() || encryptionPassword.isBlank()) {
            Toast.makeText(requireContext(), "Indirizzo server e password sono obbligatori.", Toast.LENGTH_SHORT).show()
            return false
        }

        // --- MODIFICA CRUCIALE: Usiamo i setter espliciti ---
        SettingsManager.setServerAddress(serverAddress)
        SettingsManager.setEncryptionPassword(encryptionPassword)

        Toast.makeText(requireContext(), "Configurazione backend salvata.", Toast.LENGTH_SHORT).show()
        return true
    }

    /**
     * Aggiorna lo stato di abilitazione del pulsante "Prosegui" globale dell'Activity.
     */
    private fun updateNextButtonState() {
        val serverAddress = serverAddressEditText.text.toString().trim()
        val encryptionPassword = encryptionPasswordEditText.text.toString().trim()

        val isFormValid = !serverAddress.isBlank() && !encryptionPassword.isBlank()

        Log.d(TAG, "updateNextButtonState called. Form is valid: $isFormValid")

        callback?.setNextButtonEnabled(isFormValid)
    }
}