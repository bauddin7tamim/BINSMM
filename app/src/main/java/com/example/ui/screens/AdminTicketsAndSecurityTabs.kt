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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.data.model.SupportCategory
import com.example.data.model.SupportTicket
import com.example.data.model.SupportTicketMessage
import com.example.data.model.SupportTicketStatus
import com.example.data.model.UiState
import com.example.ui.auth.reauthenticateAdminWithGoogleClicked
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorStateCard
import com.example.ui.components.IntegrationNoticeBanner
import com.example.ui.components.LoadingStateBox
import com.example.ui.components.StatusPill
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.JetBrainsMonoFamily
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.Flow

// --- ADMIN SUPPORT TICKETS MANAGEMENT ---

@Composable
fun AdminSupportTicketsTab(
    ticketsState: UiState<List<SupportTicket>>,
    isBusy: Boolean,
    observeMessages: (String) -> Flow<List<SupportTicketMessage>>,
    onReplyTicket: (SupportTicket, String) -> Unit,
    onUpdateStatus: (SupportTicket, String) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("all") }
    var selectedTicket by remember { mutableStateOf<SupportTicket?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search tickets by number, user email, Order ID, or subject...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Tickets") },
                trailingIcon = {
                    if (search.isNotEmpty()) {
                        IconButton(onClick = { search = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_tickets_search")
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    FilterChip(
                        selected = statusFilter == "all",
                        onClick = { statusFilter = "all" },
                        label = { Text("All Tickets") }
                    )
                }
                items(SupportTicketStatus.entries) { st ->
                    FilterChip(
                        selected = statusFilter == st.key,
                        onClick = { statusFilter = st.key },
                        label = { Text(st.displayName) }
                    )
                }
            }
        }

        when (ticketsState) {
            is UiState.Loading -> item { LoadingStateBox("Loading customer support tickets...") }
            is UiState.Error -> item { ErrorStateCard(ticketsState.message) }
            is UiState.Success -> {
                val filtered = ticketsState.data.filter { ticket ->
                    val matchesSt = statusFilter == "all" || ticket.status == statusFilter
                    val q = search.trim()
                    val matchesQ = q.isBlank() ||
                        ticket.ticketNumber.contains(q, ignoreCase = true) ||
                        ticket.userEmail.contains(q, ignoreCase = true) ||
                        ticket.orderId.contains(q, ignoreCase = true) ||
                        ticket.subject.contains(q, ignoreCase = true)
                    matchesSt && matchesQ
                }

                if (ticketsState.data.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "No Support Tickets",
                            description = "Customer support tickets submitted in the Support Hub appear here.",
                            icon = Icons.Default.ConfirmationNumber
                        )
                    }
                } else if (filtered.isEmpty()) {
                    item { EmptyStateCard("No Matching Tickets", "Adjust search query or status filter.") }
                } else {
                    items(filtered, key = { it.id }) { ticket ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedTicket = ticket }
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${ticket.ticketNumber} • ${ticket.userEmail}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    StatusPill(status = ticket.status)
                                }
                                Text(ticket.subject, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    text = ticket.lastMessagePreview.ifBlank { ticket.description },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = buildString {
                                        append(SupportCategory.fromKey(ticket.category).displayName)
                                        if (ticket.orderId.isNotBlank()) append(" • Order: ${ticket.orderId}")
                                        append(" • ${formatTimestamp(ticket.updatedAt ?: ticket.createdAt)}")
                                    },
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

    selectedTicket?.let { ticket ->
        val thread by remember(ticket.id) { observeMessages(ticket.id) }
            .collectAsStateWithLifecycle(initialValue = emptyList())
        var nextStatus by remember(ticket.id) { mutableStateOf(ticket.status) }
        var replyText by remember(ticket.id) { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { selectedTicket = null },
            title = {
                Column {
                    Text("${ticket.ticketNumber} — ${ticket.userName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(ticket.userEmail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                ) {
                    Text(ticket.subject, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (ticket.orderId.isNotBlank()) {
                        Text(
                            "Order ID: ${ticket.orderId}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(SupportTicketStatus.entries) { st ->
                            FilterChip(
                                selected = nextStatus == st.key,
                                onClick = {
                                    nextStatus = st.key
                                    onUpdateStatus(ticket, st.key)
                                },
                                label = { Text(st.displayName) }
                            )
                        }
                    }
                    HorizontalDivider()
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 210.dp)
                    ) {
                        if (thread.isEmpty()) {
                            item { Text(ticket.description, style = MaterialTheme.typography.bodySmall) }
                        } else {
                            items(thread, key = { it.id }) { msg ->
                                val isAdmin = msg.senderRole == "admin"
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isAdmin) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            text = "${msg.senderName} • ${formatTimestamp(msg.createdAt)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isAdmin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(msg.message, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            placeholder = { Text("Write Admin reply...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            enabled = !isBusy && replyText.isNotBlank(),
                            onClick = {
                                onReplyTicket(ticket, replyText)
                                replyText = ""
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Admin Reply", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            },
            confirmButton = {
                if (nextStatus != SupportTicketStatus.CLOSED.key) {
                    Button(
                        enabled = !isBusy,
                        onClick = {
                            onUpdateStatus(ticket, SupportTicketStatus.CLOSED.key)
                            selectedTicket = null
                        }
                    ) { Text("Close Ticket") }
                } else {
                    TextButton(onClick = { selectedTicket = null }) { Text("Done") }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedTicket = null }) { Text("Dismiss") }
            }
        )
    }
}

// --- ADMIN EMAIL & PASSWORD SECURITY MANAGEMENT ---

@Composable
fun AdminSecurityTab(
    isBusy: Boolean,
    isOwnerUidPinned: Boolean,
    onPinOwnerUid: () -> Unit,
    onReauthWithPassword: (String) -> Unit,
    onChangeEmail: (String) -> Unit,
    onChangePassword: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleScope = LocalLifecycleOwner.current.lifecycleScope
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val currentUser = Firebase.auth.currentUser
    val providerIds = currentUser?.providerData?.map { it.providerId }.orEmpty()
    val hasPasswordProvider = "password" in providerIds

    var reauthStatusMsg by remember { mutableStateOf<String?>(null) }
    var currentPassword by remember { mutableStateOf("") }
    var newEmail by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Owner Admin Identity & Continuity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "Signed-in Admin Email: ${currentUser?.email ?: "Unknown"}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Admin UID: ${currentUser?.uid ?: "Unknown"} • Providers: ${providerIds.joinToString(", ").ifBlank { "google.com" }}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isOwnerUidPinned) "UID Pinned (Survives Email Update)" else "UID Not Pinned Yet",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOwnerUidPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        OutlinedButton(
                            enabled = !isBusy,
                            onClick = onPinOwnerUid,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isOwnerUidPinned) "Re-Sync Pin" else "Pin Admin UID")
                        }
                    }
                }
            }
        }

        item {
            IntegrationNoticeBanner(
                title = "Firebase Auth Security Notice",
                message = "Passwords are NEVER saved in Firestore or logs—only Firebase Auth handles password hashing. Changing Admin email requires verified ownership and deployed Cloud Function 'adminSyncOwnerUid' so your UID stays authorized.",
                isWarning = !isOwnerUidPinned
            )
        }

        // Step 1: Reauthenticate
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("1. Reauthenticate Session (Required by Firebase)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Button(
                        enabled = !isBusy,
                        onClick = {
                            reauthenticateAdminWithGoogleClicked(
                                context = context,
                                credentialManager = credentialManager,
                                onSuccess = { reauthStatusMsg = "Google session reauthenticated cleanly." },
                                onError = { err -> reauthStatusMsg = err },
                                scope = lifecycleScope
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("admin_reauth_google_button")
                    ) {
                        Text("Reauthenticate with Google")
                    }

                    if (hasPasswordProvider) {
                        OutlinedTextField(
                            value = currentPassword,
                            onValueChange = { currentPassword = it },
                            label = { Text("Current Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedButton(
                            enabled = !isBusy && currentPassword.isNotBlank(),
                            onClick = {
                                onReauthWithPassword(currentPassword)
                                currentPassword = ""
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Reauthenticate with Current Password")
                        }
                    }

                    if (!reauthStatusMsg.isNullOrBlank()) {
                        Text(
                            text = reauthStatusMsg!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Step 2: Update Admin Email
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MarkEmailRead, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("2. Change Admin Email (With Verification)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    OutlinedTextField(
                        value = newEmail,
                        onValueChange = { newEmail = it },
                        label = { Text("New Admin Email Address") },
                        placeholder = { Text("new-owner@domain.com") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_new_email_input")
                    )
                    Button(
                        enabled = !isBusy && newEmail.trim().contains("@") && newEmail.trim().contains("."),
                        onClick = {
                            onChangeEmail(newEmail)
                            newEmail = ""
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("admin_change_email_button")
                    ) {
                        Text("Send Verification & Update Email")
                    }
                }
            }
        }

        // Step 3: Change / Set Password
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("3. Change Admin Password", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New Password (8+ characters)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_new_password_input")
                    )
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm New Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_confirm_password_input")
                    )
                    if (confirmPassword.isNotEmpty() && newPassword != confirmPassword) {
                        Text(
                            text = "Passwords do not match.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Button(
                        enabled = !isBusy && newPassword.length >= 8 && newPassword == confirmPassword,
                        onClick = {
                            onChangePassword(newPassword)
                            newPassword = ""
                            confirmPassword = ""
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("admin_change_password_button")
                    ) {
                        Text("Update Admin Password")
                    }
                }
            }
        }
    }
}
