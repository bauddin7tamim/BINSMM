package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AdminAuditLog
import com.example.data.model.AppConfig
import com.example.data.model.ServiceCategory
import com.example.data.model.SmmOrder
import com.example.data.model.SmmProviderConfig
import com.example.data.model.SmmService
import com.example.data.model.UserNotification
import com.example.data.model.UserProfile
import com.example.data.model.WalletTransaction
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import java.util.Locale
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.functions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

class BinsmmRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val functions: FirebaseFunctions = Firebase.functions(FUNCTIONS_REGION)
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = FirebaseAuth.getInstance(),
        functions = Firebase.functions(FUNCTIONS_REGION)
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing BINSMM data.")
    }

    private fun <T> Flow<T>.logAndRethrow(
        op: OperationType,
        path: String,
        expectedUid: String? = null
    ): Flow<T> = catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && (expectedUid == null || auth.currentUser?.uid == expectedUid)) {
            handleFirestoreError(exceptionToLog, op, path, auth)
        }
        throw error
    }

    private fun mapFunctionsError(error: Throwable, fallback: String): Throwable {
        val fnEx = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFunctionsException>()
            .firstOrNull()
        return when (fnEx?.code) {
            FirebaseFunctionsException.Code.NOT_FOUND -> IllegalStateException(
                "Cloud Functions backend is not deployed to $FUNCTIONS_REGION yet. Deploy functions/ via Firebase CLI to process this action.",
                fnEx
            )
            FirebaseFunctionsException.Code.UNAVAILABLE -> IllegalStateException(
                "Cloud Functions backend unavailable. Check network connectivity or emulator status.",
                fnEx
            )
            null -> error
            else -> IllegalStateException(fnEx.message?.takeIf { it.isNotBlank() } ?: fallback, fnEx)
        }
    }

    // --- USER PROFILE & REGISTRATION ---

    suspend fun ensureUserRegistered(whatsappHandle: String? = null): Result<UserProfile> {
        return runCatching {
            val user = auth.currentUser
                ?: throw IllegalStateException("User must be signed in before registering.")
            val docRef = db.collection("users").document(user.uid)
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                val rawProfile = snapshot.toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: UserProfile(userId = user.uid, email = user.email.orEmpty())
                val isVerifiedOwner = checkIsAdmin()
                val existingProfile = if (!isVerifiedOwner && rawProfile.role != "user") {
                    rawProfile.copy(role = "user")
                } else {
                    rawProfile
                }
                if (!whatsappHandle.isNullOrBlank() && existingProfile.whatsappHandle != whatsappHandle && existingProfile.accountStatus == "active") {
                    val updateMap = mapOf(
                        "whatsappHandle" to whatsappHandle.trim(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    docRef.update(updateMap).await()
                    return@runCatching existingProfile.copy(whatsappHandle = whatsappHandle.trim())
                }
                existingProfile
            } else {
                val displayName = user.displayName?.takeIf { it.isNotBlank() }
                    ?: user.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
                    ?: "BINSMM Member"
                val email = user.email?.takeIf { it.isNotBlank() } ?: "${user.uid}@binsmm.app"
                val payload = mutableMapOf<String, Any>(
                    "userId" to user.uid,
                    "displayName" to displayName.take(100),
                    "email" to email.take(200),
                    "role" to "user",
                    "accountStatus" to "active",
                    "balance" to 0.0,
                    "totalOrders" to 0L,
                    "pendingOrders" to 0L,
                    "completedOrders" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                user.photoUrl?.toString()?.takeIf { it.isNotBlank() }?.let {
                    payload["photoUrl"] = it.take(500)
                }
                whatsappHandle?.trim()?.takeIf { it.isNotBlank() }?.let {
                    payload["whatsappHandle"] = it.take(40)
                }
                docRef.set(payload).await()

                // Send welcome notification
                createNotification(
                    userId = user.uid,
                    title = "Welcome to BINSMM",
                    message = "Your account is active. Configure payment & SMM provider APIs in Admin to activate live fulfillment.",
                    type = "system"
                )
                UserProfile(
                    id = user.uid,
                    userId = user.uid,
                    displayName = displayName,
                    email = email,
                    photoUrl = user.photoUrl?.toString(),
                    whatsappHandle = whatsappHandle,
                    role = "user",
                    accountStatus = "active",
                    balance = 0.0
                )
            }
        }.onFailure { e ->
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.WRITE, "users/${auth.currentUser?.uid}", auth)
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) null
                    else snapshot.toObject(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .logAndRethrow(OperationType.GET, path, userId)
        )
    }

    suspend fun updateUserContactProfile(displayName: String, whatsappHandle: String?): Result<Unit> {
        return runCatching {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            val updates = mutableMapOf<String, Any>(
                "displayName" to displayName.trim().take(100),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!whatsappHandle.isNullOrBlank()) {
                updates["whatsappHandle"] = whatsappHandle.trim().take(40)
            }
            docRef.update(updates).await()
            Unit
        }.onFailure { e ->
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.UPDATE, "users/${auth.currentUser?.uid}", auth)
        }
    }

    suspend fun checkIsAdmin(): Boolean {
        val user = auth.currentUser ?: return false
        if (!user.isEmailVerified) return false
        if (user.email.equals(BOOTSTRAP_ADMIN_EMAIL, ignoreCase = true)) {
            return true
        }
        return try {
            val snap = db.collection("admins").document(user.uid).get().await()
            snap.exists() && snap.getBoolean("isVerifiedOwnerPin") == true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun requireAdminUser(): Pair<String, String> {
        val user = auth.currentUser
            ?: throw IllegalStateException("Administrator authentication required.")
        if (!checkIsAdmin()) {
            throw IllegalStateException("Administrator privileges required for this action.")
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
        val payload = mapOf(
            "adminUid" to user.uid,
            "adminEmail" to email.take(200),
            "action" to action.take(80),
            "targetType" to targetType,
            "targetId" to targetId.ifBlank { "global" }.take(128),
            "summary" to summary.trim().ifBlank { action }.take(500),
            "createdAt" to FieldValue.serverTimestamp()
        )
        db.collection("admin_audit_logs").document(logId).set(payload).await()
    }

    // --- SERVICES CATALOG ---

    fun observeServices(): Flow<List<SmmService>> = flow {
        val path = "services"
        emitAll(
            db.collection("services")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SmmService::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(compareBy({ it.category }, { it.ratePer1000 }))
                }
                .logAndRethrow(OperationType.LIST, path)
        )
    }

    private fun normalizeServiceKey(category: String, name: String): String {
        val cleaned = name
            .replace(Regex("\\[\\s*demo\\s*]", RegexOption.IGNORE_CASE), "")
            .trim()
            .lowercase()
            .replace(Regex("\\s+"), " ")
        return "${category.trim().lowercase()}::$cleaned"
    }

    suspend fun seedTemplateCatalog(): Result<Int> {
        return runCatching {
            requireAdminUser()
            val templates = defaultTemplateServices()
            val existingSnap = db.collection("services").get().await()
            val existingIds = existingSnap.documents.map { it.id }.toMutableSet()
            val existingKeys = existingSnap.documents.mapNotNull { doc ->
                val cat = doc.getString("category") ?: return@mapNotNull null
                val name = doc.getString("name") ?: return@mapNotNull null
                normalizeServiceKey(cat, name)
            }.toMutableSet()

            val batch = db.batch()
            var createdCount = 0
            for (svc in templates) {
                val key = normalizeServiceKey(svc.category, svc.name)
                if (svc.id in existingIds || key in existingKeys) {
                    continue
                }
                val docRef = db.collection("services").document(svc.id)
                val payload = mapOf(
                    "name" to svc.name,
                    "category" to svc.category,
                    "description" to svc.description,
                    "ratePer1000" to svc.ratePer1000,
                    "providerCostPer1000" to svc.providerCostPer1000,
                    "minQuantity" to svc.minQuantity,
                    "maxQuantity" to svc.maxQuantity,
                    "averageTime" to svc.averageTime,
                    "refillSupported" to svc.refillSupported,
                    "active" to svc.active,
                    "providerId" to svc.providerId,
                    "providerServiceId" to svc.providerServiceId,
                    "integrationStatus" to svc.integrationStatus,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(docRef, payload)
                existingIds.add(svc.id)
                existingKeys.add(key)
                createdCount++
            }
            if (createdCount > 0) {
                batch.commit().await()
                logAdminAction(
                    action = "TEMPLATES_LOADED",
                    targetType = "catalog",
                    targetId = "demo_templates",
                    summary = "Loaded $createdCount DEMO template services across Instagram, YouTube, Facebook & Telegram."
                )
            }
            createdCount
        }.onFailure { e ->
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.CREATE, "services", auth)
        }
    }

    // --- ORDERS ---

    fun observeUserOrders(userId: String): Flow<List<SmmOrder>> = flow {
        val path = "orders"
        emitAll(
            db.collection("orders")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SmmOrder::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logAndRethrow(OperationType.LIST, path, userId)
        )
    }

    suspend fun placeOrder(
        service: SmmService,
        targetLink: String,
        quantity: Long,
        userProfile: UserProfile?,
        idempotencyKey: String = UUID.randomUUID().toString().replace("-", "")
    ): Result<String> {
        return runCatching {
            requireUserId()
            require(service.id.isNotBlank()) { "Please select a valid service." }
            require(targetLink.trim().length >= 5) { "Enter a valid target link or username." }

            if (service.isDemoService) {
                throw IllegalStateException(
                    "DEMO service cannot process real orders. Connect a live SMM Provider API in Admin to activate this service."
                )
            }
            if (userProfile?.accountStatus == "suspended") {
                throw IllegalStateException(
                    "Your account is suspended. Ordering is disabled; contact WhatsApp support."
                )
            }
            require(quantity in service.minQuantity..service.maxQuantity) {
                "Quantity must be between ${service.minQuantity} and ${service.maxQuantity}."
            }

            val payload = hashMapOf<String, Any>(
                "serviceId" to service.id,
                "targetLink" to targetLink.trim().take(500),
                "quantity" to quantity,
                "idempotencyKey" to idempotencyKey
            )
            val result = functions
                .getHttpsCallable("createSmmOrder")
                .call(payload)
                .await()

            val data = result.getData() as? Map<*, *>
            val orderId = data?.get("orderId")?.toString().orEmpty()
            check(orderId.isNotBlank()) { "Server did not return a valid order ID." }
            orderId
        }.recoverCatching { e ->
            if (e is CancellationException) throw e
            throw mapFunctionsError(e, "Failed to create order via Cloud Functions.")
        }
    }

    suspend fun cancelOrder(order: SmmOrder): Result<Unit> {
        return runCatching {
            requireUserId()
            require(order.id.isNotBlank()) { "Invalid order ID" }
            functions
                .getHttpsCallable("cancelSmmOrder")
                .call(hashMapOf("orderId" to order.id))
                .await()
            Unit
        }.recoverCatching { e ->
            if (e is CancellationException) throw e
            throw mapFunctionsError(e, "Could not cancel order.")
        }
    }

    // --- WALLET & TRANSACTIONS ---

    fun observeUserTransactions(userId: String): Flow<List<WalletTransaction>> = flow {
        val path = "transactions"
        emitAll(
            db.collection("transactions")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        WalletTransaction::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logAndRethrow(OperationType.LIST, path, userId)
        )
    }

    suspend fun submitDepositRequest(
        amount: Double,
        gateway: String,
        referenceNote: String
    ): Result<String> {
        return runCatching {
            val uid = requireUserId()
            val userSnap = db.collection("users").document(uid).get().await()
            if (!userSnap.exists()) {
                throw IllegalStateException("User profile not found. Please wait for account sync to finish.")
            }
            if (userSnap.getString("accountStatus") == "suspended") {
                throw IllegalStateException("Your account is suspended. Deposit requests are disabled; contact WhatsApp support.")
            }
            require(amount > 0.0 && amount <= 100000.0) { "Enter a valid amount between $1 and $100,000." }
            require(referenceNote.trim().length >= 3) { "Enter a transaction reference or note." }
            val email = auth.currentUser?.email ?: "${uid}@binsmm.app"
            val txId = "dep_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            val payload = mapOf(
                "userId" to uid,
                "userEmail" to email.take(200),
                "type" to "deposit_request",
                "amount" to amount,
                "gateway" to gateway.take(80),
                "referenceNote" to referenceNote.trim().take(300),
                "status" to "pending_verification",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("transactions").document(txId).set(payload).await()
            createNotification(
                userId = uid,
                title = "Deposit Request Submitted",
                message = "Manual deposit request for $${String.format("%.2f", amount)} is pending Admin verification.",
                type = "wallet"
            )
            txId
        }.onFailure { e ->
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.CREATE, "transactions", auth)
        }
    }

    // --- NOTIFICATIONS ---

    fun observeNotifications(userId: String): Flow<List<UserNotification>> = flow {
        val path = "users/$userId/notifications"
        emitAll(
            db.collection("users").document(userId).collection("notifications")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        UserNotification::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logAndRethrow(OperationType.LIST, path, userId)
        )
    }

    suspend fun createNotification(
        userId: String,
        title: String,
        message: String,
        type: String = "system"
    ) {
        try {
            val notifId = "ntf_${UUID.randomUUID().toString().replace("-", "").take(14)}"
            val payload = mapOf(
                "title" to title.take(120),
                "message" to message.take(500),
                "type" to type,
                "read" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("users").document(userId)
                .collection("notifications").document(notifId)
                .set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "users/$userId/notifications", auth)
        }
    }

    suspend fun markNotificationRead(userId: String, notificationId: String) {
        try {
            db.collection("users").document(userId)
                .collection("notifications").document(notificationId)
                .update(mapOf("read" to true, "updatedAt" to FieldValue.serverTimestamp()))
                .await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$userId/notifications/$notificationId", auth)
        }
    }

    suspend fun markAllNotificationsRead(userId: String, notifications: List<UserNotification>) {
        notifications.filter { !it.read && it.id.isNotBlank() }.forEach { notif ->
            markNotificationRead(userId, notif.id)
        }
    }

    // --- APP CONFIG ---

    fun observeAppConfig(): Flow<AppConfig> = flow {
        val path = "app_config/general"
        emitAll(
            db.collection("app_config").document("general")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        AppConfig::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ) ?: AppConfig()
                }
                .logAndRethrow(OperationType.GET, path)
        )
    }

    // --- ADMIN OPERATIONS ---

    fun observeAllUsers(): Flow<List<UserProfile>> = flow {
        emitAll(
            db.collection("users")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logAndRethrow(OperationType.LIST, "users")
        )
    }

    fun observeAllOrders(): Flow<List<SmmOrder>> = flow {
        emitAll(
            db.collection("orders")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SmmOrder::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logAndRethrow(OperationType.LIST, "orders")
        )
    }

    fun observeAllTransactions(): Flow<List<WalletTransaction>> = flow {
        emitAll(
            db.collection("transactions")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        WalletTransaction::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logAndRethrow(OperationType.LIST, "transactions")
        )
    }

    fun observeProviders(): Flow<List<SmmProviderConfig>> = flow {
        emitAll(
            db.collection("providers")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SmmProviderConfig::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: Timestamp(0, 0) }
                }
                .logAndRethrow(OperationType.LIST, "providers")
        )
    }

    fun observeAdminAuditLogs(): Flow<List<AdminAuditLog>> = flow {
        emitAll(
            db.collection("admin_audit_logs")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AdminAuditLog::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logAndRethrow(OperationType.LIST, "admin_audit_logs")
        )
    }

    suspend fun adminSaveService(service: SmmService): Result<Unit> {
        return runCatching {
            requireAdminUser()
            val id = service.id.ifBlank {
                "svc_${service.category}_${UUID.randomUUID().toString().replace("-", "").take(8)}"
            }
            val docRef = db.collection("services").document(id)
            val isNew = service.id.isBlank() || service.createdAt == null
            val safeName = service.name.trim().take(160)
            require(safeName.length >= 2) { "Service name must be at least 2 characters." }
            require(service.minQuantity >= 1L && service.maxQuantity >= service.minQuantity) {
                "Invalid quantity bounds: Max quantity must be >= Min quantity."
            }
            if (isNew) {
                val payload = mapOf(
                    "name" to safeName,
                    "category" to service.category,
                    "description" to service.description.trim().take(600),
                    "ratePer1000" to service.ratePer1000.coerceAtLeast(0.0),
                    "providerCostPer1000" to service.providerCostPer1000.coerceAtLeast(0.0),
                    "minQuantity" to service.minQuantity,
                    "maxQuantity" to service.maxQuantity,
                    "averageTime" to service.averageTime.trim().ifBlank { "1-3 hours" }.take(80),
                    "refillSupported" to service.refillSupported,
                    "active" to service.active,
                    "providerId" to service.providerId.trim().ifBlank { "unconfigured" }.take(128),
                    "providerServiceId" to service.providerServiceId.trim().take(128),
                    "integrationStatus" to service.integrationStatus,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
                logAdminAction(
                    action = "SERVICE_CREATED",
                    targetType = "service",
                    targetId = id,
                    summary = "Created ${service.category} service '$safeName' ($${String.format(Locale.US, "%.2f", service.ratePer1000)}/1k)."
                )
            } else {
                val updates = mapOf(
                    "name" to safeName,
                    "category" to service.category,
                    "description" to service.description.trim().take(600),
                    "ratePer1000" to service.ratePer1000.coerceAtLeast(0.0),
                    "providerCostPer1000" to service.providerCostPer1000.coerceAtLeast(0.0),
                    "minQuantity" to service.minQuantity,
                    "maxQuantity" to service.maxQuantity,
                    "averageTime" to service.averageTime.trim().ifBlank { "1-3 hours" }.take(80),
                    "refillSupported" to service.refillSupported,
                    "active" to service.active,
                    "providerId" to service.providerId.trim().ifBlank { "unconfigured" }.take(128),
                    "providerServiceId" to service.providerServiceId.trim().take(128),
                    "integrationStatus" to service.integrationStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
                logAdminAction(
                    action = "SERVICE_UPDATED",
                    targetType = "service",
                    targetId = id,
                    summary = "Updated ${service.category} service '$safeName' (active=${service.active}, rate=$${String.format(Locale.US, "%.2f", service.ratePer1000)}/1k)."
                )
            }
            Unit
        }.onFailure { e ->
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.WRITE, "services", auth)
        }
    }

    suspend fun adminToggleServiceActive(service: SmmService, active: Boolean): Result<Unit> {
        return runCatching {
            requireAdminUser()
            require(service.id.isNotBlank()) { "Invalid service ID." }
            db.collection("services").document(service.id).update(
                mapOf(
                    "active" to active,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAdminAction(
                action = if (active) "SERVICE_ENABLED" else "SERVICE_DISABLED",
                targetType = "service",
                targetId = service.id,
                summary = "${if (active) "Enabled" else "Disabled"} service '${service.name}'."
            )
            Unit
        }.onFailure { e ->
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.UPDATE, "services/${service.id}", auth)
        }
    }

    suspend fun adminDeleteService(serviceId: String, serviceName: String = ""): Result<Unit> {
        return runCatching {
            requireAdminUser()
            require(serviceId.isNotBlank()) { "Invalid service ID." }
            db.collection("services").document(serviceId).delete().await()
            logAdminAction(
                action = "SERVICE_DELETED",
                targetType = "service",
                targetId = serviceId,
                summary = "Deleted service ${serviceName.ifBlank { serviceId }}."
            )
            Unit
        }.onFailure { e ->
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.DELETE, "services/$serviceId", auth)
        }
    }

    suspend fun adminUpdateOrderStatus(
        order: SmmOrder,
        newStatus: String,
        providerOrderId: String
    ): Result<Unit> {
        return runCatching {
            requireAdminUser()
            require(order.id.isNotBlank()) { "Invalid order ID." }
            functions
                .getHttpsCallable("adminUpdateOrderStatus")
                .call(
                    hashMapOf(
                        "orderId" to order.id,
                        "newStatus" to newStatus,
                        "providerOrderId" to providerOrderId.trim().take(128)
                    )
                )
                .await()
            Unit
        }.recoverCatching { e ->
            if (e is CancellationException) throw e
            throw mapFunctionsError(e, "Failed to update order status.")
        }
    }

    suspend fun adminCancelAndRefundOrder(
        order: SmmOrder,
        refundToWallet: Boolean,
        reason: String
    ): Result<Double> {
        return runCatching {
            requireAdminUser()
            require(order.id.isNotBlank()) { "Invalid order ID." }
            val result = functions
                .getHttpsCallable("adminCancelAndRefundOrder")
                .call(
                    hashMapOf(
                        "orderId" to order.id,
                        "refundToWallet" to refundToWallet,
                        "reason" to reason.trim()
                    )
                )
                .await()
            val data = result.getData() as? Map<*, *>
            (data?.get("refundedAmount") as? Number)?.toDouble() ?: 0.0
        }.recoverCatching { e ->
            if (e is CancellationException) throw e
            throw mapFunctionsError(e, "Order cancellation/refund failed.")
        }
    }

    suspend fun adminUpdateUserAccount(
        user: UserProfile,
        newStatus: String,
        newBalance: Double,
        adjustmentNote: String
    ): Result<Unit> {
        return runCatching {
            requireAdminUser()
            require(newStatus in listOf("active", "suspended")) { "Invalid account status." }
            val safeBalance = newBalance.coerceAtLeast(0.0)
            functions
                .getHttpsCallable("adminAdjustUserAccount")
                .call(
                    hashMapOf(
                        "targetUserId" to user.userId,
                        "newStatus" to newStatus,
                        "newBalance" to safeBalance,
                        "adjustmentNote" to adjustmentNote.trim()
                    )
                )
                .await()
            Unit
        }.recoverCatching { e ->
            if (e is CancellationException) throw e
            throw mapFunctionsError(e, "Failed to update user account.")
        }
    }

    suspend fun adminProcessTransaction(
        tx: WalletTransaction,
        approve: Boolean,
        reasonNote: String = ""
    ): Result<Unit> {
        return runCatching {
            requireAdminUser()
            require(tx.id.isNotBlank()) { "Invalid transaction ID." }
            functions
                .getHttpsCallable("adminProcessDeposit")
                .call(
                    hashMapOf(
                        "transactionId" to tx.id,
                        "approve" to approve,
                        "reasonNote" to reasonNote.trim()
                    )
                )
                .await()
            Unit
        }.recoverCatching { e ->
            if (e is CancellationException) throw e
            throw mapFunctionsError(e, "Failed to process deposit request.")
        }
    }

    suspend fun adminTestProviderConnection(
        existing: SmmProviderConfig?,
        name: String,
        baseUrl: String,
        transientApiKey: String,
        active: Boolean,
        runLiveTest: Boolean
    ): Result<String> {
        return runCatching {
            requireAdminUser()
            val cleanName = name.trim().take(120)
            require(cleanName.isNotEmpty()) { "Provider name is required." }

            val payload = hashMapOf<String, Any>(
                "name" to cleanName,
                "baseUrl" to baseUrl.trim().take(300),
                "transientApiKey" to transientApiKey.trim(),
                "active" to active,
                "runLiveTest" to runLiveTest
            )
            existing?.id?.takeIf { it.isNotBlank() }?.let { payload["providerId"] = it }

            val result = functions
                .getHttpsCallable("adminTestProviderConnection")
                .call(payload)
                .await()
            val data = result.getData() as? Map<*, *>
            data?.get("lastSyncStatus")?.toString()?.takeIf { it.isNotBlank() }
                ?: "Saved provider configuration"
        }.recoverCatching { e ->
            if (e is CancellationException) throw e
            throw mapFunctionsError(e, "Failed to save or test provider connection.")
        }
    }

    suspend fun adminDeleteProvider(providerId: String, providerName: String): Result<Unit> {
        return runCatching {
            requireAdminUser()
            require(providerId.isNotBlank()) { "Invalid provider ID." }
            db.collection("providers").document(providerId).delete().await()
            logAdminAction(
                action = "PROVIDER_DELETED",
                targetType = "provider",
                targetId = providerId,
                summary = "Deleted provider configuration '${providerName.ifBlank { providerId }}'."
            )
            Unit
        }.onFailure { e ->
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.DELETE, "providers/$providerId", auth)
        }
    }

    suspend fun adminApplyCategoryMarkup(
        services: List<SmmService>,
        category: ServiceCategory,
        markupPercent: Double
    ): Result<Int> {
        return runCatching {
            requireAdminUser()
            var updated = 0
            services.filter { it.category == category.key }.forEach { svc ->
                val baseCost = if (svc.providerCostPer1000 > 0.0) svc.providerCostPer1000 else svc.ratePer1000
                val newRate = (baseCost * (1.0 + markupPercent / 100.0)).coerceAtLeast(0.01)
                val rounded = kotlin.math.round(newRate * 100.0) / 100.0
                db.collection("services").document(svc.id).update(
                    mapOf(
                        "ratePer1000" to rounded,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                updated++
            }
            logAdminAction(
                action = "PRICING_MARKUP_APPLIED",
                targetType = "pricing",
                targetId = category.key,
                summary = "Applied ${markupPercent}% markup across $updated ${category.displayName} services."
            )
            updated
        }.onFailure { e ->
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.UPDATE, "services", auth)
        }
    }

    suspend fun adminSaveAppConfig(config: AppConfig): Result<Unit> {
        return runCatching {
            requireAdminUser()
            db.collection("app_config").document("general").set(
                mapOf(
                    "whatsappSupportNumber" to config.whatsappSupportNumber.trim().take(32),
                    "supportEmail" to config.supportEmail.trim().take(200),
                    "paymentGatewayStatus" to config.paymentGatewayStatus.trim().take(120),
                    "providerApiStatus" to config.providerApiStatus.trim().take(120),
                    "announcement" to config.announcement.trim().take(500),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
            logAdminAction(
                action = "APP_CONFIG_UPDATED",
                targetType = "config",
                targetId = "general",
                summary = "Updated panel config (gateway='${config.paymentGatewayStatus}', provider='${config.providerApiStatus}')."
            )
            Unit
        }.onFailure { e ->
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.WRITE, "app_config/general", auth)
        }
    }

    companion object {
        const val BOOTSTRAP_ADMIN_EMAIL = "bauddinsk0@gmail.com"
        const val FUNCTIONS_REGION = "us-central1"

        fun defaultTemplateServices(): List<SmmService> = listOf(
            // --- INSTAGRAM ---
            SmmService(
                id = "svc_ig_followers",
                name = "Instagram Followers [30D Refill]",
                category = ServiceCategory.INSTAGRAM.key,
                description = "DEMO template: High-retention Instagram profile followers with 30-day refill support. Connect a live Provider API in Admin to enable real orders.",
                ratePer1000 = 2.40,
                providerCostPer1000 = 1.50,
                minQuantity = 100,
                maxQuantity = 100000,
                averageTime = "1-2 hours",
                refillSupported = true,
                active = true,
                providerId = "unconfigured",
                providerServiceId = "IG-101",
                integrationStatus = "template_unconnected"
            ),
            SmmService(
                id = "svc_ig_likes",
                name = "Instagram Real Post Likes [Instant]",
                category = ServiceCategory.INSTAGRAM.key,
                description = "DEMO template: Fast-delivery Instagram photo & carousel likes. Connect a live Provider API in Admin to enable real orders.",
                ratePer1000 = 0.85,
                providerCostPer1000 = 0.45,
                minQuantity = 50,
                maxQuantity = 50000,
                averageTime = "15-30 mins",
                refillSupported = false,
                active = true,
                providerId = "unconfigured",
                providerServiceId = "IG-102",
                integrationStatus = "template_unconnected"
            ),
            SmmService(
                id = "svc_ig_reels_views",
                name = "Instagram Reels & Video Views",
                category = ServiceCategory.INSTAGRAM.key,
                description = "DEMO template: Organic-paced Instagram Reels reach and video views. Connect a live Provider API in Admin to enable real orders.",
                ratePer1000 = 0.45,
                providerCostPer1000 = 0.22,
                minQuantity = 500,
                maxQuantity = 1000000,
                averageTime = "30 mins",
                refillSupported = false,
                active = true,
                providerId = "unconfigured",
                providerServiceId = "IG-103",
                integrationStatus = "template_unconnected"
            ),

            // --- YOUTUBE ---
            SmmService(
                id = "svc_yt_views",
                name = "YouTube High-Retention Views",
                category = ServiceCategory.YOUTUBE.key,
                description = "DEMO template: High-retention YouTube watch-time views with refill guarantee. Connect a live Provider API in Admin to enable real orders.",
                ratePer1000 = 3.20,
                providerCostPer1000 = 2.10,
                minQuantity = 500,
                maxQuantity = 500000,
                averageTime = "2-6 hours",
                refillSupported = true,
                active = true,
                providerId = "unconfigured",
                providerServiceId = "YT-201",
                integrationStatus = "template_unconnected"
            ),
            SmmService(
                id = "svc_yt_subscribers",
                name = "YouTube Channel Subscribers [Refill]",
                category = ServiceCategory.YOUTUBE.key,
                description = "DEMO template: Gradual YouTube channel subscriber growth campaign. Connect a live Provider API in Admin to enable real orders.",
                ratePer1000 = 14.50,
                providerCostPer1000 = 9.80,
                minQuantity = 50,
                maxQuantity = 25000,
                averageTime = "6-12 hours",
                refillSupported = true,
                active = true,
                providerId = "unconfigured",
                providerServiceId = "YT-202",
                integrationStatus = "template_unconnected"
            ),
            SmmService(
                id = "svc_yt_likes",
                name = "YouTube Video Likes [Non-Drop]",
                category = ServiceCategory.YOUTUBE.key,
                description = "DEMO template: Stable YouTube video and Shorts likes. Connect a live Provider API in Admin to enable real orders.",
                ratePer1000 = 1.90,
                providerCostPer1000 = 1.15,
                minQuantity = 50,
                maxQuantity = 100000,
                averageTime = "1-3 hours",
                refillSupported = true,
                active = true,
                providerId = "unconfigured",
                providerServiceId = "YT-203",
                integrationStatus = "template_unconnected"
            ),

            // --- FACEBOOK ---
            SmmService(
                id = "svc_fb_page_likes",
                name = "Facebook Page Likes & Followers",
                category = ServiceCategory.FACEBOOK.key,
                description = "DEMO template: Business page likes and followers for Facebook Pages. Connect a live Provider API in Admin to enable real orders.",
                ratePer1000 = 3.60,
                providerCostPer1000 = 2.25,
                minQuantity = 100,
                maxQuantity = 50000,
                averageTime = "2-4 hours",
                refillSupported = true,
                active = true,
                providerId = "unconfigured",
                providerServiceId = "FB-301",
                integrationStatus = "template_unconnected"
            ),
            SmmService(
                id = "svc_fb_video_views",
                name = "Facebook Reels & Video Views",
                category = ServiceCategory.FACEBOOK.key,
                description = "DEMO template: Fast video and Reels views for Facebook creator posts. Connect a live Provider API in Admin to enable real orders.",
                ratePer1000 = 0.75,
                providerCostPer1000 = 0.40,
                minQuantity = 500,
                maxQuantity = 500000,
                averageTime = "1 hour",
                refillSupported = false,
                active = true,
                providerId = "unconfigured",
                providerServiceId = "FB-302",
                integrationStatus = "template_unconnected"
            ),
            SmmService(
                id = "svc_fb_post_likes",
                name = "Facebook Post Reactions & Likes",
                category = ServiceCategory.FACEBOOK.key,
                description = "DEMO template: Worldwide post likes and reactions for public Facebook posts. Connect a live Provider API in Admin to enable real orders.",
                ratePer1000 = 1.40,
                providerCostPer1000 = 0.80,
                minQuantity = 100,
                maxQuantity = 50000,
                averageTime = "30-60 mins",
                refillSupported = false,
                active = true,
                providerId = "unconfigured",
                providerServiceId = "FB-303",
                integrationStatus = "template_unconnected"
            ),

            // --- TELEGRAM ---
            SmmService(
                id = "svc_tg_members",
                name = "Telegram Channel / Group Members",
                category = ServiceCategory.TELEGRAM.key,
                description = "DEMO template: Community members for public Telegram channels and groups. Connect a live Provider API in Admin to enable real orders.",
                ratePer1000 = 1.80,
                providerCostPer1000 = 1.05,
                minQuantity = 100,
                maxQuantity = 100000,
                averageTime = "30-60 mins",
                refillSupported = true,
                active = true,
                providerId = "unconfigured",
                providerServiceId = "TG-401",
                integrationStatus = "template_unconnected"
            ),
            SmmService(
                id = "svc_tg_post_views",
                name = "Telegram Post Views [Last 5 Posts]",
                category = ServiceCategory.TELEGRAM.key,
                description = "DEMO template: Instant view boost for recent Telegram channel posts. Connect a live Provider API in Admin to enable real orders.",
                ratePer1000 = 0.35,
                providerCostPer1000 = 0.15,
                minQuantity = 100,
                maxQuantity = 1000000,
                averageTime = "10 mins",
                refillSupported = false,
                active = true,
                providerId = "unconfigured",
                providerServiceId = "TG-402",
                integrationStatus = "template_unconnected"
            ),
            SmmService(
                id = "svc_tg_reactions",
                name = "Telegram Post Positive Reactions",
                category = ServiceCategory.TELEGRAM.key,
                description = "DEMO template: Positive emoji reactions for Telegram channel announcements. Connect a live Provider API in Admin to enable real orders.",
                ratePer1000 = 0.95,
                providerCostPer1000 = 0.50,
                minQuantity = 50,
                maxQuantity = 100000,
                averageTime = "15-30 mins",
                refillSupported = false,
                active = true,
                providerId = "unconfigured",
                providerServiceId = "TG-403",
                integrationStatus = "template_unconnected"
            )
        )
    }
}
