package com.example.lifelog

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Se è la prima volta che l'activity viene creata, aggiunge il fragment
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }

        // Questa riga mostra la freccia "indietro" nella barra in alto.
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    /**
     * *** METODO AGGIUNTO PER LA CORREZIONE ***
     *
     * Questo metodo viene chiamato automaticamente dal sistema quando l'utente
     * preme il pulsante "Up" (la freccia indietro) nella ActionBar.
     *
     * @return true se l'evento di navigazione "up" è stato gestito.
     */
    override fun onSupportNavigateUp(): Boolean {
        // Chiude l'activity corrente. Questo fa sì che l'utente torni
        // alla schermata precedente nello stack, che è la nostra MainActivity.
        finish()
        return true
    }
}