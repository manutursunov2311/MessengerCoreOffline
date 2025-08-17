package tj.messengercoreoffline.chat.datasource

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import tj.messengercoreoffline.chat.model.ChatId
import tj.messengercoreoffline.chat.model.Message
import tj.messengercoreoffline.chat.model.MessageId
import tj.messengercoreoffline.chat.model.Status

interface LocalDataSource {
    fun observeMessages(chatId: ChatId): Flow<List<Message>>
    suspend fun insertMessage(message: Message)
    suspend fun updateMessage(clientId: String, update: MessageUpdate)
    suspend fun getQueuedAndFailedMessages(chatId: ChatId): List<Message>
}

data class MessageUpdate(
    val id: String? = null,
    val status: Status? = null
)

class InMemoryLocalDataSource : LocalDataSource {

    private val messages = MutableStateFlow<Map<String, Message>>(emptyMap())

    override fun observeMessages(chatId: ChatId): Flow<List<Message>> {
        return messages.map { messagesMap ->
            messagesMap.values
                .filter { it.chatId == chatId }
                .sortedBy { it.ts }
        }
    }

    override suspend fun insertMessage(message: Message) {
        val current = messages.value.toMutableMap()
        current[message.clientId] = message
        messages.value = current
    }

    override suspend fun updateMessage(clientId: String, update: MessageUpdate) {
        val current = messages.value.toMutableMap()
        val message = current[clientId] ?: return

        val updated = message.copy(
            id = update.id?.let { MessageId(it) } ?: message.id,
            status = update.status ?: message.status
        )

        current[clientId] = updated
        messages.value = current
    }

    override suspend fun getQueuedAndFailedMessages(chatId: ChatId): List<Message> {
        return messages.value.values
            .filter { it.chatId == chatId && (it.status == Status.Queued || it.status == Status.Failed) }
            .sortedBy { it.ts }
    }
}