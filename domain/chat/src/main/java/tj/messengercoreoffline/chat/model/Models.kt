package tj.messengercoreoffline.chat.model

@JvmInline
value class ChatId(val value: String)

@JvmInline
value class MessageId(val value: String)

enum class Status {
    Queued, Sending, Sent, Failed
}

data class Message(
    val id: MessageId?,
    val clientId: String,
    val chatId: ChatId,
    val text: String,
    val ts: Long,
    val status: Status
)