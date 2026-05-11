package com.apexroute.presentation.recent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.apexroute.core.theme.*
import com.apexroute.data.repository.RecentRoutesRepository
import com.apexroute.domain.model.Route

private val MAPBOX_TOKEN = com.apexroute.BuildConfig.MAPBOX_ACCESS_TOKEN

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentRouteResultScreen(
    routeId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { RecentRoutesRepository(context) }
    val route = remember(routeId) { repository.getRouteById(routeId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recent Route", color = Primary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark.copy(alpha = 0.9f))
            )
        },
        containerColor = BackgroundDark
    ) { paddingValues ->
        if (route == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Route not found.", color = TextSecondary)
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                EmbeddedMapView(route)

                Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                    DetailsCard(route, context)
                }
            }
        }
    }
}

@Composable
private fun EmbeddedMapView(route: Route) {
    val html = remember(route.id) { generateMapHtml(route) }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                setBackgroundColor(0xFF121212.toInt())
                webViewClient = WebViewClient()
                loadDataWithBaseURL("https://api.mapbox.com", html, "text/html", "UTF-8", null)
            }
        }
    )
}

private fun generateMapHtml(route: Route): String {
    val coords = route.points.joinToString(",") { "[${it.longitude},${it.latitude}]" }
    val centerLng = route.points.firstOrNull()?.longitude ?: 23.6236
    val centerLat = route.points.firstOrNull()?.latitude ?: 46.7712

    return """<!DOCTYPE html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<script src="https://api.mapbox.com/mapbox-gl-js/v3.4.0/mapbox-gl.js"></script>
<link href="https://api.mapbox.com/mapbox-gl-js/v3.4.0/mapbox-gl.css" rel="stylesheet">
<style>*{margin:0;padding:0;box-sizing:border-box}body{background:#121212}#map{position:absolute;top:0;bottom:0;width:100%}.mapboxgl-ctrl-attrib{display:none!important}</style>
</head><body><div id="map"></div>
<script>
mapboxgl.accessToken='$MAPBOX_TOKEN';
var map=new mapboxgl.Map({container:'map',style:'mapbox://styles/mapbox/dark-v11',center:[${centerLng},${centerLat}],zoom:11,attributionControl:false});
var marker=new mapboxgl.Marker({color:'#F9A826'}).setLngLat([${centerLng},${centerLat}]).addTo(map);
map.on('load',function(){
map.addSource('route',{type:'geojson',data:{type:'Feature',properties:{},geometry:{type:'LineString',coordinates:[$coords]}}});
map.addLayer({id:'route',type:'line',source:'route',layout:{'line-join':'round','line-cap':'round'},paint:{'line-color':'#00E5FF','line-width':4}});
});
</script></body></html>""".trimIndent()
}

@Composable
private fun DetailsCard(route: Route, context: Context) {
    var animationTriggered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animationTriggered = true }
    val animatedProgress by animateFloatAsState(
        targetValue = if (animationTriggered) (route.pleasureScore / 10.0).toFloat().coerceIn(0f, 1f) else 0f,
        animationSpec = tween(1500, easing = FastOutSlowInEasing),
        label = "pleasureProgress"
    )

    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .background(SurfaceDark).border(1.dp, GlassBorder, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .padding(top = 12.dp, bottom = 32.dp, start = 24.dp, end = 24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(GlassBorder))
            Spacer(modifier = Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text("Pleasure Score", style = Typography.labelSmall, color = TextSecondary)
                    Text(String.format("%.1f", route.pleasureScore), style = Typography.titleLarge.copy(fontSize = 42.sp, fontWeight = FontWeight.ExtraBold), color = Primary)
                }
                Box(Modifier.width(140.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(SurfaceVariant)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(animatedProgress).clip(RoundedCornerShape(5.dp)).background(PrimaryGradient))
                }
            }
            HorizontalDivider(color = GlassBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                Metric("Distance", String.format("%.1f km", route.totalDistanceKm))
                Metric("Duration", "${route.estimatedDurationMin} min")
                Metric("Curves", "${route.curveCount}")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { openGoogleMapsWithWaypoints(context, route) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Navigate with Google Maps", 
                    style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = Typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold), color = TextPrimary)
        Spacer(Modifier.height(2.dp))
        Text(label, style = Typography.bodyMedium, color = TextSecondary)
    }
}

private fun openGoogleMapsWithWaypoints(context: Context, route: Route) {
    if (route.points.isEmpty()) return
    val maxWaypoints = 8
    val origin = route.points.first()
    val destination = route.points.last()
    val intermediates = if (route.points.size > 2) route.points.drop(1).dropLast(1) else emptyList()
    val step = if (intermediates.isNotEmpty()) maxOf(1, intermediates.size / maxWaypoints) else 1
    val sampledWaypoints = intermediates.filterIndexed { index, _ -> index % step == 0 }.take(maxWaypoints)
    val waypointsStr = sampledWaypoints.joinToString("|") { "${it.latitude},${it.longitude}" }
    val waypointsParam = if (waypointsStr.isNotEmpty()) "&waypoints=$waypointsStr" else ""
    val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&origin=${origin.latitude},${origin.longitude}&destination=${destination.latitude},${destination.longitude}$waypointsParam&travelmode=driving")
    
    val intent = Intent(Intent.ACTION_VIEW, uri)
    intent.setPackage("com.google.android.apps.maps")
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
}
