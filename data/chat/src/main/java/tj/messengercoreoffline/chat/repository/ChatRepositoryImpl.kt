package tj.messengercoreoffline.chat.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import tj.messengercoreoffline.chat.datasource.LocalDataSource
import tj.messengercoreoffline.chat.datasource.MessageUpdate
import tj.messengercoreoffline.chat.model.ChatId
import tj.messengercoreoffline.chat.model.Message
import tj.messengercoreoffline.chat.model.Status
import tj.messengercoreoffline.chat.transport.Outgoing
import tj.messengercoreoffline.chat.transport.Transport
import tj.messengercoreoffline.chat.transport.TransportResult
import tj.messengercoreoffline.common.AppError
import tj.messengercoreoffline.common.DispatcherProvider
import tj.messengercoreoffline.common.Result
import tj.messengercoreoffline.common.TimeProvider
import tj.messengercoreoffline.connectivity.NetworkMonitor
import java.util.UUID
import kotlin.math.max

class ChatRepositoryImpl(
    private val localDataSource: LocalDataSource,
    private val transport: Transport,
    private val networkMonitor: NetworkMonitor,
    private val timeProvider: TimeProvider,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope
) : ChatRepository {

    private var outboxJob: Job? = null

    init {
        startOutboxProcessor()
    }

    override fun observeMessages(chatId: ChatId): Flow<List<Message>> {
       return localDataSource.observeMessages(chatId)
    }

    override suspend fun sendText(chatId: ChatId, text: String): Result<Message, AppError> {
        val clientId = UUID.randomUUID().toString()
        val currentTime = timeProvider.currentTimeMillis()

        val message = Message(
            id = null,
            clientId = clientId,
            chatId = chatId,
            text = text,
            ts = currentTime,
            status = if (networkMonitor.online.value) Status.Sending else Status.Queued
        )

        localDataSource.insertMessage(message)

        if (networkMonitor.online.value) {
            sendMessageToServer(message)
        }

        return Result.Success(message)
    }

    override suspend fun retryFailed(chatId: ChatId): Result<Unit, AppError> {
        if (!networkMonitor.online.value) {
            return Result.Error(AppError.Network)
        }

        val failedMessages = localDataSource.getQueuedAndFailedMessages(chatId)
        failedMessages.forEach { message ->
            localDataSource.updateMessage(message.clientId, MessageUpdate(status = Status.Sending))
            sendMessageToServer(message.copy(status = Status.Sending))
        }

        return Result.Success(Unit)
    }

    private fun startOutboxProcessor() {
        outboxJob?.cancel()
        outboxJob = scope.launch(dispatchers.io) {
            networkMonitor.online
                .filter { it }
                .collectLatest {
                    processOutbox()
                }
        }
    }

    private suspend fun processOutbox() {
        val supportChatId = ChatId("support")
        val pendingMessages = localDataSource.getQueuedAndFailedMessages(supportChatId)

        pendingMessages.forEach { message ->
            localDataSource.updateMessage(message.clientId, MessageUpdate(status = Status.Sending))
            sendMessageToServer(message.copy(status = Status.Sending))
        }
    }

    private suspend fun sendMessageToServer(message: Message) {
        scope.launch(dispatchers.io) {
            val outgoing = Outgoing(
                clientId = message.clientId,
                text = message.text,
                ts = message.ts
            )

            var attempt = 0
            val maxAttempts = 3
            var backoffMs = 1000L

            while (attempt < maxAttempts) {
                attempt++

                when (val result = transport.send(outgoing)) {
                    is TransportResult.Conflict -> {
                        localDataSource.updateMessage(
                            message.clientId,
                            MessageUpdate(status = Status.Sent)
                        )
                        return@launch
                    }
                    is TransportResult.Network -> {
                        localDataSource.updateMessage(
                            message.clientId,
                            MessageUpdate(status = Status.Queued)
                        )
                        return@launch
                    }
                    is TransportResult.Ok -> {
                        localDataSource.updateMessage(
                            message.clientId,
                            MessageUpdate(status = Status.Sent, id = result.serverMessageId)
                        )
                    }
                    is TransportResult.Timeout -> {
                        if (attempt < maxAttempts) {
                            delay(backoffMs)
                            backoffMs *= 2
                        } else {
                            localDataSource.updateMessage(
                                message.clientId,
                                MessageUpdate(status = Status.Failed)
                            )
                        }
                    }
                    is TransportResult.Unknown -> {
                        localDataSource.updateMessage(
                            message.clientId,
                            MessageUpdate(status = Status.Failed)
                        )
                        return@launch
                    }
                }
            }
        }
    }
}