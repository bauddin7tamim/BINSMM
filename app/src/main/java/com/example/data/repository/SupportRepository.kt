package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.model.KnowledgeBaseEntry
import com.example.data.model.SmmOrder
import com.example.data.model.SupportChatMessage
import com.example.data.model.SupportTicket
import com.example.data.model.SupportTicketMessage
import com.example.data.model.SupportTicketStatus
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.functions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class SupportRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val functions: FirebaseFunctions = Firebase.functions(BinsmmRepository.FUNCTIONS_REGION)
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = FirebaseAuth.getInstance(),
        functions = Firebase.functions(BinsmmRepository.FUNCTIONS_REGION)
    )

    private fun requireUid(): String = auth.currentUser?.uid
        ?: throw IllegalStateException("Please sign in with Google to use BINSMM Support.")

    private suspend fun isVerifiedAdminUser(): Boolean {
        val user = auth.currentUser ?: return false
        if (!user.isEmailVerified) return false
        if (user.email.equals(BinsmmRepository.BOOTSTRAP_ADMIN_EMAIL, ignoreCase = true)) return true
        return try {
            val snap = db.collection("admins").document(user.uid).get().await()
            snap.exists() && snap.getBoolean("isVerifiedOwnerPin") == true
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun requireAdminUser(): Pair<String, String> {
        val user = auth.currentUser ?: throw IllegalStateException("Administrator authentication required.")
        if (!isVerifiedAdminUser()) {
            throw IllegalStateException("Verified administrator access is required.")
        }
        val email = user.email?.takeIf { it.isNotBlank() } ?: "${user.uid}@binsmm.app"
        return user.uid to email
    }

    private suspend fun logAdminAction(
        action: String,
        targetType: String,
        targetId: String,
        summary: String
    ) {
        val user = auth.currentUser ?: return
        val email = user.email?.takeIf { it.isNotBlank() } ?: "${user.uid}@binsmm.app"
        val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        db.collection("admin_audit_logs").document(logId).set(
            mapOf(
                "adminUid" to user.uid,
                "adminEmail" to email.take(200),
                "action" to action.take(80),
                "targetType" to targetType,
                "targetId" to targetId.ifBlank { "global" }.take(128),
                "summary" to summary.trim().ifBlank { action }.take(500),
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    private fun <T> Flow<T>.handleErrors(op: OperationType, path: String): Flow<T> = catch { err ->
        val fsEx = generateSequence(err) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (fsEx == null && err is CancellationException) throw err
        val ex = fsEx ?: (err as? Exception)
        if (ex != null) handleFirestoreError(ex, op, path, auth)
        throw err
    }

    // --- KNOWLEDGE BASE ---

    fun observeKnowledgeBase(): Flow<List<KnowledgeBaseEntry>> = flow {
        emitAll(
            db.collection("knowledge_base")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        KnowledgeBaseEntry::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(compareBy({ it.category }, { it.title }))
                }
                .handleErrors(OperationType.LIST, "knowledge_base")
        )
    }

    suspend fun adminSaveKnowledgeBaseEntry(entry: KnowledgeBaseEntry): Result<Unit> {
        return runCatching {
            requireAdminUser()
            val cleanTitle = entry.title.trim().take(160)
            val cleanQuestion = entry.question.trim().take(400)
            val cleanAnswer = entry.answer.trim().take(2500)
            require(cleanTitle.length >= 2) { "Title must be at least 2 characters." }
            require(cleanQuestion.length >= 3) { "Question must be at least 3 characters." }
            require(cleanAnswer.length >= 3) { "Answer must be at least 3 characters." }

            val id = entry.id.ifBlank {
                "kb_${entry.category}_${UUID.randomUUID().toString().replace("-", "").take(8)}"
            }
            val isNew = entry.id.isBlank() || entry.createdAt == null
            val docRef = db.collection("knowledge_base").document(id)

            if (isNew) {
                docRef.set(
                    mapOf(
                        "title" to cleanTitle,
                        "category" to entry.category,
                        "question" to cleanQuestion,
                        "answer" to cleanAnswer,
                        "keywords" to entry.keywords.trim().take(400),
                        "active" to entry.active,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                logAdminAction(
                    action = "KB_ENTRY_CREATED",
                    targetType = "knowledge_base",
                    targetId = id,
                    summary = "Added Knowledge Base entry '$cleanTitle' (${entry.category})."
                )
            } else {
                docRef.update(
                    mapOf(
                        "title" to cleanTitle,
                        "category" to entry.category,
                        "question" to cleanQuestion,
                        "answer" to cleanAnswer,
                        "keywords" to entry.keywords.trim().take(400),
                        "active" to entry.active,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                logAdminAction(
                    action = "KB_ENTRY_UPDATED",
                    targetType = "knowledge_base",
                    targetId = id,
                    summary = "Updated Knowledge Base entry '$cleanTitle' (active=${entry.active})."
                )
            }
            Unit
        }.onFailure { e ->
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.WRITE, "knowledge_base", auth)
        }
    }

    suspend fun adminToggleKnowledgeBaseActive(entry: KnowledgeBaseEntry, active: Boolean): Result<Unit> {
        return runCatching {
            requireAdminUser()
            require(entry.id.isNotBlank()) { "Invalid Knowledge Base entry ID." }
            db.collection("knowledge_base").document(entry.id).update(
                mapOf(
                    "active" to active,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAdminAction(
                action = if (active) "KB_ENTRY_ENABLED" else "KB_ENTRY_DISABLED",
                targetType = "knowledge_base",
                targetId = entry.id,
                summary = "${if (active) "Enabled" else "Disabled"} Knowledge Base entry '${entry.title}'."
            )
            Unit
        }.onFailure { e ->
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.UPDATE, "knowledge_base/${entry.id}", auth)
        }
    }

    suspend fun adminDeleteKnowledgeBaseEntry(entryId: String, title: String): Result<Unit> {
        return runCatching {
            requireAdminUser()
            require(entryId.isNotBlank()) { "Invalid Knowledge Base entry ID." }
            db.collection("knowledge_base").document(entryId).delete().await()
            logAdminAction(
                action = "KB_ENTRY_DELETED",
                targetType = "knowledge_base",
                targetId = entryId,
                summary = "Deleted Knowledge Base entry '${title.ifBlank { entryId }}'."
            )
            Unit
        }.onFailure { e ->
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.DELETE, "knowledge_base/$entryId", auth)
        }
    }

    suspend fun adminSeedDefaultKnowledgeBase(): Result<Int> {
        return runCatching {
            requireAdminUser()
            val defaults = SupportDefaults.defaultKnowledgeBaseEntries()
            val existing = db.collection("knowledge_base").get().await()
            val existingIds = existing.documents.map { it.id }.toMutableSet()
            val existingTitles = existing.documents.mapNotNull {
                it.getString("title")?.trim()?.lowercase()
            }.toMutableSet()

            val batch = db.batch()
            var added = 0
            for (entry in defaults) {
                val titleKey = entry.title.trim().lowercase()
                if (entry.id in existingIds || titleKey in existingTitles) continue
                batch.set(
                    db.collection("knowledge_base").document(entry.id),
                    mapOf(
                        "title" to entry.title,
                        "category" to entry.category,
                        "question" to entry.question,
                        "answer" to entry.answer,
                        "keywords" to entry.keywords,
                        "active" to entry.active,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                existingIds.add(entry.id)
                existingTitles.add(titleKey)
                added++
            }
            if (added > 0) {
                batch.commit().await()
                logAdminAction(
                    action = "KB_DEFAULTS_LOADED",
                    targetType = "knowledge_base",
                    targetId = "default_kb",
                    summary = "Loaded $added default BINSMM Knowledge Base FAQ entries across categories A-F."
                )
            }
            added
        }.onFailure { e ->
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.CREATE, "knowledge_base", auth)
        }
    }

    // --- CUSTOMER-SCOPED ORDER CHECKER ---

    suspend fun findSignedInUserOrder(userId: String, candidateOrderId: String): SmmOrder? {
        val clean = candidateOrderId.trim()
        if (clean.isEmpty()) return null
        return try {
            val snap = db.collection("orders")
                .whereEqualTo("userId", userId)
                .get()
                .await()
            snap.toObjects(
                SmmOrder::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).firstOrNull { it.id.equals(clean, ignoreCase = true) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Order lookup failed safely for userId=$userId", e)
            null
        }
    }

    // --- PER-USER AI SUPPORT CHAT HISTORY ---

    fun observeSupportChat(userId: String): Flow<List<SupportChatMessage>> = flow {
        val path = "users/$userId/support_chats"
        emitAll(
            db.collection("users").document(userId).collection("support_chats")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        SupportChatMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .handleErrors(OperationType.LIST, path)
        )
    }

    suspend fun saveSupportChatMessage(
        userId: String,
        role: String,
        text: String,
        detectedOrderId: String = "",
        suggestWhatsApp: Boolean = false,
        escalatedProblem: String = ""
    ) {
        try {
            val msgId = "msg_${System.currentTimeMillis()}_${UUID.randomUUID().toString().replace("-", "").take(6)}"
            db.collection("users").document(userId)
                .collection("support_chats").document(msgId)
                .set(
                    mapOf(
                        "role" to role,
                        "text" to text.take(4000),
                        "detectedOrderId" to detectedOrderId.take(128),
                        "suggestWhatsApp" to suggestWhatsApp,
                        "escalatedProblem" to escalatedProblem.take(400),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "users/$userId/support_chats", auth)
        }
    }

    suspend fun clearSupportChat(userId: String): Result<Unit> {
        return runCatching {
            val snap = db.collection("users").document(userId).collection("support_chats").get().await()
            if (!snap.isEmpty) {
                val batch = db.batch()
                snap.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            Unit
        }.onFailure { e ->
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.DELETE, "users/$userId/support_chats", auth)
        }
    }

    // --- SUPPORT TICKETS ---

    fun observeUserSupportTickets(userId: String): Flow<List<SupportTicket>> = flow {
        emitAll(
            db.collection("support_tickets")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        SupportTicket::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .handleErrors(OperationType.LIST, "support_tickets")
        )
    }

    fun observeAllSupportTickets(): Flow<List<SupportTicket>> = flow {
        emitAll(
            db.collection("support_tickets")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        SupportTicket::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .handleErrors(OperationType.LIST, "support_tickets")
        )
    }

    fun observeTicketMessages(ticketId: String): Flow<List<SupportTicketMessage>> = flow {
        val path = "support_tickets/$ticketId/messages"
        emitAll(
            db.collection("support_tickets").document(ticketId).collection("messages")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        SupportTicketMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .handleErrors(OperationType.LIST, path)
        )
    }

    suspend fun createSupportTicket(
        category: String,
        orderId: String,
        subject: String,
        description: String,
        displayName: String
    ): Result<String> {
        return runCatching {
            val uid = requireUid()
            val cleanSubject = subject.trim().take(160)
            val cleanDesc = description.trim().take(2000)
            require(cleanSubject.length >= 3) { "Subject must be at least 3 characters." }
            require(cleanDesc.length >= 5) { "Description must be at least 5 characters." }

            val userSnap = db.collection("users").document(uid).get().await()
            if (userSnap.getString("accountStatus") == "suspended") {
                throw IllegalStateException("Suspended accounts cannot submit tickets; contact Admin WhatsApp (${SupportDefaults.ADMIN_WHATSAPP_NUMBER}).")
            }

            val email = auth.currentUser?.email?.takeIf { it.isNotBlank() } ?: "$uid@binsmm.app"
            val senderName = displayName.trim().ifBlank { email.substringBefore("@") }.take(100)
            val ticketId = "tkt_${UUID.randomUUID().toString().replace("-", "").take(14)}"
            val ticketNum = "TKT-${ticketId.removePrefix("tkt_").take(6).uppercase()}"

            val batch = db.batch()
            val ticketRef = db.collection("support_tickets").document(ticketId)
            batch.set(
                ticketRef,
                mapOf(
                    "ticketNumber" to ticketNum,
                    "userId" to uid,
                    "userEmail" to email.take(200),
                    "userName" to senderName,
                    "category" to category,
                    "orderId" to orderId.trim().take(128),
                    "subject" to cleanSubject,
                    "description" to cleanDesc,
                    "status" to SupportTicketStatus.OPEN.key,
                    "lastMessagePreview" to cleanDesc.take(300),
                    "lastReplyBy" to "customer",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val firstMsgId = "tmsg_${UUID.randomUUID().toString().replace("-", "").take(12)}"
            batch.set(
                ticketRef.collection("messages").document(firstMsgId),
                mapOf(
                    "senderUid" to uid,
                    "senderRole" to "customer",
                    "senderName" to senderName,
                    "message" to cleanDesc,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            ticketNum
        }.onFailure { e ->
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.CREATE, "support_tickets", auth)
        }
    }

    suspend fun addTicketReply(
        ticket: SupportTicket,
        message: String,
        asAdmin: Boolean
    ): Result<Unit> {
        return runCatching {
            val uid = requireUid()
            val cleanMsg = message.trim().take(2000)
            require(cleanMsg.isNotEmpty()) { "Reply cannot be empty." }
            if (asAdmin) {
                requireAdminUser()
            } else if (ticket.status == SupportTicketStatus.CLOSED.key) {
                throw IllegalStateException("This ticket is closed. Open a new ticket if you need further help.")
            }

            val senderName = if (asAdmin) {
                "BINSMM Admin"
            } else {
                auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: ticket.userName.ifBlank { "Customer" }
            }
            val msgId = "tmsg_${UUID.randomUUID().toString().replace("-", "").take(12)}"
            val ticketRef = db.collection("support_tickets").document(ticket.id)

            val nextStatus = when {
                asAdmin && ticket.status == SupportTicketStatus.OPEN.key -> SupportTicketStatus.IN_PROGRESS.key
                asAdmin -> ticket.status
                else -> SupportTicketStatus.WAITING_FOR_ADMIN.key
            }

            val batch = db.batch()
            batch.set(
                ticketRef.collection("messages").document(msgId),
                mapOf(
                    "senderUid" to uid,
                    "senderRole" to if (asAdmin) "admin" else "customer",
                    "senderName" to senderName.take(100),
                    "message" to cleanMsg,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                ticketRef,
                mapOf(
                    "lastMessagePreview" to cleanMsg.take(300),
                    "lastReplyBy" to if (asAdmin) "admin" else "customer",
                    "status" to nextStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()

            if (asAdmin) {
                val ntfId = "ntf_${UUID.randomUUID().toString().replace("-", "").take(14)}"
                db.collection("users").document(ticket.userId)
                    .collection("notifications").document(ntfId)
                    .set(
                        mapOf(
                            "title" to "Support Reply (${ticket.ticketNumber})",
                            "message" to cleanMsg.take(500),
                            "type" to "system",
                            "read" to false,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                logAdminAction(
                    action = "SUPPORT_TICKET_REPLIED",
                    targetType = "support_ticket",
                    targetId = ticket.id,
                    summary = "Replied to ticket ${ticket.ticketNumber} (${ticket.userEmail})."
                )
            }
            Unit
        }.onFailure { e ->
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.WRITE, "support_tickets/${ticket.id}", auth)
        }
    }

    suspend fun adminUpdateTicketStatus(ticket: SupportTicket, newStatus: String): Result<Unit> {
        return runCatching {
            requireAdminUser()
            require(newStatus in SupportTicketStatus.entries.map { it.key }) { "Invalid ticket status." }
            db.collection("support_tickets").document(ticket.id).update(
                mapOf(
                    "status" to newStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAdminAction(
                action = "SUPPORT_TICKET_STATUS_UPDATED",
                targetType = "support_ticket",
                targetId = ticket.id,
                summary = "Updated ticket ${ticket.ticketNumber} (${ticket.userEmail}) status to $newStatus."
            )
            Unit
        }.onFailure { e ->
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.UPDATE, "support_tickets/${ticket.id}", auth)
        }
    }

    // --- ADMIN SECURITY: EMAIL & PASSWORD MANAGEMENT (NO PASSWORD STORED IN FIRESTORE) ---

    suspend fun isOwnerUidPinned(): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        return try {
            val snap = db.collection("admins").document(uid).get().await()
            snap.exists() && snap.getBoolean("isVerifiedOwnerPin") == true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun syncOwnerAdminUid(): Result<Unit> {
        return runCatching {
            requireAdminUser()
            functions.getHttpsCallable("adminSyncOwnerUid").call().await()
            Unit
        }.recoverCatching { e ->
            if (e is CancellationException) throw e
            val fnEx = generateSequence(e) { it.cause }
                .filterIsInstance<FirebaseFunctionsException>()
                .firstOrNull()
            if (fnEx?.code == FirebaseFunctionsException.Code.NOT_FOUND) {
                throw IllegalStateException(
                    "adminSyncOwnerUid Cloud Function is not deployed yet. Deploy functions/ before changing your Admin email so Admin privileges stay bound to your UID.",
                    fnEx
                )
            }
            throw e
        }
    }

    suspend fun reauthenticateAdminWithGoogle(idToken: String): Result<Unit> {
        return runCatching {
            val user = auth.currentUser ?: throw IllegalStateException("Not signed in.")
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            user.reauthenticate(credential).await()
            Unit
        }
    }

    suspend fun reauthenticateAdminWithPassword(currentPassword: String): Result<Unit> {
        return runCatching {
            val user = auth.currentUser ?: throw IllegalStateException("Not signed in.")
            val email = user.email?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Current account does not have an email address.")
            require(currentPassword.isNotEmpty()) { "Enter your current password to reauthenticate." }
            val credential = EmailAuthProvider.getCredential(email, currentPassword)
            user.reauthenticate(credential).await()
            Unit
        }.recoverCatching { e ->
            if (e is CancellationException) throw e
            throw mapAuthSecurityError(e, "Password reauthentication failed.")
        }
    }

    suspend fun adminRequestEmailChange(newEmail: String): Result<String> {
        return runCatching {
            requireAdminUser()
            val user = auth.currentUser ?: throw IllegalStateException("Not signed in.")
            val trimmed = newEmail.trim()
            require(trimmed.contains("@") && trimmed.contains(".")) {
                "Enter a valid email address."
            }
            require(!trimmed.equals(user.email, ignoreCase = true)) {
                "New email matches your current Admin email."
            }

            // Ensure owner UID is pinned in /admins/{uid} before changing away from bootstrap email
            val alreadyPinned = isOwnerUidPinned()
            if (!alreadyPinned) {
                syncOwnerAdminUid().getOrThrow()
            }

            user.verifyBeforeUpdateEmail(trimmed).await()
            logAdminAction(
                action = "ADMIN_EMAIL_VERIFICATION_SENT",
                targetType = "admin_security",
                targetId = user.uid,
                summary = "Sent Firebase email change verification to $trimmed (owner UID pinned)."
            )
            "Verification email sent to $trimmed. Open the verification link to complete the Admin email update; your pinned Admin UID will preserve panel access."
        }.recoverCatching { e ->
            if (e is CancellationException) throw e
            throw mapAuthSecurityError(e, "Failed to initiate Admin email update.")
        }
    }

    suspend fun adminUpdatePassword(newPassword: String): Result<String> {
        return runCatching {
            requireAdminUser()
            val user = auth.currentUser ?: throw IllegalStateException("Not signed in.")
            require(newPassword.length >= 8) {
                "New password must be at least 8 characters long."
            }
            user.updatePassword(newPassword).await()
            logAdminAction(
                action = "ADMIN_PASSWORD_UPDATED",
                targetType = "admin_security",
                targetId = user.uid,
                summary = "Updated Firebase Auth password for Admin UID ${user.uid}."
            )
            "Admin password updated securely in Firebase Auth (never stored in Firestore)."
        }.recoverCatching { e ->
            if (e is CancellationException) throw e
            throw mapAuthSecurityError(e, "Failed to update Admin password.")
        }
    }

    private fun mapAuthSecurityError(error: Throwable, fallback: String): Throwable {
        if (error is FirebaseAuthRecentLoginRequiredException) {
            return IllegalStateException(
                "For security, Firebase requires recent reauthentication. Tap 'Reauthenticate with Google' (or re-enter current password) and retry.",
                error
            )
        }
        val authEx = error as? FirebaseAuthException
        return when (authEx?.errorCode) {
            "ERROR_REQUIRES_RECENT_LOGIN" -> IllegalStateException(
                "Recent authentication required. Reauthenticate below and try again.",
                authEx
            )
            "ERROR_OPERATION_NOT_ALLOWED" -> IllegalStateException(
                "This action is not enabled in Firebase Authentication Console yet (enable Email/Password provider or email change policy in Firebase Console).",
                authEx
            )
            "ERROR_WEAK_PASSWORD" -> IllegalStateException(
                "Password is too weak. Choose a stronger password (8+ characters with numbers/symbols).",
                authEx
            )
            "ERROR_EMAIL_ALREADY_IN_USE" -> IllegalStateException(
                "That email address is already linked to another Firebase account.",
                authEx
            )
            "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" -> IllegalStateException(
                "Current password is incorrect or this Google account does not have a password linked yet.",
                authEx
            )
            else -> IllegalStateException(error.localizedMessage ?: fallback, error)
        }
    }

    private companion object {
        const val TAG = "SupportRepo"
    }
}
