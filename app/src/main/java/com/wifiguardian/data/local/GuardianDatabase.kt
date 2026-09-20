package com.wifiguardian.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ScanEntity::class], version = 1, exportSchema = false)
abstract class GuardianDatabase : RoomDatabase() { abstract fun scanDao(): ScanDao }
