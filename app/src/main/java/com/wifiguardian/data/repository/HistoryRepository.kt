package com.wifiguardian.data.repository

import com.wifiguardian.data.local.ScanDao
import com.wifiguardian.data.local.ScanEntity

class HistoryRepository(private val dao: ScanDao) {
    val history = dao.observeAll()
    suspend fun save(entity: ScanEntity) = dao.insert(entity)
    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun clear() = dao.deleteAll()
}
