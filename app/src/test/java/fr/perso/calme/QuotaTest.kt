package fr.perso.calme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuotaTest {
    private class FakeStore : Quota.Store {
        var v: String? = null
        override fun read() = v
        override fun write(value: String) { v = value }
    }

    @Test fun stopsAtLimit() {
        val q = Quota(FakeStore(), { "2026-11-14" }, 2)
        assertTrue(q.tryConsume()); assertTrue(q.tryConsume())
        assertFalse(q.tryConsume())
        assertEquals(0, q.remaining)
    }

    @Test fun resetsNextDay() {
        var day = "2026-11-14"
        val q = Quota(FakeStore(), { day }, 1)
        assertTrue(q.tryConsume()); assertFalse(q.tryConsume())
        day = "2026-11-15"
        assertEquals(1, q.remaining)
        assertTrue(q.tryConsume())
    }

    @Test fun survivesCorruptedStorage() {
        val s = FakeStore().apply { v = "garbage" }
        assertEquals(3, Quota(s, { "2026-11-14" }, 3).remaining)
    }
}
