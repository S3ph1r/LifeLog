package com.example.lifelog

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.lifelog.databinding.FragmentOnboardingCompleteBinding

class OnboardingCompleteFragment : Fragment() {

    private var _binding: FragmentOnboardingCompleteBinding? = null
    private val binding get() = _binding!!

    private val viewModel: OnboardingViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingCompleteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val appPreferences = AppPreferences.getInstance(requireContext())
        val parentActivity = activity as? OnboardingActivity

        binding.buttonStart.setOnClickListener {
            it.isEnabled = false

            if (parentActivity == null || parentActivity.lastRecordedVoiceprintPath == null) {
                Toast.makeText(requireContext(), "Errore: Percorso voiceprint non trovato.", Toast.LENGTH_LONG).show()
                it.isEnabled = true
                return@setOnClickListener
            }

            Log.d("OnboardingComplete", "Avvio finalizzazione: salvataggio dati utente e password...")
            viewModel.finalizeOnboarding()

            Log.d("OnboardingComplete", "Salvataggio bloccante dello stato di completamento...")
            val success = appPreferences.saveOnboardingCompletionStateBlocking()

            if (!success) {
                Toast.makeText(requireContext(), "Errore critico durante il salvataggio dello stato.", Toast.LENGTH_LONG).show()
                it.isEnabled = true
                return@setOnClickListener
            }
            Log.d("OnboardingComplete", "Salvataggio completato con successo.")


            // --- MODIFICA CHIAVE QUI ---
            // Diciamo all'app che da ora in poi il servizio di registrazione
            // dovrebbe essere attivo di default.
            appPreferences.isServiceActive = true
            Log.d("OnboardingComplete", "Stato del servizio impostato su ATTIVO.")
            // --- FINE MODIFICA ---


            parentActivity.scheduleVoiceprintUpload(parentActivity.lastRecordedVoiceprintPath!!)

            val intent = Intent(requireActivity(), DashboardActivity::class.java).apply {
                // Non abbiamo più bisogno di questo extra, la nuova logica in Dashboard lo gestisce
                // putExtra(DashboardActivity.EXTRA_FIRST_RUN, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            requireActivity().finish()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}