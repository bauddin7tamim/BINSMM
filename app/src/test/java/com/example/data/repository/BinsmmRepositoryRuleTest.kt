package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.ServiceCategory
import com.example.data.model.SmmService
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class BinsmmRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureUserRegistered_createsProfileWithZeroBalanceAndUserRole() = runBlocking {
        val uid = signInTestUser("alice_${UUID.randomUUID().toString().take(6)}@binsmm.test")
        val repo = BinsmmRepository(firestore, auth, functions)

        val profile = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureUserRegistered(whatsappHandle = "+15551234").getOrThrow()
        }

        assertEquals(uid, profile.userId)
        assertEquals("user", profile.role)
        assertEquals(0.0, profile.balance, 0.001)

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeUserProfile(uid).first { it != null }
        }
        assertNotNull(observed)
        assertEquals(uid, observed?.userId)
    }

    @Test
    fun submitDepositRequest_createsPendingVerificationTransaction() = runBlocking {
        val uid = signInTestUser("bob_${UUID.randomUUID().toString().take(6)}@binsmm.test")
        val repo = BinsmmRepository(firestore, auth, functions)
        withTimeout(DEFAULT_TIMEOUT_MS) { repo.ensureUserRegistered().getOrThrow() }

        val txId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.submitDepositRequest(
                amount = 25.0,
                gateway = "manual_request",
                referenceNote = "REF-${UUID.randomUUID().toString().take(6)}"
            ).getOrThrow()
        }

        val txs = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeUserTransactions(uid).first { list -> list.any { it.id == txId } }
        }
        val tx = txs.first { it.id == txId }
        assertEquals("pending_verification", tx.status)
        assertEquals(25.0, tx.amount, 0.001)
    }

    @Test
    fun observeUserOrders_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = BinsmmRepository(firestore, auth, functions)

        var caught: Exception? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeUserOrders("some_uid").first()
            }
        } catch (e: Exception) {
            caught = e
        }

        val firestoreEx = caught?.unwrapFirestoreException()
        assertNotNull("Expected nested FirebaseFirestoreException, got $caught", firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun regularUser_cannotCreateServiceDirectly() = runBlocking {
        signInTestUser("user_${UUID.randomUUID().toString().take(6)}@binsmm.test")
        val repo = BinsmmRepository(firestore, auth, functions)

        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.adminSaveService(
                SmmService(
                    name = "Unauthorized Service",
                    category = ServiceCategory.INSTAGRAM.key,
                    description = "Should fail",
                    ratePer1000 = 1.0,
                    providerCostPer1000 = 0.5,
                    minQuantity = 100,
                    maxQuantity = 1000
                )
            )
        }

        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message?.contains("Administrator privileges", ignoreCase = true) == true
        )

        var directError: Exception? = null
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("services").document("svc_unauth_${UUID.randomUUID().toString().take(6)}")
                    .set(
                        mapOf(
                            "name" to "Unauthorized Service",
                            "category" to "instagram",
                            "description" to "Should fail",
                            "ratePer1000" to 1.0,
                            "providerCostPer1000" to 0.5,
                            "minQuantity" to 100L,
                            "maxQuantity" to 1000L,
                            "averageTime" to "1-3 hours",
                            "refillSupported" to false,
                            "active" to true,
                            "providerId" to "unconfigured",
                            "providerServiceId" to "",
                            "integrationStatus" to "template_unconnected",
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
            }
        } catch (e: Exception) {
            directError = e
        }
        val firestoreEx = directError?.unwrapFirestoreException()
        assertNotNull("Expected PERMISSION_DENIED writing services directly, got $directError", firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun clientCannotMutateBalanceOrCreateOrderDirectlyInFirestore() = runBlocking {
        val uid = signInTestUser("order_user_${UUID.randomUUID().toString().take(6)}@binsmm.test")
        val repo = BinsmmRepository(firestore, auth, functions)
        val profile = withTimeout(DEFAULT_TIMEOUT_MS) { repo.ensureUserRegistered().getOrThrow() }

        // Direct wallet balance modification from client SDK must be denied
        var balError: Exception? = null
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(uid)
                    .update(mapOf("balance" to 999.0, "updatedAt" to FieldValue.serverTimestamp()))
                    .await()
            }
        } catch (e: Exception) {
            balError = e
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, balError?.unwrapFirestoreException()?.code)

        // Direct order document creation from client SDK must be denied (Cloud Functions Admin SDK only)
        var orderError: Exception? = null
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("orders").document("ord_client_${UUID.randomUUID().toString().take(6)}")
                    .set(
                        mapOf(
                            "userId" to uid,
                            "userEmail" to profile.email,
                            "serviceId" to "svc_live",
                            "serviceName" to "Test",
                            "category" to "instagram",
                            "targetLink" to "https://instagram.com/test",
                            "quantity" to 100L,
                            "charge" to 1.0,
                            "status" to "pending",
                            "providerOrderId" to "",
                            "integrationNote" to "test",
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
            }
        } catch (e: Exception) {
            orderError = e
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, orderError?.unwrapFirestoreException()?.code)
    }

    @Test
    fun placeOrder_demoService_isRejectedWithoutCreatingOrder() = runBlocking {
        val uid = signInTestUser("demo_guard_${UUID.randomUUID().toString().take(6)}@binsmm.test")
        val repo = BinsmmRepository(firestore, auth, functions)
        val profile = withTimeout(DEFAULT_TIMEOUT_MS) { repo.ensureUserRegistered().getOrThrow() }
        val demoService = BinsmmRepository.defaultTemplateServices().first()

        val result = repo.placeOrder(
            service = demoService,
            targetLink = "https://instagram.com/binsmm_official",
            quantity = demoService.minQuantity,
            userProfile = profile.copy(balance = 500.0)
        )

        assertTrue("Expected DEMO service order to fail", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("DEMO", ignoreCase = true) == true)
    }

    @Test
    fun regularUser_cannotReadAuditLogs_andIsNotAdmin() = runBlocking {
        signInTestUser("regular_${UUID.randomUUID().toString().take(6)}@binsmm.test")
        val repo = BinsmmRepository(firestore, auth, functions)
        withTimeout(DEFAULT_TIMEOUT_MS) { repo.ensureUserRegistered().getOrThrow() }

        val isAdmin = withTimeout(DEFAULT_TIMEOUT_MS) { repo.checkIsAdmin() }
        assertEquals(false, isAdmin)

        var caught: Exception? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeAdminAuditLogs().first()
            }
        } catch (e: Exception) {
            caught = e
        }
        val fsEx = caught?.unwrapFirestoreException()
        assertNotNull("Expected PERMISSION_DENIED reading audit logs as non-admin, got $caught", fsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
    }

    @Test
    fun unverifiedOwnerEmail_isDeniedAdminAccess() = runBlocking {
        signInTestUser(BinsmmRepository.BOOTSTRAP_ADMIN_EMAIL)
        val repo = BinsmmRepository(firestore, auth, functions)

        // Emulator email/password accounts have isEmailVerified == false by default
        val isAdmin = withTimeout(DEFAULT_TIMEOUT_MS) { repo.checkIsAdmin() }
        assertEquals(false, isAdmin)

        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.adminDeleteService("svc_test", "Test")
        }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Administrator privileges", ignoreCase = true) == true)
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
