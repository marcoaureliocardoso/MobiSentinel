package com.mobisentinel.app.speech

import com.mobisentinel.app.monitoring.model.Transport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnnouncementQueueTest {
    @Test
    fun firstOfferStartsImmediately() {
        val queue = AnnouncementQueue()
        val first = announcement(Transport.WIFI, "first")

        assertEquals(first, queue.offer(first))
    }

    @Test
    fun secondTransportWaitsForCurrentAnnouncement() {
        val queue = AnnouncementQueue()
        queue.offer(announcement(Transport.WIFI, "current"))

        assertNull(queue.offer(announcement(Transport.CELLULAR, "pending")))
    }

    @Test
    fun newerPendingEventReplacesSameTransportInPlace() {
        val queue = AnnouncementQueue()
        val newerWifi = announcement(Transport.WIFI, "newer Wi-Fi")
        val pendingCellular = announcement(Transport.CELLULAR, "pending cellular")
        queue.offer(announcement(Transport.CELLULAR, "current"))
        queue.offer(announcement(Transport.WIFI, "older Wi-Fi"))
        queue.offer(pendingCellular)

        queue.offer(newerWifi)

        assertEquals(newerWifi, queue.completeCurrent())
        assertEquals(pendingCellular, queue.completeCurrent())
    }

    @Test
    fun completingCurrentReturnsNextItem() {
        val queue = AnnouncementQueue()
        val next = announcement(Transport.CELLULAR, "next")
        queue.offer(announcement(Transport.WIFI, "current"))
        queue.offer(next)

        assertEquals(next, queue.completeCurrent())
    }

    @Test
    fun completingLastItemReturnsNull() {
        val queue = AnnouncementQueue()
        queue.offer(announcement(Transport.WIFI, "only"))

        assertNull(queue.completeCurrent())
    }

    @Test
    fun clearingDropsCurrentAndEveryPendingItem() {
        val queue = AnnouncementQueue()
        val afterClear = announcement(Transport.WIFI, "after clear")
        queue.offer(announcement(Transport.WIFI, "current"))
        queue.offer(announcement(Transport.CELLULAR, "pending"))

        queue.clear()

        assertNull(queue.completeCurrent())
        assertEquals(afterClear, queue.offer(afterClear))
    }

    private fun announcement(transport: Transport, text: String) = Announcement(transport, text)
}
