package com.taha.newraapp.di

import com.taha.newraapp.data.local.TokenManager
import com.taha.newraapp.data.network.AuthApi
import com.taha.newraapp.data.network.AuthenticatedApiExecutor
import com.taha.newraapp.data.repository.AuthRepositoryImpl
import com.taha.newraapp.data.sync.PowerSyncManager
import com.taha.newraapp.domain.repository.AuthRepository
import com.taha.newraapp.domain.usecase.LoginUseCase
import com.taha.newraapp.data.repository.UserRepositoryImpl
import com.taha.newraapp.domain.repository.UserRepository
import com.taha.newraapp.ui.screens.login.LoginViewModel
import com.taha.newraapp.ui.screens.profile.ProfileViewModel
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Main application Koin module.
 * Contains all app-wide dependencies.
 */
val appModule = module {
    // ===========================================
    // ViewModels
    // ===========================================
    viewModel { LoginViewModel(get()) }
    viewModel { ProfileViewModel(get(), get(), get()) }
    viewModel { com.taha.newraapp.ui.screens.contacts.ContactsViewModel(get()) }
    viewModel { com.taha.newraapp.ui.screens.chat.ChatViewModel(get(), get(), get(), get(), get()) }
    
    // ===========================================
    // Local Data
    // ===========================================
    single { TokenManager(androidContext()) }

    // ===========================================
    // Repositories
    // ===========================================
    single<AuthRepository> { AuthRepositoryImpl(get()) }
    single<UserRepository> { UserRepositoryImpl(get(), get()) }

    // ===========================================
    // Socket.IO
    // ===========================================
    single { com.taha.newraapp.data.socket.SocketManager(get()) }
    single { com.taha.newraapp.data.socket.ChatSocketService(get()) }
    single { com.taha.newraapp.data.socket.MessageSyncService(get(), get(), get()) }

    // ===========================================
    // Use Cases
    // ===========================================
    factory { LoginUseCase(get(), get(), get(), get()) }
}

/**
 * Network module for API and remote data sources.
 */
val networkModule = module {
    single {
        Json { ignoreUnknownKeys = true }
    }

    single {
        OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
            .build()
    }

    single {
        Retrofit.Builder()
            .baseUrl("https://micladevops.com/api/v2/")
            .client(get())
            .addConverterFactory(get<Json>().asConverterFactory("application/json".toMediaType()))
            .build()
    }

    single { get<Retrofit>().create(AuthApi::class.java) }
    
    // Authenticated API Executor - handles token refresh for all API calls
    single { AuthenticatedApiExecutor(get(), get()) }
}

/**
 * PowerSync module for local data sync.
 */
val powerSyncModule = module {
    // PowerSync Manager - singleton for the entire app
    single {
        PowerSyncManager(
            context = androidContext(),
            apiExecutor = get(),
            authApi = get()
        )
    }
}

/**
 * Database module for local data sources.
 */
val databaseModule = module {
    // Room database
    single { 
        androidx.room.Room.databaseBuilder(
            androidContext(),
            com.taha.newraapp.data.local.LocalDatabase::class.java,
            "newra_local.db"
        )
        .fallbackToDestructiveMigration()  // Handles schema changes during development
        .build()
    }
    
    // DAOs
    single { get<com.taha.newraapp.data.local.LocalDatabase>().messageDao() }
    
    // Repositories
    single<com.taha.newraapp.domain.repository.MessageRepository> { 
        com.taha.newraapp.data.repository.MessageRepositoryImpl(get(), get()) 
    }
    
    // UseCases
    factory { com.taha.newraapp.domain.usecase.GetPrioritizedContactsUseCase(get(), get()) }
}

/**
 * All Koin modules combined
 */
val allModules = listOf(
    appModule,
    networkModule,
    powerSyncModule,
    databaseModule
)

