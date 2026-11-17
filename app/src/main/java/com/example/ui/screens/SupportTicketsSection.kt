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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AppConfig
import com.example.data.model.SupportCategory
import com.example.data.model.SupportTicket
import com.example.data.model.SupportTicketMessage
import com.example.data.model.SupportTicketStatus
import com.example.data.model.UiState
import com.example.data.repository.SupportDefaults
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorStateCard
import com.example.ui.components.LoadingStateBox
import com.example.ui.components.StatusPill
import com.example.ui.components.formatTimestamp
import com.example.ui.components.openAdminWhatsAppSupport
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.PureWhite
import com.example.ui.viewmodel.SupportViewModel
import kotlinx.coroutines.flow.Flow

@Composable
fun CustomerTicketsTab(
    ticketsState: UiState<List<SupportTicket>>,
    userDisplayName: String,
    appConfig: AppConfig,
    isSubmitting: Boolean,
    initialOrderId: String,
    observeMessages: (String) -> Flow<List<SupportTicketMessage>>,
    onCreateTicket: (category: String, orderId: String, subject: String, description: String) -> Unit,
    onReplyTicket: (SupportTicket, String) -> Unit,
    showCreateDialog: Boolean,
    onSetShowCreateDialog: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var selectedTicket by remember { mutableStateOf<SupportTicket?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { onSetShowCreateDialog(true) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("support_create_ticket_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Support Ticket", fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = {
                        openAdminWhatsAppSupport(
                            context = context,
                            phoneNumber = appConfig.whatsappSupportNumber.ifBlank { SupportDefaults.ADMIN_WHATSAPP_NUMBER },
                            problem = "Support ticket inquiry from $userDisplayName",
                            orderId = initialOrderId
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("support_tickets_whatsapp_button")
                ) {
                    Icon(Icons.Default.SupportAgent, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("WhatsApp")
                }
            }
        }

        when (ticketsState) {
            is UiState.Loading -> item { LoadingStateBox("Loading your support tickets...") }
            is UiState.Error -> item { ErrorStateCard(ticketsState.message) }
            is UiState.Success -> {
                if (ticketsState.data.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "No Support Tickets Yet",
                            description = "Need manual payment verification, a refill check, or Admin assistance? Create a ticket and track replies here.",
                            icon = Icons.Default.ConfirmationNumber,
                            actionLabel = "Create Ticket",
                            onAction = { onSetShowCreateDialog(true) }
                        )
                    }
                } else {
                    items(ticketsState.data, key = { it.id }) { ticket ->
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedTicket = ticket }
                                .testTag("ticket_card_${ticket.id}")
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = ticket.ticketNumber,
                                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    StatusPill(status = ticket.status)
                                }
                                Text(
                                    text = ticket.subject,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = ticket.lastMessagePreview.ifBlank { ticket.description },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = buildString {
                                            append(SupportCategory.fromKey(ticket.category).displayName)
                                            if (ticket.orderId.isNotBlank()) append(" • ${ticket.orderId}")
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = formatTimestamp(ticket.updatedAt ?: ticket.createdAt),
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

    if (showCreateDialog) {
        CreateTicketDialog(
            initialOrderId = initialOrderId,
            isSubmitting = isSubmitting,
            onDismiss = { onSetShowCreateDialog(false) },
            onSubmit = { cat, orderId, subj, desc ->
                onCreateTicket(cat, orderId, subj, desc)
                onSetShowCreateDialog(false)
            }
        )
    }

    selectedTicket?.let { ticket ->
        val thread by remember(ticket.id) { observeMessages(ticket.id) }
            .collectAsStateWithLifecycle(initialValue = emptyList())
        CustomerTicketThreadDialog(
            ticket = ticket,
            messages = thread,
            isSubmitting = isSubmitting,
            onSendReply = { text -> onReplyTicket(ticket, text) },
            onChatWhatsApp = {
                openAdminWhatsAppSupport(
                    context = context,
                    phoneNumber = appConfig.whatsappSupportNumber.ifBlank { SupportDefaults.ADMIN_WHATSAPP_NUMBER },
                    problem = "${ticket.ticketNumber}: ${ticket.subject}",
                    orderId = ticket.orderId
                )
            },
            onDismiss = { selectedTicket = null }
        )
    }
}

@Composable
private fun CreateTicketDialog(
    initialOrderId: String,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (category: String, orderId: String, subject: String, description: String) -> Unit
) {
    var category by remember {
        mutableStateOf(
            if (initialOrderId.isNotBlank()) SupportCategory.ORDER else SupportCategory.GENERAL
        )
    }
    var orderId by remember(initialOrderId) { mutableStateOf(initialOrderId) }
    var subject by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Support Ticket", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Category", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(SupportCategory.entries) { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.displayName) }
                        )
                    }
                }
                OutlinedTextField(
                    value = orderId,
                    onValueChange = { orderId = it },
                    label = { Text("Order ID (Optional)") },
                    placeholder = { Text("e.g. ord_abc123") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ticket_order_id_input")
                )
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject") },
                    placeholder = { Text("Brief summary of your issue") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ticket_subject_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Problem Description") },
                    placeholder = { Text("Describe the issue clearly (payment ref, link, drop details)...") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ticket_description_input")
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !isSubmitting && subject.trim().length >= 3 && description.trim().length >= 5,
                onClick = { onSubmit(category.key, orderId, subject, description) },
                modifier = Modifier.testTag("ticket_submit_button")
            ) {
                Text("Submit Ticket")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun CustomerTicketThreadDialog(
    ticket: SupportTicket,
    messages: List<SupportTicketMessage>,
    isSubmitting: Boolean,
    onSendReply: (String) -> Unit,
    onChatWhatsApp: () -> Unit,
    onDismiss: () -> Unit
) {
    var replyText by remember { mutableStateOf("") }
    val isClosed = ticket.status == SupportTicketStatus.CLOSED.key

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(ticket.ticketNumber, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(ticket.subject, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                StatusPill(status = ticket.status)
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                if (ticket.orderId.isNotBlank()) {
                    Text(
                        text = "Order ID: ${ticket.orderId}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                HorizontalDivider()
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(min = 120.dp, max = 240.dp)
                ) {
                    if (messages.isEmpty()) {
                        item {
                            Text(ticket.description, style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        items(messages, key = { it.id }) { msg ->
                            val isAdminMsg = msg.senderRole == "admin"
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isAdminMsg) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = if (isAdminMsg) "BINSMM Admin" else msg.senderName,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isAdminMsg) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = formatTimestamp(msg.createdAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(msg.message, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                if (!isClosed) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            placeholder = { Text("Add follow-up message...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            enabled = !isSubmitting && replyText.isNotBlank(),
                            onClick = {
                                onSendReply(replyText)
                                replyText = ""
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Reply", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                } else {
                    Text(
                        text = "This ticket is closed.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedButton(
                    onClick = onChatWhatsApp,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldSuccess),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.SupportAgent, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Chat with Admin on WhatsApp")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
