package com.taslim.qiblafinder

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.taslim.qiblafinder.ui.theme.Midnight
import com.taslim.qiblafinder.ui.theme.QiblaTheme

private object Routes {
    const val QIBLA = "qibla"
    const val PRAYER = "prayer"
    const val ROUTE = "route"
    const val SETTINGS = "settings"
}

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Qibla(Routes.QIBLA, "Qibla", MihrabIcons.Compass),
    Prayer(Routes.PRAYER, "Prayer", MihrabIcons.Moon),
    Route(Routes.ROUTE, "Route", MihrabIcons.Globe)
}

@Composable
fun QiblaApp(
    settings: PrayerSettingsStore,
    locationState: LocationState,
    compassState: CompassState,
    actions: AppActions
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(
            targetState = settings.onboardingComplete,
            transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(250)) },
            label = "onboarding"
        ) { onboarded ->
            if (onboarded) {
                MainScaffold(settings, locationState, compassState, actions)
            } else {
                LaunchedEffect(Unit) { actions.onCompassVisibilityChanged(false) }
                OnboardingFlow(settings, locationState, actions)
            }
        }
    }
}

@Composable
private fun MainScaffold(
    settings: PrayerSettingsStore,
    locationState: LocationState,
    compassState: CompassState,
    actions: AppActions
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.QIBLA

    LaunchedEffect(currentRoute) {
        actions.onCompassVisibilityChanged(currentRoute == Routes.QIBLA)
    }

    val openSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } }

    Scaffold(
        containerColor = Midnight,
        // Each screen handles the status bar itself so backgrounds can run edge to edge
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            AnimatedVisibility(
                visible = currentRoute != Routes.SETTINGS,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                MihrabNavBar(currentRoute = currentRoute, onNavigate = { navController.navigateToTab(it) })
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.QIBLA,
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn(tween(240)) },
            exitTransition = { fadeOut(tween(160)) }
        ) {
            composable(Routes.QIBLA) {
                QiblaScreen(
                    settings = settings,
                    compass = compassState,
                    locationState = locationState,
                    actions = actions,
                    onOpenSettings = openSettings
                )
            }
            composable(Routes.PRAYER) {
                PrayerScreen(
                    settings = settings,
                    locationState = locationState,
                    actions = actions,
                    onOpenSettings = openSettings
                )
            }
            composable(Routes.ROUTE) {
                RouteScreen(locationState = locationState, actions = actions)
            }
            composable(
                Routes.SETTINGS,
                enterTransition = { slideInHorizontally(tween(280)) { it / 4 } + fadeIn(tween(280)) },
                popExitTransition = { slideOutHorizontally(tween(220)) { it / 4 } + fadeOut(tween(200)) }
            ) {
                SettingsScreen(
                    settings = settings,
                    locationState = locationState,
                    actions = actions,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun MihrabNavBar(currentRoute: String, onNavigate: (String) -> Unit) {
    val colors = QiblaTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(Midnight.copy(alpha = 0.94f))
    ) {
        Hairline(color = colors.gold.copy(alpha = 0.12f))
        Row(
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp)
                .padding(horizontal = 28.dp)
                .selectableGroup()
        ) {
            Tab.entries.forEach { tab ->
                val selected = currentRoute == tab.route
                val tint by animateColorAsState(
                    if (selected) colors.gold else colors.textMuted,
                    tween(200),
                    label = "tabTint"
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .widthIn(min = 72.dp)
                        .height(52.dp)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onNavigate(tab.route) })
                ) {
                    Icon(tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(3.dp))
                    Text(tab.label, style = MaterialTheme.typography.labelMedium, color = tint)
                }
            }
        }
    }
}
