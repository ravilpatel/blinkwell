package com.mitalipurohit.blinkwell.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mitalipurohit.blinkwell.BlinkWellApp
import com.mitalipurohit.blinkwell.ui.about.AboutScreen
import com.mitalipurohit.blinkwell.ui.home.HomeScreen
import com.mitalipurohit.blinkwell.ui.home.HomeViewModel
import com.mitalipurohit.blinkwell.ui.onboarding.OnboardingScreen
import com.mitalipurohit.blinkwell.ui.onboarding.OnboardingViewModel
import com.mitalipurohit.blinkwell.ui.settings.SettingsScreen
import com.mitalipurohit.blinkwell.ui.settings.SettingsViewModel
import com.mitalipurohit.blinkwell.ui.stats.StatsScreen
import com.mitalipurohit.blinkwell.ui.stats.StatsViewModel
import com.mitalipurohit.blinkwell.ui.theme.TealPrimary

@Composable
fun BlinkWellNavGraph(
    navController: NavHostController = rememberNavController()
) {
    val settingsRepository = BlinkWellApp.settingsRepository
    val isOnboardingCompleted by settingsRepository.isOnboardingCompleted.collectAsState(initial = null)

    if (isOnboardingCompleted == null) {
        return // Loading preferences
    }

    val startDestination = if (isOnboardingCompleted == true) {
        Screen.Home.route
    } else {
        Screen.Onboarding.route
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute != Screen.Onboarding.route

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    Screen.bottomNavItems.forEach { screen ->
                        val selected = currentRoute == screen.route
                        NavigationBarItem(
                            icon = {
                                screen.icon?.let {
                                    Icon(
                                        imageVector = it,
                                        contentDescription = stringResource(screen.titleResId)
                                    )
                                }
                            },
                            label = { Text(stringResource(screen.titleResId)) },
                            selected = selected,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = TealPrimary,
                                selectedTextColor = TealPrimary,
                                indicatorColor = TealPrimary.copy(alpha = 0.15f)
                            ),
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Onboarding.route) {
                val onboardingViewModel = rememberOnboardingViewModel()
                OnboardingScreen(
                    viewModel = onboardingViewModel,
                    onFinished = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Home.route) {
                val homeViewModel: HomeViewModel = viewModel()
                HomeScreen(viewModel = homeViewModel)
            }

            composable(Screen.Stats.route) {
                val statsViewModel: StatsViewModel = viewModel()
                StatsScreen(viewModel = statsViewModel)
            }

            composable(Screen.Settings.route) {
                val settingsViewModel: SettingsViewModel = viewModel()
                SettingsScreen(viewModel = settingsViewModel)
            }

            composable(Screen.About.route) {
                AboutScreen()
            }
        }
    }
}

@Composable
fun rememberOnboardingViewModel(): OnboardingViewModel {
    return androidx.lifecycle.viewmodel.compose.viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return OnboardingViewModel(BlinkWellApp.settingsRepository) as T
            }
        }
    )
}
