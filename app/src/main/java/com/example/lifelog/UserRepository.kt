package com.example.lifelog

import kotlinx.coroutines.flow.Flow

/**
 * Repository per gestire i dati dell'utente.
 * È l'unico punto di accesso ai dati utente per il resto dell'app.
 */
class UserRepository(private val userDao: UserDao) {

    // Espone il Flow per osservare i dati dell'utente
    val user: Flow<User?> = userDao.getUserFlow()

    // Funzione per salvare/aggiornare i dati dell'utente
    suspend fun saveUser(user: User) {
        userDao.upsertUser(user)
    }
}