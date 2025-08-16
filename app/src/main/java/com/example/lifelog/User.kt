package com.example.lifelog

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// L'Entità: la tabella del profilo utente
@Entity(tableName = "user_profile")
data class User(
    @PrimaryKey val id: Int = 1, // ID fisso, ci sarà sempre un solo utente
    val firstName: String,
    val lastName: String,
    val alias: String,
    val serverUrl: String
)

// Il DAO: i comandi per interagire con la tabella
@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUser(user: User)

    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun getUserFlow(): Flow<User?> // Usiamo un Flow per osservare i cambiamenti
}