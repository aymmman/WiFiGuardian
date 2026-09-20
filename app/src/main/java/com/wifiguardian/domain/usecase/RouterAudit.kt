package com.wifiguardian.domain.usecase

import com.wifiguardian.domain.model.*

object RouterAudit {
    fun assess(input: RouterAuditInput): SecurityAssessment {
        val findings = mutableListOf<Finding>()
        var score = 0
        fun check(ok: Boolean, points: Int, title: String, severity: Severity, detail: String, recommendation: String) {
            if (ok) score += points else findings += Finding(title, severity, detail, recommendation)
        }
        check(input.securityMode.contains("WPA3", true) || input.securityMode.contains("WPA2", true) && input.securityMode.contains("AES", true), 20, "Modern Wi-Fi security", Severity.HIGH, "The selected security mode is not confirmed as WPA3 or WPA2-AES.", "Use WPA3 where supported, or WPA2-AES/CCMP.")
        check(input.strongPassword, 15, "Strong Wi-Fi password", Severity.HIGH, "The audit input does not confirm a strong Wi-Fi password.", "Use a unique long passphrase and do not reuse it elsewhere.")
        check(!input.wps, 10, "WPS disabled", Severity.MEDIUM, "WPS is marked as enabled.", "Disable WPS if you do not need it.")
        check(input.firmwareUpdated, 15, "Firmware updated", Severity.MEDIUM, "The firmware is not confirmed current.", "Check the manufacturer's support page or router update mechanism.")
        check(input.adminPasswordChanged, 10, "Admin password changed", Severity.HIGH, "The router admin password is not confirmed as changed from the default.", "Set a unique administrator password.")
        check(input.remoteAdminDisabled && !input.adminExposed, 10, "Remote administration restricted", Severity.HIGH, "Remote administration/exposure is not confirmed as disabled.", "Disable WAN-side administration unless you have a documented need and strong controls.")
        check(input.guestNetwork, 5, "Guest network available", Severity.LOW, "No guest network is confirmed.", "Consider a guest network for visitors and untrusted devices.")
        check(!input.upnp, 5, "UPnP minimized", Severity.LOW, "UPnP is marked as enabled.", "Disable it when you do not need automatic port mapping.")
        check(input.iotIsolated, 10, "IoT isolation", Severity.LOW, "IoT isolation is not confirmed.", "Use a separate VLAN/SSID or guest network when appropriate.")
        return SecurityAssessment(score.coerceIn(0, 100), findings)
    }
}
