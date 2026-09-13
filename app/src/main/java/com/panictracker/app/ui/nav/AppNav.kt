package com.panictracker.app.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.panictracker.app.ui.home.HomeScreen
import com.panictracker.app.ui.med.MedicationScreen
import com.panictracker.app.ui.sleep.SleepScreen
import com.panictracker.app.ui.stats.StatsScreen
import com.panictracker.app.ui.symptom.SymptomScreen

/** 하단 메뉴. 증상은 한 번만 눌러 들어갈 수 있도록 가운데에 둡니다. */
enum class Dest(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "농도", Icons.Default.ShowChart),
    SYMPTOM("symptom", "증상", Icons.Default.Favorite),
    SLEEP("sleep", "수면", Icons.Default.Bedtime),
    MEDS("meds", "약물", Icons.Default.Medication),
    STATS("stats", "통계", Icons.Default.BarChart),
}

@Composable
fun AppNav(navController: NavHostController = rememberNavController()) {
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                Dest.entries.forEach { d ->
                    NavigationBarItem(
                        selected = current == d.route,
                        onClick = {
                            if (current != d.route) {
                                navController.navigate(d.route) {
                                    popUpTo(Dest.HOME.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(d.icon, contentDescription = d.label) },
                        label = { Text(d.label) },
                    )
                }
            }
        },
    ) { inner ->
        NavHost(
            navController = navController,
            startDestination = Dest.HOME.route,
            modifier = Modifier.padding(inner),
        ) {
            composable(Dest.HOME.route) {
                HomeScreen(onOpenMedications = { navController.navigate(Dest.MEDS.route) })
            }
            composable(Dest.SYMPTOM.route) { SymptomScreen() }
            composable(Dest.SLEEP.route) { SleepScreen() }
            composable(Dest.MEDS.route) { MedicationScreen() }
            composable(Dest.STATS.route) { StatsScreen() }
        }
    }
}
