package tj.messengercoreoffline.connectivity

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface NetworkMonitor {
    val online: StateFlow<Boolean>
    fun setOnline(isOnline: Boolean)
}

class DefaultNetworkMonitor : NetworkMonitor {
    private val _online = MutableStateFlow(true);
    override val online: StateFlow<Boolean>
        get() = _online.asStateFlow()

    override fun setOnline(isOnline: Boolean) {
        _online.value = isOnline
    }
}