package com.example

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.model.LocalAppSettings
import com.example.data.model.ServiceCategory
import com.example.data.model.ThemePreference
import com.example.data.model.UiState
import com.example.data.preferences.SettingsPreferencesRepository
import com.example.data.repository.BinsmmRepository
import com.example.data.repository.SupportRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.SplashScreen
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.signOutUser
import com.example.ui.screens.AddFundsScreen
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.NewOrderScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.OrdersScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SupportHubScreen
import com.example.ui.screens.WalletTransactionsScreen
import com.example.ui.theme.BINSMMTheme
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.BinsmmViewModel
import com.example.ui.viewmodel.SupportViewModel
import com.example.util.configureAppCheck
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.functions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object BinsmmRoutes {
    const val HOME = "home"
    const val NEW_ORDER = "new_order"
    const val ORDERS = "orders"
    const val PROFILE = "profile"
    const val ADD_FUNDS = "add_funds"
    const val WALLET = "wallet"
    const val NOTIFICATIONS = "notifications"
    const val SETTINGS = "settings"
    const val SUPPORT = "support"
    const val ADMIN = "admin"
}

private data class BottomNavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val BOTTOM_TABS = listOf(
    BottomNavTab(BinsmmRoutes.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    BottomNavTab(BinsmmRoutes.NEW_ORDER, "New Order", Icons.Filled.AddCircle, Icons.Outlined.AddCircleOutline),
    BottomNavTab(BinsmmRoutes.ORDERS, "Orders", Icons.Filled.ReceiptLong, Icons.Outlined.ReceiptLong),
    BottomNavTab(BinsmmRoutes.PROFILE, "Profile", Icons.Filled.Person, Icons.Outlined.Person)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        setContent {
            BinsmmAppRoot()
        }
    }
}

@Composable
fun BinsmmAppRoot() {
    val context = LocalContext.current
    val lifecycleScope = LocalLifecycleOwner.current.lifecycleScope
    val settingsRepo = remember(context) { SettingsPreferencesRepository(context.applicationContext) }
    val settings by settingsRepo.settingsFlow.collectAsStateWithLifecycle(initialValue = LocalAppSettings())
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val systemDark = isSystemInDarkTheme()
    val isDarkEffective = when (settings.themePreference) {
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
        ThemePreference.SYSTEM -> systemDark
    }

    var showSplash by remember { mutableStateOf(true) }
    var autoSignInAttempted by remember { mutableStateOf(false) }
    var pendingWhatsappHandle by remember { mutableStateOf<String?>(null) }

    val currentUser by Firebase.auth.authStateFlow().collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    LaunchedEffect(showSplash, currentUser) {
        if (!showSplash && currentUser == null && !autoSignInAttempted) {
            autoSignInAttempted = true
            delay(150)
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {},
                onUnauthenticated = {},
                scope = lifecycleScope
            )
        }
    }

    BINSMMTheme(themePreference = settings.themePreference) {
        if (showSplash) {
            SplashScreen(onFinished = { showSplash = false })
        } else {
            val user = currentUser
            if (user == null) {
                AuthScreen(
                    isDarkTheme = isDarkEffective,
                    onToggleTheme = {
                        lifecycleScope.launch {
                            val next = if (isDarkEffective) ThemePreference.LIGHT else ThemePreference.DARK
                            settingsRepo.setThemePreference(next)
                        }
                    },
                    onAuthStarting = { whatsapp ->
                        pendingWhatsappHandle = whatsapp
                    },
                    onAuthSuccess = { whatsapp ->
                        pendingWhatsappHandle = whatsapp
                    }
                )
            } else {
                val uid = user.uid
                key(uid) {
                    MainAuthenticatedShell(
                        currentUserId = uid,
                        initialWhatsappHandle = pendingWhatsappHandle,
                        settingsRepo = settingsRepo,
                        isDarkTheme = isDarkEffective,
                        onSignOut = {
                            pendingWhatsappHandle = null
                            signOutUser(credentialManager, lifecycleScope)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MainAuthenticatedShell(
    currentUserId: String,
    initialWhatsappHandle: String?,
    settingsRepo: SettingsPreferencesRepository,
    isDarkTheme: Boolean,
    onSignOut: () -> Unit
) {
    val viewModel: BinsmmViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                BinsmmViewModel(
                    repository = BinsmmRepository(
                        db = db,
                        auth = Firebase.auth,
                        functions = Firebase.functions(BinsmmRepository.FUNCTIONS_REGION)
                    ),
                    settingsRepo = settingsRepo,
                    currentUserId = currentUserId,
                    initialWhatsappHandle = initialWhatsappHandle
                )
            }
        }
    )

    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val profileState by viewModel.userProfile.collectAsStateWithLifecycle()
    val servicesState by viewModel.services.collectAsStateWithLifecycle()
    val ordersState by viewModel.orders.collectAsStateWithLifecycle()
    val transactionsState by viewModel.transactions.collectAsStateWithLifecycle()
    val notificationsState by viewModel.notifications.collectAsStateWithLifecycle()
    val appConfig by viewModel.appConfig.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()

    var selectedCategory by remember { mutableStateOf<ServiceCategory?>(null) }

    LaunchedEffect(actionMessage) {
        val msg = actionMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomNav = BOTTOM_TABS.any { tab ->
        currentDestination?.hierarchy?.any { it.route == tab.route } == true
    }

    val navigateToTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val navigateToSupport: (String) -> Unit = { orderId ->
        navController.navigate("${BinsmmRoutes.SUPPORT}?orderId=${Uri.encode(orderId)}")
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp && showBottomNav

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = if (showBottomNav) WindowInsets.safeDrawing else WindowInsets(0),
            bottomBar = {
                if (showBottomNav && !useRail) {
                    NavigationBar(windowInsets = WindowInsets.navigationBars) {
                        BOTTOM_TABS.forEach { tab ->
                            val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("bottom_nav_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail {
                        BOTTOM_TABS.forEach { tab ->
                            val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) }
                            )
                        }
                    }
                }

                Box(
                    contentAlignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize()
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = BinsmmRoutes.HOME
                    ) {
                        composable(BinsmmRoutes.HOME) {
                            HomeDashboardScreen(
                                profileState = profileState,
                                ordersState = ordersState,
                                notificationsState = notificationsState,
                                appConfig = appConfig,
                                settings = settings,
                                isDarkTheme = isDarkTheme,
                                onToggleTheme = {
                                    val next = if (isDarkTheme) ThemePreference.LIGHT else ThemePreference.DARK
                                    viewModel.setThemePreference(next)
                                },
                                onNavigateNewOrder = { category ->
                                    selectedCategory = category
                                    navigateToTab(BinsmmRoutes.NEW_ORDER)
                                },
                                onNavigateOrders = { navigateToTab(BinsmmRoutes.ORDERS) },
                                onNavigateAddFunds = { navController.navigate(BinsmmRoutes.ADD_FUNDS) },
                                onNavigateWallet = { navController.navigate(BinsmmRoutes.WALLET) },
                                onNavigateNotifications = { navController.navigate(BinsmmRoutes.NOTIFICATIONS) },
                                onNavigateSupport = { navigateToSupport("") }
                            )
                        }

                        composable(BinsmmRoutes.NEW_ORDER) {
                            val profile = (profileState as? UiState.Success)?.data
                            NewOrderScreen(
                                selectedCategory = selectedCategory,
                                onSelectCategory = { selectedCategory = it },
                                servicesState = servicesState,
                                userProfile = profile,
                                settings = settings,
                                isAdmin = isAdmin,
                                isSubmitting = isSubmitting,
                                onSeedCatalog = {
                                    viewModel.loadTemplateServices()
                                },
                                onNavigateAdmin = {
                                    navController.navigate(BinsmmRoutes.ADMIN)
                                },
                                onSubmitOrder = { service, link, qty ->
                                    viewModel.placeOrder(service, link, qty, profile) {
                                        navigateToTab(BinsmmRoutes.ORDERS)
                                    }
                                },
                                onNavigateAddFunds = { navController.navigate(BinsmmRoutes.ADD_FUNDS) }
                            )
                        }

                        composable(BinsmmRoutes.ORDERS) {
                            OrdersScreen(
                                ordersState = ordersState,
                                settings = settings,
                                onCancelOrder = { viewModel.cancelOrder(it) },
                                onNavigateNewOrder = { navigateToTab(BinsmmRoutes.NEW_ORDER) },
                                onNavigateOrderSupport = { orderId -> navigateToSupport(orderId) }
                            )
                        }

                        composable(BinsmmRoutes.PROFILE) {
                            ProfileScreen(
                                profileState = profileState,
                                notificationsState = notificationsState,
                                appConfig = appConfig,
                                settings = settings,
                                isAdmin = isAdmin,
                                onUpdateProfile = { name, wa -> viewModel.updateProfile(name, wa) },
                                onNavigateAddFunds = { navController.navigate(BinsmmRoutes.ADD_FUNDS) },
                                onNavigateWallet = { navController.navigate(BinsmmRoutes.WALLET) },
                                onNavigateNotifications = { navController.navigate(BinsmmRoutes.NOTIFICATIONS) },
                                onNavigateSupport = { navigateToSupport("") },
                                onNavigateSettings = { navController.navigate(BinsmmRoutes.SETTINGS) },
                                onNavigateAdmin = { navController.navigate(BinsmmRoutes.ADMIN) },
                                onSignOut = onSignOut
                            )
                        }

                        composable(BinsmmRoutes.ADD_FUNDS) {
                            val profile = (profileState as? UiState.Success)?.data
                            AddFundsScreen(
                                userProfile = profile,
                                appConfig = appConfig,
                                settings = settings,
                                isSubmitting = isSubmitting,
                                onSubmitDepositRequest = { amount, gateway, note ->
                                    viewModel.submitDepositRequest(amount, gateway, note) {
                                        navController.popBackStack()
                                    }
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(BinsmmRoutes.WALLET) {
                            val profile = (profileState as? UiState.Success)?.data
                            WalletTransactionsScreen(
                                userProfile = profile,
                                transactionsState = transactionsState,
                                settings = settings,
                                onNavigateAddFunds = { navController.navigate(BinsmmRoutes.ADD_FUNDS) },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(BinsmmRoutes.NOTIFICATIONS) {
                            NotificationsScreen(
                                notificationsState = notificationsState,
                                onMarkRead = { viewModel.markNotificationRead(it) },
                                onMarkAllRead = { viewModel.markAllNotificationsRead(it) },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(BinsmmRoutes.SETTINGS) {
                            SettingsScreen(
                                settings = settings,
                                appConfig = appConfig,
                                onSetTheme = { viewModel.setThemePreference(it) },
                                onSetNotifications = { viewModel.setNotificationsEnabled(it) },
                                onSetDefaultCategory = { viewModel.setDefaultCategory(it) },
                                onSetCurrency = { viewModel.setCurrencySymbol(it) },
                                onSetCompactCards = { viewModel.setCompactOrderCards(it) },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = "${BinsmmRoutes.SUPPORT}?orderId={orderId}",
                            arguments = listOf(
                                navArgument("orderId") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                }
                            )
                        ) { backStackEntry ->
                            val initialOrderId = backStackEntry.arguments?.getString("orderId").orEmpty()
                            val profile = (profileState as? UiState.Success)?.data
                            val supportViewModel: SupportViewModel = viewModel(
                                key = "support_$currentUserId",
                                factory = viewModelFactory {
                                    initializer {
                                        val app = checkNotNull(this[APPLICATION_KEY])
                                        val databaseId = app.getString(R.string.firestore_database_id)
                                        val db = FirebaseFirestore.getInstance(databaseId)
                                        SupportViewModel(
                                            supportRepository = SupportRepository(
                                                db = db,
                                                auth = Firebase.auth,
                                                functions = Firebase.functions(BinsmmRepository.FUNCTIONS_REGION)
                                            ),
                                            currentUserId = currentUserId,
                                            appConfigFlow = viewModel.appConfig
                                        )
                                    }
                                }
                            )
                            SupportHubScreen(
                                supportViewModel = supportViewModel,
                                userProfile = profile,
                                appConfig = appConfig,
                                settings = settings,
                                initialOrderId = initialOrderId,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(BinsmmRoutes.ADMIN) {
                            if (!isAdmin) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp)
                                ) {
                                    com.example.ui.components.EmptyStateCard(
                                        title = "Administrator Access Required",
                                        description = "This panel is restricted to verified BINSMM administrators authorized by backend security rules.",
                                        actionLabel = "Go Back",
                                        onAction = { navController.popBackStack() }
                                    )
                                }
                            } else {
                                val adminViewModel: AdminViewModel = viewModel(
                                    key = "admin_$currentUserId",
                                    factory = viewModelFactory {
                                        initializer {
                                            val app = checkNotNull(this[APPLICATION_KEY])
                                            val databaseId = app.getString(R.string.firestore_database_id)
                                            val db = FirebaseFirestore.getInstance(databaseId)
                                            val functions = Firebase.functions(BinsmmRepository.FUNCTIONS_REGION)
                                            AdminViewModel(
                                                repository = BinsmmRepository(
                                                    db = db,
                                                    auth = Firebase.auth,
                                                    functions = functions
                                                ),
                                                supportRepository = SupportRepository(
                                                    db = db,
                                                    auth = Firebase.auth,
                                                    functions = functions
                                                )
                                            )
                                        }
                                    }
                                )
                                val adminMsg by adminViewModel.adminMessage.collectAsStateWithLifecycle()
                                LaunchedEffect(adminMsg) {
                                    val m = adminMsg
                                    if (!m.isNullOrBlank()) {
                                        snackbarHostState.showSnackbar(m)
                                        adminViewModel.clearMessage()
                                    }
                                }
                                AdminDashboardScreen(
                                    adminViewModel = adminViewModel,
                                    settings = settings,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
