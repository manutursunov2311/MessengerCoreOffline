package tj.messengercoreoffline.chat.usecase

import tj.messengercoreoffline.chat.model.ChatId
import tj.messengercoreoffline.chat.model.Message
import tj.messengercoreoffline.chat.repository.ChatRepository
import tj.messengercoreoffline.common.AppError
import tj.messengercoreoffline.common.Result

class SendMessageUseCase(
    private val repository: ChatRepository
) {
    suspend operator fun invoke(chatId: ChatId, text: String): Result<Message, AppError> {
        return repository.sendText(chatId, text)
    }
}