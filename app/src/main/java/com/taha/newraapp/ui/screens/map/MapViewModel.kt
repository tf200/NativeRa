package com.taha.newraapp.ui.screens.map

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * ViewModel for the Map screen.
 * Handles location fetching and permission state.
 */
class MapViewModel(
    application: Application
) : ViewModel() {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(application)

    private val _userLocation = MutableStateFlow<UserLocation?>(null)
    val userLocation: StateFlow<UserLocation?> = _userLocation.asStateFlow()

    private val _isLoadingLocation = MutableStateFlow(false)
    val isLoadingLocation: StateFlow<Boolean> = _isLoadingLocation.asStateFlow()

    private val _locationError = MutableStateFlow<String?>(null)
    val locationError: StateFlow<String?> = _locationError.asStateFlow()

    /**
     * Fetches the user's current location.
     * Prioritizes lastLocation for instant results, then refines with fresh GPS.
     * Should only be called after location permission is granted.
     */
    @SuppressLint("MissingPermission")
    fun fetchUserLocation() {
        viewModelScope.launch {
            _isLoadingLocation.value = true
            _locationError.value = null

            try {
                // Step 1: Try last known location first for immediate result
                val lastLocation = fusedLocationClient.lastLocation.await()
                if (lastLocation != null) {
                    _userLocation.value = UserLocation(
                        latitude = lastLocation.latitude,
                        longitude = lastLocation.longitude
                    )
                    // Stop loading immediately - we have a usable location
                    _isLoadingLocation.value = false
                }

                // Step 2: Fetch fresh location in background to refine
                val cancellationToken = CancellationTokenSource()
                val freshLocation = fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cancellationToken.token
                ).await()

                if (freshLocation != null) {
                    _userLocation.value = UserLocation(
                        latitude = freshLocation.latitude,
                        longitude = freshLocation.longitude
                    )
                } else if (_userLocation.value == null) {
                    // Only show error if we have no location at all
                    _locationError.value = "Unable to get location. Please try again."
                }
            } catch (e: Exception) {
                if (_userLocation.value == null) {
                    _locationError.value = "Error getting location: ${e.message}"
                }
            } finally {
                _isLoadingLocation.value = false
            }
        }
    }

    /**
     * Clears any location error.
     */
    fun clearError() {
        _locationError.value = null
    }
}

/**
 * Data class representing user's location.
 */
data class UserLocation(
    val latitude: Double,
    val longitude: Double
)
