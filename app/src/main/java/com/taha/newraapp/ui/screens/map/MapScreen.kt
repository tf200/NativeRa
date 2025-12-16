package com.taha.newraapp.ui.screens.map

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.common.MapboxOptions
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.attribution.attribution
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.logo.logo
import com.mapbox.maps.plugin.scalebar.scalebar
import com.taha.newraapp.BuildConfig
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.platform.LocalDensity
import org.koin.androidx.compose.koinViewModel
import com.mapbox.maps.extension.localization.localizeLabels
import java.util.Locale

// Initialize Mapbox access token at module level
private val mapboxTokenInitialized by lazy {
    MapboxOptions.accessToken = BuildConfig.MAPBOX_ACCESS_TOKEN
    true
}

/**
 * Full-screen map screen with user location.
 * Shows Mapbox map centered on user's current location.
 */
@Composable
fun MapScreen(
    onNavigateUp: () -> Unit,
    viewModel: MapViewModel = koinViewModel()
) {
    // Ensure Mapbox token is initialized before creating map
    check(mapboxTokenInitialized) { "Mapbox token must be initialized" }
    
    val context = LocalContext.current
    val userLocation by viewModel.userLocation.collectAsStateWithLifecycle()
    val isLoadingLocation by viewModel.isLoadingLocation.collectAsStateWithLifecycle()
    val locationError by viewModel.locationError.collectAsStateWithLifecycle()

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PermissionChecker.PERMISSION_GRANTED
        )
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        
        if (hasLocationPermission) {
            viewModel.fetchUserLocation()
        }
    }

    // Request permission on first composition if not granted
    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            viewModel.fetchUserLocation()
        }
    }

    // Map state
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var isMapReady by remember { mutableStateOf(false) }

    // System bars insets
    val density = LocalDensity.current
    val statusBars = WindowInsets.statusBars
    val navigationBars = WindowInsets.navigationBars

    val topPaddingDp = with(density) { statusBars.getTop(density).toDp() }
    val bottomPaddingDp = with(density) { navigationBars.getBottom(density).toDp() }
    val statusBarHeightPx = (statusBars.getTop(density)).toFloat()
    val navBarHeightPx = (navigationBars.getBottom(density)).toFloat()

    // Fly to user location when it changes
    LaunchedEffect(userLocation, isMapReady) {
        userLocation?.let { location ->
            if (isMapReady) {
                mapView?.mapboxMap?.flyTo(
                    CameraOptions.Builder()
                        .center(Point.fromLngLat(location.longitude, location.latitude))
                        .zoom(15.0)
                        .build()
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // We render the map if we have permission (or if we failed permission but want to show world map? 
        // Logic before was: show map if location exists OR if loading failed (error). 
        // If loading, show spinner.
        // New logic: Check if we have determined whether to show the map.
        // We can just always show the map, but initialize it once.
        
        // Single Persistent MapView
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    mapView = this
                    
                    // Configure ornaments (compass, logo, attribution) margins for system bars
                    // Configure compass position (top-right, below status bar)
                    compass.updateSettings {
                        marginTop = statusBarHeightPx + 16f
                        marginRight = 16f
                    }
                    
                    // Configure logo position (bottom-left, above nav bar)
                    logo.updateSettings {
                        marginBottom = navBarHeightPx + 16f
                        marginLeft = 16f
                    }
                    
                    // Configure attribution position (bottom-left, above nav bar, next to logo)
                    attribution.updateSettings {
                        marginBottom = navBarHeightPx + 16f
                        marginLeft = 100f
                    }
                    
                    // Disable scale bar
                    scalebar.updateSettings {
                        enabled = false
                    }
                    
                    // Load map style
                    mapboxMap.loadStyle(Style.MAPBOX_STREETS) { style ->
                        isMapReady = true
                        
                        // Enable localization for RTL support (Arabic)
                        style.localizeLabels(Locale.getDefault())
                        
                        // Enable location component
                        location.updateSettings {
                            enabled = true
                            pulsingEnabled = true
                            locationPuck = createDefault2DPuck(withBearing = true)
                        }
                    }
                }
            },
            update = { 
                // No-op updates prevents recreation
            }
        )
        
        // Initial camera position setup - done once when location is first found and map is ready
        // We do this via LaunchedEffect above (flyTo), but we might want an initial setCamera?
        // exact setCamera is better for initial load to avoid "flying" from 0,0
        // We can do this in the LaunchedEffect if isMapReady AND usageLocation is fresh.
        // Actually, flyTo is fine, but maybe setCamera is preferred for the VERY first time.
        // Let's keep the existing flyTo logic for simplicity, but maybe add a "hasMovedToInitialLocation" flag if needed.
        // For now, the flyTo in LaunchedEffect handles it.

        
        // Loading Indicator Overlay
        AnimatedVisibility(
            visible = isLoadingLocation,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        // "Getting your location..." Text if loading and no location yet
        if (isLoadingLocation && userLocation == null) {
             Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 100.dp), // Push down below center spinner
                contentAlignment = Alignment.Center
            ) {
                 Text(
                    text = "Getting your location...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface.copy(alpha=0.7f), CircleShape)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // Back button overlay
        Box(
            modifier = Modifier
                .padding(top = topPaddingDp) // Use WindowInsets padding
                .padding(16.dp)
                .align(Alignment.TopStart)
        ) {
            IconButton(
                onClick = onNavigateUp,
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Zoom Controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = bottomPaddingDp) // Use WindowInsets padding
                .padding(bottom = 100.dp, end = 24.dp) // Position above MyLocation FAB
        ) {
            SmallFloatingActionButton(
                onClick = {
                    val currentZoom = mapView?.mapboxMap?.cameraState?.zoom ?: 0.0
                    mapView?.mapboxMap?.flyTo(
                        CameraOptions.Builder()
                            .zoom(currentZoom + 1)
                            .build()
                    )
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In")
            }

            Spacer(modifier = Modifier.height(8.dp))

            SmallFloatingActionButton(
                onClick = {
                    val currentZoom = mapView?.mapboxMap?.cameraState?.zoom ?: 0.0
                    mapView?.mapboxMap?.flyTo(
                        CameraOptions.Builder()
                            .zoom(currentZoom - 1)
                            .build()
                    )
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
            }
        }

        // Re-center FAB
        FloatingActionButton(
            onClick = { viewModel.fetchUserLocation() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = bottomPaddingDp) // Use WindowInsets padding
                .padding(24.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = "My Location",
                tint = Color.White
            )
        }

        // Error snackbar
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomPaddingDp)
                .padding(16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
             locationError?.let { error ->
                Snackbar(
                    action = {
                        Text(
                            text = "Retry",
                            color = MaterialTheme.colorScheme.inversePrimary,
                            modifier = Modifier
                                .padding(8.dp)
                                .clickable { viewModel.fetchUserLocation() }
                        )
                    },
                    dismissAction = {
                        viewModel.clearError()
                    }
                ) {
                    Text(text = error)
                }
            }
            
            // Show generic error if purely "no location" but not from viewmodel error state?
            // The viewmodel sets error if fresh location fails and no last location.
            // So locationError should cover it.
        }
    }

    // Cleanup MapView when leaving composition
    DisposableEffect(Unit) {
        onDispose {
            mapView = null
        }
    }
}
