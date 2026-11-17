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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.KnowledgeBaseEntry
import com.example.data.model.SupportCategory
import com.example.data.model.UiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorStateCard
import com.example.ui.components.LoadingStateBox
import com.example.ui.components.StatusPill

@Composable
fun AdminKnowledgeBaseTab(
    kbState: UiState<List<KnowledgeBaseEntry>>,
    isBusy: Boolean,
    onSeedDefaultKb: () -> Unit,
    onSaveEntry: (KnowledgeBaseEntry) -> Unit,
    onToggleActive: (KnowledgeBaseEntry, Boolean) -> Unit,
    onDeleteEntry: (String, String) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var categoryFilter by remember { mutableStateOf<SupportCategory?>(null) }
    var editingEntry by remember { mutableStateOf<KnowledgeBaseEntry?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    enabled = !isBusy,
                    onClick = { editingEntry = KnowledgeBaseEntry() },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("admin_kb_add_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add KB FAQ")
                }
                OutlinedButton(
                    enabled = !isBusy,
                    onClick = onSeedDefaultKb,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("admin_kb_seed_button")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Load Default KB")
                }
            }
        }

        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search KB title, question, answer, or keywords...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Knowledge Base") },
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
                    .testTag("admin_kb_search")
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    FilterChip(
                        selected = categoryFilter == null,
                        onClick = { categoryFilter = null },
                        label = { Text("All Categories") }
                    )
                }
                items(SupportCategory.entries) { cat ->
                    FilterChip(
                        selected = categoryFilter == cat,
                        onClick = { categoryFilter = cat },
                        label = { Text(cat.displayName) }
                    )
                }
            }
        }

        when (kbState) {
            is UiState.Loading -> item { LoadingStateBox("Loading AI Knowledge Base...") }
            is UiState.Error -> item { ErrorStateCard(kbState.message) }
            is UiState.Success -> {
                val filtered = kbState.data.filter { entry ->
                    val matchesCat = categoryFilter == null || entry.category == categoryFilter!!.key
                    val q = search.trim()
                    val matchesQ = q.isBlank() ||
                        entry.title.contains(q, ignoreCase = true) ||
                        entry.question.contains(q, ignoreCase = true) ||
                        entry.answer.contains(q, ignoreCase = true) ||
                        entry.keywords.contains(q, ignoreCase = true)
                    matchesCat && matchesQ
                }

                if (kbState.data.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "Knowledge Base Is Empty",
                            description = "Tap 'Load Default KB' to seed categories A–F (Account, Order, Wallet, Service, API & General) or add custom FAQs for the AI Assistant.",
                            icon = Icons.Default.MenuBook,
                            actionLabel = "Load Default KB",
                            onAction = onSeedDefaultKb
                        )
                    }
                } else if (filtered.isEmpty()) {
                    item { EmptyStateCard("No Matching Entries", "Adjust search terms or category filter.") }
                } else {
                    items(filtered, key = { it.id }) { entry ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { editingEntry = entry }
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = SupportCategory.fromKey(entry.category).displayName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    StatusPill(status = if (entry.active) "active" else "suspended")
                                }
                                Text(
                                    text = "Q: ${entry.question}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = entry.answer,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Switch(
                                            checked = entry.active,
                                            enabled = !isBusy,
                                            onCheckedChange = { onToggleActive(entry, it) }
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (entry.active) "AI Enabled" else "Hidden", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Row {
                                        IconButton(onClick = { editingEntry = entry }) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit KB Entry")
                                        }
                                        IconButton(
                                            enabled = !isBusy,
                                            onClick = { onDeleteEntry(entry.id, entry.title) }
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete KB Entry",
                                                tint = MaterialTheme.colorScheme.error
                                            )
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

    editingEntry?.let { current ->
        var title by remember(current.id) { mutableStateOf(current.title) }
        var category by remember(current.id) { mutableStateOf(SupportCategory.fromKey(current.category)) }
        var question by remember(current.id) { mutableStateOf(current.question) }
        var answer by remember(current.id) { mutableStateOf(current.answer) }
        var keywords by remember(current.id) { mutableStateOf(current.keywords) }
        var active by remember(current.id) { mutableStateOf(current.active) }

        AlertDialog(
            onDismissRequest = { editingEntry = null },
            title = {
                Text(if (current.id.isBlank()) "Add Knowledge Base Entry" else "Edit Knowledge Base Entry", fontWeight = FontWeight.Bold)
            },
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
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = question,
                        onValueChange = { question = it },
                        label = { Text("Customer Question") },
                        minLines = 2,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = answer,
                        onValueChange = { answer = it },
                        label = { Text("Approved AI Answer") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = keywords,
                        onValueChange = { keywords = it },
                        label = { Text("Search Keywords") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = active, onCheckedChange = { active = it })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Active for AI Assistant", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isBusy && title.trim().length >= 2 && question.trim().length >= 3 && answer.trim().length >= 3,
                    onClick = {
                        onSaveEntry(
                            current.copy(
                                title = title.trim(),
                                category = category.key,
                                question = question.trim(),
                                answer = answer.trim(),
                                keywords = keywords.trim(),
                                active = active
                            )
                        )
                        editingEntry = null
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingEntry = null }) { Text("Cancel") }
            }
        )
    }
}
