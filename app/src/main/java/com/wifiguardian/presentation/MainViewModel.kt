package com.wifiguardian.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifiguardian.data.local.ScanEntity
import com.wifiguardian.data.repository.HistoryRepository
import com.wifiguardian.data.wifi.WifiAnalyzer
import com.wifiguardian.data.wifi.WifiRepository
import com.wifiguardian.domain.model.*
import com.wifiguardian.domain.usecase.PasswordStrength
import com.wifiguardian.domain.usecase.RouterAudit
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(private val wifi: WifiRepository, private val historyRepo: HistoryRepository) : ViewModel() {
    val current: StateFlow<CurrentWifi?> = wifi.observeCurrent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val history = historyRepo.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _networks = MutableStateFlow<List<WifiNetwork>>(emptyList())
    val networks: StateFlow<List<WifiNetwork>> = _networks.asStateFlow()
    private val _scanning = MutableStateFlow(false)
    val scanning = _scanning.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun scan() {
        _error.value = null; _scanning.value = true
        viewModelScope.launch {
            wifi.scan().collect { result ->
                result.onSuccess { _networks.value = it.sortedByDescending(WifiNetwork::rssi) }
                    .onFailure { _error.value = it.message ?: "Wi-Fi scan failed" }
                _scanning.value = false
            }
        }
    }

    fun saveAssessment(network: WifiNetwork) {
        val count = _networks.value.count { it.channel == network.channel && it.frequencyMHz / 1000 == network.frequencyMHz / 1000 }
        val assessment = WifiAnalyzer.assess(network, count)
        viewModelScope.launch { historyRepo.save(ScanEntity(timestamp = System.currentTimeMillis(), ssid = network.ssid, bssid = network.bssid, security = network.security, score = assessment.score, findings = assessment.findings.joinToString("\n") { "${it.severity}: ${it.title}" }, recommendations = assessment.findings.joinToString("\n") { it.recommendation })) }
    }

    fun deleteHistory(id: Long) = viewModelScope.launch { historyRepo.delete(id) }
    fun clearHistory() = viewModelScope.launch { historyRepo.clear() }
    fun clearError() { _error.value = null }

    fun assessPassword(value: String) = PasswordStrength.assess(value)
    fun generatePassword() = PasswordStrength.generate()
    fun assessRouter(input: RouterAuditInput) = RouterAudit.assess(input)
    fun assess(network: WifiNetwork): SecurityAssessment {
        val count = _networks.value.count { it.channel == network.channel && it.frequencyMHz / 1000 == network.frequencyMHz / 1000 }
        return WifiAnalyzer.assess(network, count)
    }
}
