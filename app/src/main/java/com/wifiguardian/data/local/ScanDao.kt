package com.wifiguardian.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {
    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<ScanEntity>>
    @Insert suspend fun insert(item: ScanEntity)
    @Query("DELETE FROM scan_history WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM scan_history") suspend fun deleteAll()
}
