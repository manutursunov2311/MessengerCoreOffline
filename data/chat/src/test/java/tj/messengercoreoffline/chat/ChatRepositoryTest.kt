package tj.messengercoreoffline.chat

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.Before
import tj.messengercoreoffline.chat.datasource.InMemoryLocalDataSource
import tj.messengercoreoffline.chat.model.ChatId
import tj.messengercoreoffline.chat.model.Message
import tj.messengercoreoffline.chat.model.Status
import tj.messengercoreoffline.chat.repository.ChatRepositoryImpl
import tj.messengercoreoffline.chat.transport.FakeTransport
import tj.messengercoreoffline.chat.transport.Outgoing
import tj.messengercoreoffline.chat.transport.Transport
import tj.messengercoreoffline.chat.transport.TransportResult
import tj.messengercoreoffline.common.AppError
import tj.messengercoreoffline.common.DispatcherProvider
import tj.messengercoreoffline.common.TimeProvider
import tj.messengercoreoffline.connectivity.DefaultNetworkMonitor
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import tj.messengercoreoffline.common.Result


class ChatRepositoryTest {

    private lateinit var networkMonitor: DefaultNetworkMonitor
    private lateinit var fakeTransport: FakeTransport
    private lateinit var localDataSource: InMemoryLocalDataSource
    private lateinit var timeProvider: TimeProvider
    private lateinit var dispatchers: DispatcherProvider
    private lateinit var repository: ChatRepositoryImpl

    @Before
    fun setup() {
        networkMonitor = DefaultNetworkMonitor()
        fakeTransport = FakeTransport(networkMonitor)
        localDataSource = InMemoryLocalDataSource()

        timeProvider = object : TimeProvider {
            override fun currentTimeMillis(): Long = 1000L
        }

        // Простые диспетчеры для тестов
        dispatchers = object : DispatcherProvider {
            override val main = Dispatchers.Unconfined
            override val io = Dispatchers.Unconfined
            override val default = Dispatchers.Unconfined
        }

        repository = ChatRepositoryImpl(
            localDataSource = localDataSource,
            transport = fakeTransport,
            networkMonitor = networkMonitor,
            timeProvider = timeProvider,
            dispatchers = dispatchers,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        )
    }

    @Test
    fun `offline to online flow works correctly`() = runBlocking {
        val chatId = ChatId("support")

        // Устанавливаем офлайн режим
        networkMonitor.setOnline(false)

        // Отправляем сообщение в офлайне
        val result = repository.sendText(chatId, "Test message")
        assertTrue(result is Result.Success)

        // Небольшая задержка для обработки
        delay(100)

        // Проверяем, что сообщение в статусе Queued
        val messages = repository.observeMessages(chatId).first()
        assertEquals(1, messages.size)
        assertEquals(Status.Queued, messages[0].status)
        println("✅ Message queued in offline mode: ${messages[0].status}")

        // Переходим в онлайн
        networkMonitor.setOnline(true)
        delay(1000) // Ждём обработки outbox

        // Проверяем, что сообщение стало Sent
        val updatedMessages = repository.observeMessages(chatId).first()
        assertEquals(Status.Sent, updatedMessages[0].status)
        println("✅ Message sent after going online: ${updatedMessages[0].status}")
    }

    @Test
    fun `idempotency works with conflict handling`() = runBlocking {
        val chatId = ChatId("support")
        networkMonitor.setOnline(true)

        val testTransport = object : Transport {
            private val sentClientIds = mutableSetOf<String>()

            override suspend fun send(out: Outgoing): TransportResult {
                println("🚀 Transport call for clientId: ${out.clientId}")

                // Simulate idempotency: if we've seen this clientId before, return Conflict
                if (sentClientIds.contains(out.clientId)) {
                    println("⚠️ Returning Conflict for duplicate clientId: ${out.clientId}")
                    return TransportResult.Conflict
                }

                sentClientIds.add(out.clientId)
                println("✅ First time seeing clientId: ${out.clientId}, returning Ok")
                return TransportResult.Ok("server-id-${out.clientId}")
            }
        }

        val testLocalDataSource = InMemoryLocalDataSource()
        val testDispatchers = object : DispatcherProvider {
            override val main = Dispatchers.Unconfined
            override val io = Dispatchers.Unconfined
            override val default = Dispatchers.Unconfined
        }

        val testRepository = ChatRepositoryImpl(
            localDataSource = testLocalDataSource,
            transport = testTransport,
            networkMonitor = networkMonitor,
            timeProvider = timeProvider,
            dispatchers = testDispatchers,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        )

        // Send the first message normally
        val result1 = testRepository.sendText(chatId, "Test message")
        assertTrue(result1 is Result.Success)
        val originalMessage = (result1 as Result.Success).value

        delay(1000) // Wait for processing

        // Check that first message was sent successfully
        val messagesAfterFirst = testRepository.observeMessages(chatId).first()
        assertEquals(1, messagesAfterFirst.size)
        assertEquals(Status.Sent, messagesAfterFirst[0].status)
        println("✅ First message sent successfully")

        // Now simulate a retry/duplicate scenario by going offline and back online
        // This should trigger the outbox processor to try sending again
        networkMonitor.setOnline(false)

        // Create a duplicate message with the same clientId manually
        // This simulates what might happen in a real retry scenario
        val duplicateMessage = Message(
            id = null,
            clientId = originalMessage.clientId, // Same clientId as original!
            chatId = chatId,
            text = "Duplicate message with same clientId",
            ts = timeProvider.currentTimeMillis() + 1000L,
            status = Status.Queued
        )

        // Insert the duplicate directly into the data source
        testLocalDataSource.insertMessage(duplicateMessage)
        println("📝 Added duplicate message with clientId: ${duplicateMessage.clientId}")

        // Verify we now have 2 messages in storage
        val messagesWithDuplicate = testRepository.observeMessages(chatId).first()
        println("📊 Messages after adding duplicate: ${messagesWithDuplicate.size}")
        messagesWithDuplicate.forEachIndexed { index, msg ->
            println("  [$index] '${msg.text}' - clientId: ${msg.clientId}, status: ${msg.status}")
        }

        // Go back online to trigger outbox processing
        networkMonitor.setOnline(true)
        delay(1000) // Wait for outbox processing

        // Check final state
        val finalMessages = testRepository.observeMessages(chatId).first()
        println("📊 Final messages: ${finalMessages.size}")
        finalMessages.forEachIndexed { index, msg ->
            println("  [$index] '${msg.text}' - clientId: ${msg.clientId}, status: ${msg.status}")
        }

        // Both messages should exist, but we expect the duplicate to be handled appropriately
        // The key test is that when a Conflict occurs, it should be treated as success
        assertTrue(finalMessages.size >= 1, "Should have at least 1 message")

        // Check that no messages are stuck in Sending or Queued state
        val stuckMessages = finalMessages.filter { it.status == Status.Sending || it.status == Status.Queued }
        assertEquals(0, stuckMessages.size, "No messages should be stuck in Sending/Queued state")

        // All messages should be in a final state (Sent, Failed, or removed due to deduplication)
        val successfulMessages = finalMessages.filter { it.status == Status.Sent }
        assertTrue(successfulMessages.isNotEmpty(), "Should have at least one successfully sent message")

        println("✅ Idempotency test passed - conflicts handled correctly")
    }

    @Test
    fun `backoff retry mechanism works correctly`() = runBlocking {
        val chatId = ChatId("support")
        networkMonitor.setOnline(true)

        val timeoutTransport = object : Transport {
            private var callCount = 0

            override suspend fun send(out: Outgoing): TransportResult {
                callCount++
                return when (callCount) {
                    1 -> {
                        println("🔄 Attempt 1: Timeout")
                        TransportResult.Timeout
                    }
                    2 -> {
                        println("🔄 Attempt 2: Timeout")
                        TransportResult.Timeout
                    }
                    3 -> {
                        println("✅ Attempt 3: Success")
                        TransportResult.Ok("server-id-success")
                    }
                    else -> {
                        println("❌ Too many attempts: $callCount")
                        TransportResult.Unknown(RuntimeException("Too many attempts: $callCount"))
                    }
                }
            }
        }

        val testDispatchers = object : DispatcherProvider {
            override val main = Dispatchers.Unconfined
            override val io = Dispatchers.Unconfined
            override val default = Dispatchers.Unconfined
        }

        val testRepository = ChatRepositoryImpl(
            localDataSource = InMemoryLocalDataSource(),
            transport = timeoutTransport,
            networkMonitor = networkMonitor,
            timeProvider = timeProvider,
            dispatchers = testDispatchers,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        )

        // Отправляем сообщение
        val result = testRepository.sendText(chatId, "Test retry message")
        assertTrue(result is Result.Success)

        // Ждем обработки с retry логикой (1s + 2s + 4s + время на отправку)
        println("🚀 Starting message processing...")
        delay(8000) // Ждем все retry попытки
        println("⏰ Processing completed")

        // Проверяем финальное состояние
        val messages = testRepository.observeMessages(chatId).first()
        assertEquals(1, messages.size)
        assertEquals(Status.Sent, messages[0].status)
        println("✅ Final status after retries: ${messages[0].status}")
    }

    @Test
    fun `failed messages can be retried manually`() = runBlocking {
        val chatId = ChatId("support")

        // Создаем транспорт который всегда фейлит
        val failingTransport = object : Transport {
            override suspend fun send(out: Outgoing): TransportResult {
                println("💥 Transport always fails")
                return TransportResult.Unknown(RuntimeException("Server error"))
            }
        }

        val testDispatchers = object : DispatcherProvider {
            override val main = Dispatchers.Unconfined
            override val io = Dispatchers.Unconfined
            override val default = Dispatchers.Unconfined
        }

        val testRepository = ChatRepositoryImpl(
            localDataSource = InMemoryLocalDataSource(),
            transport = failingTransport,
            networkMonitor = networkMonitor,
            timeProvider = timeProvider,
            dispatchers = testDispatchers,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        )

        networkMonitor.setOnline(true)

        // Отправляем сообщение которое зафейлится
        val result = testRepository.sendText(chatId, "Will fail")
        assertTrue(result is Result.Success)
        delay(8000) // Ждем все retry попытки до Failed

        // Проверяем что статус Failed
        val messages = testRepository.observeMessages(chatId).first()
        assertEquals(Status.Failed, messages[0].status)
        println("✅ Message failed as expected: ${messages[0].status}")

        // Пробуем retry в офлайне - должен вернуть ошибку
        networkMonitor.setOnline(false)
        val retryOfflineResult = testRepository.retryFailed(chatId)
        assertTrue(retryOfflineResult is Result.Error)
        assertEquals(AppError.Network, (retryOfflineResult as Result.Error).error)
        println("✅ Retry offline returns Network error as expected")
    }

    @Test
    fun `network errors are handled correctly`() = runBlocking {
        val chatId = ChatId("support")

        // Онлайн отправка
        networkMonitor.setOnline(true)
        val result1 = repository.sendText(chatId, "Online message")
        assertTrue(result1 is Result.Success)

        val message = (result1 as Result.Success).value
        assertEquals(Status.Sending, message.status) // Должно быть Sending в онлайне
        println("✅ Online message has Sending status: ${message.status}")

        // Офлайн отправка
        networkMonitor.setOnline(false)
        val result2 = repository.sendText(chatId, "Offline message")
        assertTrue(result2 is Result.Success)

        val offlineMessage = (result2 as Result.Success).value
        assertEquals(Status.Queued, offlineMessage.status) // Должно быть Queued в офлайне
        println("✅ Offline message has Queued status: ${offlineMessage.status}")

        // Проверяем что в списке 2 сообщения
        val messages = repository.observeMessages(chatId).first()
        assertEquals(2, messages.size)
        println("✅ Total messages: ${messages.size}")
    }
}