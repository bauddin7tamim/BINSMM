package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class ThemePreference(val label: String) {
    SYSTEM("System Default"),
    LIGHT("Light Mode"),
    DARK("Dark Mode")
}

enum class ServiceCategory(val key: String, val displayName: String) {
    INSTAGRAM("instagram", "Instagram"),
    YOUTUBE("youtube", "YouTube"),
    FACEBOOK("facebook", "Facebook"),
    TELEGRAM("telegram", "Telegram");

    companion object {
        fun fromKey(key: String): ServiceCategory {
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: INSTAGRAM
        }
    }
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val whatsappHandle: String? = null,
    val role: String = "user",
    val accountStatus: String = "active",
    val balance: Double = 0.0,
    val totalOrders: Long = 0L,
    val pendingOrders: Long = 0L,
    val completedOrders: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SmmService(
    @DocumentId val id: String = "",
    val name: String = "",
    val category: String = "instagram",
    val description: String = "",
    val ratePer1000: Double = 0.0,
    val providerCostPer1000: Double = 0.0,
    val minQuantity: Long = 100L,
    val maxQuantity: Long = 10000L,
    val averageTime: String = "1-3 hours",
    val refillSupported: Boolean = false,
    val active: Boolean = true,
    val providerId: String = "unconfigured",
    val providerServiceId: String = "",
    val integrationStatus: String = "template_unconnected",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val isDemoService: Boolean
        get() = integrationStatus != "provider_linked" ||
            providerId.isBlank() ||
            providerId.equals("unconfigured", ignoreCase = true)
}

@IgnoreExtraProperties
data class SmmOrder(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val serviceId: String = "",
    val serviceName: String = "",
    val category: String = "instagram",
    val targetLink: String = "",
    val quantity: Long = 0L,
    val charge: Double = 0.0,
    val status: String = "awaiting_provider_config",
    val providerOrderId: String = "",
    val integrationNote: String = "Saved in database. Awaiting live SMM Provider API integration.",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val isEligibleForCancellation: Boolean
        get() = status !in listOf("completed", "cancelled", "partial", "failed")
}

@IgnoreExtraProperties
data class WalletTransaction(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val type: String = "deposit_request",
    val amount: Double = 0.0,
    val gateway: String = "manual_request",
    val referenceNote: String = "",
    val status: String = "pending_verification",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UserNotification(
    @DocumentId val id: String = "",
    val title: String = "",
    val message: String = "",
    val type: String = "system",
    val read: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SmmProviderConfig(
    @DocumentId val id: String = "",
    val name: String = "",
    val baseUrl: String = "",
    val apiKeyMasked: String = "",
    val apiKeyConfigured: Boolean = false,
    val active: Boolean = false,
    val lastBalance: String = "Not tested",
    val lastSyncStatus: String = "Not configured",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AppConfig(
    @DocumentId val id: String = "general",
    val whatsappSupportNumber: String = "+91 6372505724",
    val supportEmail: String = "support@binsmm.app",
    val paymentGatewayStatus: String = "Not Configured (Manual Verification Queue Active)",
    val providerApiStatus: String = "Not Configured (Connect SMM v2 API in Admin)",
    val announcement: String = "Welcome to BINSMM! Configure Payment Gateway & SMM Provider API endpoints in Admin Panel to activate live fulfillment.",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AdminAuditLog(
    @DocumentId val id: String = "",
    val adminUid: String = "",
    val adminEmail: String = "",
    val action: String = "",
    val targetType: String = "",
    val targetId: String = "",
    val summary: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

enum class SupportCategory(val key: String, val displayName: String) {
    ACCOUNT("account", "Account Support"),
    ORDER("order", "Order Support"),
    WALLET("wallet", "Wallet & Payment"),
    SERVICE("service", "Service Support"),
    API_RESELLER("api_reseller", "API & Reseller"),
    GENERAL("general", "General Support");

    companion object {
        fun fromKey(key: String): SupportCategory {
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: GENERAL
        }
    }
}

enum class SupportTicketStatus(val key: String, val displayName: String) {
    OPEN("open", "Open"),
    IN_PROGRESS("in_progress", "In Progress"),
    WAITING_FOR_ADMIN("waiting_for_admin", "Waiting for Admin"),
    RESOLVED("resolved", "Resolved"),
    CLOSED("closed", "Closed");

    companion object {
        fun fromKey(key: String): SupportTicketStatus {
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: OPEN
        }
    }
}

@IgnoreExtraProperties
data class KnowledgeBaseEntry(
    @DocumentId val id: String = "",
    val title: String = "",
    val category: String = SupportCategory.GENERAL.key,
    val question: String = "",
    val answer: String = "",
    val keywords: String = "",
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SupportTicket(
    @DocumentId val id: String = "",
    val ticketNumber: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val userName: String = "",
    val category: String = SupportCategory.GENERAL.key,
    val orderId: String = "",
    val subject: String = "",
    val description: String = "",
    val status: String = SupportTicketStatus.OPEN.key,
    val lastMessagePreview: String = "",
    val lastReplyBy: String = "customer",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SupportTicketMessage(
    @DocumentId val id: String = "",
    val senderUid: String = "",
    val senderRole: String = "customer",
    val senderName: String = "",
    val message: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SupportChatMessage(
    @DocumentId val id: String = "",
    val role: String = "user",
    val text: String = "",
    val detectedOrderId: String = "",
    val suggestWhatsApp: Boolean = false,
    val escalatedProblem: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class LocalAppSettings(
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val notificationsEnabled: Boolean = true,
    val defaultCategory: ServiceCategory = ServiceCategory.INSTAGRAM,
    val currencySymbol: String = "$",
    val compactOrderCards: Boolean = false
)
