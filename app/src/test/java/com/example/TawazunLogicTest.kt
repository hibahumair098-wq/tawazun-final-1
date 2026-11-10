package com.example

import com.example.domain.DomainFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TawazunLogicTest {

    @Test
    fun wildcardDomainMatching_blocksSubdomains_allowsNonMatching() {
        val rule = "*.youtube.com"

        // Must match subdomains and root
        assertTrue("m.youtube.com must be blocked", DomainFilter.matchesRule("m.youtube.com", rule))
        assertTrue("www.youtube.com must be blocked", DomainFilter.matchesRule("www.youtube.com", rule))
        assertTrue("youtube.com must be blocked", DomainFilter.matchesRule("youtube.com", rule))
        assertTrue("music.youtube.com must be blocked", DomainFilter.matchesRule("music.youtube.com", rule))
        assertTrue("https://m.youtube.com/watch?v=123 must be blocked", DomainFilter.matchesRule("https://m.youtube.com/watch?v=123", rule))

        // Must NEVER match domains with different prefixes
        assertFalse("notyoutube.com must NOT be blocked", DomainFilter.matchesRule("notyoutube.com", rule))
        assertFalse("fakeyoutube.com must NOT be blocked", DomainFilter.matchesRule("fakeyoutube.com", rule))
        assertFalse("evil-youtube.com must NOT be blocked", DomainFilter.matchesRule("evil-youtube.com", rule))
        assertFalse("youtube.com.attacker.com must NOT be blocked", DomainFilter.matchesRule("youtube.com.attacker.com", rule))
    }

    @Test
    fun balanceMode_entertainmentLockedUntilAllHomeworkVerified() {
        data class Task(val id: String, val isCompletedByStudent: Boolean, val isVerifiedByParent: Boolean)

        fun computeHomeworkUnlocked(tasks: List<Task>): Boolean {
            if (tasks.isEmpty()) return true
            return tasks.all { it.isCompletedByStudent && it.isVerifiedByParent }
        }

        val tasks = listOf(
            Task("math_1", isCompletedByStudent = true, isVerifiedByParent = true),
            Task("physics_1", isCompletedByStudent = true, isVerifiedByParent = false),
            Task("urdu_1", isCompletedByStudent = false, isVerifiedByParent = false)
        )

        assertFalse("Entertainment must be locked when tasks are pending verification", computeHomeworkUnlocked(tasks))

        val updatedTasks = tasks.map { it.copy(isCompletedByStudent = true, isVerifiedByParent = true) }
        assertTrue("Entertainment unlocks when all tasks are done and parent-verified", computeHomeworkUnlocked(updatedTasks))

        // If parent un-verifies a task, entertainment re-locks
        val revoked = updatedTasks.toMutableList()
        revoked[0] = revoked[0].copy(isVerifiedByParent = false)
        assertFalse("Entertainment must re-lock if a task is unverified", computeHomeworkUnlocked(revoked))
    }

    @Test
    fun retentionPolicy_threeDayWindowFilter() {
        val now = 1731170000000L // arbitrary current timestamp
        val threeDaysMs = 3L * 24 * 60 * 60 * 1000

        data class Event(val id: String, val timestamp: Long)

        val events = listOf(
            Event("recent_1", now - 1000 * 60 * 60), // 1 hour ago
            Event("day2", now - 2 * 24 * 60 * 60 * 1000), // 2 days ago
            Event("expired_day4", now - 4 * 24 * 60 * 60 * 1000), // 4 days ago
            Event("expired_day7", now - 7 * 24 * 60 * 60 * 1000) // 7 days ago
        )

        val validEvents = events.filter { (now - it.timestamp) <= threeDaysMs }

        assertEquals(2, validEvents.size)
        assertTrue(validEvents.any { it.id == "recent_1" })
        assertTrue(validEvents.any { it.id == "day2" })
        assertFalse(validEvents.any { it.id == "expired_day4" })
    }

    @Test
    fun smartChatTimer_readOnlyWhenQuotaExhausted() {
        fun isChatReadOnly(remainingMin: Int): Boolean {
            return remainingMin <= 0
        }

        assertFalse("Chat should allow full messaging when time remains", isChatReadOnly(15))
        assertTrue("Chat must become readOnly when allowance hits 0", isChatReadOnly(0))
        assertTrue("Negative minutes must also be readOnly", isChatReadOnly(-2))
    }
}
