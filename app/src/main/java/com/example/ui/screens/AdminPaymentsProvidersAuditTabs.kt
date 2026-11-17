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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AdminAuditLog
import com.example.data.model.AppConfig
import com.example.data.model.SmmProviderConfig
import com.example.data.model.UiState
import com.example.data.model.WalletTransaction
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorStateCard
import com.example.ui.components.IntegrationNoticeBanner
import com.example.ui.components.LoadingStateBox
import com.example.ui.components.StatusPill
import com.example.ui.components.formatCurrency
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.JetBrainsMonoFamily

// --- 5. PAYMENT MANAGEMENT ---
@Composable
fun AdminPaymentsTab(
    transactionsState: UiState<List<WalletTransaction>>,
    appConfig: AppConfig,
    currencySymbol: String,
    isBusy: Boolean,
    onProcessTransaction: (WalletTransaction, Boolean, String) -> Unit
) {
    var selectedTx by remember { mutableStateOf<WalletTransaction?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var filterKey by remember { mutableStateOf("pending_verification") }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            IntegrationNoticeBanner(
                title = "Payment Gateway Management",
                message = "Gateway status: ${appConfig.paymentGatewayStatus}. Verify proof references before approving manual deposits.",
                isWarning = true
            )
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by user email, TX ID, gateway, or reference...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Payments") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            val filters = listOf(
                "pending_verification" to "Pending",
                "all" to "All",
                "completed" to "Approved/Completed",
                "rejected" to "Rejected",
                "refund" to "Refunds"
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(filters) { (key, label) ->
                    FilterChip(
                        selected = filterKey == key,
                        onClick = { filterKey = key },
                        label = { Text(label) }
                    )
                }
            }
        }

        when (transactionsState) {
            is UiState.Loading -> item { LoadingStateBox("Loading payment requests...") }
            is UiState.Error -> item { ErrorStateCard(transactionsState.message) }
            is UiState.Success -> {
                val filtered = transactionsState.data.filter { tx ->
                    val matchesFilter = when (filterKey) {
                        "all" -> true
                        "refund" -> tx.type == "refund"
                        else -> tx.status.equals(filterKey, ignoreCase = true)
                    }
                    val q = searchQuery.trim()
                    val matchesQuery = q.isBlank() ||
                        tx.id.contains(q, ignoreCase = true) ||
                        tx.userEmail.contains(q, ignoreCase = true) ||
                        tx.referenceNote.contains(q, ignoreCase = true) ||
                        tx.gateway.contains(q, ignoreCase = true)
                    matchesFilter && matchesQuery
                }
                if (transactionsState.data.isEmpty()) {
                    item { EmptyStateCard("No Transactions", "Deposit requests, charges, and refunds will appear here.") }
                } else if (filtered.isEmpty()) {
                    item { EmptyStateCard("No Matching Transactions", "Adjust the status filter or search query.") }
                } else {
                    items(filtered, key = { it.id }) { tx ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedTx = tx }
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${tx.userEmail} • ${tx.type.replace("_", " ").uppercase()}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${tx.id} • ${tx.gateway} • ${formatTimestamp(tx.createdAt)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (tx.referenceNote.isNotBlank()) {
                                            Text(
                                                text = tx.referenceNote,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = formatCurrency(tx.amount, currencySymbol),
                                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily),
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        StatusPill(status = tx.status)
                                    }
                                }
                                if (tx.type == "deposit_request" && tx.status == "pending_verification") {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            enabled = !isBusy,
                                            onClick = { onProcessTransaction(tx, true, "") },
                                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Approve & Credit")
                                        }
                                        OutlinedButton(
                                            enabled = !isBusy,
                                            onClick = { selectedTx = tx },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Review / Reject")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedTx?.let { tx ->
        var rejectReason by remember(tx.id) { mutableStateOf("") }
        val isPendingDeposit = tx.type == "deposit_request" && tx.status == "pending_verification"

        AlertDialog(
            onDismissRequest = { selectedTx = null },
            title = { Text("Transaction ${tx.id}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("User: ${tx.userEmail}", style = MaterialTheme.typography.bodySmall)
                            Text("UID: ${tx.userId}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily))
                            Text("Type: ${tx.type} • Gateway: ${tx.gateway}", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "Amount: ${formatCurrency(tx.amount, currencySymbol)}",
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily),
                                fontWeight = FontWeight.Bold
                            )
                            Text("Reference: ${tx.referenceNote.ifBlank { "None" }}", style = MaterialTheme.typography.bodySmall)
                            Text("Created: ${formatTimestamp(tx.createdAt)}", style = MaterialTheme.typography.labelSmall)
                            StatusPill(status = tx.status)
                        }
                    }
                    if (isPendingDeposit) {
                        OutlinedTextField(
                            value = rejectReason,
                            onValueChange = { rejectReason = it },
                            label = { Text("Rejection Reason (if rejecting)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                if (isPendingDeposit) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = !isBusy,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            onClick = {
                                onProcessTransaction(tx, false, rejectReason)
                                selectedTx = null
                            }
                        ) { Text("Reject") }
                        Button(
                            enabled = !isBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                            onClick = {
                                onProcessTransaction(tx, true, "")
                                selectedTx = null
                            }
                        ) { Text("Approve") }
                    }
                } else {
                    Button(onClick = { selectedTx = null }) { Text("Done") }
                }
            },
            dismissButton = {
                if (isPendingDeposit) {
                    TextButton(onClick = { selectedTx = null }) { Text("Close") }
                }
            }
        )
    }
}

// --- 6. PROVIDER API MANAGEMENT ---
@Composable
fun AdminProvidersTab(
    providersState: UiState<List<SmmProviderConfig>>,
    isBusy: Boolean,
    onSaveAndTestProvider: (SmmProviderConfig?, String, String, String, Boolean, Boolean) -> Unit,
    onDeleteProvider: (String, String) -> Unit
) {
    var editingProvider by remember { mutableStateOf<SmmProviderConfig?>(null) }
    var isCreating by remember { mutableStateOf(false) }
    var deletingProvider by remember { mutableStateOf<SmmProviderConfig?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            IntegrationNoticeBanner(
                title = "Cloud Functions Secret Security",
                message = "Raw provider API keys are NEVER saved in Firestore or the Android app. Provider tests and order dispatches execute server-side in Cloud Functions using Secret Manager (SMM_PROVIDER_API_KEYS_JSON) or an optional transient test key.",
                isWarning = false
            )
        }

        item {
            Button(
                onClick = {
                    editingProvider = SmmProviderConfig(name = "Primary SMM v2 Provider", active = true)
                    isCreating = true
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_add_provider")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Provider Configuration")
            }
        }

        when (providersState) {
            is UiState.Loading -> item { LoadingStateBox("Loading providers...") }
            is UiState.Error -> item { ErrorStateCard(providersState.message) }
            is UiState.Success -> {
                if (providersState.data.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "No Providers Configured",
                            description = "Add an SMM v2 provider endpoint to run a live connection test and view its balance."
                        )
                    }
                } else {
                    items(providersState.data, key = { it.id }) { prv ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(prv.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    StatusPill(status = if (prv.active) "connected" else "not_configured")
                                }
                                Text(
                                    text = prv.baseUrl.ifBlank { "No endpoint URL configured" },
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "Credential: ${prv.apiKeyMasked.ifBlank { "Backend secret not tested" }} • Balance: ${prv.lastBalance}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily)
                                )
                                Text(
                                    text = "Status: ${prv.lastSyncStatus}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            editingProvider = prv
                                            isCreating = false
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Edit / Test")
                                    }
                                    IconButton(onClick = { deletingProvider = prv }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Provider", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingProvider?.let { prv ->
        var name by remember(prv.id) { mutableStateOf(prv.name) }
        var baseUrl by remember(prv.id) { mutableStateOf(prv.baseUrl) }
        var transientApiKey by remember(prv.id) { mutableStateOf("") }
        var active by remember(prv.id) { mutableStateOf(prv.active) }

        AlertDialog(
            onDismissRequest = { editingProvider = null },
            title = { Text(if (isCreating) "Add Provider Config" else "Edit / Test ${prv.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Provider Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("SMM v2 API Endpoint URL") },
                        placeholder = { Text("https://provider.example/api/v2") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = transientApiKey,
                        onValueChange = { transientApiKey = it },
                        label = { Text("Transient API Key (never saved in DB)") },
                        placeholder = { Text(if (prv.apiKeyConfigured) "Current: ${prv.apiKeyMasked}" else "Enter key to run live test") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable when connected", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = active, onCheckedChange = { active = it })
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = !isBusy && name.isNotBlank(),
                        onClick = {
                            onSaveAndTestProvider(
                                if (isCreating) null else prv,
                                name,
                                baseUrl,
                                transientApiKey,
                                active,
                                false
                            )
                            editingProvider = null
                        }
                    ) { Text("Save") }
                    Button(
                        enabled = !isBusy && name.isNotBlank() && baseUrl.isNotBlank() && (transientApiKey.isNotBlank() || (!isCreating && prv.apiKeyConfigured)),
                        onClick = {
                            onSaveAndTestProvider(
                                if (isCreating) null else prv,
                                name,
                                baseUrl,
                                transientApiKey,
                                active,
                                true
                            )
                            editingProvider = null
                        }
                    ) {
                        Icon(Icons.Default.NetworkCheck, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Test API")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { editingProvider = null }) { Text("Cancel") }
            }
        )
    }

    deletingProvider?.let { prv ->
        AlertDialog(
            onDismissRequest = { deletingProvider = null },
            title = { Text("Delete Provider?") },
            text = { Text("Remove '${prv.name}' from provider configurations?") },
            confirmButton = {
                Button(
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        onDeleteProvider(prv.id, prv.name)
                        deletingProvider = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingProvider = null }) { Text("Cancel") }
            }
        )
    }
}

// --- 7. ADMIN AUDIT LOG ---
@Composable
fun AdminAuditLogsTab(
    auditLogsState: UiState<List<AdminAuditLog>>
) {
    var searchQuery by remember { mutableStateOf("") }
    var targetFilter by remember { mutableStateOf("all") }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            IntegrationNoticeBanner(
                title = "Immutable Admin Audit Trail",
                message = "Every administrative change is recorded in Firestore with update and delete operations strictly blocked by security rules.",
                isWarning = false
            )
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search audit logs by action, admin email, target, or summary...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Audit Logs") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            val targets = listOf("all", "user", "service", "order", "transaction", "provider", "pricing", "config", "catalog")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(targets) { target ->
                    FilterChip(
                        selected = targetFilter == target,
                        onClick = { targetFilter = target },
                        label = { Text(target.replaceFirstChar { it.uppercase() }) }
                    )
                }
            }
        }

        when (auditLogsState) {
            is UiState.Loading -> item { LoadingStateBox("Loading audit logs...") }
            is UiState.Error -> item { ErrorStateCard(auditLogsState.message) }
            is UiState.Success -> {
                val filtered = auditLogsState.data.filter { log ->
                    val matchesType = targetFilter == "all" || log.targetType.equals(targetFilter, ignoreCase = true)
                    val q = searchQuery.trim()
                    val matchesQuery = q.isBlank() ||
                        log.action.contains(q, ignoreCase = true) ||
                        log.summary.contains(q, ignoreCase = true) ||
                        log.adminEmail.contains(q, ignoreCase = true) ||
                        log.targetId.contains(q, ignoreCase = true)
                    matchesType && matchesQuery
                }
                if (auditLogsState.data.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "No Audit Entries Yet",
                            description = "Administrative actions (approvals, suspensions, refunds, service & provider changes) appear here automatically.",
                            icon = Icons.Default.Security
                        )
                    }
                } else if (filtered.isEmpty()) {
                    item { EmptyStateCard("No Matching Audit Logs", "Adjust the target filter or search query.") }
                } else {
                    items(filtered, key = { it.id }) { log ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = log.action,
                                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = formatTimestamp(log.createdAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(log.summary, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = "By ${log.adminEmail} • ${log.targetType}:${log.targetId}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
