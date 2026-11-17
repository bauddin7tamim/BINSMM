package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.ServiceCategory
import com.example.data.model.SmmProviderConfig
import com.example.data.model.SmmService
import com.example.data.model.UiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorStateCard
import com.example.ui.components.LoadingStateBox
import com.example.ui.components.StatusPill
import com.example.ui.components.formatCurrency
import com.example.ui.theme.JetBrainsMonoFamily
import java.util.Locale

// --- 3. SERVICE MANAGEMENT ---
@Composable
fun AdminServicesTab(
    servicesState: UiState<List<SmmService>>,
    providersState: UiState<List<SmmProviderConfig>>,
    currencySymbol: String,
    isBusy: Boolean,
    onSeedCatalog: () -> Unit,
    onSaveService: (SmmService) -> Unit,
    onToggleServiceActive: (SmmService, Boolean) -> Unit,
    onDeleteService: (String, String) -> Unit
) {
    var editingService by remember { mutableStateOf<SmmService?>(null) }
    var deletingService by remember { mutableStateOf<SmmService?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<ServiceCategory?>(null) }
    var activeFilter by remember { mutableStateOf("all") }

    val configuredProviders = (providersState as? UiState.Success)?.data.orEmpty()

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { editingService = SmmService() },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("admin_add_service")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Service")
                }
                OutlinedButton(
                    onClick = onSeedCatalog,
                    enabled = !isBusy,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("admin_seed_templates")
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .width(16.dp)
                                .height(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.CloudDownload, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Load Templates")
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search services by name, description, or category...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Services") },
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
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text("All Platforms") }
                    )
                }
                items(ServiceCategory.entries) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat.displayName) }
                    )
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("all" to "All", "active" to "Active", "disabled" to "Disabled")) { (key, label) ->
                    FilterChip(
                        selected = activeFilter == key,
                        onClick = { activeFilter = key },
                        label = { Text(label) }
                    )
                }
            }
        }

        when (servicesState) {
            is UiState.Loading -> item { LoadingStateBox("Loading services...") }
            is UiState.Error -> item { ErrorStateCard(servicesState.message) }
            is UiState.Success -> {
                val filtered = servicesState.data.filter { svc ->
                    val matchesCategory = selectedCategory == null || svc.category.equals(selectedCategory!!.key, ignoreCase = true)
                    val matchesActive = when (activeFilter) {
                        "active" -> svc.active
                        "disabled" -> !svc.active
                        else -> true
                    }
                    val q = searchQuery.trim()
                    val matchesQuery = q.isBlank() ||
                        svc.name.contains(q, ignoreCase = true) ||
                        svc.description.contains(q, ignoreCase = true) ||
                        svc.category.contains(q, ignoreCase = true) ||
                        svc.providerServiceId.contains(q, ignoreCase = true)
                    matchesCategory && matchesActive && matchesQuery
                }
                if (servicesState.data.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "No Services Configured",
                            description = "Tap 'Load Templates' to populate categorized DEMO services for Instagram, YouTube, Facebook & Telegram.",
                            actionLabel = if (isBusy) "Loading..." else "Load Template Services",
                            onAction = if (!isBusy) onSeedCatalog else null
                        )
                    }
                } else if (filtered.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "No Matching Services",
                            description = "Try clearing the search query or resetting category/status filters."
                        )
                    }
                } else {
                    items(filtered, key = { it.id }) { svc ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(svc.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        if (svc.description.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = svc.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${ServiceCategory.fromKey(svc.category).displayName} • Rate ${formatCurrency(svc.ratePer1000, currencySymbol)}/1k • Cost ${formatCurrency(svc.providerCostPer1000, currencySymbol)}/1k",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Min: ${svc.minQuantity} • Max: ${svc.maxQuantity} • ETA: ${svc.averageTime}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Switch(
                                            checked = svc.active,
                                            enabled = !isBusy,
                                            onCheckedChange = { onToggleServiceActive(svc, it) }
                                        )
                                        Text(
                                            text = if (svc.active) "Enabled" else "Disabled",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (svc.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    StatusPill(status = if (svc.isDemoService) "demo" else svc.integrationStatus)
                                    Row {
                                        IconButton(onClick = { editingService = svc }) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit Service")
                                        }
                                        IconButton(onClick = { deletingService = svc }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete Service", tint = MaterialTheme.colorScheme.error)
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

    editingService?.let { svc ->
        var name by remember(svc.id) { mutableStateOf(svc.name) }
        var category by remember(svc.id) { mutableStateOf(svc.category) }
        var description by remember(svc.id) { mutableStateOf(svc.description) }
        var rate by remember(svc.id) { mutableStateOf(svc.ratePer1000.toString()) }
        var cost by remember(svc.id) { mutableStateOf(svc.providerCostPer1000.toString()) }
        var minQty by remember(svc.id) { mutableStateOf(svc.minQuantity.toString()) }
        var maxQty by remember(svc.id) { mutableStateOf(svc.maxQuantity.toString()) }
        var averageTime by remember(svc.id) { mutableStateOf(svc.averageTime) }
        var providerId by remember(svc.id) { mutableStateOf(svc.providerId) }
        var providerServiceId by remember(svc.id) { mutableStateOf(svc.providerServiceId) }
        var refill by remember(svc.id) { mutableStateOf(svc.refillSupported) }
        var active by remember(svc.id) { mutableStateOf(svc.active) }

        val minParsed = minQty.toLongOrNull() ?: 0L
        val maxParsed = maxQty.toLongOrNull() ?: 0L
        val isFormValid = name.trim().length >= 2 &&
            minParsed >= 1L &&
            maxParsed >= minParsed &&
            (rate.toDoubleOrNull() ?: -1.0) >= 0.0 &&
            (cost.toDoubleOrNull() ?: -1.0) >= 0.0

        AlertDialog(
            onDismissRequest = { editingService = null },
            title = { Text(if (svc.id.isBlank()) "Add SMM Service" else "Edit Service") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Service Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(ServiceCategory.entries) { cat ->
                            FilterChip(
                                selected = category == cat.key,
                                onClick = { category = cat.key },
                                label = { Text(cat.displayName) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = rate,
                            onValueChange = { rate = it },
                            label = { Text("Rate/1k ($currencySymbol)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = cost,
                            onValueChange = { cost = it },
                            label = { Text("Cost/1k ($currencySymbol)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = minQty,
                            onValueChange = { minQty = it },
                            label = { Text("Min Qty") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = maxQty,
                            onValueChange = { maxQty = it },
                            label = { Text("Max Qty") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = averageTime,
                        onValueChange = { averageTime = it },
                        label = { Text("Average Delivery Time") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Provider Binding", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(
                                selected = providerId == "unconfigured" || providerId.isBlank(),
                                onClick = { providerId = "unconfigured" },
                                label = { Text("DEMO (Unconfigured)") }
                            )
                        }
                        items(configuredProviders, key = { it.id }) { prv ->
                            FilterChip(
                                selected = providerId == prv.id,
                                onClick = { providerId = prv.id },
                                label = { Text(prv.name) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = providerServiceId,
                        onValueChange = { providerServiceId = it },
                        label = { Text("Remote Provider Service ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = refill, onCheckedChange = { refill = it })
                        Text("Refill Supported")
                        Spacer(modifier = Modifier.width(12.dp))
                        Checkbox(checked = active, onCheckedChange = { active = it })
                        Text("Enabled")
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isBusy && isFormValid,
                    onClick = {
                        val selectedProvider = configuredProviders.firstOrNull { it.id == providerId }
                        val isLinked = selectedProvider != null && selectedProvider.active && providerServiceId.isNotBlank()
                        onSaveService(
                            svc.copy(
                                name = name.trim(),
                                category = category,
                                description = description.trim(),
                                ratePer1000 = rate.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 1.0,
                                providerCostPer1000 = cost.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.5,
                                minQuantity = minParsed,
                                maxQuantity = maxParsed,
                                averageTime = averageTime.trim().ifBlank { "1-3 hours" },
                                providerId = providerId.ifBlank { "unconfigured" },
                                providerServiceId = providerServiceId.trim(),
                                integrationStatus = if (isLinked) "provider_linked" else "template_unconnected",
                                refillSupported = refill,
                                active = active
                            )
                        )
                        editingService = null
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingService = null }) { Text("Cancel") }
            }
        )
    }

    deletingService?.let { svc ->
        AlertDialog(
            onDismissRequest = { deletingService = null },
            title = { Text("Delete Service?") },
            text = { Text("Remove '${svc.name}' from the service catalog? Existing orders referencing this service will keep their snapshots.") },
            confirmButton = {
                Button(
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        onDeleteService(svc.id, svc.name)
                        deletingService = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingService = null }) { Text("Cancel") }
            }
        )
    }
}

// --- 4. PRICE MANAGEMENT ---
@Composable
fun AdminPricingTab(
    servicesState: UiState<List<SmmService>>,
    currencySymbol: String,
    isBusy: Boolean,
    onApplyCategoryMarkup: (List<SmmService>, ServiceCategory, Double) -> Unit,
    onUpdateServicePrice: (SmmService) -> Unit
) {
    var selectedCategory by remember { mutableStateOf(ServiceCategory.INSTAGRAM) }
    var markupPercentText by remember { mutableStateOf("40") }
    val servicesList = (servicesState as? UiState.Success)?.data.orEmpty()

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
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Bulk Category Margin & Markup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(ServiceCategory.entries) { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat.displayName) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = markupPercentText,
                        onValueChange = { markupPercentText = it },
                        label = { Text("Profit Markup % over Provider Cost") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            val pct = markupPercentText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 25.0
                            onApplyCategoryMarkup(servicesList, selectedCategory, pct)
                        },
                        enabled = !isBusy && servicesList.any { it.category == selectedCategory.key },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Apply Markup to ${selectedCategory.displayName}")
                    }
                }
            }
        }

        items(servicesList.filter { it.category == selectedCategory.key }, key = { it.id }) { svc ->
            var rateInput by remember(svc.ratePer1000) { mutableStateOf(svc.ratePer1000.toString()) }
            val margin = if (svc.providerCostPer1000 > 0) {
                ((svc.ratePer1000 - svc.providerCostPer1000) / svc.providerCostPer1000) * 100.0
            } else 0.0

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(svc.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Provider Cost: ${formatCurrency(svc.providerCostPer1000, currencySymbol)}/1k • Margin: ${String.format(Locale.US, "%.1f", margin)}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = rateInput,
                            onValueChange = { rateInput = it },
                            label = { Text("Selling Rate/1k") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            enabled = !isBusy,
                            onClick = {
                                val newRate = rateInput.toDoubleOrNull()?.coerceAtLeast(0.0) ?: svc.ratePer1000
                                onUpdateServicePrice(svc.copy(ratePer1000 = newRate))
                            }
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}
