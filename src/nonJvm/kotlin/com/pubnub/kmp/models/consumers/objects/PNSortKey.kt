package com.pubnub.kmp.models.consumers.objects

actual sealed interface SortField {
    actual val fieldName: String
}

actual enum class PNKey(actual override val fieldName: String) : SortField {
    ID("id"),
    NAME("name"),
    UPDATED("updated"),
    TYPE("type"),
    STATUS("status"),
}

actual enum class PNMembershipKey(actual override val fieldName: String) : SortField {
    CHANNEL_ID("channel.id"),
    CHANNEL_NAME("channel.name"),
    CHANNEL_UPDATED("channel.updated"),
    UPDATED("updated"),
}

actual enum class PNMemberKey(actual override val fieldName: String) : SortField {
    UUID_ID("uuid.id"),
    UUID_NAME("uuid.name"),
    UUID_UPDATED("uuid.updated"),
    UPDATED("updated"),
}

actual sealed class PNSortKey<T : SortField> constructor(
    internal actual val key: T,
    internal actual val dir: String,
) {
    actual class PNAsc<T : SortField> actual constructor(key: T) : PNSortKey<T>(key = key, dir = "asc")

    actual class PNDesc<T : SortField> actual constructor(key: T) : PNSortKey<T>(key = key, dir = "desc")

    actual fun toSortParameter(): String {
        return key.fieldName + ":" + dir
    }

    actual companion object {
        actual fun asc(key: PNKey): PNSortKey<PNKey> {
            return PNAsc(key)
        }

        actual fun desc(key: PNKey): PNSortKey<PNKey> {
            return PNDesc(key)
        }
    }
}
