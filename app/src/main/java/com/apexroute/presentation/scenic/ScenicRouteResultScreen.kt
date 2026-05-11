package com.apexroute.presentation.scenic

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.apexroute.core.theme.*
import com.apexroute.domain.model.Route

private val MAPBOX_TOKEN = com.apexroute.BuildConfig.MAPBOX_ACCESS_TOKEN

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScenicRouteResultScreen(
    onBack: () -> Unit,
    onRegenerate: () -> Unit,
    viewModel: ScenicRouteViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val routes = uiState.routes
    val selectedIndex = uiState.selectedRouteIndex
    val selectedRoute = routes.getOrNull(selectedIndex)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scenic Alternatives", style = Typography.titleLarge.copy(fontSize = 22.sp), color = Primary) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary) } },
                actions = { IconButton(onClick = onRegenerate) { Icon(Icons.Default.Refresh, "Regenerate", tint = Primary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        containerColor = BackgroundDark
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (routes.isNotEmpty()) {
                ScrollableTabRow(
                    selectedTabIndex = selectedIndex,
                    containerColor = SurfaceDark,
                    contentColor = Primary,
                    edgePadding = 16.dp
                ) {
                    routes.forEachIndexed { index, _ ->
                        Tab(
                            selected = selectedIndex == index,
                            onClick = { viewModel.selectRoute(index) },
                            text = { Text("Route ${index + 1}") }
                        )
                    }
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                if (routes.isNotEmpty()) MapView(routes, selectedIndex)
            }
            if (selectedRoute != null) DetailsCard(selectedRoute)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MapView(routes: List<Route>, selectedIndex: Int) {
    val html = remember(routes, selectedIndex) { buildMapHtml(routes, selectedIndex) }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                settings.javaScriptEnabled = true; settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true; settings.useWideViewPort = true
                setBackgroundColor(0xFF121212.toInt()); webViewClient = WebViewClient()
                loadDataWithBaseURL("https://api.mapbox.com", html, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL("https://api.mapbox.com", html, "text/html", "UTF-8", null)
        }
    )
}

private fun buildMapHtml(routes: List<Route>, selectedIndex: Int): String {
    val startPt = routes.firstOrNull()?.points?.firstOrNull()
    val cLng = startPt?.longitude ?: 23.6
    val cLat = startPt?.latitude ?: 46.7
    
    val colors = listOf("#4FC3F7", "#F9A826", "#E91E63", "#4CAF50")

    val addSourcesAndLayers = routes.mapIndexed { idx, route ->
        val coords = route.points.joinToString(",") { "[${it.longitude},${it.latitude}]" }
        val isSelected = idx == selectedIndex
        val color = colors[idx % colors.size]
        val opacity = if (isSelected) "0.95" else "0.3"
        val width = if (isSelected) "6" else "3"
        val blurOpacity = if (isSelected) "0.25" else "0.05"
        """
        var rc$idx=[$coords];
        var gj$idx={type:'Feature',geometry:{type:'LineString',coordinates:rc$idx}};
        map.addSource('rg$idx',{type:'geojson',data:gj$idx});
        map.addLayer({id:'rg$idx',type:'line',source:'rg$idx',layout:{'line-join':'round','line-cap':'round'},paint:{'line-color':'$color','line-width':12,'line-opacity':$blurOpacity,'line-blur':8}});
        map.addSource('r$idx',{type:'geojson',data:gj$idx});
        map.addLayer({id:'r$idx',type:'line',source:'r$idx',layout:{'line-join':'round','line-cap':'round'},paint:{'line-color':'$color','line-width':$width,'line-opacity':$opacity}});
        """.trimIndent()
    }.joinToString("\n")

    val markersJs = if (routes.isNotEmpty() && routes[0].points.isNotEmpty()) {
        val rc = routes[0].points
        val startC = "[${rc.first().longitude},${rc.first().latitude}]"
        val endC = "[${rc.last().longitude},${rc.last().latitude}]"
        """
        map.addSource('s',{type:'geojson',data:{type:'Feature',geometry:{type:'Point',coordinates:$startC}}});
        map.addLayer({id:'sg',type:'circle',source:'s',paint:{'circle-radius':12,'circle-color':'#4CAF50','circle-opacity':0.3}});
        map.addLayer({id:'sp',type:'circle',source:'s',paint:{'circle-radius':7,'circle-color':'#4CAF50','circle-stroke-width':3,'circle-stroke-color':'#fff'}});
        map.addSource('e',{type:'geojson',data:{type:'Feature',geometry:{type:'Point',coordinates:$endC}}});
        map.addLayer({id:'eg',type:'circle',source:'e',paint:{'circle-radius':12,'circle-color':'#F44336','circle-opacity':0.3}});
        map.addLayer({id:'ep',type:'circle',source:'e',paint:{'circle-radius':7,'circle-color':'#F44336','circle-stroke-width':3,'circle-stroke-color':'#fff'}});
        
        var b=new mapboxgl.LngLatBounds($startC,$startC);
        rc0.forEach(function(c){b.extend(c);});
        map.fitBounds(b,{padding:50,duration:1200});
        """
    } else ""

    return """
<!DOCTYPE html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<script src="https://api.mapbox.com/mapbox-gl-js/v3.4.0/mapbox-gl.js"></script>
<link href="https://api.mapbox.com/mapbox-gl-js/v3.4.0/mapbox-gl.css" rel="stylesheet">
<style>*{margin:0;padding:0}body{background:#121212}#map{position:absolute;top:0;bottom:0;width:100%}.mapboxgl-ctrl-attrib{display:none!important}</style>
</head><body><div id="map"></div><script>
mapboxgl.accessToken='$MAPBOX_TOKEN';
var map=new mapboxgl.Map({container:'map',style:'mapbox://styles/mapbox/dark-v11',center:[$cLng,$cLat],zoom:11,attributionControl:false});
map.addControl(new mapboxgl.NavigationControl(),'top-right');
map.on('load',function(){
$addSourcesAndLayers
$markersJs
});
</script></body></html>""".trimIndent()
}

@Composable
private fun DetailsCard(route: Route) {
    var animationTriggered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animationTriggered = true }
    val animatedProgress by animateFloatAsState(
        targetValue = if (animationTriggered) (route.pleasureScore / 10.0).toFloat().coerceIn(0f, 1f) else 0f,
        animationSpec = tween(1500, easing = FastOutSlowInEasing),
        label = "pleasureProgress"
    )

    val context = LocalContext.current

    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .background(SurfaceDark).border(1.dp, GlassBorder, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .padding(top = 12.dp, bottom = 32.dp, start = 24.dp, end = 24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // Drag handle pill
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(GlassBorder)
            )
            
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
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
    val uri = Uri.parse("https://www.google.com/maps/dir/?api=1" +
        "&origin=${origin.latitude},${origin.longitude}" +
        "&destination=${destination.latitude},${destination.longitude}" +
        waypointsParam +
        "&travelmode=driving"
    )
    
    val intent = Intent(Intent.ACTION_VIEW, uri)
    intent.setPackage("com.google.android.apps.maps")
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
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
