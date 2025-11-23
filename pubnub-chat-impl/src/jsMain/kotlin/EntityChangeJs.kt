@file:OptIn(ExperimentalJsExport::class)

@JsExport
@JsName("EntityChange")
sealed class EntityChangeJs<out T>(val type: String) {
    data class Updated<T>(val entity: T) : EntityChangeJs<T>("updated")
    data class Removed<T>(val id: String) : EntityChangeJs<T>("removed")
}
