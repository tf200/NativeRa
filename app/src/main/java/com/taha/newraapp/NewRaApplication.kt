package com.taha.newraapp

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.taha.newraapp.data.socket.GlobalMessageHandler
import com.taha.newraapp.data.socket.PresenceService
import com.taha.newraapp.di.allModules
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class NewRaApplication : Application(), ImageLoaderFactory {
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
        
        // Initialize GlobalMessageHandler to process incoming messages at app level
        // This ensures messages are stored even when no chat room is open
        val globalMessageHandler: GlobalMessageHandler by inject()
        globalMessageHandler.initialize()
    }
    
    /**
     * Provide custom ImageLoader with VideoFrameDecoder for video thumbnails.
     */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }
}
