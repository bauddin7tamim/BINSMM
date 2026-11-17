package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.model.AppConfig
import com.example.data.model.LocalAppSettings
import com.example.data.model.ServiceCategory
import com.example.data.model.SmmOrder
import com.example.data.model.UiState
import com.example.data.model.UserNotification
import com.example.data.model.UserProfile
import com.example.data.repository.SupportDefaults
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorStateCard
import com.example.ui.components.IntegrationNoticeBanner
import com.example.ui.components.LoadingStateBox
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatMetricCard
import com.example.ui.components.StatusPill
import com.example.ui.components.categoryColor
import com.example.ui.components.categoryIcon
import com.example.ui.components.formatCurrency
import com.example.ui.components.formatTimestamp
import com.example.ui.components.openAdminWhatsAppSupport
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.DeepAmethyst
import com.example.ui.theme.DeepRoyalBlue
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.PureWhite
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.VividPurple

@Composable
fun HomeDashboardScreen(
    profileState: UiState<UserProfile?>,
    ordersState: UiState<List<SmmOrder>>,
    notificationsState: UiState<List<UserNotification>>,
    appConfig: AppConfig,
    settings: LocalAppSettings,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onNavigateNewOrder: (ServiceCategory) -> Unit,
    onNavigateOrders: () -> Unit,
    onNavigateAddFunds: () -> Unit,
    onNavigateWallet: () -> Unit,
    onNavigateNotifications: () -> Unit,
    onNavigateSupport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val profile = (profileState as? UiState.Success)?.data
    val orders = (ordersState as? UiState.Success)?.data.orEmpty()
    val notifications = (notificationsState as? UiState.Success)?.data.orEmpty()
    val unreadCount = notifications.count { !it.read }

    val totalOrders = if (orders.isNotEmpty()) orders.size.toLong() else (profile?.totalOrders ?: 0L)
    val pendingOrders = if (orders.isNotEmpty()) {
        orders.count { it.status in listOf("pending", "processing", "awaiting_provider_config") }.toLong()
    } else {
        profile?.pendingOrders ?: 0L
    }
    val completedOrders = if (orders.isNotEmpty()) {
        orders.count { it.status == "completed" }.toLong()
    } else {
        profile?.completedOrders ?: 0L
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 720.dp)
    ) {
        // 1. Top Header Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(R.drawable.binsmm_launcher_icon)
                            .build(),
                        contentDescription = "BINSMM Logo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "BINSMM",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Hi, ${profile?.displayName?.ifBlank { "Partner" } ?: "Partner"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier.testTag("home_theme_toggle")
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Switch Theme",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = onNavigateNotifications,
                        modifier = Modifier.testTag("home_notifications_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (unreadCount > 0) {
                                    Badge { Text(unreadCount.coerceAtMost(99).toString()) }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Notifications",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // 2. Hero Available Balance Card
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(DeepRoyalBlue, ElectricIndigo, DeepAmethyst)
                            )
                        )
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(R.drawable.img_dashboard_wave)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .alpha(0.32f)
                    )
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Available Balance",
                                style = MaterialTheme.typography.titleSmall,
                                color = PureWhite.copy(alpha = 0.85f)
                            )
                            Surface(
                                color = PureWhite.copy(alpha = 0.16f),
                                shape = CircleShape
                            ) {
                                Text(
                                    text = (profile?.accountStatus ?: "active").uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PureWhite,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = formatCurrency(profile?.balance ?: 0.0, settings.currencySymbol),
                            style = MaterialTheme.typography.displaySmall.copy(fontFamily = JetBrainsMonoFamily),
                            fontWeight = FontWeight.Bold,
                            color = PureWhite
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onNavigateAddFunds,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PureWhite,
                                    contentColor = DeepRoyalBlue
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("home_add_funds_button")
                            ) {
                                Icon(Icons.Default.AddCard, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Funds", fontWeight = FontWeight.Bold)
                            }
                            FilledTonalButton(
                                onClick = onNavigateWallet,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = PureWhite.copy(alpha = 0.18f),
                                    contentColor = PureWhite
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("home_wallet_button")
                            ) {
                                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Wallet", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        // 3. Honest Integration Readiness Banner
        item {
            IntegrationNoticeBanner(
                title = "Backend Integration Status",
                message = "Payment Gateway: ${appConfig.paymentGatewayStatus}\nSMM Provider API: ${appConfig.providerApiStatus}",
                isWarning = true
            )
        }

        // 4. 2x2 Metrics Grid (Balance, Total Orders, Pending Orders, Completed Orders)
        item {
            if (profileState is UiState.Error) {
                ErrorStateCard(message = profileState.message)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatMetricCard(
                            title = "Available Balance",
                            value = formatCurrency(profile?.balance ?: 0.0, settings.currencySymbol),
                            icon = Icons.Default.AccountBalanceWallet,
                            accentColor = RoyalBlue,
                            subtitle = "Wallet",
                            modifier = Modifier.weight(1f)
                        )
                        StatMetricCard(
                            title = "Total Orders",
                            value = totalOrders.toString(),
                            icon = Icons.AutoMirrored.Filled.ReceiptLong,
                            accentColor = VividPurple,
                            subtitle = "All Time",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatMetricCard(
                            title = "Pending Orders",
                            value = pendingOrders.toString(),
                            icon = Icons.Default.HourglassTop,
                            accentColor = AmberWarning,
                            subtitle = "Queue",
                            modifier = Modifier.weight(1f)
                        )
                        StatMetricCard(
                            title = "Completed Orders",
                            value = completedOrders.toString(),
                            icon = Icons.Default.CheckCircle,
                            accentColor = EmeraldSuccess,
                            subtitle = "Delivered",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 5. Platform Services Quick Launch (Instagram, YouTube, Facebook, Telegram)
        item {
            SectionHeader(
                title = "Platform Services",
                actionText = "Place Order",
                onActionClick = { onNavigateNewOrder(settings.defaultCategory) }
            )
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(ServiceCategory.entries) { category ->
                    val brandColor = categoryColor(category.key)
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, brandColor.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .width(152.dp)
                            .clickable { onNavigateNewOrder(category) }
                            .testTag("platform_shortcut_${category.key}")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(brandColor.copy(alpha = 0.14f))
                            ) {
                                Icon(
                                    imageVector = categoryIcon(category.key),
                                    contentDescription = category.displayName,
                                    tint = brandColor
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = category.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Explore services",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 6. Recent Orders Preview
        item {
            SectionHeader(
                title = "Recent Orders",
                actionText = "View All",
                onActionClick = onNavigateOrders
            )
        }

        when (ordersState) {
            is UiState.Loading -> item { LoadingStateBox("Loading recent orders...") }
            is UiState.Error -> item { ErrorStateCard(ordersState.message) }
            is UiState.Success -> {
                if (ordersState.data.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "No Orders Yet",
                            description = "Select Instagram, YouTube, Facebook, or Telegram services to create your first order.",
                            actionLabel = "Create First Order",
                            onAction = { onNavigateNewOrder(settings.defaultCategory) }
                        )
                    }
                } else {
                    items(ordersState.data.take(3), key = { it.id }) { order ->
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateOrders() }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(categoryColor(order.category).copy(alpha = 0.12f))
                                ) {
                                    Icon(
                                        imageVector = categoryIcon(order.category),
                                        contentDescription = order.category,
                                        tint = categoryColor(order.category)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = order.serviceName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Qty ${order.quantity} • ${formatCurrency(order.charge, settings.currencySymbol)} • ${formatTimestamp(order.createdAt)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                StatusPill(status = order.status)
                            }
                        }
                    }
                }
            }
        }

        // 7. AI Customer Support & Direct Admin WhatsApp Card
        item {
            val whatsappNumber = appConfig.whatsappSupportNumber.ifBlank { SupportDefaults.ADMIN_WHATSAPP_NUMBER }
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(DeepRoyalBlue, VividPurple)))
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "BINSMM AI Support",
                                tint = PureWhite
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "BINSMM AI Support & Help Desk",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Instant Hindi, English & Hinglish assistance for orders, wallet deposits, refills & tickets.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onNavigateSupport,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("home_ai_support_button")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ask AI Assistant", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        FilledTonalButton(
                            onClick = { openAdminWhatsAppSupport(context, whatsappNumber) },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = EmeraldSuccess.copy(alpha = 0.16f),
                                contentColor = EmeraldSuccess
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("home_whatsapp_support_card")
                        ) {
                            Icon(Icons.Default.SupportAgent, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("WhatsApp Admin", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
