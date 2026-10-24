package org.koitharu.kotatsu.scrobbling.discord.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiscordPresenceControllerTest {

	private class FakeTransport : DiscordPresenceTransport {
		var active = false
		var canConnect = true
		var connects = 0
		var disconnects = 0
		val statuses = ArrayList<PresenceStatus>()
		val activities = ArrayList<DiscordNativeActivity>()
		override val isActive get() = active

		override suspend fun connect(): Boolean {
			if (!canConnect) return false
			connects++
			active = true
			return true
		}

		override fun setOnlineStatus(status: PresenceStatus) {
			statuses += status
		}

		override fun setActivity(activity: DiscordNativeActivity) {
			activities += activity
		}

		override fun disconnect(restoreStatus: Boolean) {
			disconnects++
			active = false
			if (restoreStatus) statuses += PresenceStatus.Online
		}
	}

	private var config = DiscordPresenceConfig(isEnabled = true, isSignedIn = true, isReadingPresence = true)
	private val transport = FakeTransport()
	private val readerA = Any()
	private val readerB = Any()

	private fun TestScope.controller() = DiscordPresenceController(
		transport = transport,
		scope = backgroundScope,
		elapsedRealtime = { currentTime },
		epochSeconds = { 1_000 + currentTime / 1000 },
		config = { config },
	)

	private fun activity(chapter: String = "1") = suspend { DiscordNativeActivity(name = "n", state = chapter) }

	@Test
	fun `reading sets do not disturb and shows the activity`() = runTest {
		val c = controller()
		c.enter(readerA)
		c.update(readerA, true, build = activity())
		runCurrent()
		assertEquals(1, transport.connects)
		assertEquals(PresenceStatus.DoNotDisturb, transport.statuses.last())
		assertEquals(1, transport.activities.size)
	}

	@Test
	fun `idle reader sets idle and active reading goes back to do not disturb`() = runTest {
		val c = controller()
		c.update(readerA, true, build = activity())
		runCurrent()
		c.setIdle(readerA)
		assertEquals(PresenceStatus.Idle, transport.statuses.last())
		c.update(readerA, true, build = activity("2"))
		assertEquals(PresenceStatus.DoNotDisturb, transport.statuses.last())
	}

	@Test
	fun `leaving restores online and ends the session after 5 seconds`() = runTest {
		val c = controller()
		c.update(readerA, true, build = activity())
		runCurrent()
		c.leave(readerA)
		advanceTimeBy(4_999)
		assertTrue(transport.active)
		assertEquals(0, transport.disconnects)
		advanceTimeBy(2)
		assertFalse(transport.active)
		assertEquals(1, transport.disconnects)
		assertEquals(PresenceStatus.Online, transport.statuses.last())
	}

	@Test
	fun `re-entering within 5 seconds keeps the session and timestamp`() = runTest {
		val c = controller()
		c.update(readerA, true, build = activity())
		runCurrent()
		val start = transport.activities.last().startTimestamp
		c.leave(readerA)
		advanceTimeBy(3_000)
		c.enter(readerB)
		advanceTimeBy(10_000)
		assertTrue(transport.active)
		assertEquals(0, transport.disconnects)
		assertEquals(1, transport.connects)
		c.update(readerB, true, build = activity("2"))
		advanceTimeBy(20_000)
		assertEquals(1, transport.connects)
		assertEquals(start, transport.activities.last().startTimestamp)
		assertEquals("2", transport.activities.last().state)
	}

	@Test
	fun `timestamp starts again after the session ended`() = runTest {
		val c = controller()
		c.update(readerA, true, build = activity())
		runCurrent()
		val first = transport.activities.last().startTimestamp
		c.leave(readerA)
		advanceTimeBy(6_000)
		advanceTimeBy(60_000)
		c.update(readerA, true, build = activity())
		runCurrent()
		assertEquals(2, transport.connects)
		assertTrue(transport.activities.last().startTimestamp > first)
	}

	@Test
	fun `rapid leave and enter never duplicates the session`() = runTest {
		val c = controller()
		c.update(readerA, true, build = activity())
		runCurrent()
		repeat(5) {
			c.leave(readerA)
			advanceTimeBy(1_000)
			c.enter(readerA)
		}
		advanceTimeBy(30_000)
		assertEquals(1, transport.connects)
		assertEquals(0, transport.disconnects)
	}

	@Test
	fun `closing reader cannot end the next readers session`() = runTest {
		val c = controller()
		c.update(readerA, true, build = activity())
		runCurrent()
		c.enter(readerB)
		c.leave(readerA)
		c.release(readerA)
		advanceTimeBy(30_000)
		assertTrue(transport.active)
		assertEquals(0, transport.disconnects)
	}

	@Test
	fun `stale reader cannot change the status`() = runTest {
		val c = controller()
		c.update(readerA, true, build = activity())
		runCurrent()
		c.enter(readerB)
		val count = transport.statuses.size
		c.setIdle(readerA)
		assertEquals(count, transport.statuses.size)
	}

	@Test
	fun `release schedules the same delayed teardown`() = runTest {
		val c = controller()
		c.update(readerA, true, build = activity())
		runCurrent()
		c.release(readerA)
		advanceTimeBy(4_000)
		assertTrue(transport.active)
		advanceTimeBy(2_000)
		assertFalse(transport.active)
	}

	@Test
	fun `without reading presence leaving ends the session at once`() = runTest {
		config = config.copy(isReadingPresence = false)
		val c = controller()
		c.update(readerA, true, build = activity())
		runCurrent()
		assertFalse(transport.statuses.contains(PresenceStatus.DoNotDisturb))
		c.leave(readerA)
		assertFalse(transport.active)
		assertEquals(1, transport.disconnects)
	}

	@Test
	fun `without reading presence the online status is never touched`() = runTest {
		config = config.copy(isReadingPresence = false)
		val c = controller()
		c.update(readerA, true, build = activity())
		runCurrent()
		c.setIdle(readerA)
		c.update(readerA, true, idle = true, build = activity("2"))
		advanceTimeBy(20_000)
		c.leave(readerA)
		assertTrue(transport.statuses.isEmpty())
		assertFalse(transport.active)
	}

	@Test
	fun `turning the feature off stops at once`() = runTest {
		val c = controller()
		c.update(readerA, true, build = activity())
		runCurrent()
		config = config.copy(isEnabled = false)
		c.update(readerA, true, build = activity("2"))
		assertFalse(transport.active)
		assertEquals(1, transport.disconnects)
	}

	@Test
	fun `nothing happens when not signed in`() = runTest {
		config = config.copy(isSignedIn = false)
		val c = controller()
		c.update(readerA, true, build = activity())
		runCurrent()
		assertEquals(0, transport.connects)
		assertTrue(transport.activities.isEmpty())
	}

	@Test
	fun `updates are spaced 16 seconds apart and the latest wins`() = runTest {
		val c = controller()
		c.update(readerA, true, build = activity("1"))
		runCurrent()
		c.update(readerA, true, build = activity("2"))
		c.update(readerA, true, build = activity("3"))
		advanceTimeBy(15_000)
		assertEquals(1, transport.activities.size)
		advanceTimeBy(1_100)
		assertEquals(2, transport.activities.size)
		assertEquals("3", transport.activities.last().state)
	}

	@Test
	fun `timestamp is hidden when switched off`() = runTest {
		val c = controller()
		c.update(readerA, false, build = activity())
		runCurrent()
		assertEquals(0L, transport.activities.last().startTimestamp)
	}

	@Test
	fun `failed connect shows nothing`() = runTest {
		transport.canConnect = false
		val c = controller()
		c.update(readerA, true, build = activity())
		runCurrent()
		assertTrue(transport.activities.isEmpty())
	}
}
