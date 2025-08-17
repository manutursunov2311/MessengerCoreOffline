package tj.messengercoreoffline.chat.transport

data class Outgoing(
    val clientId: String,
    val text: String,
    val ts: Long
)

interface Transport {
    suspend fun send(out: Outgoing): TransportResult
}

sealed interface TransportResult {
    data class Ok(val serverMessageId: String) : TransportResult
    data object Conflict : TransportResult
    data object Network : TransportResult
    data object Timeout : TransportResult
    data class Unknown(val cause: Throwable) : TransportResult
}