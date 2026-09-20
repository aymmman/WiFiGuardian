package com.wifiguardian.data.wifi

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.wifiguardian.domain.model.CurrentWifi
import com.wifiguardian.domain.model.WifiNetwork
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class WifiRepository(private val context: Context) {
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivity = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun permissionGranted(): Boolean = if (Build.VERSION.SDK_INT >= 33) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    } else ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun observeCurrent(): Flow<CurrentWifi?> = callbackFlow {
        fun emitCurrent() { try { trySend(readCurrent()) } catch (_: SecurityException) { trySend(null) } catch (_: Exception) { trySend(null) } }
        emitCurrent()
        val receiver = object : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) { emitCurrent() } }
        val filter = IntentFilter().apply {
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(WifiManager.RSSI_CHANGED_ACTION)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        awaitClose { context.unregisterReceiver(receiver) }
    }

    fun scan(): Flow<Result<List<WifiNetwork>>> = callbackFlow {
        if (!permissionGranted()) { trySend(Result.failure(SecurityException("Wi-Fi permission not granted"))); close(); return@callbackFlow }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val updated = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                try {
                    val results = wifi.scanResults.map { it.toModel() }
                    trySend(if (updated) Result.success(results) else Result.success(results))
                } catch (e: SecurityException) { trySend(Result.failure(e)) }
                close()
            }
        }
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        try {
            if (!wifi.startScan()) {
                val cached = wifi.scanResults.map { it.toModel() }
                if (cached.isNotEmpty()) trySend(Result.success(cached)) else trySend(Result.failure(IllegalStateException("Wi-Fi scan request was throttled or failed")))
                close()
            }
        } catch (e: Exception) { trySend(Result.failure(e)); close() }
        awaitClose { try { context.unregisterReceiver(receiver) } catch (_: Exception) {} }
    }

    private fun ScanResult.toModel(): WifiNetwork {
        val (security, encryption) = WifiAnalyzer.fromCapabilities(capabilities ?: "")
        return WifiNetwork(
            ssid = SSID.takeIf { it.isNotBlank() } ?: "Hidden / unavailable",
            bssid = BSSID.takeIf { it.isNotBlank() }, rssi = level, frequencyMHz = frequency,
            channel = WifiAnalyzer.channel(frequency), generation = WifiAnalyzer.generation(capabilities ?: ""),
            capabilities = capabilities ?: "", security = security, encryption = encryption,
            wpsAdvertised = (capabilities ?: "").contains("WPS", ignoreCase = true)
        )
    }

    private fun readCurrent(): CurrentWifi? {
        val network = connectivity.activeNetwork ?: return null
        val caps = connectivity.getNetworkCapabilities(network) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
        val info = if (Build.VERSION.SDK_INT >= 31) caps.transportInfo as? WifiInfo else @Suppress("DEPRECATION") wifi.connectionInfo
        val link = connectivity.getLinkProperties(network)
        val ssid = info?.ssid?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }?.trim('"')
        val bssid = info?.bssid?.takeIf { it.isNotBlank() && it != "02:00:00:00:00:00" }
        val freq = info?.frequency?.takeIf { it > 0 }
        val scanMatch = try { wifi.scanResults.firstOrNull { it.BSSID.equals(bssid, true) } } catch (_: Exception) { null }
        val (security, encryption) = WifiAnalyzer.fromCapabilities(scanMatch?.capabilities ?: "")
        val addresses = link?.linkAddresses.orEmpty().mapNotNull { it.address.hostAddress }
        val gateway = link?.routes?.firstOrNull { it.gateway?.hostAddress != null }?.gateway?.hostAddress
        return CurrentWifi(
            ssid, bssid, security.takeIf { scanMatch != null }, encryption.takeIf { scanMatch != null },
            info?.rssi?.takeIf { it > -127 }, freq, freq?.let { WifiAnalyzer.channel(it) }, info?.linkSpeed?.takeIf { it >= 0 },
            addresses.firstOrNull(), gateway, link?.dnsServers.orEmpty().mapNotNull { it.hostAddress },
            scanMatch?.capabilities, scanMatch?.let { it.capabilities.contains("WPS", true) }, scanMatch?.let { WifiAnalyzer.generation(it.capabilities) }
        )
    }
}
