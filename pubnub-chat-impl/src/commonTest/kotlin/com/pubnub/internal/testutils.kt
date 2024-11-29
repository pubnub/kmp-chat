import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

suspend fun delayForHistory() = withContext(Dispatchers.Default) {
    delay(1.seconds)
}
