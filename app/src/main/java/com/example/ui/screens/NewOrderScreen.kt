package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.data.model.LocalAppSettings
import com.example.data.model.ServiceCategory
import com.example.data.model.SmmService
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorStateCard
import com.example.ui.components.IntegrationNoticeBanner
import com.example.ui.components.LoadingStateBox
import com.example.ui.components.StatusPill
import com.example.ui.components.categoryColor
import com.example.ui.components.categoryIcon
import com.example.ui.components.formatCurrency
import com.example.ui.theme.JetBrainsMonoFamily

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewOrderScreen(
    selectedCategory: ServiceCategory?,
    onSelectCategory: (ServiceCategory?) -> Unit,
    servicesState: UiState<List<SmmService>>,
    userProfile: UserProfile?,
    settings: LocalAppSettings,
    isAdmin: Boolean,
    isSubmitting: Boolean,
    onSeedCatalog: () -> Unit,
    onNavigateAdmin: () -> Unit,
    onSubmitOrder: (SmmService, String, Long) -> Unit,
    onNavigateAddFunds: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedService by remember { mutableStateOf<SmmService?>(null) }
    var targetLink by remember { mutableStateOf("") }
    var quantityText by remember { mutableStateOf("1000") }

    val allServices = (servicesState as? UiState.Success)?.data.orEmpty()
    val activeServices = remember(allServices) { allServices.filter { it.active } }
    val filteredServices = remember(activeServices, selectedCategory, searchQuery) {
        activeServices.filter { svc ->
            val matchesCategory = selectedCategory == null ||
                svc.category.equals(selectedCategory.key, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() ||
                svc.name.contains(searchQuery, ignoreCase = true) ||
                svc.description.contains(searchQuery, ignoreCase = true) ||
                svc.category.contains(searchQuery, ignoreCase = true) ||
                svc.providerServiceId.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    LaunchedEffect(selectedCategory, filteredServices) {
        val currentId = selectedService?.id
        val matched = if (currentId != null) {
            filteredServices.firstOrNull { it.id == currentId }
        } else {
            null
        }
        if (matched != null) {
            selectedService = matched
        } else {
            selectedService = filteredServices.firstOrNull()
            selectedService?.let { svc ->
                val defaultQty = 1000L.coerceIn(svc.minQuantity, svc.maxQuantity)
                quantityText = defaultQty.toString()
            }
        }
    }

    val quantity = quantityText.toLongOrNull() ?: 0L
    val currentSvc = selectedService
    val isDemoSelected = currentSvc?.isDemoService == true
    val calculatedCharge = if (currentSvc != null && quantity > 0) {
        (quantity.toDouble() / 1000.0) * currentSvc.ratePer1000
    } else 0.0
    val currentBalance = userProfile?.balance ?: 0.0
    val isSuspended = userProfile?.accountStatus == "suspended"
    val quantityValid = currentSvc != null && quantity in currentSvc.minQuantity..currentSvc.maxQuantity
    val linkValid = targetLink.trim().length >= 5
    val balanceSufficient = calculatedCharge <= 0.0 || currentBalance >= calculatedCharge

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 720.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "New Order",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Browse catalog services, filter by platform, and check pricing & limits.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isAdmin) {
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onSeedCatalog,
                        enabled = !isSubmitting,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("header_load_templates_button")
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Load Templates", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // Category Filters: All + Instagram, YouTube, Facebook, Telegram
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    val isAllSelected = selectedCategory == null
                    FilterChip(
                        selected = isAllSelected,
                        onClick = { onSelectCategory(null) },
                        label = {
                            Text(
                                text = "All (${activeServices.size})",
                                fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Apps,
                                contentDescription = "All Platforms",
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.testTag("category_chip_all")
                    )
                }
                items(ServiceCategory.entries) { category ->
                    val isSelected = category == selectedCategory
                    val brandColor = categoryColor(category.key)
                    val categoryCount = activeServices.count {
                        it.category.equals(category.key, ignoreCase = true)
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectCategory(category) },
                        label = {
                            Text(
                                text = "${category.displayName} ($categoryCount)",
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = categoryIcon(category.key),
                                contentDescription = category.displayName,
                                tint = if (isSelected) brandColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = brandColor.copy(alpha = 0.14f),
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.testTag("category_chip_${category.key}")
                    )
                }
            }
        }

        // Search Box
        item {
            val searchPlaceholder = selectedCategory?.let { "Search ${it.displayName} services..." }
                ?: "Search all Instagram, YouTube, Facebook & Telegram services..."
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(searchPlaceholder) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("new_order_search_input")
            )
        }

        // Services List
        when (servicesState) {
            is UiState.Loading -> item { LoadingStateBox("Syncing service catalog from Firestore...") }
            is UiState.Error -> item { ErrorStateCard(servicesState.message) }
            is UiState.Success -> {
                if (activeServices.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "Service Catalog Not Seeded",
                            description = if (isAdmin) {
                                "No services are saved in Firestore yet. Load categorized DEMO template services for Instagram, YouTube, Facebook & Telegram."
                            } else {
                                "The service catalog is being configured by the panel administrator."
                            },
                            actionLabel = if (isAdmin) {
                                if (isSubmitting) "Loading Template Services..." else "Load Template Services"
                            } else null,
                            onAction = if (isAdmin && !isSubmitting) onSeedCatalog else null
                        )
                    }
                } else if (filteredServices.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "No Matching Services Found",
                            description = "Try clearing your search filter or switching to 'All' platforms.",
                            actionLabel = if (searchQuery.isNotBlank() || selectedCategory != null) "Reset Filters" else null,
                            onAction = if (searchQuery.isNotBlank() || selectedCategory != null) {
                                {
                                    searchQuery = ""
                                    onSelectCategory(null)
                                }
                            } else null
                        )
                    }
                } else {
                    items(filteredServices, key = { it.id }) { svc ->
                        val isChosen = svc.id == currentSvc?.id
                        val brandColor = categoryColor(svc.category)
                        val categoryObj = ServiceCategory.fromKey(svc.category)
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isChosen) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ),
                            border = BorderStroke(
                                width = if (isChosen) 1.5.dp else 1.dp,
                                color = if (isChosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedService = svc
                                    val clamped = (quantityText.toLongOrNull() ?: 1000L).coerceIn(svc.minQuantity, svc.maxQuantity)
                                    quantityText = clamped.toString()
                                }
                                .testTag("service_card_${svc.id}")
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = if (isChosen) Icons.Default.CheckCircle else categoryIcon(svc.category),
                                            contentDescription = categoryObj.displayName,
                                            tint = if (isChosen) MaterialTheme.colorScheme.primary else brandColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = svc.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = categoryObj.displayName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = brandColor,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${formatCurrency(svc.ratePer1000, settings.currencySymbol)}/1k",
                                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = svc.description.ifBlank { "Standard SMM catalog service." },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = if (isChosen) 4 else 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "Min: ${svc.minQuantity} • Max: ${svc.maxQuantity}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    if (svc.averageTime.isNotBlank()) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Schedule,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(12.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = svc.averageTime,
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }
                                    }
                                    if (svc.refillSupported) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Verified,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(12.dp),
                                                    tint = MaterialTheme.colorScheme.secondary
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Refill",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                            }
                                        }
                                    }
                                    StatusPill(
                                        status = if (svc.isDemoService) "demo" else svc.integrationStatus
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Order Checkout Form
        if (currentSvc != null) {
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Order Details",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            StatusPill(status = if (isDemoSelected) "demo" else currentSvc.integrationStatus)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${currentSvc.name} (${formatCurrency(currentSvc.ratePer1000, settings.currencySymbol)}/1k)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )

                        if (isSuspended) {
                            Spacer(modifier = Modifier.height(10.dp))
                            IntegrationNoticeBanner(
                                title = "Account Suspended",
                                message = "Your BINSMM account is currently suspended. Ordering is disabled; please contact WhatsApp support.",
                                isWarning = true
                            )
                        } else if (isDemoSelected) {
                            Spacer(modifier = Modifier.height(10.dp))
                            IntegrationNoticeBanner(
                                title = "DEMO Service — Real Orders Disabled",
                                message = "This is a DEMO template service with no connected SMM Provider API. Real orders cannot be processed and your wallet will not be charged until a live Provider API is configured and linked to this service in Admin.",
                                isWarning = true
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = targetLink,
                            onValueChange = { targetLink = it },
                            label = { Text("Target Link or @Username") },
                            placeholder = { Text("https://instagram.com/username") },
                            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("order_link_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = quantityText,
                            onValueChange = { input ->
                                if (input.all { it.isDigit() }) quantityText = input
                            },
                            label = { Text("Quantity (Min ${currentSvc.minQuantity} - Max ${currentSvc.maxQuantity})") },
                            leadingIcon = { Icon(Icons.Default.Numbers, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            isError = quantityText.isNotBlank() && !quantityValid,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("order_quantity_input")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(500L, 1000L, 5000L, 10000L)
                                .filter { it in currentSvc.minQuantity..currentSvc.maxQuantity }
                                .forEach { preset ->
                                    OutlinedButton(
                                        onClick = { quantityText = preset.toString() },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text("$preset")
                                    }
                                }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Estimated Charge",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatCurrency(calculatedCharge, settings.currencySymbol),
                                    style = MaterialTheme.typography.headlineSmall.copy(fontFamily = JetBrainsMonoFamily),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Wallet Balance",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatCurrency(currentBalance, settings.currencySymbol),
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily),
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (balanceSufficient) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        if (!isDemoSelected && !balanceSufficient) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = onNavigateAddFunds,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("order_insufficient_add_funds_button")
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Insufficient Balance — Go to Add Funds")
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                onSubmitOrder(currentSvc, targetLink, quantity)
                            },
                            enabled = !isSubmitting && !isSuspended && !isDemoSelected && linkValid && quantityValid && balanceSufficient,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("submit_order_button")
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.ShoppingBag, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = when {
                                        isSuspended -> "Account Suspended — Ordering Disabled"
                                        isDemoSelected -> "DEMO Service — Provider API Required"
                                        else -> "Place Order (${formatCurrency(calculatedCharge, settings.currencySymbol)})"
                                    },
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }

                        if (isDemoSelected && isAdmin) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = onNavigateAdmin,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("configure_provider_admin_button")
                            ) {
                                Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Configure Provider API in Admin")
                            }
                        }
                    }
                }
            }
        }
    }
}
