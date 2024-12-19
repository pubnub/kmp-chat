package com.pubnub.kmp

import com.pubnub.api.models.consumer.objects.PNKey
import com.pubnub.api.models.consumer.objects.PNMemberKey
import com.pubnub.api.models.consumer.objects.PNMembershipKey
import com.pubnub.api.models.consumer.objects.PNSortKey
import extractSortKeys
import kotlin.test.Test
import kotlin.test.assertEquals

class ConvertersTest {
    @Test
    fun test_extractSortKeys() {
        val sortKeys1 =
            js(
                """{"updated": null, "status": "asc", "type": "desc", "channel.id": null, "channel.name": "asc", "channel.updated": "desc", "channel.status": null, "channel.type": "asc"}"""
            )
        val sortKeysResult1: List<PNSortKey<PNMembershipKey>> = extractSortKeys(sortKeys1)
        assertEquals(
            listOf(
                PNSortKey.PNAsc(PNMembershipKey.UPDATED),
                PNSortKey.PNAsc(PNMembershipKey.STATUS),
                PNSortKey.PNDesc(PNMembershipKey.TYPE),
                PNSortKey.PNAsc(PNMembershipKey.CHANNEL_ID),
                PNSortKey.PNAsc(PNMembershipKey.CHANNEL_NAME),
                PNSortKey.PNDesc(PNMembershipKey.CHANNEL_UPDATED),
                PNSortKey.PNAsc(PNMembershipKey.CHANNEL_STATUS),
                PNSortKey.PNAsc(PNMembershipKey.CHANNEL_TYPE),
            ).map { it.toSortParameter() },
            sortKeysResult1.map { it.toSortParameter() }
        )

        val sortKeys2 =
            js(
                """{"updated": null, "status": "asc", "type": "desc", "uuid.id": null, "uuid.name": "asc", "uuid.updated": "desc", "uuid.status": null, "uuid.type": "asc"}"""
            )
        val sortKeysResult2: List<PNSortKey<PNMemberKey>> = extractSortKeys(sortKeys2)

        assertEquals(
            listOf(
                PNSortKey.PNAsc(PNMemberKey.UPDATED),
                PNSortKey.PNAsc(PNMemberKey.STATUS),
                PNSortKey.PNDesc(PNMemberKey.TYPE),
                PNSortKey.PNAsc(PNMemberKey.UUID_ID),
                PNSortKey.PNAsc(PNMemberKey.UUID_NAME),
                PNSortKey.PNDesc(PNMemberKey.UUID_UPDATED),
                PNSortKey.PNAsc(PNMemberKey.UUID_STATUS),
                PNSortKey.PNAsc(PNMemberKey.UUID_TYPE),
            ).map { it.toSortParameter() },
            sortKeysResult2.map { it.toSortParameter() }
        )

        val sortKeys3 = js("""{"updated": null, "status": "asc", "type": "desc", "id": null, "name": "asc"}""")
        val sortKeysResult3: List<PNSortKey<PNKey>> = extractSortKeys(sortKeys3)

        assertEquals(
            listOf(
                PNSortKey.PNAsc(PNKey.UPDATED),
                PNSortKey.PNAsc(PNKey.STATUS),
                PNSortKey.PNDesc(PNKey.TYPE),
                PNSortKey.PNAsc(PNKey.ID),
                PNSortKey.PNAsc(PNKey.NAME),
            ).map { it.toSortParameter() },
            sortKeysResult3.map { it.toSortParameter() }
        )
    }
}
