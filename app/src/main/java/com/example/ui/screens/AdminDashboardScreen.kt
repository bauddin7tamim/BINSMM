package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DesignServices
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AdminAuditLog
import com.example.data.model.AppConfig
import com.example.data.model.LocalAppSettings
import com.example.data.model.SmmProviderConfig
import com.example.data.model.UiState
import com.example.ui.components.IntegrationNoticeBanner
import com.example.ui.components.StatMetricCard
import com.example.ui.components.formatCurrency
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanInfo
import com.example.ui.theme.DeepRoyalBlue
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.PureWhite
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.VividPurple
import com.example.ui.viewmodel.AdminViewModel
import java.util.Locale

enum class AdminSection(val title: String, val icon: ImageVector) {
    OVERVIEW("Dashboard", Icons.Default.Dashboard),
    USERS("Users", Icons.Default.Group),
    SERVICES("Services", Icons.Default.DesignServices),
    ORDERS("Orders", Icons.AutoMirrored.Filled.ReceiptLong),
    PAYMENTS("Payments", Icons.Default.Payments),
    PROVIDERS("Providers", Icons.Default.Api),
    PRICING("Pricing", Icons.Default.AttachMoney),
    KNOWLEDGE_BASE("Knowledge Base", Icons.Default.MenuBook),
    SUPPORT_TICKETS("Tickets", Icons.Default.ConfirmationNumber),
    ADMIN_SECURITY("Security", Icons.Default.Security),
    AUDIT_LOGS("Audit Log", Icons.Default.History)
}

private data class AdminDashboardMetrics(
    val totalUsers: Int,
    val totalOrders: Int,
    val totalRevenue: Double,
    val completedOrders: Int,
    val totalDeposits: Double,
    val pendingPayments: Int,
    val activeServices: Int,
    val totalServices: Int,
    val providerBalanceDisplay: String,
    val providerSubtitle: String
)

private fun computeProviderBalanceDisplay(
    providers: List<SmmProviderConfig>,
    currencySymbol: String
): Pair<String, String> {
    if (providers.isEmpty()) {
        return "Not connected" to "0 providers"
    }
    val connected = providers.filter { it.lastSyncStatus.startsWith("Connected", ignoreCase = true) }
    if (connected.isEmpty()) {
        return "Not connected" to "${providers.size} configured"
    }
    val parsedValues = connected.mapNotNull { prv ->
        Regex("[-+]?\\d+(?:\\.\\d+)?").find(prv.lastBalance)?.value?.toDoubleOrNull()
    }
    val valueText = when {
        connected.size == 1 -> connected.first().lastBalance
        parsedValues.size == connected.size -> formatCurrency(parsedValues.sum(), currencySymbol)
        else -> connected.first().lastBalance
    }
    return valueText to "${connected.size} connected"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    adminViewModel: AdminViewModel,
    settings: LocalAppSettings,
    onBack: () -> Unit
) {
    var selectedSection by remember { mutableStateOf(AdminSection.OVERVIEW) }

    val usersState by adminViewModel.allUsers.collectAsStateWithLifecycle()
    val servicesState by adminViewModel.allServices.collectAsStateWithLifecycle()
    val ordersState by adminViewModel.allOrders.collectAsStateWithLifecycle()
    val transactionsState by adminViewModel.allTransactions.collectAsStateWithLifecycle()
    val providersState by adminViewModel.providers.collectAsStateWithLifecycle()
    val auditLogsState by adminViewModel.auditLogs.collectAsStateWithLifecycle()
    val knowledgeBaseState by adminViewModel.knowledgeBase.collectAsStateWithLifecycle()
    val supportTicketsState by adminViewModel.allSupportTickets.collectAsStateWithLifecycle()
    val isOwnerUidPinned by adminViewModel.isOwnerUidPinned.collectAsStateWithLifecycle()
    val appConfig by adminViewModel.appConfig.collectAsStateWithLifecycle()
    val isBusy by adminViewModel.isBusy.collectAsStateWithLifecycle()

    val users = (usersState as? UiState.Success)?.data.orEmpty()
    val services = (servicesState as? UiState.Success)?.data.orEmpty()
    val orders = (ordersState as? UiState.Success)?.data.orEmpty()
    val transactions = (transactionsState as? UiState.Success)?.data.orEmpty()
    val providers = (providersState as? UiState.Success)?.data.orEmpty()
    val auditLogs = (auditLogsState as? UiState.Success)?.data.orEmpty()

    val (providerBalDisplay, providerSub) = remember(providers, settings.currencySymbol) {
        computeProviderBalanceDisplay(providers, settings.currencySymbol)
    }

    val metrics = remember(users, services, orders, transactions, providerBalDisplay, providerSub) {
        AdminDashboardMetrics(
            totalUsers = users.size,
            totalOrders = orders.size,
            totalRevenue = orders.filter { it.status != "cancelled" }.sumOf { it.charge },
            completedOrders = orders.count { it.status == "completed" },
            totalDeposits = transactions
                .filter { it.type == "deposit_request" && it.status == "completed" }
                .sumOf { it.amount },
            pendingPayments = transactions.count {
                it.type == "deposit_request" && it.status == "pending_verification"
            },
            activeServices = services.count { it.active },
            totalServices = services.size,
            providerBalanceDisplay = providerBalDisplay,
            providerSubtitle = providerSub
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BINSMM Admin Suite", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("admin_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .widthIn(max = 860.dp)
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(AdminSection.entries) { section ->
                    FilterChip(
                        selected = selectedSection == section,
                        onClick = { selectedSection = section },
                        label = { Text(section.title) },
                        leadingIcon = {
                            Icon(
                                imageVector = section.icon,
                                contentDescription = section.title,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.testTag("admin_tab_${section.name}")
                    )
                }
            }

            when (selectedSection) {
                AdminSection.OVERVIEW -> AdminOverviewTab(
                    metrics = metrics,
                    recentLogs = auditLogs.take(3),
                    currencySymbol = settings.currencySymbol,
                    appConfig = appConfig,
                    isBusy = isBusy,
                    onSaveAppConfig = { adminViewModel.saveAppConfig(it) },
                    onNavigateSection = { selectedSection = it }
                )

                AdminSection.USERS -> AdminUsersTab(
                    usersState = usersState,
                    ordersState = ordersState,
                    currencySymbol = settings.currencySymbol,
                    isBusy = isBusy,
                    onUpdateUser = { user, status, balance, note ->
                        adminViewModel.updateUserAccount(user, status, balance, note)
                    }
                )

                AdminSection.SERVICES -> AdminServicesTab(
                    servicesState = servicesState,
                    providersState = providersState,
                    currencySymbol = settings.currencySymbol,
                    isBusy = isBusy,
                    onSeedCatalog = { adminViewModel.seedTemplateCatalog() },
                    onSaveService = { adminViewModel.saveService(it) },
                    onToggleServiceActive = { svc, active ->
                        adminViewModel.toggleServiceActive(svc, active)
                    },
                    onDeleteService = { id, name -> adminViewModel.deleteService(id, name) }
                )

                AdminSection.ORDERS -> AdminOrdersTab(
                    ordersState = ordersState,
                    currencySymbol = settings.currencySymbol,
                    isBusy = isBusy,
                    onUpdateOrderStatus = { order, status, providerOrderId ->
                        adminViewModel.updateOrderStatus(order, status, providerOrderId)
                    },
                    onCancelAndRefundOrder = { order, refund, reason ->
                        adminViewModel.cancelAndRefundOrder(order, refund, reason)
                    }
                )

                AdminSection.PAYMENTS -> AdminPaymentsTab(
                    transactionsState = transactionsState,
                    appConfig = appConfig,
                    currencySymbol = settings.currencySymbol,
                    isBusy = isBusy,
                    onProcessTransaction = { tx, approve, reason ->
                        adminViewModel.processTransaction(tx, approve, reason)
                    }
                )

                AdminSection.PROVIDERS -> AdminProvidersTab(
                    providersState = providersState,
                    isBusy = isBusy,
                    onSaveAndTestProvider = { existing, name, url, key, active, test ->
                        adminViewModel.saveAndTestProvider(existing, name, url, key, active, test)
                    },
                    onDeleteProvider = { id, name ->
                        adminViewModel.deleteProvider(id, name)
                    }
                )

                AdminSection.PRICING -> AdminPricingTab(
                    servicesState = servicesState,
                    currencySymbol = settings.currencySymbol,
                    isBusy = isBusy,
                    onApplyCategoryMarkup = { list, category, pct ->
                        adminViewModel.applyCategoryMarkup(list, category, pct)
                    },
                    onUpdateServicePrice = { svc ->
                        adminViewModel.saveService(svc)
                    }
                )

                AdminSection.KNOWLEDGE_BASE -> AdminKnowledgeBaseTab(
                    kbState = knowledgeBaseState,
                    isBusy = isBusy,
                    onSeedDefaultKb = { adminViewModel.seedDefaultKnowledgeBase() },
                    onSaveEntry = { adminViewModel.saveKnowledgeBaseEntry(it) },
                    onToggleActive = { entry, active -> adminViewModel.toggleKnowledgeBaseActive(entry, active) },
                    onDeleteEntry = { id, title -> adminViewModel.deleteKnowledgeBaseEntry(id, title) }
                )

                AdminSection.SUPPORT_TICKETS -> AdminSupportTicketsTab(
                    ticketsState = supportTicketsState,
                    isBusy = isBusy,
                    observeMessages = { ticketId -> adminViewModel.observeTicketMessages(ticketId) },
                    onReplyTicket = { ticket, message -> adminViewModel.replyToSupportTicket(ticket, message) },
                    onUpdateStatus = { ticket, newStatus -> adminViewModel.updateSupportTicketStatus(ticket, newStatus) }
                )

                AdminSection.ADMIN_SECURITY -> AdminSecurityTab(
                    isBusy = isBusy,
                    isOwnerUidPinned = isOwnerUidPinned,
                    onPinOwnerUid = { adminViewModel.syncOwnerAdminUid() },
                    onReauthWithPassword = { currentPassword -> adminViewModel.reauthenticateWithPassword(currentPassword) },
                    onChangeEmail = { newEmail -> adminViewModel.requestAdminEmailChange(newEmail) },
                    onChangePassword = { newPassword -> adminViewModel.updateAdminPassword(newPassword) }
                )

                AdminSection.AUDIT_LOGS -> AdminAuditLogsTab(auditLogsState = auditLogsState)
            }
        }
    }
}

@Composable
private fun AdminOverviewTab(
    metrics: AdminDashboardMetrics,
    recentLogs: List<AdminAuditLog>,
    currencySymbol: String,
    appConfig: AppConfig,
    isBusy: Boolean,
    onSaveAppConfig: (AppConfig) -> Unit,
    onNavigateSection: (AdminSection) -> Unit
) {
    var whatsappNumber by remember(appConfig.whatsappSupportNumber) { mutableStateOf(appConfig.whatsappSupportNumber) }
    var supportEmail by remember(appConfig.supportEmail) { mutableStateOf(appConfig.supportEmail) }
    var gatewayStatus by remember(appConfig.paymentGatewayStatus) { mutableStateOf(appConfig.paymentGatewayStatus) }
    var providerStatus by remember(appConfig.providerApiStatus) { mutableStateOf(appConfig.providerApiStatus) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(DeepRoyalBlue, VividPurple)))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AdminPanelSettings,
                        contentDescription = null,
                        tint = PureWhite,
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "BINSMM Executive Control",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PureWhite
                        )
                        Text(
                            text = "Live Firestore metrics • Role-verified access • Immutable audit trail",
                            style = MaterialTheme.typography.bodySmall,
                            color = PureWhite.copy(alpha = 0.88f)
                        )
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StatMetricCard(
                        title = "Total Users",
                        value = metrics.totalUsers.toString(),
                        icon = Icons.Default.Group,
                        accentColor = RoyalBlue,
                        modifier = Modifier.weight(1f)
                    )
                    StatMetricCard(
                        title = "Total Orders",
                        value = metrics.totalOrders.toString(),
                        subtitle = "${metrics.completedOrders} done",
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        accentColor = VividPurple,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StatMetricCard(
                        title = "Total Revenue",
                        value = formatCurrency(metrics.totalRevenue, currencySymbol),
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        accentColor = EmeraldSuccess,
                        modifier = Modifier.weight(1f)
                    )
                    StatMetricCard(
                        title = "Total Deposits",
                        value = formatCurrency(metrics.totalDeposits, currencySymbol),
                        icon = Icons.Default.Savings,
                        accentColor = CyanInfo,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StatMetricCard(
                        title = "Pending Payments",
                        value = metrics.pendingPayments.toString(),
                        icon = Icons.Default.PendingActions,
                        accentColor = AmberWarning,
                        modifier = Modifier.weight(1f)
                    )
                    StatMetricCard(
                        title = "Active Services",
                        value = metrics.activeServices.toString(),
                        subtitle = "${metrics.totalServices} total",
                        icon = Icons.Default.DesignServices,
                        accentColor = RoyalBlue,
                        modifier = Modifier.weight(1f)
                    )
                }
                StatMetricCard(
                    title = "Provider Balance",
                    value = metrics.providerBalanceDisplay,
                    subtitle = metrics.providerSubtitle,
                    icon = Icons.Default.AccountBalanceWallet,
                    accentColor = VividPurple,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { onNavigateSection(AdminSection.PAYMENTS) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Payments (${metrics.pendingPayments})")
                }
                OutlinedButton(
                    onClick = { onNavigateSection(AdminSection.PROVIDERS) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Provider APIs")
                }
            }
        }

        item {
            IntegrationNoticeBanner(
                title = "System Readiness",
                message = "Only connected SMM v2 Providers with backend credentials process real fulfillment. Template services remain DEMO until linked.",
                isWarning = metrics.providerBalanceDisplay == "Not connected"
            )
        }

        if (recentLogs.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Recent Admin Activity", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { onNavigateSection(AdminSection.AUDIT_LOGS) }) {
                                Text("View All")
                            }
                        }
                        recentLogs.forEach { log ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "${log.action} • ${formatTimestamp(log.createdAt)}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(log.summary, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Panel Support & Status Config", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = whatsappNumber,
                        onValueChange = { whatsappNumber = it },
                        label = { Text("WhatsApp Support Number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = supportEmail,
                        onValueChange = { supportEmail = it },
                        label = { Text("Support Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = gatewayStatus,
                        onValueChange = { gatewayStatus = it },
                        label = { Text("Payment Gateway Status Label") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = providerStatus,
                        onValueChange = { providerStatus = it },
                        label = { Text("Provider API Status Label") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = {
                            onSaveAppConfig(
                                appConfig.copy(
                                    whatsappSupportNumber = whatsappNumber,
                                    supportEmail = supportEmail,
                                    paymentGatewayStatus = gatewayStatus,
                                    providerApiStatus = providerStatus
                                )
                            )
                        },
                        enabled = !isBusy,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Panel Config")
                    }
                }
            }
        }
    }
}
