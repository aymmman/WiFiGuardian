package com.wifiguardian.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.wifiguardian.data.repository.HistoryRepository
import com.wifiguardian.data.wifi.WifiRepository

class ViewModelFactory(private val wifi: WifiRepository, private val history: HistoryRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(wifi, history) as T
}
