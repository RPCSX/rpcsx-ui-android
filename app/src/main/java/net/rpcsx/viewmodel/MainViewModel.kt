
package net.rpcsx.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow

class MainViewModel : ViewModel() {
    private val _isBottomNavigationVisible = MutableStateFlow(true)
    val isBottomNavigationVisible = _isBottomNavigationVisible.asStateFlow()

    private val _itemUsageMap = MutableStateFlow<Map<String, Long>>(emptyMap())
    val itemUsageMap: StateFlow<Map<String, Long>> = _itemUsageMap    

    fun setBottomNavigationVisibility(visible: Boolean) {
        _isBottomNavigationVisible.value = visible
    }

    fun recordItemUsage(key: String) {
        _itemUsageMap.value = _itemUsageMap.value.toMutableMap().apply {
            this[key] = System.currentTimeMillis()
        }
    }
}
