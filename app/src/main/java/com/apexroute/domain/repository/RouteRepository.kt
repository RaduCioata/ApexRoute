package com.apexroute.domain.repository

import com.apexroute.domain.model.GeoPoint
import com.apexroute.domain.model.Route

/**
 * Port (interface) for route generation — implemented in the Data layer.
 */
interface RouteRepository {
    suspend fun generateRoundTrip(
        start: GeoPoint,
        durationMinutes: Int
    ): Route

    suspend fun generateScenicRoute(
        pointA: GeoPoint,
        pointB: GeoPoint
    ): List<Route>
}
