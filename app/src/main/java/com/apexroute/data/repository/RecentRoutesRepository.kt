package com.apexroute.data.repository

import android.content.Context
import com.apexroute.domain.model.GeoPoint
import com.apexroute.domain.model.Route
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class RecentRoutesRepository(private val context: Context) {
    private val fileName = "recent_routes.json"
    private val maxRoutes = 10

    fun saveRoute(route: Route) {
        val routes = getRecentRoutes().toMutableList()
        routes.removeAll { it.id == route.id }
        routes.add(0, route)
        if (routes.size > maxRoutes) {
            routes.removeAt(routes.size - 1)
        }
        saveToFile(routes)
    }

    fun getRecentRoutes(): List<Route> {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return emptyList()

        val routes = mutableListOf<Route>()
        try {
            val jsonArray = JSONArray(file.readText())
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val pointsArray = obj.getJSONArray("points")
                val points = mutableListOf<GeoPoint>()
                for (j in 0 until pointsArray.length()) {
                    val pObj = pointsArray.getJSONObject(j)
                    points.add(GeoPoint(pObj.getDouble("lat"), pObj.getDouble("lon")))
                }
                
                routes.add(
                    Route(
                        id = obj.getString("id"),
                        points = points,
                        totalDistanceKm = obj.getDouble("totalDistanceKm"),
                        estimatedDurationMin = obj.getInt("estimatedDurationMin"),
                        pleasureScore = obj.getDouble("pleasureScore"),
                        totalElevationGainM = obj.getDouble("totalElevationGainM"),
                        curveCount = obj.getInt("curveCount")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return routes
    }
    
    fun getRouteById(id: String): Route? {
        return getRecentRoutes().find { it.id == id }
    }

    private fun saveToFile(routes: List<Route>) {
        val jsonArray = JSONArray()
        routes.forEach { route ->
            val obj = JSONObject()
            obj.put("id", route.id)
            obj.put("totalDistanceKm", route.totalDistanceKm)
            obj.put("estimatedDurationMin", route.estimatedDurationMin)
            obj.put("pleasureScore", route.pleasureScore)
            obj.put("totalElevationGainM", route.totalElevationGainM)
            obj.put("curveCount", route.curveCount)
            
            val pointsArray = JSONArray()
            route.points.forEach { p ->
                val pObj = JSONObject()
                pObj.put("lat", p.latitude)
                pObj.put("lon", p.longitude)
                pointsArray.put(pObj)
            }
            obj.put("points", pointsArray)
            jsonArray.put(obj)
        }
        
        val file = File(context.filesDir, fileName)
        file.writeText(jsonArray.toString())
    }
}
