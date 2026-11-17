package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.KnowledgeBaseEntry
import com.example.data.model.SupportCategory
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SupportRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun regularUser_cannotModifyKnowledgeBase_butCanRead() = runBlocking {
        signInTestUser("kb_user_${UUID.randomUUID().toString().take(6)}@binsmm.test")
        val supportRepo = SupportRepository(firestore, auth, functions)

        val saveRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            supportRepo.adminSaveKnowledgeBaseEntry(
                KnowledgeBaseEntry(
                    title = "Unauthorized FAQ",
                    category = SupportCategory.GENERAL.key,
                    question = "Should fail?",
                    answer = "Yes, blocked by Firestore rules."
                )
            )
        }
        assertTrue(saveRes.isFailure)
        assertTrue(
            saveRes.exceptionOrNull()?.message?.contains("administrator", ignoreCase = true) == true
        )

        var directError: Exception? = null
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("knowledge_base").document("kb_unauth_${UUID.randomUUID().toString().take(6)}")
                    .set(
                        mapOf(
                            "title" to "Unauthorized FAQ",
                            "category" to "general",
                            "question" to "Should fail?",
                            "answer" to "Yes, blocked by Firestore rules.",
                            "keywords" to "test",
                            "active" to true,
                            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                        )
                    ).await()
            }
        } catch (e: Exception) {
            directError = e
        }
        val fsEx = directError?.unwrapFirestoreException()
        assertNotNull("Expected nested FirebaseFirestoreException, got $directError", fsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)

        val entries = withTimeout(FLOW_TIMEOUT_MS) {
            supportRepo.observeKnowledgeBase().first()
        }
        assertNotNull(entries)
    }

    @Test
    fun user_canSaveSupportChatAndCreateSupportTicket_withStrictIsolation() = runBlocking {
        val uid1 = signInTestUser("tkt_u1_${UUID.randomUUID().toString().take(6)}@binsmm.test")
        val binsmmRepo = BinsmmRepository(firestore, auth, functions)
        withTimeout(DEFAULT_TIMEOUT_MS) { binsmmRepo.ensureUserRegistered().getOrThrow() }

        val supportRepo = SupportRepository(firestore, auth, functions)

        // Save and read own AI chat message
        withTimeout(DEFAULT_TIMEOUT_MS) {
            supportRepo.saveSupportChatMessage(
                userId = uid1,
                role = "user",
                text = "Order ord_test123 ka status kya hai?",
                detectedOrderId = "ord_test123"
            )
        }
        val chatMessages = withTimeout(FLOW_TIMEOUT_MS) {
            supportRepo.observeSupportChat(uid1).first { it.any { m -> m.text.contains("ord_test123") } }
        }
        assertTrue(chatMessages.isNotEmpty())

        // Create own Support Ticket
        val ticketNum = withTimeout(DEFAULT_TIMEOUT_MS) {
            supportRepo.createSupportTicket(
                category = SupportCategory.WALLET.key,
                orderId = "",
                subject = "Deposit pending verification",
                description = "Submitted UPI UTR 998877 for wallet top-up.",
                displayName = "Alice Test"
            ).getOrThrow()
        }
        assertTrue(ticketNum.startsWith("TKT-"))

        val tickets = withTimeout(FLOW_TIMEOUT_MS) {
            supportRepo.observeUserSupportTickets(uid1).first { it.any { t -> t.ticketNumber == ticketNum } }
        }
        val created = tickets.first { it.ticketNumber == ticketNum }
        assertEquals("open", created.status)

        // Customer-scoped order lookup safely returns null for missing orders
        val missingOrder = withTimeout(DEFAULT_TIMEOUT_MS) {
            supportRepo.findSignedInUserOrder(uid1, "ord_nonexistent_999")
        }
        assertNull(missingOrder)

        // Second user cannot read first user's support chat
        auth.signOut()
        signInTestUser("tkt_u2_${UUID.randomUUID().toString().take(6)}@binsmm.test")
        var caught: Exception? = null
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(uid1).collection("support_chats").get().await()
            }
        } catch (e: Exception) {
            caught = e
        }
        val permEx = caught?.unwrapFirestoreException()
        assertNotNull("Expected PERMISSION_DENIED when reading another user's support chat", permEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, permEx?.code)
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 10_000L
        const val FLOW_TIMEOUT_MS = 10_000L
    }
}
