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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.LocalAppSettings
import com.example.data.model.SmmOrder
import com.example.data.model.UiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorStateCard
import com.example.ui.components.LoadingStateBox
import com.example.ui.components.StatusPill
import com.example.ui.components.categoryColor
import com.example.ui.components.categoryIcon
import com.example.ui.components.formatCurrency
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.JetBrainsMonoFamily

private val ORDER_STATUS_FILTERS = listOf(
    "all" to "All",
    "awaiting_provider_config" to "Awaiting API",
    "pending" to "Pending",
    "processing" to "Processing",
    "in_progress" to "In Progress",
    "completed" to "Completed",
    "partial" to "Partial",
    "not_started" to "Not Started",
    "not_received" to "Not Received",
    "failed" to "Failed",
    "cancelled" to "Cancelled"
)

@Composable
fun OrdersScreen(
    ordersState: UiState<List<SmmOrder>>,
    settings: LocalAppSettings,
    onCancelOrder: (SmmOrder) -> Unit,
    onNavigateNewOrder: () -> Unit,
    onNavigateOrderSupport: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var search by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("all") }
    var selectedOrder by remember { mutableStateOf<SmmOrder?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 720.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Order History",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Track your social campaign orders, charges, and API fulfillment states.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search by Order ID, service, or link...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Orders") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("orders_search_input")
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ORDER_STATUS_FILTERS) { (key, label) ->
                    FilterChip(
                        selected = statusFilter == key,
                        onClick = { statusFilter = key },
                        label = { Text(label) },
                        modifier = Modifier.testTag("orders_filter_$key")
                    )
                }
            }
        }

        when (ordersState) {
            is UiState.Loading -> item { LoadingStateBox("Loading order history...") }
            is UiState.Error -> item { ErrorStateCard(ordersState.message) }
            is UiState.Success -> {
                val filtered = ordersState.data.filter { order ->
                    (statusFilter == "all" || order.status.equals(statusFilter, ignoreCase = true)) &&
                        (search.isBlank() ||
                            order.id.contains(search, ignoreCase = true) ||
                            order.serviceName.contains(search, ignoreCase = true) ||
                            order.targetLink.contains(search, ignoreCase = true))
                }
                if (filtered.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "No Matching Orders",
                            description = "Orders placed from New Order appear here with live Firestore synchronization.",
                            actionLabel = "Place New Order",
                            onAction = onNavigateNewOrder
                        )
                    }
                } else {
                    items(filtered, key = { it.id }) { order ->
                        OrderHistoryCard(
                            order = order,
                            compact = settings.compactOrderCards,
                            currencySymbol = settings.currencySymbol,
                            onClick = { selectedOrder = order }
                        )
                    }
                }
            }
        }
    }

    selectedOrder?.let { order ->
        AlertDialog(
            onDismissRequest = { selectedOrder = null },
            title = {
                Column {
                    Text(
                        text = "Order Details",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = order.id,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailRow("Service", order.serviceName)
                    DetailRow("Platform", order.category.replaceFirstChar { it.uppercase() })
                    DetailRow("Target Link", order.targetLink)
                    DetailRow("Quantity", order.quantity.toString())
                    DetailRow("Charge", formatCurrency(order.charge, settings.currencySymbol))
                    DetailRow("Status", order.status.replace("_", " ").uppercase())
                    if (order.providerOrderId.isNotBlank()) {
                        DetailRow("Provider Order ID", order.providerOrderId)
                    }
                    DetailRow("Created", formatTimestamp(order.createdAt))
                    HorizontalDivider()
                    Text(
                        text = order.integrationNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = {
                            val orderId = order.id
                            selectedOrder = null
                            onNavigateOrderSupport(orderId)
                        },
                        modifier = Modifier.testTag("order_details_ask_ai_button")
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ask AI Support About This Order")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedOrder = null }) {
                    Text("Close")
                }
            },
            dismissButton = {
                if (order.isEligibleForCancellation) {
                    TextButton(
                        onClick = {
                            onCancelOrder(order)
                            selectedOrder = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cancel Order")
                    }
                }
            }
        )
    }
}

@Composable
private fun OrderHistoryCard(
    order: SmmOrder,
    compact: Boolean,
    currencySymbol: String,
    onClick: () -> Unit
) {
    val brandColor = categoryColor(order.category)
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("order_card_${order.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(brandColor.copy(alpha = 0.14f))
                    ) {
                        Icon(
                            imageVector = categoryIcon(order.category),
                            contentDescription = order.category,
                            tint = brandColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = order.serviceName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = order.id,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                StatusPill(status = order.status)
            }

            if (!compact) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = order.targetLink,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Qty: ${order.quantity}",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily)
                )
                Text(
                    text = formatCurrency(order.charge, currencySymbol),
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = formatTimestamp(order.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.6f)
        )
    }
}
