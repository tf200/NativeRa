package com.taha.newraapp.ui.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Audio recorder utility using MediaRecorder with AAC encoding.
 * Provides amplitude sampling for waveform visualization.
 */
class AudioRecorder(private val context: Context) {
    
    companion object {
        private const val TAG = "AudioRecorder"
        private const val SAMPLE_RATE = 44100
        private const val BIT_RATE = 32000 // Low bitrate for small files (~240KB/min)
    }
    
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()
    
    private val _recordingDuration = MutableStateFlow(0L)
    val recordingDuration: StateFlow<Long> = _recordingDuration.asStateFlow()
    
    private val _amplitude = MutableStateFlow(0)
    val amplitude: StateFlow<Int> = _amplitude.asStateFlow()
    
    private var startTime: Long = 0L
    
    /**
     * Start recording audio to a temporary file.
     * @return true if recording started successfully
     */
    fun startRecording(): Boolean {
        if (_isRecording.value) {
            Log.w(TAG, "Already recording")
            return false
        }
        
        try {
            // Create output file
            outputFile = createAudioFile()
            
            // Initialize MediaRecorder
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(SAMPLE_RATE)
                setAudioEncodingBitRate(BIT_RATE)
                setOutputFile(outputFile?.absolutePath)
                
                prepare()
                start()
            }
            
            startTime = System.currentTimeMillis()
            _isRecording.value = true
            _recordingDuration.value = 0L
            
            Log.d(TAG, "Recording started: ${outputFile?.absolutePath}")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            cleanup()
            return false
        }
    }
    
    /**
     * Stop recording and return the file path.
     * @return File path of the recorded audio, or null if failed
     */
    fun stopRecording(): String? {
        if (!_isRecording.value) {
            Log.w(TAG, "Not recording")
            return null
        }
        
        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            _isRecording.value = false
            
            val path = outputFile?.absolutePath
            Log.d(TAG, "Recording stopped: $path, size: ${outputFile?.length()} bytes")
            path
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording", e)
            cleanup()
            null
        }
    }
    
    /**
     * Cancel recording and delete the file.
     */
    fun cancelRecording() {
        if (!_isRecording.value) return
        
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recorder", e)
        }
        
        // Delete the partial file
        outputFile?.delete()
        cleanup()
        
        Log.d(TAG, "Recording cancelled")
    }
    
    /**
     * Update recording duration (call this from a timer).
     */
    fun updateDuration() {
        if (_isRecording.value) {
            _recordingDuration.value = System.currentTimeMillis() - startTime
        }
    }
    
    /**
     * Get current amplitude for waveform visualization.
     * Call this periodically (e.g., every 100ms).
     * @return Amplitude value (0-32767)
     */
    fun updateAmplitude(): Int {
        return try {
            val amp = mediaRecorder?.maxAmplitude ?: 0
            _amplitude.value = amp
            amp
        } catch (e: Exception) {
            0
        }
    }
    
    private fun cleanup() {
        mediaRecorder?.release()
        mediaRecorder = null
        outputFile = null
        _isRecording.value = false
        _recordingDuration.value = 0L
        _amplitude.value = 0
    }
    
    private fun createAudioFile(): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "AUDIO_$timestamp.m4a"
        val storageDir = File(context.filesDir, "audio")
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        return File(storageDir, fileName)
    }
    
    /**
     * Format duration in milliseconds to MM:SS string.
     */
    fun formatDuration(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / 1000) / 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
