package com.wifiguardian.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_history")
data class ScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val ssid: String,
    val bssid: String?,
    val security: String,
    val score: Int,
    val findings: String,
    val recommendations: String
)
