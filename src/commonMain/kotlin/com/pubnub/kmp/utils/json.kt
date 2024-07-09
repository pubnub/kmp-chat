internal fun Any?.tryLong(): Long? {
    return when (this) {
        is Number -> toLong()
        is String -> toLongOrNull()
        else -> null
    }
}

internal fun Any?.tryInt(): Int? {
    return when (this) {
        is Number -> toInt()
        is String -> toIntOrNull()
        else -> null
    }
}

internal fun Any?.tryDouble(): Double? {
    return when (this) {
        is Number -> toDouble()
        is String -> toDoubleOrNull()
        else -> null
    }
}
