package com.apexroute.data.repository

import com.apexroute.data.algorithm.RoundTripGenerator
import com.apexroute.data.remote.OsmApi
import com.apexroute.data.remote.RoadGraph
import com.apexroute.domain.model.GeoPoint
import com.apexroute.domain.model.Route
import com.apexroute.domain.repository.RouteRepository
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/**
 * Concrete implementation of [RouteRepository].
 * Orchestrates: OSM data fetch → graph build → algorithm run → Route creation.
 */
class RouteRepositoryImpl : RouteRepository {

    private val osmApi = OsmApi()
    private val avgSpeedKmh = 50.0

    override suspend fun generateRoundTrip(
        start: GeoPoint,
        durationMinutes: Int
    ): Route {
        val targetDistanceKm = (durationMinutes / 60.0) * avgSpeedKmh
        val targetDistanceM = targetDistanceKm * 1000.0

        val searchRadiusM = (targetDistanceM / (2 * Math.PI) * 2.0)
            .coerceIn(2_000.0, 40_000.0)

        val (nodes, ways) = osmApi.fetchRoadNetwork(start, searchRadiusM)
        if (nodes.isEmpty() || ways.isEmpty()) {
            throw RuntimeException("No road data found near the specified location.")
        }

        val graph = RoadGraph.build(nodes, ways)
        if (graph.nodeCount < 10) {
            throw RuntimeException("Insufficient road network data.")
        }

        val startNodeId = graph.nearestNode(start)
            ?: throw RuntimeException("Could not locate a road near the starting point.")

        val generator = RoundTripGenerator(graph)
        val result = generator.generate(startNodeId, targetDistanceM)
            ?: throw RuntimeException("Could not generate a round trip route. Try a different starting point or duration.")

        val actualDurationMin = ((result.totalDistanceM / 1000.0) / avgSpeedKmh * 60).toInt()
        val pleasureScore = (result.averagePleasure * 10).coerceIn(0.0, 10.0)

        return Route(
            id = UUID.randomUUID().toString(),
            points = result.points,
            totalDistanceKm = result.totalDistanceM / 1000.0,
            estimatedDurationMin = actualDurationMin,
            pleasureScore = pleasureScore,
            totalElevationGainM = 0.0,
            curveCount = result.curveCount
        )
    }

    override suspend fun generateScenicRoute(
        pointA: GeoPoint,
        pointB: GeoPoint
    ): List<Route> {
        // Calculate bounding box that covers both points with padding
        val distAB = GeoPoint.distanceM(pointA, pointB)
        val padding = distAB * 0.3 // 30% padding around the line

        val centerLat = (pointA.latitude + pointB.latitude) / 2
        val paddingLat = (padding / 111320.0) // approx degrees per meter
        val paddingLon = (padding / (111320.0 * kotlin.math.cos(Math.toRadians(centerLat))))

        val south = min(pointA.latitude, pointB.latitude) - paddingLat
        val north = max(pointA.latitude, pointB.latitude) + paddingLat
        val west = min(pointA.longitude, pointB.longitude) - paddingLon
        val east = max(pointA.longitude, pointB.longitude) + paddingLon

        val bbox = OsmApi.BBox(south, west, north, east)
        val (nodes, ways) = osmApi.fetchRoadNetwork(bbox)
        if (nodes.isEmpty() || ways.isEmpty()) {
            throw RuntimeException("No road data found in the route area.")
        }

        val graph = RoadGraph.build(nodes, ways)
        if (graph.nodeCount < 10) {
            throw RuntimeException("Insufficient road network data.")
        }

        val nodeA = graph.nearestNode(pointA)
            ?: throw RuntimeException("Could not locate a road near point A.")
        val nodeB = graph.nearestNode(pointB)
            ?: throw RuntimeException("Could not locate a road near point B.")

        val generator = RoundTripGenerator(graph)
        val results = generator.generateScenic(nodeA, nodeB)
        if (results.isEmpty()) {
            throw RuntimeException("Could not find a scenic route between the two points. They may be too far apart or not connected.")
        }

        return results.map { result ->
            val actualDurationMin = ((result.totalDistanceM / 1000.0) / avgSpeedKmh * 60).toInt()
            val pleasureScore = (result.averagePleasure * 10).coerceIn(0.0, 10.0)

            Route(
                id = UUID.randomUUID().toString(),
                points = result.points,
                totalDistanceKm = result.totalDistanceM / 1000.0,
                estimatedDurationMin = actualDurationMin,
                pleasureScore = pleasureScore,
                totalElevationGainM = 0.0,
                curveCount = result.curveCount
            )
        }
    }
}
