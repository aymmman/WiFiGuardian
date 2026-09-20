package com.wifiguardian.presentation

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.wifiguardian.domain.model.SecurityAssessment
import com.wifiguardian.domain.model.WifiNetwork
import java.io.File
import java.text.DateFormat
import java.util.Date

object ReportExporter {
    fun shareText(context: Context, network: WifiNetwork, assessment: SecurityAssessment) {
        val text = buildText(network, assessment)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "Export security report"))
    }

    fun sharePdf(context: Context, network: WifiNetwork, assessment: SecurityAssessment) {
        val doc = PdfDocument(); val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create(); val page = doc.startPage(pageInfo)
        val paint = Paint().apply { textSize = 12f }
        val lines = buildText(network, assessment).lines()
        var y = 36f
        lines.forEach { line -> if (y > 810) return@forEach; page.canvas.drawText(line.take(90), 30f, y, paint); y += 18f }
        doc.finishPage(page)
        val file = File(context.cacheDir, "wifi_guardian_report_${System.currentTimeMillis()}.pdf")
        file.outputStream().use { doc.writeTo(it) }; doc.close()
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Export PDF report"))
    }

    private fun buildText(n: WifiNetwork, a: SecurityAssessment): String = buildString {
        appendLine("WiFi Guardian — Wi-Fi Security Audit")
        appendLine("Date: ${DateFormat.getDateTimeInstance().format(Date())}")
        appendLine("SSID: ${n.ssid}")
        appendLine("BSSID: ${n.bssid ?: "Not available"}")
        appendLine("Security: ${n.security}")
        appendLine("Encryption: ${n.encryption}")
        appendLine("Signal: ${n.rssi} dBm")
        appendLine("Frequency: ${n.frequencyMHz} MHz")
        appendLine("Channel: ${n.channel ?: "Not available"}")
        appendLine("Security score: ${a.score}/100")
        appendLine("\nFindings:")
        if (a.findings.isEmpty()) appendLine("No specific findings from observable scan data.")
        a.findings.forEach { appendLine("- ${it.severity}: ${it.title} — ${it.detail}"); appendLine("  Recommendation: ${it.recommendation}") }
        appendLine("\nLimitations: This report reflects observable Wi-Fi configuration data only. It does not prove exploitability, password strength, router firmware state, or the absence of hidden vulnerabilities.")
    }
}
