package com.apexroute.domain.model

/**
 * Represents a generated driving route with pleasure metrics.
 */
data class Route(
    val id: String,
    val points: List<GeoPoint>,
    val totalDistanceKm: Double,
    val estimatedDurationMin: Int,
    val pleasureScore: Double,
    val totalElevationGainM: Double,
    val curveCount: Int
)
