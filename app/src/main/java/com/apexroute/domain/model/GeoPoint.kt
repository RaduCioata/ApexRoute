package com.apexroute.domain.model

import kotlin.math.*

/**
 * Represents a geographic coordinate (WGS84).
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double
) {
    companion object {
        private const val EARTH_RADIUS_M = 6_371_000.0

        /**
         * Haversine distance between two points in meters.
         */
        fun distanceM(a: GeoPoint, b: GeoPoint): Double {
            val dLat = Math.toRadians(b.latitude - a.latitude)
            val dLon = Math.toRadians(b.longitude - a.longitude)
            val lat1 = Math.toRadians(a.latitude)
            val lat2 = Math.toRadians(b.latitude)
            val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
            return 2 * EARTH_RADIUS_M * asin(sqrt(h))
        }

        /**
         * Returns a new GeoPoint offset from [origin] by [distanceM] meters
         * along [bearingDeg] degrees (0 = North, 90 = East).
         */
        fun offset(origin: GeoPoint, bearingDeg: Double, distanceM: Double): GeoPoint {
            val lat1 = Math.toRadians(origin.latitude)
            val lon1 = Math.toRadians(origin.longitude)
            val bearing = Math.toRadians(bearingDeg)
            val angularDist = distanceM / EARTH_RADIUS_M

            val lat2 = asin(
                sin(lat1) * cos(angularDist) +
                        cos(lat1) * sin(angularDist) * cos(bearing)
            )
            val lon2 = lon1 + atan2(
                sin(bearing) * sin(angularDist) * cos(lat1),
                cos(angularDist) - sin(lat1) * sin(lat2)
            )
            return GeoPoint(Math.toDegrees(lat2), Math.toDegrees(lon2))
        }

        /**
         * Bearing from point [a] to point [b] in degrees (0-360).
         */
        fun bearing(a: GeoPoint, b: GeoPoint): Double {
            val lat1 = Math.toRadians(a.latitude)
            val lat2 = Math.toRadians(b.latitude)
            val dLon = Math.toRadians(b.longitude - a.longitude)
            val y = sin(dLon) * cos(lat2)
            val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
            return (Math.toDegrees(atan2(y, x)) + 360) % 360
        }
    }
}
