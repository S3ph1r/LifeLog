package com.example.lifelog.ui.onboarding

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.lifelog.R
import com.example.lifelog.data.ConfigManager // <-- NUOVO IMPORT

import com.google.android.material.textfield.TextInputEditText

class UserInfoFragment : Fragment() {

    private lateinit var firstNameEditText: TextInputEditText
    private lateinit var lastNameEditText: TextInputEditText
    private lateinit var aliasEditText: TextInputEditText
    private var callback: OnboardingFragmentCallback? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnboardingFragmentCallback) {
            callback = context
        } else {
            throw RuntimeException("$context must implement OnboardingFragmentCallback")
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
        setupTextWatchers()
        loadDataFromConfig() // Carica i dati dal nuovo ConfigManager
    }

    override fun onDetach() {
        super.onDetach()
        callback = null
    }

    private fun initializeViews(view: View) {
        firstNameEditText = view.findViewById(R.id.firstNameEditText)
        lastNameEditText = view.findViewById(R.id.lastNameEditText)
        aliasEditText = view.findViewById(R.id.aliasEditText)
    }

    private fun loadDataFromConfig() {
        val config = ConfigManager.getConfig()
        firstNameEditText.setText(config.userFirstName)
        lastNameEditText.setText(config.userLastName)
        aliasEditText.setText(config.userAlias)
        updateNextButtonState()
    }

    private fun setupTextWatchers() {
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

    fun handleNextButtonClick(): Boolean {
        val firstName = firstNameEditText.text.toString().trim()
        val lastName = lastNameEditText.text.toString().trim()
        val alias = aliasEditText.text.toString().trim()

        if (firstName.isBlank() || lastName.isBlank() || alias.isBlank()) {
            Toast.makeText(requireContext(), "Per favore, compila tutti i campi.", Toast.LENGTH_SHORT).show()
            return false
        }

        // --- SALVATAGGIO IMMEDIATO TRAMITE CONFIGMANAGER ---
        ConfigManager.updateFirstName(firstName)
        ConfigManager.updateLastName(lastName)
        ConfigManager.updateAlias(alias)

        return true
    }

    private fun updateNextButtonState() {
        val isFormValid = !firstNameEditText.text.isNullOrBlank() &&
                !lastNameEditText.text.isNullOrBlank() &&
                !aliasEditText.text.isNullOrBlank()
        callback?.setNextButtonEnabled(isFormValid)
    }
}