package com.apexroute.presentation.roundtrip

import com.apexroute.domain.model.GeoPoint
import com.apexroute.domain.model.Route

/**
 * UI state for the Round Trip flow (setup + result).
 */
data class RoundTripUiState(
    val durationMinutes: Int = 60,
    val startPoint: GeoPoint? = null,
    val isLoading: Boolean = false,
    val loadingMessage: String = "Fetching road network…",
    val route: Route? = null,
    val error: String? = null
)
