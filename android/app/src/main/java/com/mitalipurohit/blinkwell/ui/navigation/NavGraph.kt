package com.mitalipurohit.blinkwell.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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
import com.mitalipurohit.blinkwell.ui.streak.StreakScreen
import com.mitalipurohit.blinkwell.ui.theme.TealPrimary
import kotlinx.coroutines.launch

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
        "main"
    } else {
        Screen.Onboarding.route
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Onboarding.route) {
            val onboardingViewModel = rememberOnboardingViewModel()
            OnboardingScreen(
                viewModel = onboardingViewModel,
                onFinished = {
                    navController.navigate("main") {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable("main") {
            MainTabPagerScreen()
        }
    }
}

@Composable
fun MainTabPagerScreen(
    homeViewModel: HomeViewModel = viewModel(),
    statsViewModel: StatsViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val homeIndex = remember { Screen.bottomNavItems.indexOf(Screen.Home).coerceAtLeast(0) }
    val pagerState = rememberPagerState(
        initialPage = homeIndex,
        pageCount = { Screen.bottomNavItems.size }
    )
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        bottomBar = {
            NavigationBar {
                Screen.bottomNavItems.forEachIndexed { index, screen ->
                    val selected = pagerState.currentPage == index
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
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            beyondViewportPageCount = 1
        ) { page ->
            when (Screen.bottomNavItems[page]) {
                Screen.Stats -> StatsScreen(viewModel = statsViewModel)
                Screen.Streak -> StreakScreen(viewModel = statsViewModel)
                Screen.Home -> HomeScreen(viewModel = homeViewModel)
                Screen.Settings -> SettingsScreen(viewModel = settingsViewModel)
                Screen.About -> AboutScreen()
                else -> HomeScreen(viewModel = homeViewModel)
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
