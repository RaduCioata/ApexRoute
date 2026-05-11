package com.apexroute.presentation.roundtrip

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.Color
import com.apexroute.core.theme.*
import com.apexroute.domain.model.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

val accessToken = com.apexroute.BuildConfig.MAPBOX_ACCESS_TOKEN

/**
 * Screen where the user configures round-trip parameters (duration, start point)
 * and initiates route generation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoundTripSetupScreen(
    onRouteGenerated: () -> Unit,
    onBack: () -> Unit,
    viewModel: RoundTripViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.route) {
        if (uiState.route != null) {
            onRouteGenerated()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Round Trip Generator",
                        style = Typography.titleLarge.copy(fontSize = 22.sp),
                        color = Primary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = BackgroundDark
    ) { paddingValues ->
        if (uiState.isLoading) {
            LoadingContent(
                message = uiState.loadingMessage,
                modifier = Modifier.fillMaxSize().padding(paddingValues)
            )
        } else {
            SetupContent(
                uiState = uiState,
                onDurationChange = viewModel::setDuration,
                onStartPointChange = viewModel::setStartPoint,
                onGenerate = viewModel::generateRoute,
                onDismissError = viewModel::clearError,
                modifier = Modifier.fillMaxSize().padding(paddingValues)
            )
        }
    }
}

@Composable
private fun SetupContent(
    uiState: RoundTripUiState,
    onDurationChange: (Int) -> Unit,
    onStartPointChange: (GeoPoint) -> Unit,
    onGenerate: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var showMapPicker by remember { mutableStateOf(false) }
    var showManualInput by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Error display
        if (uiState.error != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                    .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text(uiState.error, color = MaterialTheme.colorScheme.error, style = Typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onDismissError) {
                        Text("Dismiss", color = Primary)
                    }
                }
            }
        }

        // ── Duration Section ──
        Text("Trip Duration", style = Typography.titleMedium, color = TextPrimary)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(GlassBackground)
                .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EditableDurationDisplay(
                    durationMinutes = uiState.durationMinutes,
                    onDurationChange = onDurationChange
                )

                Spacer(modifier = Modifier.height(8.dp))

                Slider(
                    value = uiState.durationMinutes.toFloat(),
                    onValueChange = { onDurationChange(it.toInt()) },
                    valueRange = 15f..240f,
                    steps = 14,
                    colors = SliderDefaults.colors(
                        thumbColor = Primary,
                        activeTrackColor = Primary,
                        inactiveTrackColor = SurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("15 min", style = Typography.bodyMedium, color = TextSecondary)
                    Text("4 hours", style = Typography.bodyMedium, color = TextSecondary)
                }
            }
        }

        // ── Starting Point Section ──
        Text("Starting Point", style = Typography.titleMedium, color = TextPrimary)

        // Current location display
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(GlassBackground)
                .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Primary, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (uiState.startPoint != null) {
                            "${String.format("%.4f", uiState.startPoint.latitude)}, ${String.format("%.4f", uiState.startPoint.longitude)}"
                        } else "No location set",
                        style = Typography.bodyLarge,
                        color = TextPrimary
                    )
                }
            }
        }

        // Location option buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GpsLocationButton(
                onLocationReceived = onStartPointChange,
                modifier = Modifier.weight(1f)
            )
            LocationOptionButton(
                label = "Pick on Map",
                modifier = Modifier.weight(1f),
                onClick = { showMapPicker = !showMapPicker }
            )
            LocationOptionButton(
                label = "Address",
                modifier = Modifier.weight(1f),
                onClick = { showManualInput = !showManualInput }
            )
        }

        // Map picker (expandable)
        if (showMapPicker) {
            MapPickerView(
                currentPoint = uiState.startPoint ?: GeoPoint(46.7712, 23.6236),
                onPointSelected = { point ->
                    onStartPointChange(point)
                }
            )
        }

        // Address search input (expandable)
        if (showManualInput) {
            AddressSearchInput(
                onPointSet = { point ->
                    onStartPointChange(point)
                    showManualInput = false
                }
            )
        }

        // Estimated trip info
        val estimatedDistanceKm = (uiState.durationMinutes / 60.0) * 50
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Primary.copy(alpha = 0.1f))
                .padding(16.dp)
        ) {
            Text(
                "≈ ${String.format("%.0f", estimatedDistanceKm)} km estimated route • avg 50 km/h on curvy roads",
                style = Typography.bodyMedium,
                color = Primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Generate button
        Button(
            onClick = onGenerate,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PrimaryGradient)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate Route", style = Typography.titleMedium.copy(fontSize = 18.sp), color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ── GPS Location Button ──

@Composable
private fun GpsLocationButton(
    onLocationReceived: (GeoPoint) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var permissionGranted by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionGranted = permissions.values.any { it }
        if (permissionGranted) {
            getLastKnownLocation(context)?.let { onLocationReceived(it) }
        }
    }

    LocationOptionButton(
        label = "Use GPS",
        modifier = modifier,
        onClick = {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    )
}

@Composable
private fun LocationOptionButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceVariant)
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = Typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Primary)
    }
}

@SuppressLint("MissingPermission")
private fun getLastKnownLocation(context: Context): GeoPoint? {
    return try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        loc?.let { GeoPoint(it.latitude, it.longitude) }
    } catch (_: Exception) {
        null
    }
}

// ── Map Picker ──

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MapPickerView(
    currentPoint: GeoPoint,
    onPointSelected: (GeoPoint) -> Unit
) {
    val selectedPoint = remember { mutableStateOf(currentPoint) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    setBackgroundColor(0xFF121212.toInt())

                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onMapTap(lat: Double, lng: Double) {
                            val point = GeoPoint(lat, lng)
                            selectedPoint.value = point
                            onPointSelected(point)
                        }
                    }, "Android")

                    webViewClient = WebViewClient()
                    loadDataWithBaseURL(
                        "https://api.mapbox.com",
                        generatePickerHtml(currentPoint),
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            }
        )

        // Label overlay
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(BackgroundDark.copy(alpha = 0.85f))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text("Tap to set starting point", style = Typography.bodyMedium, color = Primary)
        }
    }
}

private fun generatePickerHtml(center: GeoPoint): String {
    return """
<!DOCTYPE html>
<html><head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<script src="https://api.mapbox.com/mapbox-gl-js/v3.4.0/mapbox-gl.js"></script>
<link href="https://api.mapbox.com/mapbox-gl-js/v3.4.0/mapbox-gl.css" rel="stylesheet">
<style>*{margin:0;padding:0}body{background:#121212}#map{position:absolute;top:0;bottom:0;width:100%}.mapboxgl-ctrl-attrib{display:none!important}</style>
</head><body><div id="map"></div>
<script>
mapboxgl.accessToken='$accessToken';
var map=new mapboxgl.Map({container:'map',style:'mapbox://styles/mapbox/dark-v11',center:[${center.longitude},${center.latitude}],zoom:11,attributionControl:false});
var marker=new mapboxgl.Marker({color:'#F9A826'}).setLngLat([${center.longitude},${center.latitude}]).addTo(map);
map.on('click',function(e){
    marker.setLngLat(e.lngLat);
    Android.onMapTap(e.lngLat.lat,e.lngLat.lng);
});
</script></body></html>
    """.trimIndent()
}

// ── Editable Duration Display ──

@Composable
private fun EditableDurationDisplay(
    durationMinutes: Int,
    onDurationChange: (Int) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    if (isEditing) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        OutlinedTextField(
            value = editText,
            onValueChange = { newVal ->
                // Only allow digits
                editText = newVal.filter { it.isDigit() }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    val mins = editText.toIntOrNull()
                    if (mins != null && mins in 15..240) {
                        onDurationChange(mins)
                    }
                    isEditing = false
                    focusManager.clearFocus()
                }
            ),
            singleLine = true,
            textStyle = Typography.titleLarge.copy(
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = Primary
            ),
            modifier = Modifier
                .width(160.dp)
                .focusRequester(focusRequester),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary,
                cursorColor = Primary,
                focusedTextColor = Primary,
                unfocusedTextColor = Primary,
                unfocusedBorderColor = GlassBorder
            )
        )
        Text(
            "Enter 15–240 and press Done",
            style = Typography.bodyMedium,
            color = TextSecondary
        )
    } else {
        Text(
            "$durationMinutes",
            style = Typography.titleLarge.copy(fontSize = 56.sp, fontWeight = FontWeight.Bold),
            color = Primary,
            modifier = Modifier.clickable {
                editText = durationMinutes.toString()
                isEditing = true
            }
        )
        Text(
            "minutes  ✎",
            style = Typography.bodyLarge,
            color = TextSecondary,
            modifier = Modifier.clickable {
                editText = durationMinutes.toString()
                isEditing = true
            }
        )
    }
}

// ── Address Search Input (Mapbox Geocoding) ──

@Composable
private fun AddressSearchInput(
    onPointSet: (GeoPoint) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GeocodingResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GlassBackground)
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search address") },
                placeholder = { Text("e.g. Strada Memorandumului, Cluj", color = TextSecondary.copy(alpha = 0.5f)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Primary) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (query.isNotBlank()) {
                            focusManager.clearFocus()
                            scope.launch {
                                isSearching = true
                                errorMsg = null
                                results = emptyList()
                                try {
                                    results = geocode(query)
                                    if (results.isEmpty()) errorMsg = "No results found"
                                } catch (e: Exception) {
                                    errorMsg = e.message ?: "Geocoding failed"
                                }
                                isSearching = false
                            }
                        }
                    }
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    focusedLabelColor = Primary,
                    cursorColor = Primary,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    unfocusedBorderColor = GlassBorder,
                    unfocusedLabelColor = TextSecondary
                )
            )

            // Search button
            Button(
                onClick = {
                    if (query.isNotBlank()) {
                        focusManager.clearFocus()
                        scope.launch {
                            isSearching = true
                            errorMsg = null
                            results = emptyList()
                            try {
                                results = geocode(query)
                                if (results.isEmpty()) errorMsg = "No results found"
                            } catch (e: Exception) {
                                errorMsg = e.message ?: "Geocoding failed"
                            }
                            isSearching = false
                        }
                    }
                },
                enabled = query.isNotBlank() && !isSearching,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = BackgroundDark)
            ) {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = BackgroundDark,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Search", style = Typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            }

            // Error
            if (errorMsg != null) {
                Text(errorMsg!!, style = Typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }

            // Results list
            results.forEach { result ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceVariant)
                        .clickable {
                            onPointSet(result.point)
                        }
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            result.name,
                            style = Typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = TextPrimary
                        )
                        if (result.address.isNotBlank()) {
                            Text(result.address, style = Typography.bodyMedium, color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

private data class GeocodingResult(
    val name: String,
    val address: String,
    val point: GeoPoint
)

/**
 * Calls the Mapbox Geocoding API to convert an address string to coordinates.
 */
private suspend fun geocode(query: String): List<GeocodingResult> = withContext(Dispatchers.IO) {
    val encoded = URLEncoder.encode(query, "UTF-8")
    val url = "https://api.mapbox.com/geocoding/v5/mapbox.places/$encoded.json" +
            "?access_token=$accessToken&limit=5&language=ro"
    val text = URL(url).readText()
    val json = JSONObject(text)
    val features = json.getJSONArray("features")
    val results = mutableListOf<GeocodingResult>()
    for (i in 0 until features.length()) {
        val feature = features.getJSONObject(i)
        val name = feature.optString("text", "")
        val placeName = feature.optString("place_name", "")
        val center = feature.getJSONArray("center")
        val lng = center.getDouble(0)
        val lat = center.getDouble(1)
        results.add(GeocodingResult(name, placeName, GeoPoint(lat, lng)))
    }
    results
}

// ── Loading Animation ──

@Composable
private fun LoadingContent(message: String, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(2000, easing = LinearEasing)),
        label = "rotation"
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(80.dp).rotate(rotation).clip(CircleShape)
                .border(4.dp, Primary, CircleShape)
                .border(4.dp, Primary.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(Primary))
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(message, style = Typography.titleMedium, color = Primary)
        Spacer(modifier = Modifier.height(8.dp))
        Text("This may take a few seconds…", style = Typography.bodyMedium, color = TextSecondary)
    }
}
