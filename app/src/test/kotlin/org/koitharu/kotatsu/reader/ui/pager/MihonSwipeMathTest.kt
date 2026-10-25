package org.koitharu.kotatsu.reader.ui.pager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MihonSwipeMathTest {

	private fun target(first: Int, offset: Float, velocity: Float, delta: Float, current: Int, count: Int = 10) =
		MihonSwipeMath.targetPage(first, offset, velocity, delta, count, current, flingDistance = 75f, minVelocity = 1200f)

	@Test
	fun slowForwardDragTurnsPastFortyPercent() {
		assertEquals(3, target(first = 3, offset = 0.35f, velocity = 0f, delta = 10f, current = 3))
		assertEquals(4, target(first = 3, offset = 0.65f, velocity = 0f, delta = 10f, current = 3))
	}

	@Test
	fun slowBackwardDragNeedsFortyPercentToTurn() {
		// the page at the left edge is before the one the drag started on
		assertEquals(2, target(first = 2, offset = 0.35f, velocity = 0f, delta = 10f, current = 3))
		assertEquals(3, target(first = 2, offset = 0.45f, velocity = 0f, delta = 10f, current = 3))
	}

	@Test
	fun fastFlingGoesInItsDirection() {
		assertEquals(4, target(first = 3, offset = 0.1f, velocity = 3000f, delta = 200f, current = 3))
		assertEquals(3, target(first = 3, offset = 0.9f, velocity = -3000f, delta = 200f, current = 3))
	}

	@Test
	fun shortFastDragIsNotAFling() {
		assertEquals(3, target(first = 3, offset = 0.1f, velocity = 3000f, delta = 20f, current = 3))
	}

	@Test
	fun targetStaysInRange() {
		assertEquals(9, target(first = 9, offset = 0.9f, velocity = 3000f, delta = 200f, current = 9))
		assertEquals(0, target(first = 0, offset = 0f, velocity = -3000f, delta = 200f, current = 0))
	}

	@Test
	fun durationIsCapped() {
		assertEquals(600, MihonSwipeMath.settleDuration(distance = 1000, size = 1000, velocity = 1f, maxDurationMs = 600))
	}

	@Test
	fun slowReleaseDurationGrowsWithDistance() {
		val short = MihonSwipeMath.settleDuration(distance = 200, size = 1000, velocity = 0f, maxDurationMs = 600)
		val long = MihonSwipeMath.settleDuration(distance = 800, size = 1000, velocity = 0f, maxDurationMs = 600)
		assertTrue(short < long)
	}
}
