package com.pubnub.kmp.models.consumers.objects

expect enum class PNKey : SortField {
    ID,
    NAME,
    UPDATED,
    TYPE,
    STATUS;

    override val fieldName: String
}

expect enum class PNMembershipKey : SortField {
    CHANNEL_ID,
    CHANNEL_NAME,
    CHANNEL_UPDATED,
    UPDATED;

    override val fieldName: String
}

expect enum class PNMemberKey : SortField {
    UUID_ID,
    UUID_NAME,
    UUID_UPDATED,
    UPDATED;

    override val fieldName: String
}

expect sealed interface SortField {
    val fieldName: String
}

expect sealed class PNSortKey<T : SortField> {
    class PNAsc<T : SortField>(key: T) : PNSortKey<T>
    class PNDesc<T : SortField>(key: T) : PNSortKey<T>

    fun toSortParameter(): String

    companion object {
        fun asc(key: PNKey): PNSortKey<PNKey>
        fun desc(key: PNKey): PNSortKey<PNKey>
    }

    internal val key: T
    internal val dir: String
}

