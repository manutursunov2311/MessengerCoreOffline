package tj.messengercoreoffline.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tj.messengercoreoffline.chat.model.ChatId
import tj.messengercoreoffline.chat.usecase.ObserveMessagesUseCase
import tj.messengercoreoffline.chat.usecase.SendMessageUseCase
import tj.messengercoreoffline.connectivity.NetworkMonitor

class ChatViewModel(
    private val sendMessageUseCase: SendMessageUseCase,
    private val observeMessagesUseCase: ObserveMessagesUseCase,
    private val networkMonitor: NetworkMonitor,
    private val scope: CoroutineScope
) {

    private val supportChatId = ChatId("support")

    val messages = observeMessagesUseCase(supportChatId)
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val isOnline = networkMonitor.online
        .stateIn(scope, SharingStarted.Eagerly, true)

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        scope.launch {
            sendMessageUseCase(supportChatId, text.trim())
        }
    }

    fun toggleOnlineStatus() {
        networkMonitor.setOnline(!isOnline.value)
    }
}