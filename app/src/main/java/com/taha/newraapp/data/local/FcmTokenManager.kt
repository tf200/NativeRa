package com.taha.newraapp.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

private val Context.dataStore by preferencesDataStore(name = "fcm_prefs")

class FcmTokenManager(private val context: Context) {
    
    companion object {
        private val FCM_TOKEN_KEY = stringPreferencesKey("fcm_token")
    }

    val fcmToken: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[FCM_TOKEN_KEY]
    }

    suspend fun saveToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[FCM_TOKEN_KEY] = token
        }
    }

    suspend fun getStoredToken(): String? {
        return fcmToken.first()
    }
    
    suspend fun getCurrentFcmToken(): String? {
        return try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
