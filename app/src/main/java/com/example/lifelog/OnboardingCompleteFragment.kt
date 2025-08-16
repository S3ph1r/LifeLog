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

    // Accede al ViewModel condiviso
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

        // Otteniamo la nostra nuova istanza di AppPreferences
        val appPreferences = AppPreferences.getInstance(requireContext())
        val parentActivity = activity as? OnboardingActivity

        binding.buttonStart.setOnClickListener {
            it.isEnabled = false // Disabilita subito il pulsante

            if (parentActivity == null || parentActivity.lastRecordedVoiceprintPath == null) {
                Toast.makeText(requireContext(), "Errore: Percorso voiceprint non trovato.", Toast.LENGTH_LONG).show()
                it.isEnabled = true
                return@setOnClickListener
            }

            // --- NUOVA ORCHESTRAZIONE DEL SALVATAGGIO ---

            // 1. Dici al ViewModel di salvare in background i dati utente (Room) e la password.
            Log.d("OnboardingComplete", "Avvio finalizzazione: salvataggio dati utente e password...")
            viewModel.finalizeOnboarding()

            // 2. Esegui il salvataggio BLOCCANTE solo per il flag di completamento.
            //    Questo è istantaneo e sicuro.
            Log.d("OnboardingComplete", "Salvataggio bloccante dello stato di completamento...")
            val success = appPreferences.saveOnboardingCompletionStateBlocking()

            if (!success) {
                // Caso raro, ma gestiamolo: se SharedPreferences non riesce a scrivere.
                Toast.makeText(requireContext(), "Errore critico durante il salvataggio dello stato.", Toast.LENGTH_LONG).show()
                it.isEnabled = true
                return@setOnClickListener
            }
            Log.d("OnboardingComplete", "Salvataggio completato con successo.")


            // 3. Pianifica l'upload del voiceprint in background (invariato)
            parentActivity.scheduleVoiceprintUpload(parentActivity.lastRecordedVoiceprintPath!!)


            // 4. Avvia la Dashboard (invariato)
            val intent = Intent(requireActivity(), DashboardActivity::class.java).apply {
                putExtra(DashboardActivity.EXTRA_FIRST_RUN, true)
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