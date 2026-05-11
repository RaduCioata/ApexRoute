package com.apexroute.presentation.routeresult

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
import com.apexroute.core.theme.*
import com.apexroute.domain.model.Route
import com.apexroute.presentation.roundtrip.RoundTripViewModel

private val MAPBOX_TOKEN = com.apexroute.BuildConfig.MAPBOX_ACCESS_TOKEN

/**
 * Screen displaying the generated route on a Mapbox map with route details.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteResultScreen(
    onBack: () -> Unit,
    onRegenerate: () -> Unit,
    viewModel: RoundTripViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val route = uiState.route

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Your Route",
                        style = Typography.titleLarge.copy(fontSize = 22.sp),
                        color = Primary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRegenerate) {
                        Icon(Icons.Default.Refresh, contentDescription = "Regenerate", tint = Primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        containerColor = BackgroundDark
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (route != null) {
                    EmbeddedMapView(route = route)
                }
            }

            if (route != null) {
                RouteDetailsCard(route = route)
            }
        }
    }
}

/**
 * Renders a Mapbox GL JS map with the route data embedded directly in the HTML.
 * This avoids all JavaScript timing issues.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EmbeddedMapView(route: Route) {
    val html = remember(route.id) { generateMapHtml(route) }

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
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                setBackgroundColor(0xFF121212.toInt())
                webViewClient = WebViewClient()
                loadDataWithBaseURL(
                    "https://api.mapbox.com",
                    html,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        }
    )
}

/**
 * Generates a self-contained HTML page with the Mapbox map and route data
 * embedded directly. The route is drawn automatically when the map loads.
 */
private fun generateMapHtml(route: Route): String {
    val coords = route.points.joinToString(",") { "[${it.longitude},${it.latitude}]" }
    val centerLng = route.points.firstOrNull()?.longitude ?: 23.6236
    val centerLat = route.points.firstOrNull()?.latitude ?: 46.7712

    return """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<script src="https://api.mapbox.com/mapbox-gl-js/v3.4.0/mapbox-gl.js"></script>
<link href="https://api.mapbox.com/mapbox-gl-js/v3.4.0/mapbox-gl.css" rel="stylesheet">
<style>
*{margin:0;padding:0;box-sizing:border-box}
body{background:#121212}
#map{position:absolute;top:0;bottom:0;width:100%}
.mapboxgl-ctrl-attrib{display:none!important}
</style>
</head>
<body>
<div id="map"></div>
<script>
mapboxgl.accessToken='$MAPBOX_TOKEN';
var routeCoords=[$coords];
var map=new mapboxgl.Map({
    container:'map',
    style:'mapbox://styles/mapbox/dark-v11',
    center:[$centerLng,$centerLat],
    zoom:12,
    attributionControl:false
});
map.addControl(new mapboxgl.NavigationControl(),'top-right');
map.on('load',function(){
    var geojson={type:'Feature',geometry:{type:'LineString',coordinates:routeCoords}};

    map.addSource('route-glow',{type:'geojson',data:geojson});
    map.addLayer({
        id:'route-glow',type:'line',source:'route-glow',
        layout:{'line-join':'round','line-cap':'round'},
        paint:{'line-color':'#F9A826','line-width':12,'line-opacity':0.25,'line-blur':8}
    });

    map.addSource('route',{type:'geojson',data:geojson});
    map.addLayer({
        id:'route',type:'line',source:'route',
        layout:{'line-join':'round','line-cap':'round'},
        paint:{'line-color':'#F9A826','line-width':4,'line-opacity':0.95}
    });

    if(routeCoords.length>0){
        var startPt={type:'Feature',geometry:{type:'Point',coordinates:routeCoords[0]}};
        map.addSource('start',{type:'geojson',data:startPt});
        map.addLayer({
            id:'start-glow',type:'circle',source:'start',
            paint:{'circle-radius':14,'circle-color':'#F9A826','circle-opacity':0.25,'circle-blur':1}
        });
        map.addLayer({
            id:'start-point',type:'circle',source:'start',
            paint:{'circle-radius':7,'circle-color':'#F9A826','circle-stroke-width':3,'circle-stroke-color':'#fff'}
        });
    }

    var bounds=routeCoords.reduce(function(b,c){return b.extend(c)},
        new mapboxgl.LngLatBounds(routeCoords[0],routeCoords[0]));
    map.fitBounds(bounds,{padding:50,duration:1200});
});
</script>
</body>
</html>
    """.trimIndent()
}

@Composable
private fun RouteDetailsCard(route: Route) {
    var animationTriggered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animationTriggered = true }
    val animatedProgress by animateFloatAsState(
        targetValue = if (animationTriggered) (route.pleasureScore / 10.0).toFloat().coerceIn(0f, 1f) else 0f,
        animationSpec = tween(1500, easing = FastOutSlowInEasing),
        label = "pleasureProgress"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .background(SurfaceDark)
            .border(
                width = 1.dp,
                color = GlassBorder,
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
            )
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Pleasure Score", style = Typography.labelSmall, color = TextSecondary)
                    Text(
                        text = String.format("%.1f", route.pleasureScore),
                        style = Typography.titleLarge.copy(fontSize = 42.sp, fontWeight = FontWeight.ExtraBold),
                        color = Primary
                    )
                }
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(SurfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedProgress)
                            .clip(RoundedCornerShape(5.dp))
                            .background(PrimaryGradient)
                    )
                }
            }

            HorizontalDivider(color = GlassBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricItem("Distance", String.format("%.1f km", route.totalDistanceKm))
                MetricItem("Duration", "${route.estimatedDurationMin} min")
                MetricItem("Curves", "${route.curveCount}")
            }
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = Typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold), color = TextPrimary)
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, style = Typography.bodyMedium, color = TextSecondary)
    }
}
