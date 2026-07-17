package br.com.marcocardoso.mobisentinel.speech

class AnnouncementQueue {
    private var current: Announcement? = null
    private val pending = ArrayDeque<Announcement>()

    fun offer(item: Announcement): Announcement? {
        if (current == null) {
            current = item
            return item
        }

        val existingIndex = pending.indexOfFirst { it.transport == item.transport }
        if (existingIndex >= 0) {
            pending[existingIndex] = item
        } else {
            pending.addLast(item)
        }
        return null
    }

    fun completeCurrent(): Announcement? {
        current = pending.removeFirstOrNull()
        return current
    }

    fun clear() {
        current = null
        pending.clear()
    }
}
