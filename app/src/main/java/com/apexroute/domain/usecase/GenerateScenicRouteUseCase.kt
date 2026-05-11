package com.apexroute.domain.usecase

import com.apexroute.domain.model.GeoPoint
import com.apexroute.domain.model.Route
import com.apexroute.domain.repository.RouteRepository

/**
 * Use case: generate a scenic route from point A to point B,
 * optimised for driving pleasure (curves + elevation) instead of shortest path.
 */
class GenerateScenicRouteUseCase(
    private val repository: RouteRepository
) {
    suspend operator fun invoke(
        pointA: GeoPoint?,
        pointB: GeoPoint?
    ): Result<List<Route>> {
        if (pointA == null || pointB == null) {
            return Result.failure(IllegalArgumentException("Ambele puncte trebuie selectate."))
        }
        
        return try {
            val routes = repository.generateScenicRoute(pointA, pointB)
            Result.success(routes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
