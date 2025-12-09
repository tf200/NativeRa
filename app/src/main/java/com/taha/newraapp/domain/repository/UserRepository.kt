package com.taha.newraapp.domain.repository

import com.taha.newraapp.domain.model.User
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    suspend fun getCurrentUser(): User?
    suspend fun getAllUsers(): List<User>
    fun observeAllUsers(): Flow<List<User>>
    suspend fun getUsersByCenter(center: String): List<User>
    suspend fun getActiveUsers(): List<User>
}
