package tj.messengercoreoffline.chat.repository

import kotlinx.coroutines.flow.Flow
import tj.messengercoreoffline.chat.model.ChatId
import tj.messengercoreoffline.chat.model.Message
import tj.messengercoreoffline.common.AppError
import tj.messengercoreoffline.common.Result

interface ChatRepository {
    fun observeMessages(chatId: ChatId): Flow<List<Message>>
    suspend fun sendText(chatId: ChatId, text: String): Result<Message, AppError>
    suspend fun retryFailed(chatId: ChatId): Result<Unit, AppError>
}