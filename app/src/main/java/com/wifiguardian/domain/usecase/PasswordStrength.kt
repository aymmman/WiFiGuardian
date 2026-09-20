package com.wifiguardian.domain.usecase

import com.wifiguardian.domain.model.PasswordAssessment
import java.security.SecureRandom

object PasswordStrength {
    private val common = setOf("password", "password123", "qwerty", "qwerty123", "12345678", "123456789", "letmein", "admin", "welcome", "iloveyou", "wifi", "wireless")

    fun assess(password: String): PasswordAssessment {
        if (password.isEmpty()) return PasswordAssessment(0, "Enter a password", listOf("Use a long, unique passphrase."))
        var score = (password.length * 4).coerceAtMost(60)
        if (password.any(Char::isUpperCase)) score += 8
        if (password.any(Char::isLowerCase)) score += 8
        if (password.any(Char::isDigit)) score += 8
        if (password.any { !it.isLetterOrDigit() }) score += 8
        val lower = password.lowercase()
        val reasons = mutableListOf<String>()
        if (lower in common) { score = minOf(score, 15); reasons += "This matches a very common password pattern." }
        if (password.length < 12) reasons += "Use at least 12 characters; longer is better."
        if (Regex("(.)\\1{2,}").containsMatchIn(password)) { score -= 8; reasons += "Repeated characters reduce unpredictability." }
        if (Regex("0123|1234|2345|3456|4567|5678|6789|abcd|qwer", RegexOption.IGNORE_CASE).containsMatchIn(password)) { score -= 10; reasons += "Avoid obvious sequential or keyboard patterns." }
        if (password.toSet().size <= 3) { score -= 10; reasons += "Very little character variety is used." }
        if (reasons.isEmpty()) reasons += "No obvious weakness was detected by the local heuristic checks."
        val final = score.coerceIn(0, 100)
        val label = when { final >= 80 -> "Strong"; final >= 60 -> "Good"; final >= 40 -> "Fair"; else -> "Weak" }
        return PasswordAssessment(final, label, reasons)
    }

    fun generate(length: Int = 20): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%^&*_-+="
        val random = SecureRandom()
        return buildString(length) { repeat(length) { append(chars[random.nextInt(chars.length)]) } }
    }
}
