@file:OptIn(ExperimentalJsExport::class, ExperimentalJsStatic::class)

import com.pubnub.api.utils.TimetokenUtil
import kotlin.js.Date

@JsExport
class TimetokenUtils {
    companion object {
        @JsStatic
        fun unixToTimetoken(unixTime: Any): Double {
            val unixTimeNumber = unixTime.tryLong() ?: error("The value passed as unixTime is NaN")
            return TimetokenUtil.unixToTimetoken(unixTimeNumber).toDouble()
        }

        @JsStatic
        fun timetokenToUnix(timetoken: Any): Double {
            val timetokenNumber = timetoken.tryLong() ?: error("The value passed as timetoken is NaN")
            return TimetokenUtil.timetokenToUnix(timetokenNumber).toDouble()
        }

        @JsStatic
        fun timetokenToDate(timetoken: Any): Date {
            return Date(timetokenToUnix(timetoken).toDouble())
        }

        @JsStatic
        fun dateToTimetoken(date: Date): Double {
            @Suppress("USELESS_IS_CHECK")
            if (date !is Date) {
                error("The value passed as date is not an instance of Date")
            }
            return unixToTimetoken(date.getTime())
        }
    }
}
