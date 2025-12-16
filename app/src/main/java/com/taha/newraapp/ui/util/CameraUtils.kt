package com.taha.newraapp.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.graphics.scale
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Utility object for camera operations.
 * Handles file creation, URI generation, and image compression.
 */
object CameraUtils {
    private const val TAG = "CameraUtils"
    
    // Max photo resolution (width) for compression
    const val MAX_PHOTO_WIDTH = 1280
    const val JPEG_QUALITY = 80
    
    /**
     * Create a temporary file for photo capture.
     */
    fun createImageFile(context: Context): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "IMG_${timeStamp}.jpg"
        
        val cameraDir = File(context.cacheDir, "camera")
        if (!cameraDir.exists()) {
            cameraDir.mkdirs()
        }
        
        return File(cameraDir, fileName)
    }
    
    /**
     * Create a temporary file for video capture.
     */
    fun createVideoFile(context: Context): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "VID_${timeStamp}.mp4"
        
        val cameraDir = File(context.cacheDir, "camera")
        if (!cameraDir.exists()) {
            cameraDir.mkdirs()
        }
        
        return File(cameraDir, fileName)
    }
    
    /**
     * Get a FileProvider URI for a file.
     * Required for sharing files with camera app on Android 7.0+.
     */
    fun getUriForFile(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
    
    /**
     * Compress an image to reduce file size while maintaining quality.
     * Returns the URI of the compressed image.
     * 
     * @param context Application context
     * @param sourceUri URI of the original image
     * @param maxWidth Maximum width in pixels (height scales proportionally)
     * @return URI of the compressed image (same as source, but file is modified)
     */
    fun compressImage(
        context: Context,
        sourceUri: Uri,
        maxWidth: Int = MAX_PHOTO_WIDTH
    ): Uri {
        try {
            // Load bitmap with inJustDecodeBounds to get dimensions
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }
            
            val originalWidth = options.outWidth
            options.outHeight
            
            // Calculate sample size for memory-efficient loading
            var sampleSize = 1
            if (originalWidth > maxWidth) {
                sampleSize = (originalWidth.toFloat() / maxWidth).toInt()
            }
            
            // Load bitmap with sample size
            options.apply {
                inJustDecodeBounds = false
                inSampleSize = sampleSize
            }
            
            val bitmap = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: return sourceUri
            
            // Scale to exact max width if still too large
            val scaledBitmap = if (bitmap.width > maxWidth) {
                val ratio = maxWidth.toFloat() / bitmap.width
                val newHeight = (bitmap.height * ratio).toInt()
                bitmap.scale(maxWidth, newHeight).also {
                    if (it != bitmap) bitmap.recycle()
                }
            } else {
                bitmap
            }
            
            // Get file path from URI and overwrite with compressed version
            val file = getFileFromUri(context, sourceUri)
            if (file != null && file.exists()) {
                FileOutputStream(file).use { output ->
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
                }
                scaledBitmap.recycle()
                Log.d(TAG, "Compressed image: ${file.length() / 1024}KB")
            }
            
            return sourceUri
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing image", e)
            return sourceUri
        }
    }
    
    /**
     * Get the File object from a FileProvider URI.
     */
    private fun getFileFromUri(context: Context, uri: Uri): File? {
        val path = uri.path ?: return null
        
        // FileProvider URIs have format: /external_path/... or /cache/...
        // We need to extract the actual file path
        val cameraDir = File(context.cacheDir, "camera")
        val fileName = path.substringAfterLast("/")
        
        val file = File(cameraDir, fileName)
        return if (file.exists()) file else null
    }
    
    /**
     * Get file size in bytes from URI.
     */
    fun getFileSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.openInputStream(uri)?.use { it.available().toLong() } ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Error getting file size", e)
            0
        }
    }
    
    /**
     * Clean up old camera cache files.
     * Call this periodically to free up space.
     */
    fun cleanCameraCache(context: Context, maxAgeMs: Long = 24 * 60 * 60 * 1000) {
        val cameraDir = File(context.cacheDir, "camera")
        if (!cameraDir.exists()) return
        
        val cutoff = System.currentTimeMillis() - maxAgeMs
        cameraDir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) {
                file.delete()
            }
        }
    }
}
