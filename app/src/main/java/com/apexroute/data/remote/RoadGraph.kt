package com.apexroute.data.remote

import com.apexroute.domain.model.GeoPoint
import kotlin.math.abs

/**
 * In-memory directed graph representing a road network.
 * Nodes are OSM node IDs; edges carry distance and pleasure metadata.
 */
class RoadGraph {

    data class Edge(
        val from: Long,
        val to: Long,
        val distanceM: Double,
        val curvatureScore: Double,   // 0.0 (straight) – 1.0 (very curvy)
        val roadType: String
    )

    private val adjacency = mutableMapOf<Long, MutableList<Edge>>()
    private val nodePositions = mutableMapOf<Long, GeoPoint>()

    val nodeCount: Int get() = nodePositions.size

    fun addNode(id: Long, position: GeoPoint) {
        nodePositions[id] = position
    }

    fun addEdge(edge: Edge) {
        adjacency.getOrPut(edge.from) { mutableListOf() }.add(edge)
    }

    fun getPosition(nodeId: Long): GeoPoint? = nodePositions[nodeId]

    fun getNeighbors(nodeId: Long): List<Edge> = adjacency[nodeId] ?: emptyList()

    fun getAllNodeIds(): Set<Long> = nodePositions.keys

    /**
     * Finds the graph node nearest to the given [point].
     */
    fun nearestNode(point: GeoPoint): Long? {
        return nodePositions.minByOrNull { (_, pos) ->
            GeoPoint.distanceM(pos, point)
        }?.key
    }

    companion object {

        /**
         * Builds a [RoadGraph] from raw OSM data (nodes + ways).
         */
        fun build(
            nodes: Map<Long, GeoPoint>,
            ways: List<OsmWay>
        ): RoadGraph {
            val graph = RoadGraph()

            // Only add nodes that are referenced by at least one way
            val referencedNodeIds = mutableSetOf<Long>()
            ways.forEach { way -> referencedNodeIds.addAll(way.nodeIds) }

            for (nodeId in referencedNodeIds) {
                val pos = nodes[nodeId] ?: continue
                graph.addNode(nodeId, pos)
            }

            // Create edges for each consecutive node pair in every way
            for (way in ways) {
                val wayNodes = way.nodeIds.filter { nodes.containsKey(it) }
                if (wayNodes.size < 2) continue

                for (i in 0 until wayNodes.size - 1) {
                    val fromId = wayNodes[i]
                    val toId = wayNodes[i + 1]
                    val fromPos = nodes[fromId] ?: continue
                    val toPos = nodes[toId] ?: continue

                    val dist = GeoPoint.distanceM(fromPos, toPos)
                    val curvature = computeLocalCurvature(wayNodes, i, nodes)

                    val edge = Edge(
                        from = fromId,
                        to = toId,
                        distanceM = dist,
                        curvatureScore = curvature,
                        roadType = way.highway
                    )

                    graph.addEdge(edge)

                    // Add reverse edge if not one-way
                    if (!way.oneway) {
                        graph.addEdge(edge.copy(from = toId, to = fromId))
                    }
                }
            }

            return graph
        }

        /**
         * Estimates curvature at segment [index] by measuring the angle change
         * with neighbouring segments. Returns 0–1 (straight to very curvy).
         */
        private fun computeLocalCurvature(
            wayNodes: List<Long>,
            index: Int,
            nodes: Map<Long, GeoPoint>
        ): Double {
            // Need at least 3 consecutive nodes to measure an angle
            if (index == 0 && wayNodes.size < 3) return 0.0
            if (index >= wayNodes.size - 1) return 0.0

            val prev = if (index > 0) index - 1 else index
            val curr = index
            val next = if (index + 2 < wayNodes.size) index + 2 else index + 1

            val pA = nodes[wayNodes[prev]] ?: return 0.0
            val pB = nodes[wayNodes[curr]] ?: return 0.0
            val pC = nodes[wayNodes[next]] ?: return 0.0

            val bearingAB = GeoPoint.bearing(pA, pB)
            val bearingBC = GeoPoint.bearing(pB, pC)

            var angleDiff = abs(bearingBC - bearingAB)
            if (angleDiff > 180) angleDiff = 360 - angleDiff

            // Normalise: 0° = straight (score 0), 90°+ = very curvy (score ~1)
            return (angleDiff / 90.0).coerceIn(0.0, 1.0)
        }
    }
}
