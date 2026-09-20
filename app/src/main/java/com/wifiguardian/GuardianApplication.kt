package com.wifiguardian

import android.app.Application
import androidx.room.Room
import com.wifiguardian.data.local.GuardianDatabase
import com.wifiguardian.data.repository.HistoryRepository
import com.wifiguardian.data.wifi.WifiRepository

class GuardianApplication : Application() {
    lateinit var wifiRepository: WifiRepository
    lateinit var historyRepository: HistoryRepository
        private set

    override fun onCreate() {
        super.onCreate()
        wifiRepository = WifiRepository(this)
        val db = Room.databaseBuilder(this, GuardianDatabase::class.java, "wifi_guardian.db").build()
        historyRepository = HistoryRepository(db.scanDao())
    }
}
