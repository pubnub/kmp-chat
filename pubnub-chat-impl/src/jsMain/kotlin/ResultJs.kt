@file:OptIn(ExperimentalJsExport::class)

@JsExport
@JsName("Result")
sealed class ResultJs<T>(val success: Boolean) {
    data class Success<T>(val data: T) : ResultJs<T>(true)

    data class Failure(val error: String) : ResultJs<Nothing>(false)

    fun onSuccess(callback: (T) -> Unit): ResultJs<T> {
        if (this is Success) {
            callback(data)
        }
        return this
    }

    fun onFailure(callback: (String) -> Unit): ResultJs<T> {
        if (this is Failure) {
            callback(error)
        }
        return this
    }
}
