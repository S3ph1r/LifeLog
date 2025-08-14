package com.example.lifelog

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Rappresenta la singola riga nella tabella delle impostazioni.
 * Useremo sempre un ID fisso (1) perché ci sarà una sola riga di configurazione.
 */
@Entity(tableName = "settings")
data class Settings(
    @PrimaryKey
    val id: Int = 1,

    val userFirstName: String,
    val userLastName: String,
    val userAlias: String,
    val serverUrl: String
)