package tj.messengercoreoffline.common

sealed interface AppError {
    data object Network : AppError
    data object Timeout : AppError
    data class Unknown(val cause: Throwable) : AppError
}
