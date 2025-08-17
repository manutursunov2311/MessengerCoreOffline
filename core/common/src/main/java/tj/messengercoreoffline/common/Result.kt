package tj.messengercoreoffline.common

sealed interface Result<out T, out E> {
    data class Success<T>(val value: T) : Result<T, Nothing>
    data class Error<E>(val error: E) : Result<Nothing, E>
}

inline fun <T, E> Result<T, E>.fold(
    onSuccess: (T) -> Unit = {},
    onError: (E) -> Unit = {}
) {
    when (this) {
        is Result.Success -> onSuccess(value)
        is Result.Error -> onError(error)
    }
}

inline fun <T, E, R> Result<T, E>.map(transform: (T) -> R) : Result<R, E> {
    return when (this) {
        is Result.Success -> Result.Success(transform(value))
        is Result.Error -> this
    }
}