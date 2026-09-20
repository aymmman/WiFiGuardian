package com.wifiguardian.data.wifi

import com.wifiguardian.domain.model.*

object WifiAnalyzer {
    fun assess(network: WifiNetwork, channelCount: Int = 1): SecurityAssessment {
        var score = when {
            network.security.contains("WPA3") -> 95
            network.security.contains("WPA2") && network.encryption == "AES/CCMP" -> 82
            network.security.contains("WPA") -> 65
            network.security == "WEP" -> 35
            network.security == "Open" -> 20
            else -> 55
        }
        val findings = mutableListOf<Finding>()
        when (network.security) {
            "Open" -> findings += Finding("Open network", Severity.HIGH, "No Wi-Fi authentication/encryption was advertised by this access point.", "Enable WPA2-AES or WPA3 on a network you control.")
            "WEP" -> { score -= 5; findings += Finding("Legacy WEP", Severity.HIGH, "WEP is obsolete and provides inadequate protection.", "Replace WEP with WPA2-AES or WPA3 on your router.") }
            else -> Unit
        }
        if (network.capabilities.contains("TKIP", ignoreCase = true)) {
            score -= 10
            findings += Finding("Legacy TKIP capability", Severity.MEDIUM, "The beacon advertises TKIP, a legacy encryption option.", "Prefer WPA2-AES/CCMP or WPA3 and disable legacy compatibility if possible.")
        }
        if (network.security.contains("WPA2") && !network.security.contains("WPA3")) {
            findings += Finding("WPA3 not observed", Severity.INFO, "The scan did not observe WPA3 on this BSSID. This is not proof that WPA3 is unsupported by the router.", "If your router and clients support it, consider WPA3 or WPA2/WPA3 transition mode.")
        }
        if (network.wpsAdvertised) {
            score -= 5
            findings += Finding("WPS advertised", Severity.MEDIUM, "The beacon advertises WPS. A scan cannot confirm the router's complete WPS configuration.", "If you do not need WPS, disable it in the router settings.")
        }
        if (channelCount >= 6) {
            findings += Finding("Potential channel congestion", Severity.LOW, "$channelCount nearby networks were observed on this channel.", "Re-check the environment at different times and choose a less congested channel where practical.")
        }
        return SecurityAssessment(score.coerceIn(0, 100), findings)
    }

    fun fromCapabilities(cap: String): Pair<String, String> {
        val c = cap.uppercase()
        val security = when {
            "SAE" in c && "EAP" in c -> "WPA3-Enterprise / WPA3"
            "SAE" in c -> "WPA3"
            "OWE" in c -> "OWE"
            "WPA2" in c -> "WPA2"
            "WPA" in c -> "WPA"
            "WEP" in c -> "WEP"
            else -> "Open"
        }
        val encryption = when {
            "CCMP" in c -> "AES/CCMP"
            "GCMP" in c -> "GCMP"
            "TKIP" in c -> "TKIP"
            else -> "None observed"
        }
        return security to encryption
    }

    fun channel(frequency: Int): Int? = when {
        frequency in 2412..2484 -> if (frequency == 2484) 14 else ((frequency - 2407) / 5)
        frequency in 5000..5900 -> (frequency - 5000) / 5
        frequency in 5925..7125 -> (frequency - 5950) / 5
        else -> null
    }

    fun generation(cap: String): String? {
        val c = cap.uppercase()
        return when {
            "EHT" in c -> "Wi-Fi 7"
            "HE" in c -> "Wi-Fi 6/6E"
            "VHT" in c -> "Wi-Fi 5"
            "HT" in c -> "Wi-Fi 4"
            else -> null
        }
    }
}
