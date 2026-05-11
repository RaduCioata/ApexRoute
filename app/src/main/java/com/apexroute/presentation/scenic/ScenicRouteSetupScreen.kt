package com.apexroute.presentation.scenic

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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import com.apexroute.core.theme.*
import com.apexroute.domain.model.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

private val MAPBOX_TOKEN = com.apexroute.BuildConfig.MAPBOX_ACCESS_TOKEN

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScenicRouteSetupScreen(
    onRouteGenerated: () -> Unit,
    onBack: () -> Unit,
    viewModel: ScenicRouteViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.routes) {
        if (uiState.routes.isNotEmpty()) onRouteGenerated()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scenic Route A → B", style = Typography.titleLarge.copy(fontSize = 22.sp), color = Primary) },
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
            LoadingContent(uiState.loadingMessage, Modifier.fillMaxSize().padding(paddingValues))
        } else {
            SetupContent(
                uiState = uiState,
                onSetPointA = { point, label -> viewModel.setPointA(point, label) },
                onSetPointB = { point, label -> viewModel.setPointB(point, label) },
                onGenerate = viewModel::generateRoute,
                onDismissError = viewModel::clearError,
                modifier = Modifier.fillMaxSize().padding(paddingValues)
            )
        }
    }
}

@Composable
private fun SetupContent(
    uiState: ScenicRouteUiState,
    onSetPointA: (GeoPoint, String) -> Unit,
    onSetPointB: (GeoPoint, String) -> Unit,
    onGenerate: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        if (uiState.error != null) {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                    .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text(uiState.error, color = MaterialTheme.colorScheme.error, style = Typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onDismissError) { Text("Dismiss", color = Primary) }
                }
            }
        }

        // ── Point A ──
        PointSection(
            label = "Starting Point (A)",
            currentPoint = uiState.pointA,
            currentLabel = uiState.pointALabel,
            accentChar = "A",
            onPointSet = onSetPointA
        )

        // Divider with arrow
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("▼", style = Typography.titleLarge, color = Primary)
        }

        // ── Point B ──
        PointSection(
            label = "Destination (B)",
            currentPoint = uiState.pointB,
            currentLabel = uiState.pointBLabel,
            accentChar = "B",
            onPointSet = onSetPointB
        )

        Spacer(Modifier.height(8.dp))

        // Generate button
        Button(
            onClick = onGenerate,
            enabled = uiState.pointA != null && uiState.pointB != null,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (uiState.pointA != null && uiState.pointB != null) SecondaryGradient else Brush.linearGradient(listOf(SurfaceVariant, SurfaceVariant)))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = if (uiState.pointA != null && uiState.pointB != null) Color.White else TextSecondary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Generate Scenic Route", style = Typography.titleMedium.copy(fontSize = 18.sp), color = if (uiState.pointA != null && uiState.pointB != null) Color.White else TextSecondary)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PointSection(
    label: String,
    currentPoint: GeoPoint?,
    currentLabel: String,
    accentChar: String,
    onPointSet: (GeoPoint, String) -> Unit
) {
    var showSearch by remember { mutableStateOf(false) }

    Text(label, style = Typography.titleMedium, color = TextPrimary)

    // Current point display
    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GlassBackground)
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .clickable { showSearch = !showSearch }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(Primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(accentChar, style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Primary)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                if (currentPoint != null) {
                    Text(
                        currentLabel.ifBlank { "${String.format("%.4f", currentPoint.latitude)}, ${String.format("%.4f", currentPoint.longitude)}" },
                        style = Typography.bodyLarge, color = TextPrimary, maxLines = 2
                    )
                } else {
                    Text("Tap to search address", style = Typography.bodyLarge, color = TextSecondary)
                }
            }
        }
    }

    if (showSearch) {
        AddressSearch(onPointSet = { point, name ->
            onPointSet(point, name)
            showSearch = false
        })
    }
}

@Composable
private fun AddressSearch(onPointSet: (GeoPoint, String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GeoResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GlassBackground)
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                label = { Text("Search address") },
                placeholder = { Text("e.g. Turda, Cluj", color = TextSecondary.copy(alpha = 0.5f)) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Primary) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    if (query.isNotBlank()) {
                        focusManager.clearFocus()
                        scope.launch {
                            isSearching = true; errorMsg = null; results = emptyList()
                            try { results = geocode(query); if (results.isEmpty()) errorMsg = "No results" }
                            catch (e: Exception) { errorMsg = e.message }
                            isSearching = false
                        }
                    }
                }),
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary, focusedLabelColor = Primary, cursorColor = Primary,
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                    unfocusedBorderColor = GlassBorder, unfocusedLabelColor = TextSecondary
                )
            )
            Button(
                onClick = {
                    if (query.isNotBlank()) {
                        focusManager.clearFocus()
                        scope.launch {
                            isSearching = true; errorMsg = null; results = emptyList()
                            try { results = geocode(query); if (results.isEmpty()) errorMsg = "No results" }
                            catch (e: Exception) { errorMsg = e.message }
                            isSearching = false
                        }
                    }
                },
                enabled = query.isNotBlank() && !isSearching,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = BackgroundDark)
            ) {
                if (isSearching) { CircularProgressIndicator(Modifier.size(18.dp), BackgroundDark, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                Text("Search", style = Typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            }
            if (errorMsg != null) Text(errorMsg!!, style = Typography.bodyMedium, color = MaterialTheme.colorScheme.error)

            results.forEach { r ->
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SurfaceVariant)
                        .clickable { onPointSet(r.point, r.name) }.padding(12.dp)
                ) {
                    Column {
                        Text(r.name, style = Typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
                        if (r.address.isNotBlank()) Text(r.address, style = Typography.bodyMedium, color = TextSecondary)
                    }
                }
            }
        }
    }
}

private data class GeoResult(val name: String, val address: String, val point: GeoPoint)

private suspend fun geocode(query: String): List<GeoResult> = withContext(Dispatchers.IO) {
    val encoded = URLEncoder.encode(query, "UTF-8")
    val url = "https://api.mapbox.com/geocoding/v5/mapbox.places/$encoded.json?access_token=$MAPBOX_TOKEN&limit=5&language=ro"
    val json = JSONObject(URL(url).readText())
    val features = json.getJSONArray("features")
    (0 until features.length()).map { i ->
        val f = features.getJSONObject(i)
        val c = f.getJSONArray("center")
        GeoResult(f.optString("text", ""), f.optString("place_name", ""), GeoPoint(c.getDouble(1), c.getDouble(0)))
    }
}

@Composable
private fun LoadingContent(message: String, modifier: Modifier = Modifier) {
    val rotation by rememberInfiniteTransition(label = "l").animateFloat(
        0f, 360f, infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "r"
    )
    Column(modifier, Arrangement.Center, Alignment.CenterHorizontally) {
        Box(Modifier.size(80.dp).rotate(rotation).clip(CircleShape).border(4.dp, Primary, CircleShape), Alignment.Center) {
            Box(Modifier.size(16.dp).clip(CircleShape).background(Primary))
        }
        Spacer(Modifier.height(32.dp))
        Text(message, style = Typography.titleMedium, color = Primary)
        Spacer(Modifier.height(8.dp))
        Text("This may take a few seconds…", style = Typography.bodyMedium, color = TextSecondary)
    }
}
