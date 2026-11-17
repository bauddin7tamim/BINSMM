package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AdminAuditLog
import com.example.data.model.AppConfig
import com.example.data.model.KnowledgeBaseEntry
import com.example.data.model.ServiceCategory
import com.example.data.model.SmmOrder
import com.example.data.model.SmmProviderConfig
import com.example.data.model.SmmService
import com.example.data.model.SupportTicket
import com.example.data.model.SupportTicketMessage
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.WalletTransaction
import com.example.data.repository.BinsmmRepository
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
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

class AdminViewModel(
    private val repository: BinsmmRepository,
    private val supportRepository: SupportRepository
) : ViewModel() {

    private val _adminMessage = MutableStateFlow<String?>(null)
    val adminMessage: StateFlow<String?> = _adminMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    val allUsers: StateFlow<UiState<List<UserProfile>>> = repository.observeAllUsers()
        .map<List<UserProfile>, UiState<List<UserProfile>>> { UiState.Success(it) }
        .catch { e -> emit(UiState.Error(unwrapMsg(e, "Failed to load users"))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val allServices: StateFlow<UiState<List<SmmService>>> = repository.observeServices()
        .map<List<SmmService>, UiState<List<SmmService>>> { UiState.Success(it) }
        .catch { e -> emit(UiState.Error(unwrapMsg(e, "Failed to load services"))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val allOrders: StateFlow<UiState<List<SmmOrder>>> = repository.observeAllOrders()
        .map<List<SmmOrder>, UiState<List<SmmOrder>>> { UiState.Success(it) }
        .catch { e -> emit(UiState.Error(unwrapMsg(e, "Failed to load orders"))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val allTransactions: StateFlow<UiState<List<WalletTransaction>>> = repository.observeAllTransactions()
        .map<List<WalletTransaction>, UiState<List<WalletTransaction>>> { UiState.Success(it) }
        .catch { e -> emit(UiState.Error(unwrapMsg(e, "Failed to load payments"))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val providers: StateFlow<UiState<List<SmmProviderConfig>>> = repository.observeProviders()
        .map<List<SmmProviderConfig>, UiState<List<SmmProviderConfig>>> { UiState.Success(it) }
        .catch { e -> emit(UiState.Error(unwrapMsg(e, "Failed to load providers"))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val auditLogs: StateFlow<UiState<List<AdminAuditLog>>> = repository.observeAdminAuditLogs()
        .map<List<AdminAuditLog>, UiState<List<AdminAuditLog>>> { UiState.Success(it) }
        .catch { e -> emit(UiState.Error(unwrapMsg(e, "Failed to load audit logs"))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val appConfig: StateFlow<AppConfig> = repository.observeAppConfig()
        .catch { emit(AppConfig()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AppConfig())

    val knowledgeBase: StateFlow<UiState<List<KnowledgeBaseEntry>>> = supportRepository.observeKnowledgeBase()
        .map<List<KnowledgeBaseEntry>, UiState<List<KnowledgeBaseEntry>>> { UiState.Success(it) }
        .catch { e -> emit(UiState.Error(unwrapMsg(e, "Failed to load Knowledge Base"))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val allSupportTickets: StateFlow<UiState<List<SupportTicket>>> = supportRepository.observeAllSupportTickets()
        .map<List<SupportTicket>, UiState<List<SupportTicket>>> { UiState.Success(it) }
        .catch { e -> emit(UiState.Error(unwrapMsg(e, "Failed to load support tickets"))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val _isOwnerUidPinned = MutableStateFlow(false)
    val isOwnerUidPinned: StateFlow<Boolean> = _isOwnerUidPinned.asStateFlow()

    init {
        refreshOwnerPinStatus()
    }

    fun refreshOwnerPinStatus() {
        viewModelScope.launch {
            _isOwnerUidPinned.value = supportRepository.isOwnerUidPinned()
        }
    }

    fun clearMessage() {
        _adminMessage.value = null
    }

    fun seedTemplateCatalog() {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            repository.seedTemplateCatalog()
                .onSuccess { count ->
                    _adminMessage.value = if (count > 0) {
                        "Loaded $count DEMO template services across Instagram, YouTube, Facebook & Telegram."
                    } else {
                        "All DEMO template services are already present in Firestore (no duplicates added)."
                    }
                }
                .onFailure { e -> _adminMessage.value = e.localizedMessage ?: "Seed failed." }
            _isBusy.value = false
        }
    }

    fun saveService(service: SmmService) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.adminSaveService(service)
                .onSuccess { _adminMessage.value = "Service '${service.name}' saved." }
                .onFailure { e -> _adminMessage.value = e.localizedMessage ?: "Failed to save service." }
            _isBusy.value = false
        }
    }

    fun toggleServiceActive(service: SmmService, active: Boolean) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.adminToggleServiceActive(service, active)
                .onSuccess {
                    _adminMessage.value = "Service '${service.name}' ${if (active) "enabled" else "disabled"}."
                }
                .onFailure { e -> _adminMessage.value = e.localizedMessage ?: "Failed to toggle service." }
            _isBusy.value = false
        }
    }

    fun deleteService(serviceId: String, serviceName: String = "") {
        viewModelScope.launch {
            _isBusy.value = true
            repository.adminDeleteService(serviceId, serviceName)
                .onSuccess { _adminMessage.value = "Service deleted." }
                .onFailure { e -> _adminMessage.value = e.localizedMessage ?: "Failed to delete service." }
            _isBusy.value = false
        }
    }

    fun updateOrderStatus(order: SmmOrder, newStatus: String, providerOrderId: String) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.adminUpdateOrderStatus(order, newStatus, providerOrderId)
                .onSuccess { _adminMessage.value = "Order ${order.id} updated to $newStatus." }
                .onFailure { e -> _adminMessage.value = e.localizedMessage ?: "Order update failed." }
            _isBusy.value = false
        }
    }

    fun cancelAndRefundOrder(order: SmmOrder, refundToWallet: Boolean, reason: String) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.adminCancelAndRefundOrder(order, refundToWallet, reason)
                .onSuccess { refunded ->
                    _adminMessage.value = if (refunded > 0.0) {
                        "Order ${order.id} cancelled and $${String.format(Locale.US, "%.2f", refunded)} refunded."
                    } else {
                        "Order ${order.id} cancelled."
                    }
                }
                .onFailure { e -> _adminMessage.value = e.localizedMessage ?: "Cancellation failed." }
            _isBusy.value = false
        }
    }

    fun updateUserAccount(user: UserProfile, status: String, balance: Double, note: String) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.adminUpdateUserAccount(user, status, balance, note)
                .onSuccess { _adminMessage.value = "Updated user ${user.displayName}." }
                .onFailure { e -> _adminMessage.value = e.localizedMessage ?: "User update failed." }
            _isBusy.value = false
        }
    }

    fun processTransaction(tx: WalletTransaction, approve: Boolean, reasonNote: String = "") {
        viewModelScope.launch {
            _isBusy.value = true
            repository.adminProcessTransaction(tx, approve, reasonNote)
                .onSuccess {
                    _adminMessage.value = if (approve) {
                        "Approved deposit of $${String.format(Locale.US, "%.2f", tx.amount)}."
                    } else {
                        "Rejected deposit request."
                    }
                }
                .onFailure { e -> _adminMessage.value = e.localizedMessage ?: "Transaction update failed." }
            _isBusy.value = false
        }
    }

    fun saveAndTestProvider(
        existing: SmmProviderConfig?,
        name: String,
        baseUrl: String,
        rawApiKey: String,
        active: Boolean,
        runLiveTest: Boolean
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.adminTestProviderConnection(
                existing = existing,
                name = name.trim(),
                baseUrl = baseUrl.trim(),
                transientApiKey = rawApiKey.trim(),
                active = active,
                runLiveTest = runLiveTest
            ).onSuccess { syncStatus ->
                _adminMessage.value = "Provider '${name.trim()}': $syncStatus"
            }.onFailure { e ->
                _adminMessage.value = e.localizedMessage ?: "Failed to save or test provider."
            }
            _isBusy.value = false
        }
    }

    fun deleteProvider(providerId: String, providerName: String) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.adminDeleteProvider(providerId, providerName)
                .onSuccess { _adminMessage.value = "Provider '$providerName' removed." }
                .onFailure { e -> _adminMessage.value = e.localizedMessage ?: "Failed to delete provider." }
            _isBusy.value = false
        }
    }

    fun applyCategoryMarkup(services: List<SmmService>, category: ServiceCategory, markupPercent: Double) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.adminApplyCategoryMarkup(services, category, markupPercent)
                .onSuccess { count ->
                    _adminMessage.value = "Applied ${markupPercent}% markup to $count ${category.displayName} services."
                }
                .onFailure { e -> _adminMessage.value = e.localizedMessage ?: "Markup update failed." }
            _isBusy.value = false
        }
    }

    fun saveAppConfig(config: AppConfig) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.adminSaveAppConfig(config)
                .onSuccess { _adminMessage.value = "Panel configuration saved." }
                .onFailure { e -> _adminMessage.value = e.localizedMessage ?: "Config save failed." }
            _isBusy.value = false
        }
    }

    // --- KNOWLEDGE BASE MANAGEMENT ---

    fun seedDefaultKnowledgeBase() {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            supportRepository.adminSeedDefaultKnowledgeBase()
                .onSuccess { count ->
                    _adminMessage.value = if (count > 0) {
                        "Loaded $count default Knowledge Base FAQ entries across Categories A-F."
                    } else {
                        "All default Knowledge Base entries are already loaded."
                    }
                }
                .onFailure { e -> _adminMessage.value = e.localizedMessage ?: "Failed to load Knowledge Base defaults." }
            _isBusy.value = false
        }
    }

    fun saveKnowledgeBaseEntry(entry: KnowledgeBaseEntry) {
        viewModelScope.launch {
            _isBusy.value = true
            supportRepository.adminSaveKnowledgeBaseEntry(entry)
                .onSuccess { _adminMessage.value = "Knowledge Base entry '${entry.title}' saved." }
                .onFailure { e -> _adminMessage.value = e.localizedMessage ?: "Failed to save KB entry." }
            _isBusy.value = false
        }
    }

    fun toggleKnowledgeBaseActive(entry: KnowledgeBaseEntry, active: Boolean) {
        viewModelScope.launch {
            _isBusy.value = true
            supportRepository.adminToggleKnowledgeBaseActive(entry, active)
                .onSuccess {
                    _adminMessage.value = "KB entry '${entry.title}' ${if (active) "activated" else "disabled"}."
                }
                .onFailure { e -> _adminMessage.value = e.localizedMessage ?: "Failed to update KB entry." }
            _isBusy.value = false
        }
    }

    fun deleteKnowledgeBaseEntry(entryId: String, title: String) {
        viewModelScope.launch {
            _isBusy.value = true
            supportRepository.adminDeleteKnowledgeBaseEntry(entryId, title)
                .onSuccess { _adminMessage.value = "Knowledge Base entry deleted." }
                .onFailure { e -> _adminMessage.value = e.localizedMessage ?: "Failed to delete KB entry." }
            _isBusy.value = false
        }
    }

    // --- SUPPORT TICKET MANAGEMENT ---

    fun observeTicketMessages(ticketId: String): Flow<List<SupportTicketMessage>> {
        return supportRepository.observeTicketMessages(ticketId)
            .catch { emit(emptyList()) }
    }

    fun replyToSupportTicket(ticket: SupportTicket, message: String) {
        viewModelScope.launch {
            _isBusy.value = true
            supportRepository.addTicketReply(ticket, message, asAdmin = true)
                .onSuccess { _adminMessage.value = "Admin reply sent on ${ticket.ticketNumber}." }
                .onFailure { e -> _adminMessage.value = e.localizedMessage ?: "Failed to reply to ticket." }
            _isBusy.value = false
        }
    }

    fun updateSupportTicketStatus(ticket: SupportTicket, newStatus: String) {
        viewModelScope.launch {
            _isBusy.value = true
            supportRepository.adminUpdateTicketStatus(ticket, newStatus)
                .onSuccess { _adminMessage.value = "Ticket ${ticket.ticketNumber} marked $newStatus." }
                .onFailure { e -> _adminMessage.value = e.localizedMessage ?: "Failed to update ticket status." }
            _isBusy.value = false
        }
    }

    // --- ADMIN SECURITY (EMAIL & PASSWORD MANAGEMENT) ---

    fun syncOwnerAdminUid() {
        viewModelScope.launch {
            _isBusy.value = true
            supportRepository.syncOwnerAdminUid()
                .onSuccess {
                    _isOwnerUidPinned.value = true
                    _adminMessage.value = "Owner Admin UID verified and pinned in backend for continuous Admin access."
                }
                .onFailure { e -> _adminMessage.value = e.localizedMessage ?: "Could not pin Owner UID." }
            _isBusy.value = false
        }
    }

    fun reauthenticateWithPassword(currentPassword: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            supportRepository.reauthenticateAdminWithPassword(currentPassword)
                .onSuccess {
                    _adminMessage.value = "Admin session reauthenticated."
                    onSuccess()
                }
                .onFailure { e -> _adminMessage.value = e.localizedMessage ?: "Reauthentication failed." }
            _isBusy.value = false
        }
    }

    fun requestAdminEmailChange(newEmail: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            supportRepository.adminRequestEmailChange(newEmail)
                .onSuccess { msg ->
                    _isOwnerUidPinned.value = true
                    _adminMessage.value = msg
                    onSuccess()
                }
                .onFailure { e -> _adminMessage.value = e.localizedMessage ?: "Failed to update Admin email." }
            _isBusy.value = false
        }
    }

    fun updateAdminPassword(newPassword: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            supportRepository.adminUpdatePassword(newPassword)
                .onSuccess { msg ->
                    _adminMessage.value = msg
                    onSuccess()
                }
                .onFailure { e -> _adminMessage.value = e.localizedMessage ?: "Failed to update Admin password." }
            _isBusy.value = false
        }
    }

    private fun unwrapMsg(error: Throwable, fallback: String): String {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val root = firestoreError ?: error
        Log.w("AdminVM", fallback, root)
        return root.localizedMessage ?: fallback
    }
}
