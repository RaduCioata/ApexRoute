package com.apexroute.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.apexroute.presentation.home.HomeScreen
import com.apexroute.presentation.home.HomeViewModel
import com.apexroute.presentation.recent.RecentRouteResultScreen
import com.apexroute.presentation.roundtrip.RoundTripSetupScreen
import com.apexroute.presentation.roundtrip.RoundTripViewModel
import com.apexroute.presentation.routeresult.RouteResultScreen
import com.apexroute.presentation.scenic.ScenicRouteResultScreen
import com.apexroute.presentation.scenic.ScenicRouteSetupScreen
import com.apexroute.presentation.scenic.ScenicRouteViewModel

/**
 * Top-level navigation graph for ApexRoute.
 * Uses nested nav graphs to scope ViewModels per feature flow.
 */
@Composable
fun ApexRouteNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {

        composable("home") { entry ->
            val viewModel: HomeViewModel = viewModel(entry)
            HomeScreen(
                viewModel = viewModel,
                onRoundTripClick = { navController.navigate("roundtrip_flow") },
                onScenicRouteClick = { navController.navigate("scenic_flow") },
                onRecentRouteClick = { routeId -> navController.navigate("recent_route_result/$routeId") }
            )
        }

        composable(
            route = "recent_route_result/{routeId}",
            arguments = listOf(navArgument("routeId") { type = NavType.StringType })
        ) { entry ->
            val routeId = entry.arguments?.getString("routeId") ?: return@composable
            RecentRouteResultScreen(
                routeId = routeId,
                onBack = { navController.popBackStack() }
            )
        }

        // ── Round Trip Flow ──
        navigation(startDestination = "roundtrip_setup", route = "roundtrip_flow") {
            composable("roundtrip_setup") { entry ->
                val parentEntry = remember(entry) { navController.getBackStackEntry("roundtrip_flow") }
                val viewModel: RoundTripViewModel = viewModel(parentEntry)
                RoundTripSetupScreen(
                    viewModel = viewModel,
                    onRouteGenerated = { navController.navigate("route_result") },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("route_result") { entry ->
                val parentEntry = remember(entry) { navController.getBackStackEntry("roundtrip_flow") }
                val viewModel: RoundTripViewModel = viewModel(parentEntry)
                RouteResultScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack("home", inclusive = false) },
                    onRegenerate = { navController.popBackStack("roundtrip_setup", inclusive = false) }
                )
            }
        }

        // ── Scenic Route Flow ──
        navigation(startDestination = "scenic_setup", route = "scenic_flow") {
            composable("scenic_setup") { entry ->
                val parentEntry = remember(entry) { navController.getBackStackEntry("scenic_flow") }
                val viewModel: ScenicRouteViewModel = viewModel(parentEntry)
                ScenicRouteSetupScreen(
                    viewModel = viewModel,
                    onRouteGenerated = { navController.navigate("scenic_result") },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("scenic_result") { entry ->
                val parentEntry = remember(entry) { navController.getBackStackEntry("scenic_flow") }
                val viewModel: ScenicRouteViewModel = viewModel(parentEntry)
                ScenicRouteResultScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack("home", inclusive = false) },
                    onRegenerate = {
                        viewModel.clearRoute()
                        navController.popBackStack("scenic_setup", inclusive = false)
                    }
                )
            }
        }
    }
}
