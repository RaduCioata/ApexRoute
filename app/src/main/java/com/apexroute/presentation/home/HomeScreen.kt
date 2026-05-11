package com.apexroute.presentation.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.apexroute.core.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onRoundTripClick: () -> Unit = {},
    onScenicRouteClick: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "ApexRoute", 
                        style = Typography.titleLarge,
                        color = Primary
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = BackgroundDark
    ) { paddingValues ->
        // Premium gradient mesh background effect
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Primary.copy(alpha = 0.15f), Color.Transparent)
                        )
                    )
            )
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    text = "Welcome back, Driver",
                    style = Typography.titleMedium,
                    color = TextPrimary
                )
                
                Text(
                    text = "Where would you like to drive today? Choose a route type that maximizes your pleasure.",
                    style = Typography.bodyLarge,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(16.dp))

                RouteOptionCard(
                    title = "Round Trip Generator",
                    description = "Input a duration (e.g., 60 min) and we will generate a circular route with the best curves.",
                    icon = Icons.Default.Refresh,
                    gradient = PrimaryGradient,
                    onClick = onRoundTripClick
                )

                RouteOptionCard(
                    title = "Scenic Route A to B",
                    description = "Navigate between two points optimized for winding roads and elevation.",
                    icon = Icons.Default.LocationOn,
                    gradient = SecondaryGradient,
                    onClick = onScenicRouteClick
                )
            }
        }
    }
}

@Composable
fun RouteOptionCard(
    title: String,
    description: String,
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
            .scale(scale)
            .clip(RoundedCornerShape(24.dp))
            .background(SurfaceDark)
            .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(gradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(20.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = Typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = description,
                    style = Typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }
    }
}
