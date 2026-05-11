package com.apexroute.data.remote

import android.util.JsonReader
import com.apexroute.domain.model.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Client for the OpenStreetMap Overpass API.
 * Fetches driveable road segments within a bounding box.
 */
class OsmApi {

    companion object {
        /**
         * Multiple Overpass API mirrors — the client tries each in order
         * until one succeeds. This handles DNS issues on Android emulators
         * and distributes load across public servers.
         */
        private val OVERPASS_ENDPOINTS = listOf(
            "https://overpass.kumi.systems/api/interpreter",
            "https://z.overpass-api.de/api/interpreter",
            "https://lz4.overpass-api.de/api/interpreter",
            "https://overpass-api.de/api/interpreter"
        )
        private const val TIMEOUT_MS = 60_000 // Increased timeout for large routes
    }

    /**
     * Fetches all driveable road ways and their nodes within a bounding box
     * centred on [center] with the given [radiusM] in meters.
     *
     * @return Pair of (nodeId → GeoPoint map, list of Way objects)
     */
    suspend fun fetchRoadNetwork(
        center: GeoPoint,
        radiusM: Double
    ): Pair<Map<Long, GeoPoint>, List<OsmWay>> = withContext(Dispatchers.IO) {
        val bbox = boundingBox(center, radiusM)
        val query = buildQuery(bbox)
        executeQuery(query)
    }

    /**
     * Fetches road network exactly within the specified bounding box.
     */
    suspend fun fetchRoadNetwork(
        bbox: BBox
    ): Pair<Map<Long, GeoPoint>, List<OsmWay>> = withContext(Dispatchers.IO) {
        val query = buildQuery(bbox)
        executeQuery(query)
    }

    private fun boundingBox(center: GeoPoint, radiusM: Double): BBox {
        val south = GeoPoint.offset(center, 180.0, radiusM)
        val north = GeoPoint.offset(center, 0.0, radiusM)
        val west = GeoPoint.offset(center, 270.0, radiusM)
        val east = GeoPoint.offset(center, 90.0, radiusM)
        return BBox(south.latitude, west.longitude, north.latitude, east.longitude)
    }

    private fun buildQuery(bbox: BBox): String {
        return """
            [out:json][timeout:60];
            (
              way["highway"~"^(motorway|trunk|primary|secondary|tertiary|unclassified|residential)$"]
                (${bbox.south},${bbox.west},${bbox.north},${bbox.east});
            );
            out body;
            >;
            out skel qt;
        """.trimIndent()
    }

    /**
     * Tries each Overpass endpoint in order. Returns the first successful response.
     * Throws with a combined error message if all endpoints fail.
     */
    private fun executeQuery(query: String): Pair<Map<Long, GeoPoint>, List<OsmWay>> {
        val errors = mutableListOf<String>()

        for (endpoint in OVERPASS_ENDPOINTS) {
            try {
                return executeSingleRequest(endpoint, query)
            } catch (e: Exception) {
                errors.add("$endpoint → ${e.message}")
            }
        }

        throw RuntimeException(
            "All Overpass API endpoints failed:\n${errors.joinToString("\n")}\n\n" +
            "Please check your internet connection."
        )
    }

    private fun executeSingleRequest(endpoint: String, query: String): Pair<Map<Long, GeoPoint>, List<OsmWay>> {
        val url = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

            val body = "data=${URLEncoder.encode(query, "UTF-8")}"
            conn.outputStream.bufferedWriter().use { it.write(body) }

            if (conn.responseCode != 200) {
                throw RuntimeException("HTTP ${conn.responseCode}")
            }

            parseResponse(conn.inputStream)
        } finally {
            conn.disconnect()
        }
    }

    private fun parseResponse(inputStream: InputStream): Pair<Map<Long, GeoPoint>, List<OsmWay>> {
        val nodes = mutableMapOf<Long, GeoPoint>()
        val ways = mutableListOf<OsmWay>()
        
        JsonReader(inputStream.bufferedReader()).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                val name = reader.nextName()
                if (name == "elements") {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        parseElement(reader, nodes, ways)
                    }
                    reader.endArray()
                } else {
                    reader.skipValue()
                }
            }
            reader.endObject()
        }
        return Pair(nodes, ways)
    }

    private fun parseElement(reader: JsonReader, nodes: MutableMap<Long, GeoPoint>, ways: MutableList<OsmWay>) {
        var type = ""
        var id: Long = 0
        var lat = 0.0
        var lon = 0.0
        val nodeIds = mutableListOf<Long>()
        val tags = mutableMapOf<String, String>()

        reader.beginObject()
        while (reader.hasNext()) {
            val key = reader.nextName()
            when (key) {
                "type" -> type = reader.nextString()
                "id" -> id = reader.nextLong()
                "lat" -> lat = reader.nextDouble()
                "lon" -> lon = reader.nextDouble()
                "nodes" -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        nodeIds.add(reader.nextLong())
                    }
                    reader.endArray()
                }
                "tags" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        tags[reader.nextName()] = reader.nextString()
                    }
                    reader.endObject()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (type == "node") {
            nodes[id] = GeoPoint(lat, lon)
        } else if (type == "way") {
            ways.add(OsmWay(id, nodeIds, tags))
        }
    }

    data class BBox(val south: Double, val west: Double, val north: Double, val east: Double)
}

/**
 * Represents an OpenStreetMap way (road segment).
 */
data class OsmWay(
    val id: Long,
    val nodeIds: List<Long>,
    val tags: Map<String, String>
) {
    val highway: String get() = tags["highway"] ?: ""
    val name: String get() = tags["name"] ?: ""
    val oneway: Boolean get() = tags["oneway"] == "yes"
}
