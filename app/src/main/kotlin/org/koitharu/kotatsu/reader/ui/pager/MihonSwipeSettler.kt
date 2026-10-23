package org.koitharu.kotatsu.reader.ui.pager

import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import org.koitharu.kotatsu.core.util.ext.recyclerView
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Settles a swipe release like Mihon's ViewPager: its page choice rules, a velocity based
 * duration and the same ease-out curve. Active only while [isActive] is true, otherwise the
 * original fling listener (page snapping of [ViewPager2]) is used.
 *
 * Releases slower than the RecyclerView minimum fling velocity never reach a fling listener
 * and are still snapped by ViewPager2.
 */
class MihonSwipeSettler(
	private val pager: ViewPager2,
	private val isActive: () -> Boolean,
) {

	private val density = pager.resources.displayMetrics.density
	private val flingDistance = 25 * density
	private val minVelocity = 400 * density
	private var downPos = 0f
	private var lastPos = 0f

	fun attach() {
		val rv = pager.recyclerView ?: return
		val original = rv.onFlingListener
		rv.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
			override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
				val pos = if (pager.orientation == ViewPager2.ORIENTATION_HORIZONTAL) e.x else e.y
				if (e.actionMasked == MotionEvent.ACTION_DOWN) {
					downPos = pos
				}
				lastPos = pos
				return false
			}
		})
		rv.onFlingListener = object : RecyclerView.OnFlingListener() {
			override fun onFling(velocityX: Int, velocityY: Int): Boolean {
				if (!isActive()) {
					return original?.onFling(velocityX, velocityY) ?: false
				}
				return settle(rv, velocityX, velocityY) ?: (original?.onFling(velocityX, velocityY) ?: false)
			}
		}
	}

	/**
	 * @return true if the settle was started, false if there is nothing to scroll,
	 * null if the default behaviour has to be used.
	 */
	private fun settle(rv: RecyclerView, velocityX: Int, velocityY: Int): Boolean? {
		val lm = rv.layoutManager as? LinearLayoutManager ?: return null
		val horizontal = lm.orientation == LinearLayoutManager.HORIZONTAL
		if (horizontal && rv.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
			return null
		}
		val size = if (horizontal) {
			rv.width - rv.paddingLeft - rv.paddingRight
		} else {
			rv.height - rv.paddingTop - rv.paddingBottom
		}
		val itemCount = rv.adapter?.itemCount ?: return null
		val first = lm.findFirstVisibleItemPosition()
		val view = lm.findViewByPosition(first)
		if (size <= 0 || itemCount == 0 || first == RecyclerView.NO_POSITION || view == null) {
			return null
		}
		val offsetPx = if (horizontal) {
			rv.paddingLeft + lm.getLeftDecorationWidth(view) - view.left
		} else {
			rv.paddingTop + lm.getTopDecorationHeight(view) - view.top
		}
		// No offset: not a swipe release, e.g. the end of a fake drag
		if (offsetPx <= 0) {
			return false
		}
		// Positive velocity moves forward, like the page index
		val velocity = (if (horizontal) velocityX else velocityY).toFloat()
		val offset = offsetPx.toFloat() / size
		val delta = abs(lastPos - downPos)
		val target = determineTargetPage(first, offset, velocity, delta, itemCount)
		val distance = target * size - (first * size + offsetPx)
		if (distance == 0) {
			return false
		}
		val duration = settleDuration(distance, size, abs(velocity))
		if (horizontal) {
			rv.smoothScrollBy(distance, 0, MihonPageScroll.interpolator, duration)
		} else {
			rv.smoothScrollBy(0, distance, MihonPageScroll.interpolator, duration)
		}
		return true
	}

	private fun determineTargetPage(first: Int, offset: Float, velocity: Float, delta: Float, count: Int): Int {
		val target = if (delta > flingDistance && abs(velocity) > minVelocity) {
			if (velocity > 0) first + 1 else first
		} else {
			val truncator = if (first >= pager.currentItem) 0.4f else 0.6f
			first + (offset + truncator).toInt()
		}
		return target.coerceIn(0, count - 1)
	}

	private fun settleDuration(distance: Int, size: Int, velocity: Float): Int {
		val ratio = min(1f, abs(distance).toFloat() / size)
		val half = size / 2
		val influence = sin((ratio - 0.5f) * 0.3f * (PI / 2).toFloat())
		val travel = half + half * influence
		val duration = if (velocity > 0f) {
			4 * (1000 * abs(travel / velocity)).roundToInt()
		} else {
			((abs(distance).toFloat() / size + 1) * 100).toInt()
		}
		return min(duration, MihonPageScroll.MAX_DURATION_MS.toInt())
	}
}
