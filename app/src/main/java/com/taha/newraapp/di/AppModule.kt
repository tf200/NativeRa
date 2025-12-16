package com.taha.newraapp.di

import com.taha.newraapp.data.local.TokenManager
import com.taha.newraapp.data.network.AuthApi
import com.taha.newraapp.data.network.AttachmentApi
import com.taha.newraapp.data.network.AuthenticatedApiExecutor
import com.taha.newraapp.data.network.CallApi
import com.taha.newraapp.data.network.ChatApi
import com.taha.newraapp.data.repository.AuthRepositoryImpl
import com.taha.newraapp.data.repository.CallRepositoryImpl
import com.taha.newraapp.data.sync.PowerSyncManager
import com.taha.newraapp.domain.repository.AuthRepository
import com.taha.newraapp.domain.repository.CallRepository
import com.taha.newraapp.domain.usecase.LoginUseCase
import com.taha.newraapp.data.repository.UserRepositoryImpl
import com.taha.newraapp.domain.repository.UserRepository
import com.taha.newraapp.ui.screens.login.LoginViewModel
import com.taha.newraapp.ui.screens.profile.ProfileViewModel
import com.taha.newraapp.ui.components.ScaffoldViewModel
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidApplication
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
    viewModel { LoginViewModel(get(), get()) }
    viewModel { ProfileViewModel(
        userRepository = get(),
        tokenManager = get(),
        powerSyncManager = get(),
        authApi = get(),
        apiExecutor = get(),
        attachmentRepository = get(),
        messageDao = get(),
        socketManager = get()
    ) }
    viewModel { ScaffoldViewModel(get(), get()) }
    viewModel { com.taha.newraapp.ui.screens.contacts.ContactsViewModel(get(), get(), get()) }
    viewModel { com.taha.newraapp.ui.screens.chat.ChatViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { com.taha.newraapp.ui.screens.quickaccess.QuickAccessViewModel(get(), get()) }
    viewModel { com.taha.newraapp.ui.screens.map.MapViewModel(androidApplication()) }
    viewModel { com.taha.newraapp.ui.screens.call.OutgoingCallViewModel(get(), get()) }
    
    // ===========================================
    // Local Data
    // ===========================================
    single { TokenManager(androidContext()) }
    single { com.taha.newraapp.data.local.FcmTokenManager(androidContext()) }

    // ===========================================
    // Repositories
    // ===========================================
    single<AuthRepository> { AuthRepositoryImpl(get(), get()) }
    single<UserRepository> { UserRepositoryImpl(get(), get()) }
    single<CallRepository> { CallRepositoryImpl(get(), get()) }


    // ===========================================
    // Socket.IO
    // ===========================================
    single { com.taha.newraapp.data.network.NetworkConnectivityObserver(androidContext()) }
    single { com.taha.newraapp.data.socket.SocketManager(get()) }
    // SocketConnectionManager - monitors network and auto-reconnects socket
    single { com.taha.newraapp.data.socket.SocketConnectionManager(get(), get()) }
    // Eagerly initialize ChatSocketService to ensure listeners are registered before connection
    single(createdAtStart = true) { com.taha.newraapp.data.socket.ChatSocketService(get()) }
    single { com.taha.newraapp.data.socket.MessageSyncService(get(), get(), get(), get(), get()) }
    single { com.taha.newraapp.data.socket.PresenceService(get(), get(), get()) }
    single { com.taha.newraapp.data.socket.TypingService(get()) }
    single { com.taha.newraapp.data.socket.CallSocketService(get()) }
    // GlobalMessageHandler - starts message listeners at app level (not screen level)
    single { com.taha.newraapp.data.socket.GlobalMessageHandler(get(), get(), get(), get(), get()) }
    
    // ChatPreloadService - preloads chat data before navigation for smooth transitions
    single { com.taha.newraapp.data.service.ChatPreloadService(get(), get()) }
    
    // FcmMessageHandler - orchestrates FCM message routing
    single { com.taha.newraapp.data.service.FcmMessageHandler(get(), androidContext()) }
    
    // MessageNotificationManager - handles WhatsApp-style notifications
    single { com.taha.newraapp.data.service.MessageNotificationManager(androidContext(), get()) }
    
    // CallNotificationManager - handles incoming call notifications
    single { com.taha.newraapp.data.service.CallNotificationManager(androidContext()) }
    
    // JitsiMeetManager - handles video/audio calls via Jitsi Meet SDK
    single { com.taha.newraapp.data.call.JitsiMeetManager(androidContext()) }

    // ===========================================
    // Use Cases
    // ===========================================
    factory { LoginUseCase(get(), get(), get(), get(), get()) }
    factory { com.taha.newraapp.domain.usecase.UpdateFcmTokenUseCase(get(), get()) }
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
    single { get<Retrofit>().create(ChatApi::class.java) }
    single { get<Retrofit>().create(AttachmentApi::class.java) }
    single { get<Retrofit>().create(CallApi::class.java) }
    
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
            .fallbackToDestructiveMigration(false)  // Handles schema changes during development
        .build()
    }
    
    // DAOs
    single { get<com.taha.newraapp.data.local.LocalDatabase>().messageDao() }
    single { get<com.taha.newraapp.data.local.LocalDatabase>().pendingUploadDao() }
    
    // WorkManager
    single { androidx.work.WorkManager.getInstance(androidContext()) }
    
    // Attachment Repository
    single { 
        com.taha.newraapp.data.repository.AttachmentRepository(
            context = androidContext(),
            pendingUploadDao = get(),
            messageDao = get(),
            attachmentApi = get(),
            apiExecutor = get(),
            workManager = get()
        ) 
    }
    
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

