package com.wifiguardian.domain.model

enum class Severity { INFO, LOW, MEDIUM, HIGH }

data class Finding(val title: String, val severity: Severity, val detail: String, val recommendation: String)

data class WifiNetwork(
    val ssid: String,
    val bssid: String?,
    val rssi: Int,
    val frequencyMHz: Int,
    val channel: Int?,
    val generation: String?,
    val capabilities: String,
    val security: String,
    val encryption: String,
    val wpsAdvertised: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class CurrentWifi(
    val ssid: String?, val bssid: String?, val security: String?, val encryption: String?,
    val rssi: Int?, val frequencyMHz: Int?, val channel: Int?, val linkSpeedMbps: Int?,
    val localIp: String?, val gateway: String?, val dns: List<String>, val capabilities: String?,
    val wpsAdvertised: Boolean?, val generation: String?
)

data class SecurityAssessment(val score: Int, val findings: List<Finding>)

data class RouterAuditInput(
    val manufacturer: String, val model: String, val firmware: String, val securityMode: String,
    val wps: Boolean, val adminExposed: Boolean, val guestNetwork: Boolean, val upnp: Boolean,
    val strongPassword: Boolean, val firmwareUpdated: Boolean, val adminPasswordChanged: Boolean,
    val remoteAdminDisabled: Boolean, val iotIsolated: Boolean
)

data class PasswordAssessment(val score: Int, val label: String, val reasons: List<String>)
