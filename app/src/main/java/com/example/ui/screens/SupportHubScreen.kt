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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.SupportAssistantEngine
import com.example.data.model.AppConfig
import com.example.data.model.LocalAppSettings
import com.example.data.model.SmmOrder
import com.example.data.model.SupportChatMessage
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.repository.SupportDefaults
import com.example.ui.components.ErrorStateCard
import com.example.ui.components.LoadingStateBox
import com.example.ui.components.StatusPill
import com.example.ui.components.formatCurrency
import com.example.ui.components.formatTimestamp
import com.example.ui.components.openAdminWhatsAppSupport
import com.example.ui.theme.DeepRoyalBlue
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.PureWhite
import com.example.ui.theme.VividPurple
import com.example.ui.viewmodel.SupportViewModel

private val QUICK_SUPPORT_PROMPTS = listOf(
    "How do I place an order?",
    "मेरा ऑर्डर पेंडिंग क्यों है?",
    "Balance add karne ke baad credit nahi hua",
    "What does DEMO service mean?",
    "How do refills and refunds work?"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportHubScreen(
    supportViewModel: SupportViewModel,
    userProfile: UserProfile?,
    appConfig: AppConfig,
    settings: LocalAppSettings,
    initialOrderId: String = "",
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var showCreateTicketDialog by remember { mutableStateOf(false) }
    var prefillTicketOrderId by remember(initialOrderId) { mutableStateOf(initialOrderId) }

    val chatState by supportViewModel.chatMessages.collectAsStateWithLifecycle()
    val ticketsState by supportViewModel.userTickets.collectAsStateWithLifecycle()
    val isGenerating by supportViewModel.isGeneratingReply.collectAsStateWithLifecycle()
    val isSubmittingTicket by supportViewModel.isSubmittingTicket.collectAsStateWithLifecycle()
    val latestOrder by supportViewModel.latestVerifiedOrder.collectAsStateWithLifecycle()
    val snackbarMsg by supportViewModel.supportMessage.collectAsStateWithLifecycle()

    LaunchedEffect(initialOrderId) {
        if (initialOrderId.isNotBlank()) {
            val history = (chatState as? UiState.Success)?.data
            if (history != null && history.none { it.text.contains(initialOrderId) }) {
                supportViewModel.sendCustomerMessage("Please check status and help with my Order ID $initialOrderId")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Brush.linearGradient(listOf(DeepRoyalBlue, VividPurple)))
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "BINSMM AI Assistant",
                                tint = PureWhite,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("BINSMM Support Hub", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("AI Assistant • Hindi / English / Hinglish", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("support_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selectedTab == 0) {
                        IconButton(
                            onClick = { supportViewModel.clearConversation() },
                            modifier = Modifier.testTag("support_clear_chat_button")
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear AI Chat History")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .widthIn(max = 800.dp)
        ) {
            if (!snackbarMsg.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(snackbarMsg!!, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        androidx.compose.material3.TextButton(onClick = { supportViewModel.clearMessage() }) {
                            Text("OK")
                        }
                    }
                }
            }

            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("AI Assistant") },
                    icon = { Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("support_tab_ai")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        val openCount = (ticketsState as? UiState.Success)?.data
                            ?.count { it.status !in listOf("resolved", "closed") } ?: 0
                        Text(if (openCount > 0) "My Tickets ($openCount)" else "My Tickets")
                    },
                    icon = { Icon(Icons.Default.ConfirmationNumber, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("support_tab_tickets")
                )
            }

            if (selectedTab == 0) {
                AiChatSupportTab(
                    chatState = chatState,
                    isGenerating = isGenerating,
                    latestVerifiedOrder = latestOrder,
                    appConfig = appConfig,
                    settings = settings,
                    initialOrderId = initialOrderId,
                    onSendMessage = { supportViewModel.sendCustomerMessage(it) },
                    onOpenTicket = { orderId ->
                        prefillTicketOrderId = orderId.ifBlank { initialOrderId }
                        selectedTab = 1
                        showCreateTicketDialog = true
                    },
                    onOpenWhatsApp = { question, orderId ->
                        openAdminWhatsAppSupport(
                            context = context,
                            phoneNumber = appConfig.whatsappSupportNumber.ifBlank { SupportDefaults.ADMIN_WHATSAPP_NUMBER },
                            problem = question,
                            orderId = orderId
                        )
                    }
                )
            } else {
                CustomerTicketsTab(
                    ticketsState = ticketsState,
                    userDisplayName = userProfile?.displayName ?: "BINSMM User",
                    appConfig = appConfig,
                    isSubmitting = isSubmittingTicket,
                    initialOrderId = prefillTicketOrderId,
                    observeMessages = { ticketId -> supportViewModel.observeTicketMessages(ticketId) },
                    onCreateTicket = { cat, ordId, subj, desc ->
                        supportViewModel.createSupportTicket(
                            category = cat,
                            orderId = ordId,
                            subject = subj,
                            description = desc,
                            displayName = userProfile?.displayName ?: "BINSMM User"
                        )
                    },
                    onReplyTicket = { ticket, msg -> supportViewModel.replyToTicket(ticket, msg) },
                    showCreateDialog = showCreateTicketDialog,
                    onSetShowCreateDialog = { showCreateTicketDialog = it }
                )
            }
        }
    }
}

@Composable
private fun AiChatSupportTab(
    chatState: UiState<List<SupportChatMessage>>,
    isGenerating: Boolean,
    latestVerifiedOrder: SmmOrder?,
    appConfig: AppConfig,
    settings: LocalAppSettings,
    initialOrderId: String,
    onSendMessage: (String) -> Unit,
    onOpenTicket: (String) -> Unit,
    onOpenWhatsApp: (String, String?) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val messages = (chatState as? UiState.Success)?.data.orEmpty()
    val lastUserQuestion = messages.lastOrNull { it.role == "user" }?.text.orEmpty()

    LaunchedEffect(messages.size, isGenerating) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding()
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (latestVerifiedOrder != null) {
                item(key = "verified_order_banner") {
                    VerifiedOrderSummaryCard(
                        order = latestVerifiedOrder,
                        currencySymbol = settings.currencySymbol
                    )
                }
            }

            when (chatState) {
                is UiState.Loading -> item { LoadingStateBox("Loading support conversation...") }
                is UiState.Error -> item { ErrorStateCard(chatState.message) }
                is UiState.Success -> {
                    if (messages.isEmpty()) {
                        item {
                            AiWelcomeHeroCard(
                                onOpenWhatsApp = {
                                    onOpenWhatsApp("I need support with BINSMM.", initialOrderId)
                                }
                            )
                        }
                    } else {
                        items(messages, key = { it.id }) { msg ->
                            ChatBubbleItem(
                                message = msg,
                                fallbackQuestion = lastUserQuestion,
                                onOpenTicket = { onOpenTicket(msg.detectedOrderId) },
                                onOpenWhatsApp = {
                                    onOpenWhatsApp(
                                        msg.escalatedProblem.ifBlank { lastUserQuestion },
                                        msg.detectedOrderId
                                    )
                                }
                            )
                        }
                    }
                }
            }

            if (isGenerating) {
                item(key = "ai_typing") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 6.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "BINSMM AI is checking knowledge base & order status...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Quick question chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(QUICK_SUPPORT_PROMPTS) { prompt ->
                AssistChip(
                    onClick = { if (!isGenerating) onSendMessage(prompt) },
                    label = { Text(prompt, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        // Persistent Admin WhatsApp + Ticket bar + Input box
        Surface(
            tonalElevation = 4.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            onOpenWhatsApp(
                                lastUserQuestion.ifBlank { "I need help from BINSMM Admin." },
                                latestVerifiedOrder?.id ?: initialOrderId
                            )
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldSuccess),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("ai_chat_whatsapp_admin_button")
                    ) {
                        Icon(Icons.Default.SupportAgent, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Chat with Admin on WhatsApp", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { onOpenTicket(latestVerifiedOrder?.id ?: initialOrderId) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("ai_chat_open_ticket_button")
                    ) {
                        Icon(Icons.Default.ConfirmationNumber, contentDescription = "Open Support Ticket", modifier = Modifier.size(18.dp))
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Ask in English, Hindi, or Hinglish (or paste Order ID)...") },
                        singleLine = false,
                        maxLines = 3,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_support_input")
                    )
                    Button(
                        enabled = !isGenerating && inputText.trim().isNotEmpty(),
                        onClick = {
                            val toSend = inputText
                            inputText = ""
                            onSendMessage(toSend)
                        },
                        shape = CircleShape,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("ai_support_send_button"),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Support Message")
                    }
                }
            }
        }
    }
}

@Composable
private fun AiWelcomeHeroCard(onOpenWhatsApp: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(DeepRoyalBlue, VividPurple)))
                ) {
                    Icon(Icons.Default.SmartToy, contentDescription = null, tint = PureWhite)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Namaste! I'm BINSMM AI Support", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Ask in Hindi, English, or Hinglish", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                text = "I can guide you on placing orders, checking your own Order IDs safely, wallet deposits & screenshots, Instagram/YouTube/Facebook/Telegram services, and refills or cancellations.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onOpenWhatsApp,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldSuccess),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.SupportAgent, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Chat with Admin on WhatsApp (${SupportDefaults.ADMIN_WHATSAPP_NUMBER})")
            }
        }
    }
}

@Composable
private fun VerifiedOrderSummaryCard(order: SmmOrder, currencySymbol: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Verified Order: ${order.id}",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                StatusPill(status = order.status)
            }
            Text(order.serviceName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = "Qty ${order.quantity} • ${formatCurrency(order.charge, currencySymbol)} • ${formatTimestamp(order.createdAt)}",
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = SupportAssistantEngine.describeOrderStatus(order.status),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChatBubbleItem(
    message: SupportChatMessage,
    fallbackQuestion: String,
    onOpenTicket: () -> Unit,
    onOpenWhatsApp: () -> Unit
) {
    val isUser = message.role == "user"
    Column(
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            border = if (isUser) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
            modifier = Modifier.widthIn(max = 560.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) PureWhite else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = formatTimestamp(message.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUser) PureWhite.copy(alpha = 0.78f) else MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!isUser && message.suggestWhatsApp) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onOpenWhatsApp,
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess, contentColor = PureWhite),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Default.SupportAgent, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Chat with Admin on WhatsApp", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = onOpenTicket,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Ticket", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}
