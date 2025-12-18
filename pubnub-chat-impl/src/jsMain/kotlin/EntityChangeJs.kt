@file:OptIn(ExperimentalJsExport::class)

external interface EntityChangeHandler<T> {
    val onUpdated: ((entity: T) -> Unit)?
    val onRemoved: ((id: String) -> Unit)?
}

@JsExport
@JsName("EntityChange")
sealed class EntityChangeJs<T>(val type: String) {
    data class Updated<T>(val entity: T) : EntityChangeJs<T>("updated")

    data class Removed<T>(val id: String) : EntityChangeJs<T>("removed")

    fun handle(handler: EntityChangeHandler<T>) {
        when (this) {
            is Updated -> handler.onUpdated?.invoke(entity)
            is Removed -> handler.onRemoved?.invoke(id)
        }
    }
}
