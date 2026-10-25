package org.koitharu.kotatsu.reader.ui.pager

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Page choice and duration of a swipe release, as in Mihon's ViewPager. No Android dependencies. */
object MihonSwipeMath {

	/**
	 * @param first page at the left edge of the viewport
	 * @param offset how far [first] is scrolled away, 0..1 of a page
	 * @param velocity release velocity, positive moves forward
	 * @param delta distance travelled by the finger
	 * @param current page the pager was settled on before the drag
	 */
	fun targetPage(
		first: Int,
		offset: Float,
		velocity: Float,
		delta: Float,
		count: Int,
		current: Int,
		flingDistance: Float,
		minVelocity: Float,
	): Int {
		val target = if (delta > flingDistance && abs(velocity) > minVelocity) {
			if (velocity > 0) first + 1 else first
		} else {
			val truncator = if (first >= current) 0.4f else 0.6f
			first + (offset + truncator).toInt()
		}
		return target.coerceIn(0, count - 1)
	}

	fun settleDuration(distance: Int, size: Int, velocity: Float, maxDurationMs: Int): Int {
		val ratio = min(1f, abs(distance).toFloat() / size)
		val half = size / 2
		val influence = sin((ratio - 0.5f) * 0.3f * (PI / 2).toFloat())
		val travel = half + half * influence
		val duration = if (velocity > 0f) {
			4 * (1000 * abs(travel / velocity)).roundToInt()
		} else {
			((abs(distance).toFloat() / size + 1) * 100).toInt()
		}
		return min(duration, maxDurationMs)
	}
}
