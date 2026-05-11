package com.apexroute.data.algorithm

import com.apexroute.data.remote.RoadGraph
import com.apexroute.domain.model.GeoPoint
import java.util.PriorityQueue
import kotlin.math.abs

/**
 * Generates circular (round-trip) routes optimised for driving pleasure.
 *
 * Strategy — True-Loop with Edge Exclusion + Multi-Radius Search:
 * 1. Try multiple radius scales to find routes matching the target distance
 * 2. Place N waypoints in a circle, snap to nearest graph nodes
 * 3. Chain A* with edge-exclusion to force true loops (no backtracking)
 * 4. Score routes by both pleasure AND distance accuracy, pick the best
 */
class RoundTripGenerator(
    private val graph: RoadGraph
) {

    private val alpha = 1.0
    private val gamma = 2.5
    private val reusePenalty = 20.0

    data class GeneratedRoute(
        val nodeIds: List<Long>,
        val points: List<GeoPoint>,
        val totalDistanceM: Double,
        val averagePleasure: Double,
        val curveCount: Int
    )

    fun generate(startNodeId: Long, targetDistanceM: Double): GeneratedRoute? {
        val startPos = graph.getPosition(startNodeId) ?: return null
        val baseRadius = targetDistanceM / (2 * Math.PI)

        var bestRoute: GeneratedRoute? = null
        var bestScore = Double.MIN_VALUE

        // Try multiple radius scales to match target distance
        val radiusScales = listOf(0.5, 0.7, 0.85, 1.0, 1.2, 1.5, 1.8)

        for (radiusScale in radiusScales) {
            val radius = baseRadius * radiusScale

            for (waypointCount in listOf(3, 4)) {
                val rotationAttempts = 8
                for (rotation in 0 until rotationAttempts) {
                    val offsetDeg = rotation * (360.0 / rotationAttempts)
                    val waypoints = generateWaypoints(startPos, radius, waypointCount, offsetDeg)
                    val snappedNodes = waypoints.mapNotNull { graph.nearestNode(it) }

                    if (snappedNodes.size < waypointCount) continue
                    if (snappedNodes.toSet().size < waypointCount) continue

                    val sequence = mutableListOf(startNodeId)
                    sequence.addAll(snappedNodes)
                    sequence.add(startNodeId)

                    val route = buildTrueLoop(sequence) ?: continue

                    // Score: 70% distance accuracy + 30% pleasure
                    val distanceError = abs(route.totalDistanceM - targetDistanceM) / targetDistanceM
                    val distanceFit = (1.0 - distanceError).coerceIn(0.0, 1.0)
                    val score = distanceFit * 0.7 + route.averagePleasure * 0.3

                    if (score > bestScore) {
                        bestScore = score
                        bestRoute = route
                    }
                }
            }
        }

        return bestRoute
    }

    /**
     * Scenic A→B: generates up to 3 alternative pleasure-weighted A* paths between two nodes.
     */
    fun generateScenic(fromNodeId: Long, toNodeId: Long): List<GeneratedRoute> {
        val routes = mutableListOf<GeneratedRoute>()
        val usedEdges = mutableSetOf<Pair<Long, Long>>()
        val alternativesToFind = 3

        for (i in 0 until alternativesToFind) {
            val path = aStarWithExclusion(fromNodeId, toNodeId, usedEdges) ?: break

            var totalDistance = 0.0
            var totalPleasure = 0.0
            var segmentCount = 0
            var curves = 0

            for (j in 0 until path.size - 1) {
                val a = path[j]
                val b = path[j + 1]
                usedEdges.add(Pair(a, b))
                usedEdges.add(Pair(b, a)) // Penalise this edge in future iterations

                val edge = graph.getNeighbors(a).find { it.to == b }
                if (edge != null) {
                    totalDistance += edge.distanceM
                    totalPleasure += getCombinedPleasure(edge)
                    segmentCount++
                    if (edge.curvatureScore > 0.3) curves++
                }
            }

            // Uniqueness check for alternatives:
            // Ensure the new route is not identical to a previous one (can happen if graph is sparse)
            val isDuplicate = routes.any { existing -> existing.nodeIds == path }
            if (!isDuplicate) {
                val points = path.mapNotNull { graph.getPosition(it) }
                val avgPleasure = if (segmentCount > 0) totalPleasure / segmentCount else 0.0

                routes.add(
                    GeneratedRoute(
                        nodeIds = path,
                        points = points,
                        totalDistanceM = totalDistance,
                        averagePleasure = avgPleasure,
                        curveCount = curves
                    )
                )
            }
        }
        return routes
    }

    private fun generateWaypoints(
        center: GeoPoint, radiusM: Double, count: Int, rotationOffsetDeg: Double
    ): List<GeoPoint> {
        return (0 until count).map { i ->
            val bearing = rotationOffsetDeg + i * (360.0 / count)
            GeoPoint.offset(center, bearing, radiusM)
        }
    }

    private fun buildTrueLoop(sequence: List<Long>): GeneratedRoute? {
        val allNodeIds = mutableListOf<Long>()
        var totalDistance = 0.0
        var totalPleasure = 0.0
        var segmentCount = 0
        var curves = 0
        val usedEdges = mutableSetOf<Pair<Long, Long>>()

        for (i in 0 until sequence.size - 1) {
            val from = sequence[i]
            val to = sequence[i + 1]
            if (from == to) continue

            val segment = aStarWithExclusion(from, to, usedEdges) ?: return null

            for (j in 0 until segment.size - 1) {
                usedEdges.add(Pair(segment[j], segment[j + 1]))
                usedEdges.add(Pair(segment[j + 1], segment[j]))
            }

            if (allNodeIds.isNotEmpty() && segment.isNotEmpty()) {
                allNodeIds.addAll(segment.drop(1))
            } else {
                allNodeIds.addAll(segment)
            }

            for (j in 0 until segment.size - 1) {
                val edge = graph.getNeighbors(segment[j]).find { it.to == segment[j + 1] }
                if (edge != null) {
                    totalDistance += edge.distanceM
                    totalPleasure += getCombinedPleasure(edge)
                    segmentCount++
                    if (edge.curvatureScore > 0.3) curves++
                }
            }
        }

        if (allNodeIds.isEmpty()) return null

        val totalEdges = allNodeIds.size - 1
        val uniqueNodePairs = (0 until allNodeIds.size - 1).map {
            setOf(allNodeIds[it], allNodeIds[it + 1])
        }.toSet().size
        val uniqueRatio = if (totalEdges > 0) uniqueNodePairs.toDouble() / totalEdges else 0.0
        if (uniqueRatio < 0.7) return null

        val points = allNodeIds.mapNotNull { graph.getPosition(it) }
        val avgPleasure = if (segmentCount > 0) totalPleasure / segmentCount else 0.0

        return GeneratedRoute(allNodeIds, points, totalDistance, avgPleasure, curves)
    }

    private fun getRoadTypePenalty(roadType: String): Double {
        return when (roadType) {
            "motorway" -> 5.0
            "trunk" -> 3.0
            "primary" -> 2.0
            "secondary" -> 1.0
            "tertiary" -> 0.8
            "unclassified" -> 0.7
            "residential" -> 1.5 // Too many stops/houses
            else -> 1.0
        }
    }

    private fun getRoadPleasure(roadType: String): Double {
        return when (roadType) {
            "motorway" -> 0.0
            "trunk" -> 0.1
            "primary" -> 0.3
            "secondary" -> 0.8
            "tertiary" -> 1.0
            "unclassified" -> 1.0
            "residential" -> 0.4
            else -> 0.5
        }
    }

    private fun getCombinedPleasure(edge: RoadGraph.Edge): Double {
        val roadPleasure = getRoadPleasure(edge.roadType)
        // 60% curvature, 40% road type
        return (edge.curvatureScore * 0.6) + (roadPleasure * 0.4)
    }

    private fun aStarWithExclusion(
        startId: Long, goalId: Long, usedEdges: Set<Pair<Long, Long>>
    ): List<Long>? {
        val goalPos = graph.getPosition(goalId) ?: return null
        data class Entry(val nodeId: Long, val fScore: Double)

        val openSet = PriorityQueue<Entry>(compareBy { it.fScore })
        val gScore = mutableMapOf<Long, Double>()
        val cameFrom = mutableMapOf<Long, Long>()
        val closedSet = mutableSetOf<Long>()

        gScore[startId] = 0.0
        val startPos = graph.getPosition(startId) ?: return null
        openSet.add(Entry(startId, GeoPoint.distanceM(startPos, goalPos)))

        var iterations = 0
        while (openSet.isNotEmpty() && iterations < 60_000) {
            iterations++
            val current = openSet.poll() ?: break
            if (current.nodeId == goalId) return reconstructPath(cameFrom, goalId)
            if (!closedSet.add(current.nodeId)) continue

            for (edge in graph.getNeighbors(current.nodeId)) {
                if (edge.to in closedSet) continue
                
                val penalty = getRoadTypePenalty(edge.roadType)
                val baseCost = (alpha * edge.distanceM * penalty) - (gamma * edge.curvatureScore * edge.distanceM)
                var cost = baseCost.coerceAtLeast(edge.distanceM * 0.1)
                
                if (Pair(current.nodeId, edge.to) in usedEdges) cost *= reusePenalty

                val tentG = (gScore[current.nodeId] ?: Double.MAX_VALUE) + cost
                if (tentG < (gScore[edge.to] ?: Double.MAX_VALUE)) {
                    cameFrom[edge.to] = current.nodeId
                    gScore[edge.to] = tentG
                    val h = graph.getPosition(edge.to)?.let { GeoPoint.distanceM(it, goalPos) * 0.5 } ?: Double.MAX_VALUE
                    openSet.add(Entry(edge.to, tentG + h))
                }
            }
        }
        return null
    }

    private fun reconstructPath(cameFrom: Map<Long, Long>, goal: Long): List<Long> {
        val path = mutableListOf(goal)
        var current = goal
        while (cameFrom.containsKey(current)) {
            current = cameFrom[current]!!
            path.add(0, current)
        }
        return path
    }
}
