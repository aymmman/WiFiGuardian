package com.wifiguardian

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wifiguardian.domain.model.*
import com.wifiguardian.presentation.*
import com.wifiguardian.ui.theme.WiFiGuardianTheme
import java.text.DateFormat
import java.util.Date

private object Routes { const val DASH = "dashboard"; const val SCAN = "scanner"; const val AUDIT = "audit"; const val PASSWORD = "password"; const val CHANNELS = "channels"; const val EDU = "education"; const val HISTORY = "history" }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as GuardianApplication
        setContent {
            WiFiGuardianTheme {
                GuardianApp(app.wifiRepository, app.historyRepository)
            }
        }
    }
}

@Composable
private fun GuardianApp(wifi: com.wifiguardian.data.wifi.WifiRepository, history: com.wifiguardian.data.repository.HistoryRepository) {
    val nav = rememberNavController(); val vm: MainViewModel = viewModel(factory = ViewModelFactory(wifi, history)); val context = LocalContext.current
    var showPermissionInfo by remember { mutableStateOf(false) }
    val permissions = remember { if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES, Manifest.permission.ACCESS_FINE_LOCATION) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted -> if (granted.values.all { it }) vm.scan() }
    LaunchedEffect(Unit) { if (!permissions.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) showPermissionInfo = true else vm.scan() }

    if (showPermissionInfo) AlertDialog(
        onDismissRequest = { showPermissionInfo = false },
        title = { Text("Wi-Fi scan permission") },
        text = { Text("Android requires Wi-Fi/nearby-device access for real scan results. On Android 10–16, Wi-Fi scanning also requires precise location permission and Location services enabled. WiFi Guardian does not use Wi-Fi data to track your location.") },
        confirmButton = { TextButton(onClick = { showPermissionInfo = false; launcher.launch(permissions) }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { showPermissionInfo = false }) { Text("Not now") } }
    )

    Scaffold(bottomBar = { BottomBar(nav) }) { padding ->
        NavHost(navController = nav, startDestination = Routes.DASH, modifier = Modifier.padding(padding)) {
            composable(Routes.DASH) { DashboardScreen(vm, nav) }
            composable(Routes.SCAN) { ScannerScreen(vm, nav) }
            composable("network/{ssid}") { back ->
                val encoded = back.arguments?.getString("ssid") ?: ""
                val network = vm.networks.collectAsState().value.firstOrNull { it.ssid == encoded }
                NetworkAnalysisScreen(network, vm, nav)
            }
            composable(Routes.AUDIT) { RouterAuditScreen(vm) }
            composable(Routes.PASSWORD) { PasswordScreen(vm) }
            composable(Routes.CHANNELS) { ChannelScreen(vm) }
            composable(Routes.EDU) { EducationScreen() }
            composable(Routes.HISTORY) { HistoryScreen(vm) }
        }
    }
}

@Composable private fun BottomBar(nav: NavHostController) {
    val current = nav.currentBackStackEntryAsState().value?.destination?.route
    NavigationBar {
        NavItem("Home", Icons.Default.Dashboard, current == Routes.DASH) { nav.navigate(Routes.DASH) }
        NavItem("Scan", Icons.Default.WifiFind, current == Routes.SCAN) { nav.navigate(Routes.SCAN) }
        NavItem("Audit", Icons.Default.Security, current == Routes.AUDIT) { nav.navigate(Routes.AUDIT) }
        NavItem("History", Icons.Default.History, current == Routes.HISTORY) { nav.navigate(Routes.HISTORY) }
    }
}
@Composable private fun RowScope.NavItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) { NavigationBarItem(selected = selected, onClick = onClick, icon = { Icon(icon, null) }, label = { Text(label) }) }

@Composable private fun Screen(title: String, nav: NavHostController? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (nav != null) IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back") }
            Column { Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Wi-Fi Security Auditor", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
        }
        content()
    }
}

@Composable private fun DashboardScreen(vm: MainViewModel, nav: NavHostController) {
    val current by vm.current.collectAsState(); val networks by vm.networks.collectAsState(); val error by vm.error.collectAsState(); val ctx = LocalContext.current
    val matched = networks.firstOrNull { it.bssid != null && it.bssid.equals(current?.bssid, true) }
    val assessment = matched?.let(vm::assess)
    Screen("Dashboard") {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            item {
                val score = assessment?.score
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Security Score", style = MaterialTheme.typography.titleMedium)
                        Text(score?.toString() ?: "—", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black, color = scoreColor(score))
                        Text(if (score == null) "Not enough observable data" else scoreLabel(score), color = scoreColor(score), fontWeight = FontWeight.Bold)
                    }
                }
            }
            item { SectionCard("Current Wi-Fi") {
                InfoRow("SSID", current?.ssid ?: "Not available on this device")
                InfoRow("BSSID", current?.bssid ?: "Not available on this device")
                InfoRow("Security", current?.security ?: "Not available on this device")
                InfoRow("Encryption", current?.encryption ?: "Not available on this device")
                InfoRow("Signal", current?.rssi?.let { "$it dBm" } ?: "Not available on this device")
                InfoRow("Frequency", current?.frequencyMHz?.let { "$it MHz" } ?: "Not available on this device")
                InfoRow("Channel", current?.channel?.toString() ?: "Not available on this device")
                InfoRow("Link speed", current?.linkSpeedMbps?.let { "$it Mbps" } ?: "Not available on this device")
                InfoRow("Local IP", current?.localIp ?: "Not available on this device")
                InfoRow("Gateway", current?.gateway ?: "Not available on this device")
                InfoRow("DNS", current?.dns?.joinToString(", ")?.takeIf { it.isNotBlank() } ?: "Not available on this device")
            } }
            if (assessment != null) item { SectionCard("Findings") { assessment.findings.take(5).forEach { FindingRow(it) } } }
            if (error != null) item { ErrorCard(error!!) { vm.clearError() } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { vm.scan() }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Refresh") }; OutlinedButton(onClick = { nav.navigate(Routes.PASSWORD) }, modifier = Modifier.weight(1f)) { Text("Password") } } }
            item { Text("Observable data only. A score is an assessment of visible configuration signals, not a claim of exploitability.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable private fun ScannerScreen(vm: MainViewModel, nav: NavHostController) {
    val networks by vm.networks.collectAsState(); val scanning by vm.scanning.collectAsState(); val error by vm.error.collectAsState(); var filter by remember { mutableStateOf("All") }; var sort by remember { mutableStateOf("Signal") }
    val filtered = networks.filter { filter == "All" || when (filter) { "2.4 GHz" -> it.frequencyMHz in 2400..2500; "5 GHz" -> it.frequencyMHz in 4900..5900; "6 GHz" -> it.frequencyMHz in 5925..7125; "Open" -> it.security == "Open"; "WPA3" -> it.security.contains("WPA3"); else -> true } }.let { list -> if (sort == "Signal") list.sortedByDescending { it.rssi } else list.sortedBy { it.channel ?: 999 } }
    Screen("Wi-Fi Scanner") {
        Column(Modifier.fillMaxSize()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { Button(onClick = vm::scan, enabled = !scanning) { if (scanning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Default.WifiFind, null); Spacer(Modifier.width(6.dp)); Text(if (scanning) "Scanning…" else "Scan") }; OutlinedButton(onClick = { sort = if (sort == "Signal") "Channel" else "Signal" }) { Text("Sort: $sort") } }
            FilterRow(filter) { filter = it }
            if (error != null) ErrorCard(error!!) { vm.clearError() }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                items(filtered, key = { "${it.bssid}-${it.frequencyMHz}" }) { n -> NetworkCard(n) { nav.navigate("network/${n.ssid}") } }
                if (filtered.isEmpty()) item { EmptyState("No scan results", "Run a scan after granting the required permissions and enabling Wi-Fi/Location services if Android requires it.") }
            }
        }
    }
}

@Composable private fun NetworkAnalysisScreen(network: WifiNetwork?, vm: MainViewModel, nav: NavHostController) {
    Screen("Network Analysis", nav) {
        if (network == null) { EmptyState("Network unavailable", "The scan result may have expired or been refreshed."); return@Screen }
        val a = vm.assess(network); val ctx = LocalContext.current
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            item { SectionCard(network.ssid) { InfoRow("Security", network.security); InfoRow("Encryption", network.encryption); InfoRow("Signal", "${network.rssi} dBm"); InfoRow("Frequency", "${network.frequencyMHz} MHz"); InfoRow("Channel", network.channel?.toString() ?: "Not available"); InfoRow("Generation", network.generation ?: "Not available"); InfoRow("BSSID", network.bssid ?: "Not available") } }
            item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text("Security score: ${a.score}/100", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = scoreColor(a.score)); Spacer(Modifier.height(8.dp)); a.findings.forEach { FindingRow(it) } } } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { vm.saveAssessment(network) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(6.dp)); Text("Save") }; OutlinedButton(onClick = { ReportExporter.shareText(ctx, network, a) }, modifier = Modifier.weight(1f)) { Text("Share TXT") }; OutlinedButton(onClick = { ReportExporter.sharePdf(ctx, network, a) }) { Text("PDF") } } }
            item { Text("Important: scan observations cannot establish password strength, router firmware status, hidden configuration, or exploitability.", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable private fun RouterAuditScreen(vm: MainViewModel) {
    var manufacturer by remember { mutableStateOf("") }; var model by remember { mutableStateOf("") }; var firmware by remember { mutableStateOf("") }; var security by remember { mutableStateOf("WPA2-AES") }
    var wps by remember { mutableStateOf(false) }; var admin by remember { mutableStateOf(false) }; var guest by remember { mutableStateOf(true) }; var upnp by remember { mutableStateOf(false) }; var strong by remember { mutableStateOf(true) }; var updated by remember { mutableStateOf(true) }; var adminPw by remember { mutableStateOf(true) }; var remoteOff by remember { mutableStateOf(true) }; var iot by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<SecurityAssessment?>(null) }
    Screen("My Router Audit") {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Text("Self-audit only: these answers are supplied by you; the app does not log in to the router or probe it.", style = MaterialTheme.typography.bodySmall) }
            item { OutlinedTextField(manufacturer, { manufacturer = it }, label = { Text("Manufacturer") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(model, { model = it }, label = { Text("Router model") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(firmware, { firmware = it }, label = { Text("Firmware version") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(security, { security = it }, label = { Text("Wi-Fi security mode") }, modifier = Modifier.fillMaxWidth()) }
            item { Check("WPS disabled", !wps) { wps = !it }; Check("Admin interface exposed", admin) { admin = it }; Check("Guest network configured", guest) { guest = it }; Check("UPnP enabled", upnp) { upnp = it }; Check("Strong Wi-Fi password", strong) { strong = it }; Check("Firmware updated", updated) { updated = it }; Check("Admin password changed", adminPw) { adminPw = it }; Check("Remote administration disabled", remoteOff) { remoteOff = it }; Check("IoT devices isolated where appropriate", iot) { iot = it } }
            item { Button(onClick = { result = vm.assessRouter(RouterAuditInput(manufacturer, model, firmware, security, wps, admin, guest, upnp, strong, updated, adminPw, remoteOff, iot)) }, modifier = Modifier.fillMaxWidth()) { Text("Generate audit") } }
            result?.let { a -> item { SectionCard("Audit result — ${a.score}/100") { a.findings.forEach { FindingRow(it) }; if (a.findings.isEmpty()) Text("All supplied checklist items passed.") } } }
        }
    }
}

@Composable private fun PasswordScreen(vm: MainViewModel) {
    var password by remember { mutableStateOf("") }; var assessment by remember { mutableStateOf(vm.assessPassword("")) }; var generated by remember { mutableStateOf("") }
    Screen("Password Strength") {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Text("Everything stays on this device. The password is not stored or uploaded.", style = MaterialTheme.typography.bodySmall) }
            item { OutlinedTextField(password, { password = it; assessment = vm.assessPassword(it) }, label = { Text("Wi-Fi password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true) }
            item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text("${assessment.label} — ${assessment.score}/100", style = MaterialTheme.typography.titleLarge, color = scoreColor(assessment.score), fontWeight = FontWeight.Bold); assessment.reasons.forEach { Text("• $it", modifier = Modifier.padding(top = 6.dp)) } } } }
            item { Button(onClick = { generated = vm.generatePassword(); password = generated; assessment = vm.assessPassword(generated) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Shuffle, null); Spacer(Modifier.width(6.dp)); Text("Generate secure password") } }
            if (generated.isNotEmpty()) item { SelectionText(generated) }
            item { Text("This is a heuristic strength estimator, not a password-cracking test. A password can score well and still be exposed elsewhere.", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable private fun ChannelScreen(vm: MainViewModel) {
    val networks by vm.networks.collectAsState(); val grouped = networks.groupBy { it.channel ?: 0 }.toSortedMap(); var band by remember { mutableStateOf("2.4 GHz") }
    val filtered = networks.filter { when (band) { "2.4 GHz" -> it.frequencyMHz in 2400..2500; "5 GHz" -> it.frequencyMHz in 4900..5900; else -> it.frequencyMHz in 5925..7125 } }.groupBy { it.channel ?: 0 }.toSortedMap()
    Screen("Channel Analyzer") {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("2.4 GHz", "5 GHz", "6 GHz").forEach { FilterChip(selected = band == it, onClick = { band = it }, label = { Text(it) }) } }
        Spacer(Modifier.height(12.dp)); Text("Observed networks per channel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
            items(filtered.entries.toList()) { (channel, items) -> val intensity = (items.size * 12).coerceAtMost(100); Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Channel ${if (channel == 0) "unknown" else channel}", fontWeight = FontWeight.Bold); Text("${items.size} networks") }; LinearProgressIndicator({ intensity / 100f }, Modifier.fillMaxWidth().padding(top = 8.dp)); if (items.size >= 6) Text("Potentially congested", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium) } } }
            item { Text("Channel conditions are environmental and change over time. The app recommends observation, not a guaranteed optimal channel.", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable private fun EducationScreen() {
    val topics = listOf(
        "WPA2 vs WPA3" to "WPA3 adds newer authentication protections. WPA2-AES remains widely deployed. Security type alone does not prove a network is exploitable.",
        "WEP" to "WEP is obsolete and should not be used for modern networks.",
        "Wi-Fi encryption" to "Encryption protects wireless traffic from unauthorized observers. Modern deployments generally use AES/CCMP or newer WPA3 ciphers.",
        "WPS" to "Wi-Fi Protected Setup can simplify onboarding. If you do not need it, disabling it reduces unnecessary configuration surface.",
        "Strong passwords" to "Use a long, unique passphrase. Avoid common words, personal details, and reused credentials.",
        "Rogue access points" to "A rogue AP is an unauthorized access point. Defenses include trusted network policies, device management, segmentation, and user awareness.",
        "Evil-twin attacks" to "An attacker may imitate a legitimate network name to trick users or devices. This app explains the concept but does not create or operate such networks.",
        "Deauthentication" to "Deauthentication frames can disrupt Wi-Fi sessions. WiFi Guardian does not transmit deauthentication or packet-injection traffic.",
        "Segmentation & guest networks" to "Separate guest and IoT devices when appropriate so compromise of one device does not automatically expose trusted systems.",
        "Firmware updates" to "Router firmware updates can address security defects. Verify updates through the manufacturer's normal support mechanism."
    )
    Screen("Security Education") { LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) { items(topics) { (t, d) -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(t, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(d, modifier = Modifier.padding(top = 6.dp)) } } } } }
}

@Composable private fun HistoryScreen(vm: MainViewModel) {
    val history by vm.history.collectAsState(); var confirm by remember { mutableStateOf(false) }
    Screen("Security History") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(enabled = history.isNotEmpty(), onClick = { confirm = true }) { Text("Delete all") } }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            items(history, key = { it.id }) { h -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(h.ssid, fontWeight = FontWeight.Bold); Text("${h.score}/100", color = scoreColor(h.score), fontWeight = FontWeight.Bold) }; Text("${DateFormat.getDateTimeInstance().format(Date(h.timestamp))} • ${h.security}", style = MaterialTheme.typography.bodySmall); if (h.findings.isNotBlank()) Text(h.findings, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)); IconButton(onClick = { vm.deleteHistory(h.id) }, modifier = Modifier.align(Alignment.End)) { Icon(Icons.Default.Delete, "Delete") } } } }
            if (history.isEmpty()) item { EmptyState("No saved audits", "Save a network analysis to build a local history.") }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Delete history?") }, text = { Text("This permanently removes all saved local audit records.") }, confirmButton = { TextButton(onClick = { confirm = false; vm.clearHistory() }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}

@Composable private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); content() } } }
@Composable private fun InfoRow(label: String, value: String) { Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 12.dp)) } }
@Composable private fun FindingRow(f: Finding) { val c = when (f.severity) { Severity.HIGH -> MaterialTheme.colorScheme.error; Severity.MEDIUM -> Color(0xFFFFA000); Severity.LOW -> MaterialTheme.colorScheme.tertiary; Severity.INFO -> MaterialTheme.colorScheme.primary }; Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { AssistChip(onClick = {}, label = { Text(f.severity.name) }, colors = AssistChipDefaults.assistChipColors(labelColor = c)); Spacer(Modifier.width(8.dp)); Text(f.title, fontWeight = FontWeight.Bold) }; Text(f.detail, modifier = Modifier.padding(top = 4.dp)); Text("Recommendation: ${f.recommendation}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) } }
@Composable private fun NetworkCard(n: WifiNetwork, onClick: () -> Unit) { Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) { Column(Modifier.padding(14.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(n.ssid, fontWeight = FontWeight.Bold); Text("${n.rssi} dBm") }; Text("${n.security} • ${n.encryption}"); Text("${n.frequencyMHz} MHz • ch ${n.channel ?: "?"} • ${n.generation ?: "Wi-Fi generation unavailable"}", style = MaterialTheme.typography.bodySmall); if (n.wpsAdvertised) Text("WPS advertised", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) } } }
@Composable private fun FilterRow(selected: String, onSelect: (String) -> Unit) { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("All", "2.4 GHz", "5 GHz", "6 GHz", "Open", "WPA3").forEach { FilterChip(selected = selected == it, onClick = { onSelect(it) }, label = { Text(it) }) } } }
@Composable private fun ErrorCard(text: String, onDismiss: () -> Unit) { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Text(text, Modifier.weight(1f)); IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Dismiss") } } } }
@Composable private fun EmptyState(title: String, detail: String) { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.WifiOff, null, Modifier.size(44.dp)); Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)); Text(detail, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) } }
@Composable private fun Check(label: String, checked: Boolean, onChange: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Checkbox(checked, onChange); Text(label) } }
@Composable private fun SelectionText(value: String) { Card(Modifier.fillMaxWidth()) { Text(value, modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold) } }
private fun scoreColor(score: Int?): Color = when { score == null -> Color.Gray; score >= 80 -> Color(0xFF2E7D32); score >= 60 -> Color(0xFFF9A825); else -> Color(0xFFC62828) }
private fun scoreLabel(score: Int): String = when { score >= 80 -> "Strong observable configuration"; score >= 60 -> "Moderate observable configuration"; else -> "Needs attention" }

private fun Context.openLocationSettings() { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
