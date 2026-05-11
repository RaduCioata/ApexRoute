package com.apexroute.presentation.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexroute.core.theme.*
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onRoundTripClick: () -> Unit = {},
    onScenicRouteClick: () -> Unit = {},
    onRecentRouteClick: (String) -> Unit = {}
) {
    val recentRoutes by viewModel.recentRoutes.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadRecentRoutes()
    }
    // Entrance animation state
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true
    }

    val alphaAnim by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "alpha"
    )

    val slideAnim by animateFloatAsState(
        targetValue = if (isVisible) 0f else 50f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "slide"
    )

    Scaffold(
        containerColor = BackgroundDark,
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Background ambient glow
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Primary.copy(alpha = 0.15f), Color.Transparent)
                        )
                    )
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .graphicsLayer {
                        alpha = alphaAnim
                        translationY = slideAnim
                    },
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // Hero Section
                item {
                    Column {
                        Text(
                            text = "ApexRoute",
                            style = Typography.headlineLarge.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 40.sp
                            ),
                            color = Primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Welcome back, Driver.",
                            style = Typography.titleLarge,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Where will the road take you today?",
                            style = Typography.bodyLarge,
                            color = TextSecondary
                        )
                    }
                }

                // Route Actions
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = "GENERATE A ROUTE",
                            style = Typography.labelSmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(start = 8.dp)
                        )

                        FeatureCard(
                            title = "Round Trip",
                            subtitle = "Circular loop based on duration",
                            icon = Icons.Default.Refresh,
                            gradient = PrimaryGradient,
                            onClick = onRoundTripClick
                        )

                        FeatureCard(
                            title = "Scenic Route",
                            subtitle = "Point A to B via the best roads",
                            icon = Icons.Default.LocationOn,
                            gradient = SecondaryGradient,
                            onClick = onScenicRouteClick
                        )
                    }
                }

                // Recent Routes
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = "RECENT ROUTES",
                            style = Typography.labelSmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(start = 8.dp)
                        )

                        if (recentRoutes.isEmpty()) {
                            Text(
                                text = "No recent routes yet. Generate one above!",
                                style = Typography.bodyMedium,
                                color = TextSecondary,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        } else {
                            recentRoutes.forEach { route ->
                                RecentRouteCard(
                                    title = if (route.curveCount > 50) "Curvy Adventure" else "Scenic Drive",
                                    distance = String.format("%.1f km", route.totalDistanceKm),
                                    duration = "${route.estimatedDurationMin}m",
                                    score = (route.pleasureScore.toFloat()),
                                    onClick = { onRecentRouteClick(route.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FeatureCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    gradient: Brush,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.96f else 1f, label = "scale")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .scale(scale)
            .clip(RoundedCornerShape(28.dp))
            .background(SurfaceDark)
            .border(1.dp, GlassBorder, RoundedCornerShape(28.dp))
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
    ) {
        // Decorative giant icon in the background
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.03f),
            modifier = Modifier
                .size(200.dp)
                .align(Alignment.CenterEnd)
                .offset(x = 60.dp, y = 20.dp)
        )

        // Gradient accent strip
        Box(
            modifier = Modifier
                .width(8.dp)
                .fillMaxHeight()
                .align(Alignment.CenterStart)
                .background(gradient)
        )

        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(gradient.apply { /* Just to use it as a solid background */ }),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(20.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = Typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = Typography.bodyMedium,
                    color = TextSecondary
                )
            }

            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun RecentRouteCard(
    title: String,
    distance: String,
    duration: String,
    score: Float,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceDark.copy(alpha = 0.5f))
            .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = Typography.titleMedium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$distance • $duration",
                style = Typography.bodySmall,
                color = TextSecondary
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = score.toString(),
                style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Primary
            )
            Text(
                text = "Score",
                style = Typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}
