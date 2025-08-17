package tj.messengercoreoffline.chat.transport

import kotlinx.coroutines.delay
import tj.messengercoreoffline.connectivity.NetworkMonitor
import java.util.UUID
import kotlin.random.Random

class FakeTransport(
    private val networkMonitor: NetworkMonitor
) : Transport {

    private val sentClientIds = mutableSetOf<String>()

    override suspend fun send(out: Outgoing): TransportResult {
        if (!networkMonitor.online.value) {
            return TransportResult.Network
        }

        delay(Random.nextLong(250, 751))

        if (out.clientId in sentClientIds) {
            return TransportResult.Conflict
        }

        if (Random.nextFloat() < 0.1f) {
            return TransportResult.Timeout
        }

        sentClientIds.add(out.clientId)
        val serverId = UUID.randomUUID().toString()
        return TransportResult.Ok(serverId)
    }

    fun reset() {
        sentClientIds.clear()
    }
}