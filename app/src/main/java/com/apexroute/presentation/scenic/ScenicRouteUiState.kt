package com.apexroute.presentation.scenic

import com.apexroute.domain.model.GeoPoint
import com.apexroute.domain.model.Route

data class ScenicRouteUiState(
    val pointA: GeoPoint? = null,
    val pointB: GeoPoint? = null,
    val pointALabel: String = "",
    val pointBLabel: String = "",
    val isLoading: Boolean = false,
    val loadingMessage: String = "Fetching road network…",
    val routes: List<Route> = emptyList(),
    val selectedRouteIndex: Int = 0,
    val error: String? = null
)
