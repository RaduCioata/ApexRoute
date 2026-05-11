package com.apexroute.domain.usecase

import com.apexroute.domain.model.GeoPoint
import com.apexroute.domain.model.Route
import com.apexroute.domain.repository.RouteRepository

/**
 * Use case: generate a circular (round-trip) route optimized for driving pleasure.
 */
class GenerateRoundTripUseCase(
    private val repository: RouteRepository
) {
    suspend operator fun invoke(
        start: GeoPoint,
        durationMinutes: Int
    ): Result<Route> {
        return try {
            require(durationMinutes in 15..240) {
                "Duration must be between 15 and 240 minutes"
            }
            require(start.latitude in -90.0..90.0 && start.longitude in -180.0..180.0) {
                "Invalid coordinates"
            }
            val route = repository.generateRoundTrip(start, durationMinutes)
            Result.success(route)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
