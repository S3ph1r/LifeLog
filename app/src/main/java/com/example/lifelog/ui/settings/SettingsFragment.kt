// Percorso: app/src/main/java/com/example/lifelog/ui/settings/SettingsFragment.kt

package com.example.lifelog.ui.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.example.lifelog.R

/**
 * Un Fragment che mostra la schermata delle impostazioni dell'applicazione.
 *
 * Estende PreferenceFragmentCompat, che è il componente standard di AndroidX
 * per creare schermate di preferenze basate su un file XML.
 */
class SettingsFragment : PreferenceFragmentCompat() {

    /**
     * Questo metodo viene chiamato quando il Fragment deve creare la sua gerarchia di preferenze.
     *
     * @param savedInstanceState Se il fragment viene ricreato da uno stato precedente,
     * questo è lo stato.
     * @param rootKey Se non è nullo, questo fragment è radicato su una PreferenceScreen
     * con questa chiave.
     */
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Imposta la gerarchia di preferenze dal file XML che abbiamo creato.
        // R.xml.root_preferences fa riferimento al file res/xml/root_preferences.xml
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        // Qui potremmo aggiungere logica aggiuntiva, come listener per
        // validare l'input o eseguire azioni quando una preferenza cambia.
        // Per ora, ci basta mostrare le preferenze.
    }
}