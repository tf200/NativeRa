package com.taha.newraapp

import android.app.Application
import com.taha.newraapp.data.socket.PresenceService
import com.taha.newraapp.di.allModules
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class NewRaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(Level.DEBUG)
            androidContext(this@NewRaApplication)
            modules(allModules)
        }
        
        // Initialize PresenceService with lifecycle observer
        // This enables automatic heartbeat start/stop based on app foreground/background
        val presenceService: PresenceService by inject()
        presenceService.initialize()
    }
}
