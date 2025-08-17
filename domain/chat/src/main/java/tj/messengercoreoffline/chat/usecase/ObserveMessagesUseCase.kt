package tj.messengercoreoffline.chat.usecase

import kotlinx.coroutines.flow.Flow
import tj.messengercoreoffline.chat.model.ChatId
import tj.messengercoreoffline.chat.model.Message
import tj.messengercoreoffline.chat.repository.ChatRepository

class ObserveMessagesUseCase(
    private val repository: ChatRepository
) {
    operator fun invoke(chatId: ChatId): Flow<List<Message>> {
        return repository.observeMessages(chatId)
    }
}