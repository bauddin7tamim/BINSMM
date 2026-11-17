package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AppConfig
import com.example.data.model.LocalAppSettings
import com.example.data.model.ServiceCategory
import com.example.data.model.SmmOrder
import com.example.data.model.SmmService
import com.example.data.model.ThemePreference
import com.example.data.model.UiState
import com.example.data.model.UserNotification
import com.example.data.model.UserProfile
import com.example.data.model.WalletTransaction
import com.example.data.preferences.SettingsPreferencesRepository
import com.example.data.repository.BinsmmRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

class BinsmmViewModel(
    private val repository: BinsmmRepository,
    private val settingsRepo: SettingsPreferencesRepository,
    val currentUserId: String,
    initialWhatsappHandle: String? = null
) : ViewModel() {

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    val settings: StateFlow<LocalAppSettings> = settingsRepo.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = LocalAppSettings()
        )

    val userProfile: StateFlow<UiState<UserProfile?>> = repository.observeUserProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
        .catch { error -> emit(UiState.Error(unwrapFirestoreMessage(error, "Failed to load profile"))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    val services: StateFlow<UiState<List<SmmService>>> = repository.observeServices()
        .map<List<SmmService>, UiState<List<SmmService>>> { UiState.Success(it) }
        .catch { error -> emit(UiState.Error(unwrapFirestoreMessage(error, "Failed to load services"))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    val orders: StateFlow<UiState<List<SmmOrder>>> = repository.observeUserOrders(currentUserId)
        .map<List<SmmOrder>, UiState<List<SmmOrder>>> { UiState.Success(it) }
        .catch { error -> emit(UiState.Error(unwrapFirestoreMessage(error, "Failed to load orders"))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    val transactions: StateFlow<UiState<List<WalletTransaction>>> = repository.observeUserTransactions(currentUserId)
        .map<List<WalletTransaction>, UiState<List<WalletTransaction>>> { UiState.Success(it) }
        .catch { error -> emit(UiState.Error(unwrapFirestoreMessage(error, "Failed to load transactions"))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    val notifications: StateFlow<UiState<List<UserNotification>>> = repository.observeNotifications(currentUserId)
        .map<List<UserNotification>, UiState<List<UserNotification>>> { UiState.Success(it) }
        .catch { error -> emit(UiState.Error(unwrapFirestoreMessage(error, "Failed to load notifications"))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    val appConfig: StateFlow<AppConfig> = repository.observeAppConfig()
        .catch { error ->
            Log.w(TAG, "Using default appConfig", error)
            emit(AppConfig())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), AppConfig())

    init {
        viewModelScope.launch {
            repository.ensureUserRegistered(initialWhatsappHandle)
                .onFailure { e ->
                    _actionMessage.value = e.localizedMessage ?: "Failed to sync user account."
                }
            _isAdmin.value = repository.checkIsAdmin()
        }
    }

    fun clearMessage() {
        _actionMessage.value = null
    }

    fun loadTemplateServices() {
        if (_isSubmitting.value) return
        viewModelScope.launch {
            _isSubmitting.value = true
            repository.seedTemplateCatalog()
                .onSuccess { count ->
                    _actionMessage.value = if (count > 0) {
                        "Loaded $count DEMO template services across Instagram, YouTube, Facebook & Telegram."
                    } else {
                        "All DEMO template services are already loaded (no duplicates added)."
                    }
                }
                .onFailure { e ->
                    _actionMessage.value = e.localizedMessage ?: "Failed to load template services."
                }
            _isSubmitting.value = false
        }
    }

    fun placeOrder(
        service: SmmService,
        targetLink: String,
        quantity: Long,
        profile: UserProfile?,
        onSuccess: (String) -> Unit = {}
    ) {
        if (_isSubmitting.value) return
        val idempotencyKey = UUID.randomUUID().toString().replace("-", "")
        viewModelScope.launch {
            _isSubmitting.value = true
            repository.placeOrder(service, targetLink, quantity, profile, idempotencyKey)
                .onSuccess { orderId ->
                    _actionMessage.value = "Order $orderId placed via secure backend transaction."
                    onSuccess(orderId)
                }
                .onFailure { e ->
                    _actionMessage.value = e.localizedMessage ?: "Failed to submit order."
                }
            _isSubmitting.value = false
        }
    }

    fun cancelOrder(order: SmmOrder) {
        viewModelScope.launch {
            _isSubmitting.value = true
            repository.cancelOrder(order)
                .onSuccess { _actionMessage.value = "Order ${order.id} cancelled." }
                .onFailure { e -> _actionMessage.value = e.localizedMessage ?: "Could not cancel order." }
            _isSubmitting.value = false
        }
    }

    fun submitDepositRequest(amount: Double, gateway: String, referenceNote: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isSubmitting.value = true
            repository.submitDepositRequest(amount, gateway, referenceNote)
                .onSuccess { txId ->
                    _actionMessage.value = "Deposit request $txId submitted for Admin verification."
                    onSuccess()
                }
                .onFailure { e ->
                    _actionMessage.value = e.localizedMessage ?: "Failed to submit deposit request."
                }
            _isSubmitting.value = false
        }
    }

    fun updateProfile(displayName: String, whatsappHandle: String?) {
        viewModelScope.launch {
            _isSubmitting.value = true
            repository.updateUserContactProfile(displayName, whatsappHandle)
                .onSuccess { _actionMessage.value = "Profile updated." }
                .onFailure { e -> _actionMessage.value = e.localizedMessage ?: "Update failed." }
            _isSubmitting.value = false
        }
    }

    fun markNotificationRead(notificationId: String) {
        viewModelScope.launch {
            repository.markNotificationRead(currentUserId, notificationId)
        }
    }

    fun markAllNotificationsRead(list: List<UserNotification>) {
        viewModelScope.launch {
            repository.markAllNotificationsRead(currentUserId, list)
        }
    }

    fun setThemePreference(preference: ThemePreference) {
        viewModelScope.launch { settingsRepo.setThemePreference(preference) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setNotificationsEnabled(enabled) }
    }

    fun setDefaultCategory(category: ServiceCategory) {
        viewModelScope.launch { settingsRepo.setDefaultCategory(category) }
    }

    fun setCurrencySymbol(symbol: String) {
        viewModelScope.launch { settingsRepo.setCurrencySymbol(symbol) }
    }

    fun setCompactOrderCards(compact: Boolean) {
        viewModelScope.launch { settingsRepo.setCompactOrderCards(compact) }
    }

    private fun unwrapFirestoreMessage(error: Throwable, fallback: String): String {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, fallback, rootError)
        return rootError.localizedMessage ?: fallback
    }

    private companion object {
        const val TAG = "BinsmmVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
