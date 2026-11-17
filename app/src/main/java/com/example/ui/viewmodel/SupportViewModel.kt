package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.SupportAssistantEngine
import com.example.data.model.AppConfig
import com.example.data.model.KnowledgeBaseEntry
import com.example.data.model.SmmOrder
import com.example.data.model.SupportChatMessage
import com.example.data.model.SupportTicket
import com.example.data.model.SupportTicketMessage
import com.example.data.model.UiState
import com.example.data.repository.SupportRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class SupportViewModel(
    private val supportRepository: SupportRepository,
    val currentUserId: String,
    private val appConfigFlow: StateFlow<AppConfig>
) : ViewModel() {

    private val _isGeneratingReply = MutableStateFlow(false)
    val isGeneratingReply: StateFlow<Boolean> = _isGeneratingReply.asStateFlow()

    private val _isSubmittingTicket = MutableStateFlow(false)
    val isSubmittingTicket: StateFlow<Boolean> = _isSubmittingTicket.asStateFlow()

    private val _supportMessage = MutableStateFlow<String?>(null)
    val supportMessage: StateFlow<String?> = _supportMessage.asStateFlow()

    private val _latestVerifiedOrder = MutableStateFlow<SmmOrder?>(null)
    val latestVerifiedOrder: StateFlow<SmmOrder?> = _latestVerifiedOrder.asStateFlow()

    val chatMessages: StateFlow<UiState<List<SupportChatMessage>>> =
        supportRepository.observeSupportChat(currentUserId)
            .map<List<SupportChatMessage>, UiState<List<SupportChatMessage>>> { UiState.Success(it) }
            .catch { err -> emit(UiState.Error(unwrapMsg(err, "Failed to load AI chat history"))) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val knowledgeBase: StateFlow<UiState<List<KnowledgeBaseEntry>>> =
        supportRepository.observeKnowledgeBase()
            .map<List<KnowledgeBaseEntry>, UiState<List<KnowledgeBaseEntry>>> { UiState.Success(it) }
            .catch { err -> emit(UiState.Error(unwrapMsg(err, "Failed to load Knowledge Base"))) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val userTickets: StateFlow<UiState<List<SupportTicket>>> =
        supportRepository.observeUserSupportTickets(currentUserId)
            .map<List<SupportTicket>, UiState<List<SupportTicket>>> { UiState.Success(it) }
            .catch { err -> emit(UiState.Error(unwrapMsg(err, "Failed to load support tickets"))) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    fun clearMessage() {
        _supportMessage.value = null
    }

    fun sendCustomerMessage(text: String) {
        val cleanText = text.trim()
        if (cleanText.isEmpty() || _isGeneratingReply.value) return

        val historySnapshot = (chatMessages.value as? UiState.Success)?.data.orEmpty()
        val kbSnapshot = (knowledgeBase.value as? UiState.Success)?.data.orEmpty()
        val currentConfig = appConfigFlow.value

        viewModelScope.launch {
            _isGeneratingReply.value = true
            try {
                supportRepository.saveSupportChatMessage(
                    userId = currentUserId,
                    role = "user",
                    text = cleanText
                )

                val reply = SupportAssistantEngine.generateSupportReply(
                    userQuery = cleanText,
                    recentHistory = historySnapshot,
                    knowledgeBase = kbSnapshot,
                    appConfig = currentConfig,
                    findOrderForUser = { candidateId ->
                        supportRepository.findSignedInUserOrder(currentUserId, candidateId)
                    }
                )

                if (reply.matchedOrder != null) {
                    _latestVerifiedOrder.value = reply.matchedOrder
                }

                supportRepository.saveSupportChatMessage(
                    userId = currentUserId,
                    role = "assistant",
                    text = reply.text,
                    detectedOrderId = reply.detectedOrderId,
                    suggestWhatsApp = reply.suggestWhatsApp,
                    escalatedProblem = reply.escalatedProblem
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _supportMessage.value = e.localizedMessage ?: "Failed to send support message."
            } finally {
                _isGeneratingReply.value = false
            }
        }
    }

    fun clearConversation() {
        if (_isGeneratingReply.value) return
        viewModelScope.launch {
            supportRepository.clearSupportChat(currentUserId)
                .onSuccess {
                    _latestVerifiedOrder.value = null
                    _supportMessage.value = "Chat conversation cleared."
                }
                .onFailure { e ->
                    _supportMessage.value = e.localizedMessage ?: "Could not clear chat."
                }
        }
    }

    fun createSupportTicket(
        category: String,
        orderId: String,
        subject: String,
        description: String,
        displayName: String,
        onSuccess: (String) -> Unit = {}
    ) {
        if (_isSubmittingTicket.value) return
        viewModelScope.launch {
            _isSubmittingTicket.value = true
            supportRepository.createSupportTicket(
                category = category,
                orderId = orderId,
                subject = subject,
                description = description,
                displayName = displayName
            ).onSuccess { ticketNumber ->
                _supportMessage.value = "Support Ticket $ticketNumber submitted."
                onSuccess(ticketNumber)
            }.onFailure { e ->
                _supportMessage.value = e.localizedMessage ?: "Failed to create support ticket."
            }
            _isSubmittingTicket.value = false
        }
    }

    fun observeTicketMessages(ticketId: String): Flow<List<SupportTicketMessage>> {
        return supportRepository.observeTicketMessages(ticketId)
            .catch { emit(emptyList()) }
    }

    fun replyToTicket(ticket: SupportTicket, message: String) {
        if (_isSubmittingTicket.value) return
        viewModelScope.launch {
            _isSubmittingTicket.value = true
            supportRepository.addTicketReply(ticket, message, asAdmin = false)
                .onSuccess { _supportMessage.value = "Reply added to ${ticket.ticketNumber}." }
                .onFailure { e -> _supportMessage.value = e.localizedMessage ?: "Failed to send reply." }
            _isSubmittingTicket.value = false
        }
    }

    private fun unwrapMsg(error: Throwable, fallback: String): String {
        val fsEx = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (fsEx == null && error is CancellationException) throw error
        val root = fsEx ?: error
        Log.w(TAG, fallback, root)
        return root.localizedMessage ?: fallback
    }

    private companion object {
        const val TAG = "SupportVM"
    }
}
