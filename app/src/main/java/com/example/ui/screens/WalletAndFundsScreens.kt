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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CreditCardOff
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.AppConfig
import com.example.data.model.LocalAppSettings
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.WalletTransaction
import com.example.data.remote.PaymentGatewayRegistry
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorStateCard
import com.example.ui.components.IntegrationNoticeBanner
import com.example.ui.components.LoadingStateBox
import com.example.ui.components.StatusPill
import com.example.ui.components.formatCurrency
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.DeepAmethyst
import com.example.ui.theme.DeepRoyalBlue
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.PureWhite
import com.example.ui.theme.RoseError

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFundsScreen(
    userProfile: UserProfile?,
    appConfig: AppConfig,
    settings: LocalAppSettings,
    isSubmitting: Boolean,
    onSubmitDepositRequest: (Double, String, String) -> Unit,
    onBack: () -> Unit
) {
    var selectedGatewayId by remember { mutableStateOf("manual_request") }
    var amountText by remember { mutableStateOf("25") }
    var referenceNote by remember { mutableStateOf("") }

    val selectedGateway = PaymentGatewayRegistry.availableGateways.first { it.id == selectedGatewayId }
    val amountValue = amountText.toDoubleOrNull() ?: 0.0
    val isSuspended = userProfile?.accountStatus == "suspended"
    val canSubmitManual = !isSuspended && selectedGateway.isConfigured && amountValue > 0.0 && referenceNote.trim().length >= 3

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Funds", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("add_funds_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .widthIn(max = 720.dp)
        ) {
            item {
                if (isSuspended) {
                    IntegrationNoticeBanner(
                        title = "Account Suspended",
                        message = "Deposit requests are disabled while your account is suspended. Contact WhatsApp support.",
                        isWarning = true
                    )
                } else {
                    IntegrationNoticeBanner(
                        title = "Payment Gateway Integration Notice",
                        message = "Status: ${appConfig.paymentGatewayStatus}. Instant card/UPI gateways are disabled until live backend payment keys and webhook verification are configured. Manual deposit requests are queued for Admin approval.",
                        isWarning = true
                    )
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Current Available Balance",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatCurrency(userProfile?.balance ?: 0.0, settings.currencySymbol),
                                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = JetBrainsMonoFamily),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Select Payment Method",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(PaymentGatewayRegistry.availableGateways, key = { it.id }) { gateway ->
                val isSelected = gateway.id == selectedGatewayId
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedGatewayId = gateway.id }
                        .testTag("gateway_card_${gateway.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (gateway.isConfigured) Icons.Default.Payments else Icons.Default.CreditCardOff,
                            contentDescription = gateway.name,
                            tint = if (gateway.isConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = gateway.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = gateway.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusPill(status = if (gateway.isConfigured) "active" else "not_configured")
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (!selectedGateway.isConfigured) {
                            Text(
                                text = "${selectedGateway.name} is Not Configured",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Live checkout cannot run until a secure server-side payment gateway integration is connected. Choose 'Manual Deposit Verification Request' above to submit a deposit reference for Admin verification.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "Manual Deposit Verification Request",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = amountText,
                                onValueChange = { input ->
                                    if (input.all { it.isDigit() || it == '.' }) amountText = input
                                },
                                label = { Text("Deposit Amount (${settings.currencySymbol})") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("add_funds_amount_input")
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("10", "25", "50", "100").forEach { preset ->
                                    OutlinedButton(
                                        onClick = { amountText = preset },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("${settings.currencySymbol}$preset")
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = referenceNote,
                                onValueChange = { referenceNote = it },
                                label = { Text("Transfer Reference / Transaction ID") },
                                placeholder = { Text("e.g. BANK-REF-948210 or Wise Receipt") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("add_funds_reference_input")
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    onSubmitDepositRequest(amountValue, selectedGateway.id, referenceNote)
                                },
                                enabled = canSubmitManual && !isSubmitting,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("submit_deposit_request_button")
                            ) {
                                if (isSubmitting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.AddCard, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Submit Verification Request")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletTransactionsScreen(
    userProfile: UserProfile?,
    transactionsState: UiState<List<WalletTransaction>>,
    settings: LocalAppSettings,
    onNavigateAddFunds: () -> Unit,
    onBack: () -> Unit
) {
    var filterType by remember { mutableStateOf("all") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wallet & Transactions", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("wallet_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .widthIn(max = 720.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.linearGradient(listOf(DeepRoyalBlue, DeepAmethyst)))
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Wallet Balance",
                                style = MaterialTheme.typography.bodySmall,
                                color = PureWhite.copy(alpha = 0.8f)
                            )
                            Text(
                                text = formatCurrency(userProfile?.balance ?: 0.0, settings.currencySymbol),
                                style = MaterialTheme.typography.displaySmall.copy(fontFamily = JetBrainsMonoFamily),
                                fontWeight = FontWeight.Bold,
                                color = PureWhite
                            )
                        }
                        Button(
                            onClick = onNavigateAddFunds,
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = PureWhite,
                                contentColor = DeepRoyalBlue
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Add Funds", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(
                        listOf(
                            "all" to "All",
                            "deposit_request" to "Deposits",
                            "order_charge" to "Order Charges",
                            "admin_adjustment" to "Adjustments"
                        )
                    ) { (key, label) ->
                        FilterChip(
                            selected = filterType == key,
                            onClick = { filterType = key },
                            label = { Text(label) }
                        )
                    }
                }
            }

            when (transactionsState) {
                is UiState.Loading -> item { LoadingStateBox("Loading wallet ledger...") }
                is UiState.Error -> item { ErrorStateCard(transactionsState.message) }
                is UiState.Success -> {
                    val itemsList = transactionsState.data.filter {
                        filterType == "all" || it.type == filterType
                    }
                    if (itemsList.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "No Transactions Recorded",
                                description = "Deposit verification requests and order charges will appear here.",
                                actionLabel = "Add Funds",
                                onAction = onNavigateAddFunds
                            )
                        }
                    } else {
                        items(itemsList, key = { it.id }) { tx ->
                            val isCredit = tx.type in listOf("deposit_request", "refund", "admin_adjustment")
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                (if (isCredit) EmeraldSuccess else RoseError).copy(alpha = 0.12f)
                                            )
                                    ) {
                                        Icon(
                                            imageVector = if (isCredit) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                            contentDescription = tx.type,
                                            tint = if (isCredit) EmeraldSuccess else RoseError
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = tx.type.replace("_", " ").replaceFirstChar { it.uppercase() },
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = tx.referenceNote,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = formatTimestamp(tx.createdAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "${if (isCredit) "+" else "-"}${formatCurrency(tx.amount, settings.currencySymbol)}",
                                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily),
                                            fontWeight = FontWeight.Bold,
                                            color = if (isCredit) EmeraldSuccess else RoseError
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        StatusPill(status = tx.status)
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
