package com.mitalipurohit.blinkwell.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.mitalipurohit.blinkwell.R

sealed class Screen(val route: String, val titleResId: Int, val icon: ImageVector? = null) {
    object Onboarding : Screen("onboarding", R.string.app_name)
    object Home : Screen("home", R.string.nav_home, Icons.Default.Home)
    object Stats : Screen("stats", R.string.nav_stats, Icons.Default.BarChart)
    object Streak : Screen("streak", R.string.nav_streak)
    object Settings : Screen("settings", R.string.nav_settings, Icons.Default.Settings)

    companion object {
        val bottomNavItems = listOf(Home, Stats, Settings)
    }
}
