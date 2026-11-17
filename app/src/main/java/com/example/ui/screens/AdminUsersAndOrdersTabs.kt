package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ServiceCategory
import com.example.data.model.SmmOrder
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorStateCard
import com.example.ui.components.LoadingStateBox
import com.example.ui.components.StatusPill
import com.example.ui.components.formatCurrency
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.JetBrainsMonoFamily

// --- 1. USER MANAGEMENT ---
@Composable
fun AdminUsersTab(
    usersState: UiState<List<UserProfile>>,
    ordersState: UiState<List<SmmOrder>>,
    currencySymbol: String,
    isBusy: Boolean,
    onUpdateUser: (UserProfile, String, Double, String) -> Unit
) {
    var selectedUser by remember { mutableStateOf<UserProfile?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("all") }

    val allOrders = (ordersState as? UiState.Success)?.data.orEmpty()

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search users by name, email, WhatsApp, or UID...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Users") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_users_search")
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("all" to "All", "active" to "Active", "suspended" to "Suspended")) { (key, label) ->
                    FilterChip(
                        selected = statusFilter == key,
                        onClick = { statusFilter = key },
                        label = { Text(label) }
                    )
                }
            }
        }

        when (usersState) {
            is UiState.Loading -> item { LoadingStateBox("Loading users...") }
            is UiState.Error -> item { ErrorStateCard(usersState.message) }
            is UiState.Success -> {
                val filtered = usersState.data.filter { user ->
                    val matchesStatus = statusFilter == "all" || user.accountStatus.equals(statusFilter, ignoreCase = true)
                    val q = searchQuery.trim()
                    val matchesQuery = q.isBlank() ||
                        user.displayName.contains(q, ignoreCase = true) ||
                        user.email.contains(q, ignoreCase = true) ||
                        user.userId.contains(q, ignoreCase = true) ||
                        (user.whatsappHandle?.contains(q, ignoreCase = true) == true)
                    matchesStatus && matchesQuery
                }

                if (usersState.data.isEmpty()) {
                    item { EmptyStateCard("No Registered Users", "Users appear here upon Google Sign-In.") }
                } else if (filtered.isEmpty()) {
                    item { EmptyStateCard("No Matching Users", "Adjust your search query or account status filter.") }
                } else {
                    items(filtered, key = { it.userId }) { user ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedUser = user }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = user.displayName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        StatusPill(status = user.accountStatus)
                                    }
                                    Text(
                                        text = user.email,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (!user.whatsappHandle.isNullOrBlank()) {
                                        Text(
                                            text = "WhatsApp: ${user.whatsappHandle}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Balance: ${formatCurrency(user.balance, currencySymbol)} • Orders: ${user.totalOrders} (${user.pendingOrders} pending)",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily)
                                    )
                                }
                                IconButton(
                                    onClick = { selectedUser = user },
                                    modifier = Modifier.testTag("manage_user_${user.userId}")
                                ) {
                                    Icon(Icons.Default.ManageAccounts, contentDescription = "View & Manage User")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedUser?.let { user ->
        val userOrders = remember(allOrders, user.userId) {
            allOrders.filter { it.userId == user.userId }
        }
        var balanceText by remember(user.userId) { mutableStateOf(user.balance.toString()) }
        var status by remember(user.userId) { mutableStateOf(user.accountStatus) }
        var note by remember(user.userId) { mutableStateOf("Admin account review") }

        AlertDialog(
            onDismissRequest = { selectedUser = null },
            title = { Text(user.displayName, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Email: ${user.email}", style = MaterialTheme.typography.bodySmall)
                            Text("UID: ${user.userId}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily))
                            Text(
                                "WhatsApp: ${user.whatsappHandle?.ifBlank { "Not provided" } ?: "Not provided"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Role: ${user.role.uppercase()} (read-only) • Joined: ${formatTimestamp(user.createdAt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Orders: ${user.totalOrders} total • ${user.pendingOrders} pending • ${user.completedOrders} completed",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily)
                            )
                        }
                    }

                    Text("Account Status", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("active", "suspended").forEach { st ->
                            FilterChip(
                                selected = status == st,
                                onClick = { status = st },
                                label = { Text(st.uppercase()) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = balanceText,
                        onValueChange = { balanceText = it },
                        label = { Text("Wallet Balance ($currencySymbol)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Audit Reason Note") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider()
                    Text(
                        text = "Order History (${userOrders.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (userOrders.isEmpty()) {
                        Text(
                            text = "No orders placed by this user yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        userOrders.take(15).forEach { ord ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = ord.serviceName,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        StatusPill(status = ord.status)
                                    }
                                    Text(
                                        text = "${ord.id} • Qty ${ord.quantity} • ${formatCurrency(ord.charge, currencySymbol)}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily)
                                    )
                                    Text(
                                        text = "${ord.targetLink} • ${formatTimestamp(ord.createdAt)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isBusy,
                    onClick = {
                        val newBal = balanceText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: user.balance
                        onUpdateUser(user, status, newBal, note)
                        selectedUser = null
                    }
                ) { Text("Save Changes") }
            },
            dismissButton = {
                TextButton(onClick = { selectedUser = null }) { Text("Close") }
            }
        )
    }
}

// --- 2. ORDER MANAGEMENT ---
@Composable
fun AdminOrdersTab(
    ordersState: UiState<List<SmmOrder>>,
    currencySymbol: String,
    isBusy: Boolean,
    onUpdateOrderStatus: (SmmOrder, String, String) -> Unit,
    onCancelAndRefundOrder: (SmmOrder, Boolean, String) -> Unit
) {
    var selectedOrder by remember { mutableStateOf<SmmOrder?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("all") }
    var categoryFilter by remember { mutableStateOf<ServiceCategory?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search orders by ID, user email, service, or link...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Orders") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_orders_search")
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val statuses = listOf(
                    "all" to "All Statuses",
                    "pending" to "Pending",
                    "processing" to "Processing",
                    "in_progress" to "In Progress",
                    "awaiting_provider_config" to "Awaiting API",
                    "completed" to "Completed",
                    "partial" to "Partial",
                    "not_started" to "Not Started",
                    "not_received" to "Not Received",
                    "failed" to "Failed",
                    "cancelled" to "Cancelled"
                )
                items(statuses) { (key, label) ->
                    FilterChip(
                        selected = statusFilter == key,
                        onClick = { statusFilter = key },
                        label = { Text(label) }
                    )
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    FilterChip(
                        selected = categoryFilter == null,
                        onClick = { categoryFilter = null },
                        label = { Text("All Platforms") }
                    )
                }
                items(ServiceCategory.entries) { cat ->
                    FilterChip(
                        selected = categoryFilter == cat,
                        onClick = { categoryFilter = cat },
                        label = { Text(cat.displayName) }
                    )
                }
            }
        }

        when (ordersState) {
            is UiState.Loading -> item { LoadingStateBox("Loading all orders...") }
            is UiState.Error -> item { ErrorStateCard(ordersState.message) }
            is UiState.Success -> {
                val filtered = ordersState.data.filter { order ->
                    val matchesStatus = statusFilter == "all" || order.status.equals(statusFilter, ignoreCase = true)
                    val matchesCategory = categoryFilter == null || order.category.equals(categoryFilter!!.key, ignoreCase = true)
                    val q = searchQuery.trim()
                    val matchesQuery = q.isBlank() ||
                        order.id.contains(q, ignoreCase = true) ||
                        order.userEmail.contains(q, ignoreCase = true) ||
                        order.serviceName.contains(q, ignoreCase = true) ||
                        order.targetLink.contains(q, ignoreCase = true) ||
                        order.providerOrderId.contains(q, ignoreCase = true)
                    matchesStatus && matchesCategory && matchesQuery
                }

                if (ordersState.data.isEmpty()) {
                    item { EmptyStateCard("No Orders Yet", "User orders appear here for admin status management.") }
                } else if (filtered.isEmpty()) {
                    item { EmptyStateCard("No Matching Orders", "Adjust search terms or status/platform filters.") }
                } else {
                    items(filtered, key = { it.id }) { order ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedOrder = order }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(
                                        text = order.serviceName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${order.id} • ${ServiceCategory.fromKey(order.category).displayName} • ${order.userEmail}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Qty ${order.quantity} • ${formatCurrency(order.charge, currencySymbol)} • ${formatTimestamp(order.createdAt)}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily)
                                    )
                                    Text(
                                        text = order.targetLink,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    StatusPill(status = order.status)
                                }
                                IconButton(
                                    onClick = { selectedOrder = order },
                                    modifier = Modifier.testTag("manage_order_${order.id}")
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Manage Order")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedOrder?.let { order ->
        var nextStatus by remember(order.id) { mutableStateOf(order.status) }
        var providerOrderId by remember(order.id) { mutableStateOf(order.providerOrderId) }
        var showRefundStep by remember(order.id) { mutableStateOf(false) }
        var refundWallet by remember(order.id) { mutableStateOf(order.charge > 0.0) }
        var cancelReason by remember(order.id) { mutableStateOf("Cancelled by Admin") }

        AlertDialog(
            onDismissRequest = { selectedOrder = null },
            title = { Text("Order ${order.id}", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(order.serviceName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("User: ${order.userEmail}", style = MaterialTheme.typography.bodySmall)
                            Text("UID: ${order.userId}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily))
                            Text(
                                "Platform: ${ServiceCategory.fromKey(order.category).displayName} • Qty: ${order.quantity}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Charge: ${formatCurrency(order.charge, currencySymbol)}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text("Link: ${order.targetLink}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text("Created: ${formatTimestamp(order.createdAt)}", style = MaterialTheme.typography.labelSmall)
                            if (order.integrationNote.isNotBlank()) {
                                Text("Note: ${order.integrationNote}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Text("Fulfillment Status", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(
                            listOf(
                                "awaiting_provider_config",
                                "pending",
                                "processing",
                                "in_progress",
                                "completed",
                                "partial",
                                "not_started",
                                "not_received",
                                "failed",
                                "cancelled"
                            )
                        ) { st ->
                            FilterChip(
                                selected = nextStatus == st,
                                onClick = { nextStatus = st },
                                label = { Text(st.replace("_", " ")) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = providerOrderId,
                        onValueChange = { providerOrderId = it },
                        label = { Text("External Provider Order ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (order.isEligibleForCancellation) {
                        HorizontalDivider()
                        if (!showRefundStep) {
                            OutlinedButton(
                                onClick = { showRefundStep = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Cancel, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cancel & Refund Order")
                            }
                        } else {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        "Confirm Cancellation & Refund",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    if (order.charge > 0.0) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(checked = refundWallet, onCheckedChange = { refundWallet = it })
                                            Text(
                                                "Refund ${formatCurrency(order.charge, currencySymbol)} to wallet",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                    OutlinedTextField(
                                        value = cancelReason,
                                        onValueChange = { cancelReason = it },
                                        label = { Text("Cancellation / Refund Reason") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(
                                            onClick = { showRefundStep = false },
                                            modifier = Modifier.weight(1f)
                                        ) { Text("Back") }
                                        Button(
                                            enabled = !isBusy,
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                            onClick = {
                                                onCancelAndRefundOrder(order, refundWallet, cancelReason)
                                                selectedOrder = null
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) { Text("Confirm") }
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Order is ${order.status}; cancellation/refund is only available for active orders.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isBusy,
                    onClick = {
                        onUpdateOrderStatus(order, nextStatus, providerOrderId)
                        selectedOrder = null
                    }
                ) { Text("Update Status") }
            },
            dismissButton = {
                TextButton(onClick = { selectedOrder = null }) { Text("Close") }
            }
        )
    }
}
