package com.example.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ServiceCategory
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.CyanInfo
import com.example.ui.theme.DeepRoyalBlue
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.FacebookBlue
import com.example.ui.theme.InstagramRose
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.PureWhite
import com.example.ui.theme.RoseContainer
import com.example.ui.theme.RoseError
import com.example.ui.theme.TelegramSky
import com.example.ui.theme.VividPurple
import com.example.ui.theme.YouTubeRed
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

fun formatCurrency(amount: Double, symbol: String = "$"): String {
    return "$symbol${String.format(Locale.US, "%.2f", amount)}"
}

fun formatTimestamp(ts: Timestamp?): String {
    val date = ts?.toDate() ?: return "Just now"
    return SimpleDateFormat("MMM dd, HH:mm", Locale.US).format(date)
}

fun categoryColor(category: String): Color {
    return when (ServiceCategory.fromKey(category)) {
        ServiceCategory.INSTAGRAM -> InstagramRose
        ServiceCategory.YOUTUBE -> YouTubeRed
        ServiceCategory.FACEBOOK -> FacebookBlue
        ServiceCategory.TELEGRAM -> TelegramSky
    }
}

fun categoryIcon(category: String): ImageVector {
    return when (ServiceCategory.fromKey(category)) {
        ServiceCategory.INSTAGRAM -> Icons.Default.Campaign
        ServiceCategory.YOUTUBE -> Icons.Default.PlayCircleFilled
        ServiceCategory.FACEBOOK -> Icons.Default.Public
        ServiceCategory.TELEGRAM -> Icons.AutoMirrored.Filled.Send
    }
}

fun buildAdminWhatsAppUri(
    phoneNumber: String = "+91 6372505724",
    problem: String = "",
    orderId: String? = null
): Uri {
    val sanitized = phoneNumber.replace(Regex("[^0-9]"), "").ifBlank { "916372505724" }
    val cleanProblem = problem.trim().ifBlank { "General BINSMM support inquiry" }
    val cleanOrder = orderId?.trim().takeIf { !it.isNullOrBlank() } ?: "Not provided"
    val prefilledText = buildString {
        appendLine("Hello Admin, I need help with BINSMM.")
        appendLine()
        appendLine("My Problem: $cleanProblem")
        append("My Order ID: $cleanOrder")
    }
    return Uri.parse("https://wa.me/$sanitized?text=${Uri.encode(prefilledText)}")
}

fun openAdminWhatsAppSupport(
    context: Context,
    phoneNumber: String = "+91 6372505724",
    problem: String = "",
    orderId: String? = null
) {
    val targetUri = buildAdminWhatsAppUri(phoneNumber, problem, orderId)
    val intent = Intent(Intent.ACTION_VIEW, targetUri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(
            context,
            "No browser or WhatsApp app found. Admin WhatsApp: $phoneNumber",
            Toast.LENGTH_LONG
        ).show()
    }
}

fun openWhatsAppSupport(context: Context, phoneNumber: String) {
    openAdminWhatsAppSupport(
        context = context,
        phoneNumber = phoneNumber,
        problem = "I need assistance with my BINSMM account."
    )
}

@Composable
fun StatMetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.14f))
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        maxLines = 1
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = JetBrainsMonoFamily),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun StatusPill(status: String, modifier: Modifier = Modifier) {
    val (bg, fg, label) = when (status.lowercase()) {
        "completed", "active", "connected", "provider_linked", "resolved" ->
            Triple(EmeraldContainer, EmeraldSuccess, status.replace("_", " ").replaceFirstChar { it.uppercase() })
        "pending", "pending_verification", "waiting_for_admin", "not_started" ->
            Triple(AmberContainer, AmberWarning, status.replace("_", " ").replaceFirstChar { it.uppercase() })
        "processing", "in_progress", "open" ->
            Triple(CyanContainer, CyanInfo, status.replace("_", " ").replaceFirstChar { it.uppercase() })
        "partial" ->
            Triple(
                MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.colorScheme.secondary,
                "Partial"
            )
        "awaiting_provider_config" ->
            Triple(
                MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.colorScheme.secondary,
                "Awaiting API"
            )
        "template_unconnected", "demo" ->
            Triple(
                AmberContainer,
                AmberWarning,
                "DEMO"
            )
        "cancelled", "rejected", "suspended", "failed", "not_received", "closed" ->
            Triple(RoseContainer, RoseError, status.replace("_", " ").replaceFirstChar { it.uppercase() })
        else ->
            Triple(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.onSurfaceVariant,
                status.replace("_", " ").replaceFirstChar { it.uppercase() }
            )
    }

    Surface(
        color = bg,
        shape = CircleShape,
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            maxLines = 1
        )
    }
}

@Composable
fun IntegrationNoticeBanner(
    title: String,
    message: String,
    isWarning: Boolean = true,
    modifier: Modifier = Modifier
) {
    val accent = if (isWarning) AmberWarning else MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = accent.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = if (isWarning) Icons.Default.WarningAmber else Icons.Default.Info,
                contentDescription = null,
                tint = accent,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun EmptyStateCard(
    title: String,
    description: String,
    icon: ImageVector = Icons.Default.Inbox,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(DeepRoyalBlue.copy(alpha = 0.15f), VividPurple.copy(alpha = 0.15f)))
                    )
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Button(onClick = onAction, shape = RoundedCornerShape(12.dp)) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun LoadingStateBox(label: String = "Syncing BINSMM data...", modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = true, enter = fadeIn()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = modifier
                .fillMaxWidth()
                .padding(32.dp)
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ErrorStateCard(message: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (actionText != null && onActionClick != null) {
            androidx.compose.material3.TextButton(onClick = onActionClick) {
                Text(actionText, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
