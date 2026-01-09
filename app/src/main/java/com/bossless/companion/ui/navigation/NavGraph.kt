package com.bossless.companion.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bossless.companion.data.local.ThemeMode
import com.bossless.companion.ui.screens.jobdetail.JobDetailScreen
import com.bossless.companion.ui.screens.jobs.JobsScreen
import com.bossless.companion.ui.screens.login.LoginScreen
import com.bossless.companion.ui.screens.notifications.NotificationsScreen
import com.bossless.companion.ui.screens.pin.PinSetupScreen
import com.bossless.companion.ui.screens.pin.PinUnlockScreen
import com.bossless.companion.ui.screens.profile.ProfileScreen
import com.bossless.companion.ui.screens.timer.TimerScreen
import kotlinx.coroutines.delay

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    object Login : Screen("login", "Login")
    object PinSetup : Screen("pin_setup", "Set PIN")
    object PinUnlock : Screen("pin_unlock", "Unlock")
    object Jobs : Screen("jobs", "Jobs", Icons.Default.Home)
    object Notifications : Screen("notifications", "Notifications", Icons.Default.Notifications)
    object Timer : Screen("timer", "Timer", Icons.Default.DateRange)
    object Profile : Screen("profile", "Profile", Icons.Default.AccountCircle)
    object JobDetail : Screen("job_detail/{jobId}", "Job Detail") {
        fun createRoute(jobId: String) = "job_detail/$jobId"
    }
}

private enum class PendingGateNav {
    PinUnlock,
    PinSetup
}

@Composable
fun NavGraph(
    startDestination: String = Screen.Login.route,
    onThemeChanged: (ThemeMode) -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val (graphReady, setGraphReady) = remember { mutableStateOf(false) }
    val (pendingGateNav, setPendingGateNav) = remember { mutableStateOf<PendingGateNav?>(null) }

    val gateViewModel: SessionGateViewModel = hiltViewModel()
    val gateState by gateViewModel.state.collectAsState()

    val bottomNavItems = listOf(
        Screen.Jobs,
        Screen.Notifications,
        Screen.Timer,
        Screen.Profile
    )

    val showBottomNav = currentDestination?.route in bottomNavItems.map { it.route }

    val lifecycleOwner = LocalLifecycleOwner.current

    fun navigateToPinUnlockIfNeeded(reason: String) {
        val nowSeconds = System.currentTimeMillis() / 1000L
        val shouldGate = gateState.requiresPinUnlock(nowSeconds)

        val currentRoute = currentDestination?.route
        val alreadyOnGate = currentRoute == Screen.PinUnlock.route || currentRoute == Screen.PinSetup.route || currentRoute == Screen.Login.route

        if (shouldGate && !alreadyOnGate) {
            if (!graphReady) {
                setPendingGateNav(PendingGateNav.PinUnlock)
                return
            }
            navController.navigate(Screen.PinUnlock.route) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    fun navigateToPinSetupIfNeeded() {
        val currentRoute = currentDestination?.route
        val alreadyOnGate = currentRoute == Screen.PinSetup.route || currentRoute == Screen.PinUnlock.route || currentRoute == Screen.Login.route

        if (gateState.needsPinSetup() && !alreadyOnGate) {
            if (!graphReady) {
                setPendingGateNav(PendingGateNav.PinSetup)
                return
            }
            navController.navigate(Screen.PinSetup.route) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    // If a gate navigation was requested before the graph was ready, replay it once NavHost has set the graph.
    LaunchedEffect(graphReady, pendingGateNav, gateState.isLoggedIn, gateState.hasPin, gateState.pinUnlockRequired, gateState.tokenExpiresAtEpochSeconds) {
        if (!graphReady) return@LaunchedEffect

        when (pendingGateNav) {
            PendingGateNav.PinUnlock -> {
                setPendingGateNav(null)
                navigateToPinUnlockIfNeeded("pending")
            }
            PendingGateNav.PinSetup -> {
                setPendingGateNav(null)
                navigateToPinSetupIfNeeded()
            }
            null -> Unit
        }
    }

    // React immediately if the network layer requires a PIN unlock.
    LaunchedEffect(Unit) {
        gateViewModel.events.collect { event ->
            if (event is com.bossless.companion.data.auth.AuthGateEvent.RequirePinUnlock) {
                navigateToPinUnlockIfNeeded("auth_rejected")
            }
        }
    }

    // If we become logged-in without a PIN, force PIN setup.
    LaunchedEffect(gateState.isLoggedIn, gateState.hasPin) {
        navigateToPinSetupIfNeeded()
    }

    // If pin unlock is required (flag set), navigate.
    LaunchedEffect(gateState.isLoggedIn, gateState.hasPin, gateState.pinUnlockRequired) {
        if (gateState.isLoggedIn && gateState.hasPin && gateState.pinUnlockRequired) {
            navigateToPinUnlockIfNeeded("pin_required_flag")
        }
    }

    // Gate on resume if the token is expired.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                navigateToPinUnlockIfNeeded("resume")
                navigateToPinSetupIfNeeded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Gate when the token expiry time is reached while the app is open.
    LaunchedEffect(gateState.isLoggedIn, gateState.hasPin, gateState.tokenExpiresAtEpochSeconds) {
        val expiresAt = gateState.tokenExpiresAtEpochSeconds ?: return@LaunchedEffect
        if (!gateState.isLoggedIn || !gateState.hasPin) return@LaunchedEffect

        val now = System.currentTimeMillis() / 1000L
        val remainingSeconds = expiresAt - now
        if (remainingSeconds <= 0) {
            navigateToPinUnlockIfNeeded("expiry")
        } else {
            delay(remainingSeconds * 1000L)
            navigateToPinUnlockIfNeeded("expiry")
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon!!, contentDescription = screen.title) },
                            label = null,
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
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
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Login.route) {
                LoginScreen(
                    onLoginSuccess = {
                        val target = if (gateState.hasPin) Screen.Jobs.route else Screen.PinSetup.route
                        navController.navigate(target) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.PinSetup.route) {
                PinSetupScreen(
                    onPinSet = {
                        navController.navigate(Screen.Jobs.route) {
                            popUpTo(Screen.PinSetup.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.PinUnlock.route) {
                PinUnlockScreen(
                    onUnlocked = {
                        navController.navigate(Screen.Jobs.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onRequireLogin = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Jobs.route) {
                JobsScreen(
                    onJobClick = { jobId ->
                        navController.navigate(Screen.JobDetail.createRoute(jobId))
                    }
                )
            }
            composable(Screen.Notifications.route) {
                NotificationsScreen(
                    onNavigateToJob = { jobId ->
                        navController.navigate(Screen.JobDetail.createRoute(jobId))
                    }
                )
            }
            composable(Screen.Timer.route) {
                TimerScreen()
            }
            composable(Screen.Profile.route) {
                ProfileScreen(
                    onLogout = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onThemeChanged = onThemeChanged
                )
            }
            composable(
                route = Screen.JobDetail.route,
                arguments = listOf(navArgument("jobId") { type = NavType.StringType })
            ) {
                JobDetailScreen(
                    onBackClick = { navController.popBackStack() },
                    onTimerStarted = {
                        navController.navigate(Screen.Timer.route) {
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

        SideEffect {
            if (!graphReady) setGraphReady(true)
        }
    }
}
