package com.example.ai

import android.util.Log
import com.example.data.model.AppConfig
import com.example.data.model.KnowledgeBaseEntry
import com.example.data.model.SmmOrder
import com.example.data.model.SupportChatMessage
import com.example.data.repository.SupportDefaults
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout

data class SupportAssistantReply(
    val text: String,
    val detectedOrderId: String = "",
    val matchedOrder: SmmOrder? = null,
    val suggestWhatsApp: Boolean = false,
    val escalatedProblem: String = "",
    val blockedForPrivacy: Boolean = false
)

object SupportAssistantEngine {
    private const val TAG = "SupportAI"
    private const val MODEL_NAME = "gemini-3.8-flash"
    private const val GENERATION_TIMEOUT_MS = 60_000L

    const val PRIVATE_INFO_REFUSAL = "Sorry, this information is private and cannot be shared."

    private val adminLoginPattern = Regex(
        pattern = """(?i)\b(admin|owner|administrator|panel\s*owner|एडमिन|ओनर)\b.{0,40}\b(email|e-mail|mail|password|pass|passcode|pin|login|credential|credentials|secret|uid|id|account\s*details|लॉगिन|पासवर्ड|ईमेल|आईडी|डिटेल)\b|\b(email|e-mail|password|pass|login|credential|credentials|लॉगिन|पासवर्ड|ईमेल)\b.{0,30}\b(admin|owner|administrator|एडमिन|ओनर)\b"""
    )

    private val secretKeyPattern = Regex(
        pattern = """(?i)\b(api\s*key|secret\s*key|provider\s*key|provider\s*secret|stripe\s*secret|razorpay\s*secret|webhook\s*secret|firebase\s*secret|firebase\s*key|service\s*account|private\s*key|auth\s*token|bearer\s*token|id\s*token|app\s*check\s*token|smm_provider|google-services)\b|bauddinsk"""
    )

    private val otherCustomerDataPattern = Regex(
        pattern = """(?i)\b(other\s*user|another\s*user|another\s*customer|someone\s*else|all\s*users|other\s*customer|dusre\s*user|दूसरे\s*यूजर|दूसरे\s*कस्टमर)\b.{0,40}\b(order|balance|email|password|data|info|details|phone|whatsapp)\b"""
    )

    private val ordPrefixRegex = Regex("""\b(ord_[a-zA-Z0-9_-]{4,64})\b""", RegexOption.IGNORE_CASE)
    private val contextualOrderRegex = Regex(
        """(?i)(?:order\s*(?:id|no|number|#)?|ऑर्डर\s*(?:आईडी|नंबर)?)\s*[:#\-]?\s*([a-zA-Z0-9_-]{6,64})"""
    )

    private val escalationKeywords = listOf(
        "whatsapp", "admin", "human", "agent", "person", "complaint", "refund",
        "refill", "not received", "not started", "partial", "failed", "delayed",
        "not credited", "balance not", "payment pending", "manual", "verify",
        "शिकायत", "रिफंड", "रिफिल", "एडमिन", "व्हाट्सएप", "बात", "पैसे", "क्रेडिट",
        "baat", "paisa", "paise", "nahi aaya", "nahi mila", "ruka hai", "stuck"
    )

    /**
     * Strictly blocks prompts requesting Admin login details, passwords, API keys,
     * Firebase/backend secrets, or other customers' private information while still
     * allowing normal customer self-service questions (e.g., "how do I reset my password?").
     */
    fun isSensitiveCredentialQuery(query: String): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return false
        return adminLoginPattern.containsMatchIn(trimmed) ||
            secretKeyPattern.containsMatchIn(trimmed) ||
            otherCustomerDataPattern.containsMatchIn(trimmed)
    }

    /**
     * Extracts candidate Order IDs mentioned by the customer.
     */
    fun extractOrderIds(query: String): List<String> {
        val found = linkedSetOf<String>()
        ordPrefixRegex.findAll(query).forEach { match ->
            found.add(match.groupValues[1])
        }
        contextualOrderRegex.findAll(query).forEach { match ->
            val token = match.groupValues[1].trim()
            if (token.lowercase() !in setOf("status", "check", "help", "issue", "problem", "details", "cancel", "pending")) {
                found.add(token)
            }
        }
        return found.toList()
    }

    fun describeOrderStatus(status: String): String {
        return when (status.lowercase()) {
            "awaiting_provider_config" ->
                "Awaiting Provider API Config — Saved in database, waiting for a connected SMM provider API."
            "pending" ->
                "Pending — Received by BINSMM and queued for processing."
            "processing", "in_progress" ->
                "Processing / In Progress — Delivery is actively underway."
            "completed" ->
                "Completed — The order has finished delivering."
            "partial" ->
                "Partial — Delivery stopped before reaching 100%; remaining quantity needs Admin review for a partial wallet refund."
            "cancelled" ->
                "Cancelled — This order was cancelled. If you expected a refund, please contact Admin."
            "failed" ->
                "Failed — Provider could not complete delivery (often due to invalid/private link). Contact Admin on WhatsApp for review."
            "not_started" ->
                "Not Started — Order is still waiting to begin fulfillment. If delayed, ask Admin on WhatsApp to check with the provider."
            "not_received" ->
                "Not Received — Reported as not received on target profile/link. Contact Admin on WhatsApp (+91 6372505724) with your Order ID."
            else -> status.replace("_", " ").replaceFirstChar { it.uppercase() }
        }
    }

    fun shouldSuggestWhatsApp(
        query: String,
        detectedOrderId: String,
        matchedOrder: SmmOrder?,
        aiResponseText: String
    ): Boolean {
        val lowerQ = query.lowercase()
        val lowerReply = aiResponseText.lowercase()
        if (detectedOrderId.isNotBlank() && matchedOrder == null) return true
        if (matchedOrder != null && matchedOrder.status.lowercase() in setOf(
                "failed", "partial", "not_started", "not_received", "cancelled", "awaiting_provider_config"
            )
        ) {
            return true
        }
        if (escalationKeywords.any { lowerQ.contains(it) }) return true
        if (lowerReply.contains("whatsapp") || lowerReply.contains("6372505724") || lowerReply.contains("cannot verify")) {
            return true
        }
        return false
    }

    private fun formatOrderFactBlock(
        detectedOrderIds: List<String>,
        matchedOrder: SmmOrder?
    ): String {
        return when {
            matchedOrder != null -> {
                """
                VERIFIED CUSTOMER ORDER FROM FIRESTORE (belongs to the signed-in user ONLY):
                - Order ID: ${matchedOrder.id}
                - Service Name: ${matchedOrder.serviceName}
                - Platform: ${matchedOrder.category}
                - Quantity: ${matchedOrder.quantity}
                - Charge: $${String.format(java.util.Locale.US, "%.2f", matchedOrder.charge)}
                - Status: ${matchedOrder.status} (${describeOrderStatus(matchedOrder.status)})
                - Created At: ${matchedOrder.createdAt?.toDate()?.toString() ?: "Recently"}
                - Integration / Fulfillment Note: ${matchedOrder.integrationNote}
                - Provider Order Reference Present: ${if (matchedOrder.providerOrderId.isNotBlank()) "Yes (${matchedOrder.providerOrderId})" else "Not assigned yet"}
                (Note: Do NOT invent any delivery time, ETA, refill approval, or refund approval not shown above.)
                """.trimIndent()
            }
            detectedOrderIds.isNotEmpty() -> {
                """
                ORDER VERIFICATION RESULT:
                The customer mentioned Order ID(s): ${detectedOrderIds.joinToString(", ")}.
                NO matching order exists under the signed-in user's account.
                CRITICAL: Do NOT guess or invent an order status, delivery ETA, refill approval, or refund decision.
                Explain clearly that this Order ID could not be verified under their account (it may have a typo or belong to another account), show them how to copy the exact ID from the 'Orders' tab, and invite them to click 'Chat with Admin on WhatsApp' (+91 6372505724).
                """.trimIndent()
            }
            else -> "No specific Order ID was detected in this message."
        }
    }

    private fun buildSystemInstruction(
        kbEntries: List<KnowledgeBaseEntry>,
        appConfig: AppConfig
    ): String {
        val activeKb = kbEntries.filter { it.active }.ifEmpty {
            SupportDefaults.defaultKnowledgeBaseEntries()
        }
        val kbText = activeKb.take(35).joinToString("\n\n") { entry ->
            "[Category: ${entry.category}] ${entry.title}\nQ: ${entry.question}\nA: ${entry.answer}"
        }

        return """
You are the official BINSMM AI Customer Support Assistant for the BINSMM Social Media Marketing (SMM) Panel Android app.

LANGUAGE & TONE:
- Detect the customer's language automatically and respond in the SAME language:
  * English -> natural, friendly, concise English.
  * Hindi (Devanagari) -> polite, clear Hindi (Devanagari).
  * Hinglish (Roman Hindi + English) -> clear, helpful Hinglish.
- Be friendly, calm, step-by-step, and ask relevant follow-up questions when helpful (e.g., ask for Order ID or transaction reference if missing).

STRICT PRIVACY & ANTI-HALLUCINATION RULES (MANDATORY):
1. NEVER share Admin email, Admin password, login credentials, API keys, provider secrets, webhook secrets, Firebase private config, private Admin settings, auth tokens, or other customers' information. If asked for Admin credentials, reply ONLY with: "$PRIVATE_INFO_REFUSAL"
2. NEVER invent or fabricate order status, delivery completion time, refill approvals, or wallet refund approvals.
3. Only state order details that appear in the VERIFIED CUSTOMER ORDER block provided in the prompt.
4. For unverified deposits, failed/partial/not-received orders, refill/refund requests, complaints, or anything requiring Admin intervention, advise tapping the 'Chat with Admin on WhatsApp' button (${SupportDefaults.ADMIN_WHATSAPP_NUMBER}) or creating a Support Ticket.
5. Be 100% honest about DEMO services vs Live provider services: services labeled DEMO cannot process real orders until Admin links a provider API.

CURRENT SYSTEM STATUS:
- Payment Gateway Status: ${appConfig.paymentGatewayStatus}
- SMM Provider API Status: ${appConfig.providerApiStatus}
- Official Admin WhatsApp Support: ${SupportDefaults.ADMIN_WHATSAPP_NUMBER}

APPROVED BINSMM KNOWLEDGE BASE:
$kbText
        """.trimIndent()
    }

    suspend fun generateSupportReply(
        userQuery: String,
        recentHistory: List<SupportChatMessage>,
        knowledgeBase: List<KnowledgeBaseEntry>,
        appConfig: AppConfig,
        findOrderForUser: suspend (String) -> SmmOrder?
    ): SupportAssistantReply {
        val cleanQuery = userQuery.trim()
        if (cleanQuery.isEmpty()) {
            return SupportAssistantReply(
                text = "Please type your question in English, Hindi, or Hinglish so I can help you.",
                suggestWhatsApp = false
            )
        }

        // Step 1: Hard Pre-Inference Privacy Guard
        if (isSensitiveCredentialQuery(cleanQuery)) {
            return SupportAssistantReply(
                text = PRIVATE_INFO_REFUSAL,
                blockedForPrivacy = true,
                suggestWhatsApp = false
            )
        }

        // Step 2: Detect Order IDs & verify ONLY against signed-in customer's own orders
        val detectedIds = extractOrderIds(cleanQuery)
        var matchedOrder: SmmOrder? = null
        var primaryOrderId = detectedIds.firstOrNull().orEmpty()
        for (candidate in detectedIds) {
            val found = findOrderForUser(candidate)
            if (found != null) {
                matchedOrder = found
                primaryOrderId = found.id
                break
            }
        }

        val orderFactBlock = formatOrderFactBlock(detectedIds, matchedOrder)

        val historySnippet = recentHistory.takeLast(8).joinToString("\n") { msg ->
            val speaker = if (msg.role == "user") "Customer" else "BINSMM AI"
            "$speaker: ${msg.text.take(600)}"
        }

        val composedUserTurn = """
RECENT CONVERSATION CONTEXT:
${historySnippet.ifBlank { "(New conversation)" }}

$orderFactBlock

CUSTOMER'S CURRENT MESSAGE:
$cleanQuery
        """.trimIndent()

        // Step 3: Call Firebase AI Logic Gemini model with App Check attestation & 60s timeout
        return try {
            val model = Firebase.ai.generativeModel(
                modelName = MODEL_NAME,
                systemInstruction = content {
                    text(buildSystemInstruction(knowledgeBase, appConfig))
                }
            )
            val response = withTimeout(GENERATION_TIMEOUT_MS) {
                model.generateContent(composedUserTurn)
            }
            val rawText = response.text?.trim().orEmpty()
            if (rawText.isBlank()) {
                val fallback = buildFallbackResponse(cleanQuery, detectedIds, matchedOrder, null)
                SupportAssistantReply(
                    text = fallback,
                    detectedOrderId = primaryOrderId,
                    matchedOrder = matchedOrder,
                    suggestWhatsApp = true,
                    escalatedProblem = cleanQuery.take(300)
                )
            } else {
                // Post-inference safeguard: scrub accidental sensitive leakage just in case
                val safeText = if (rawText.contains("bauddinsk0@gmail.com", ignoreCase = true)) {
                    PRIVATE_INFO_REFUSAL
                } else {
                    rawText
                }
                val suggestWa = shouldSuggestWhatsApp(cleanQuery, primaryOrderId, matchedOrder, safeText)
                SupportAssistantReply(
                    text = safeText,
                    detectedOrderId = primaryOrderId,
                    matchedOrder = matchedOrder,
                    suggestWhatsApp = suggestWa,
                    escalatedProblem = if (suggestWa) cleanQuery.take(300) else ""
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini support generation failed", e)
            val fallback = buildFallbackResponse(cleanQuery, detectedIds, matchedOrder, e.localizedMessage)
            SupportAssistantReply(
                text = fallback,
                detectedOrderId = primaryOrderId,
                matchedOrder = matchedOrder,
                suggestWhatsApp = true,
                escalatedProblem = cleanQuery.take(300)
            )
        }
    }

    internal fun buildFallbackResponse(
        query: String,
        detectedIds: List<String>,
        matchedOrder: SmmOrder?,
        errorDetail: String?
    ): String {
        val sb = StringBuilder()
        if (matchedOrder != null) {
            sb.appendLine("Here is the verified status from your account for Order `${matchedOrder.id}`:")
            sb.appendLine("• Service: ${matchedOrder.serviceName}")
            sb.appendLine("• Quantity: ${matchedOrder.quantity}")
            sb.appendLine("• Status: ${describeOrderStatus(matchedOrder.status)}")
            sb.appendLine()
        } else if (detectedIds.isNotEmpty()) {
            sb.appendLine("I could not verify Order ID `${detectedIds.first()}` under your signed-in account.")
            sb.appendLine("Please double-check the ID in your 'Orders' tab or contact Admin directly on WhatsApp.")
            sb.appendLine()
        }

        val reason = if (!errorDetail.isNullOrBlank()) {
            "AI response service is temporarily unavailable ($errorDetail)."
        } else {
            "AI response service could not generate a reply right now."
        }
        sb.appendLine(reason)
        sb.append("You can create a Support Ticket below or tap 'Chat with Admin on WhatsApp' (${SupportDefaults.ADMIN_WHATSAPP_NUMBER}) for immediate Admin help.")
        return sb.toString()
    }
}
