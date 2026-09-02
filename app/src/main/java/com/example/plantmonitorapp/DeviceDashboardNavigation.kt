package com.example.plantmonitorapp

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navigation

sealed interface DeviceDashboardNavigation {
    data object TemperatureSettings : DeviceDashboardNavigation
    data object HumiditySettings : DeviceDashboardNavigation
    data object SoilMoisture_1_Settings : DeviceDashboardNavigation
    data object SoilMoisture_2_Settings : DeviceDashboardNavigation
    data object VentilationSettings : DeviceDashboardNavigation
}

fun NavGraphBuilder.dashboardNavGraph(
    navController: NavHostController
) {
    navigation(
        route = "dashboard",
        startDestination = "dashboard/home"
    )
    {
        composable("DeviceDashboard")
        {
            DeviceDashboard(serviceViewModel.selectedDevice,
                            onNavigateClick = {destination ->
                                when(destination)
                                {
                                    DeviceDashboardNavigation.TemperatureSettings -> {
                                        // navController.navigate("TemperatureSettings")
                                    }
                                    DeviceDashboardNavigation.HumiditySettings -> {}
                                    DeviceDashboardNavigation.SoilMoisture_1_Settings -> {}
                                    DeviceDashboardNavigation.SoilMoisture_2_Settings -> {}
                                    DeviceDashboardNavigation.VentilationSettings -> {}
                                }

                            })
        }
    }
}