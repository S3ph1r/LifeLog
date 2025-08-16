package com.example.lifelog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.lifelog.databinding.FragmentConfigurationBinding

class ConfigurationFragment : Fragment() {

    private var _binding: FragmentConfigurationBinding? = null
    private val binding get() = _binding!!

    // Accede al ViewModel condiviso dell'activity.
    private val viewModel: OnboardingViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConfigurationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Popola i campi con i dati già presenti nel ViewModel.
        loadDataFromViewModel()

        // Imposta i listener per aggiornare il ViewModel in tempo reale.
        setupTextChangeListeners()
    }

    private fun loadDataFromViewModel() {
        binding.editTextFirstName.setText(viewModel.firstName)
        binding.editTextLastName.setText(viewModel.lastName)
        binding.editTextAlias.setText(viewModel.alias)
        binding.editTextServerUrl.setText(viewModel.serverUrl)
        binding.editTextPassword.setText(viewModel.password)
    }

    private fun setupTextChangeListeners() {
        val parentActivity = activity as? OnboardingActivity

        binding.editTextFirstName.doOnTextChanged { text, _, _, _ -> viewModel.firstName = text.toString() }
        binding.editTextLastName.doOnTextChanged { text, _, _, _ -> viewModel.lastName = text.toString() }

        // Per i campi obbligatori, notifica l'activity di aggiornare i pulsanti.
        binding.editTextAlias.doOnTextChanged { text, _, _, _ ->
            viewModel.alias = text.toString()
            parentActivity?.onConfigurationInputChanged()
        }
        binding.editTextServerUrl.doOnTextChanged { text, _, _, _ ->
            viewModel.serverUrl = text.toString()
            parentActivity?.onConfigurationInputChanged()
        }
        binding.editTextPassword.doOnTextChanged { text, _, _, _ ->
            viewModel.password = text.toString()
            parentActivity?.onConfigurationInputChanged()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}