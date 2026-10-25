package org.koitharu.kotatsu.reader.ui.pager.doublepage

import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.koitharu.kotatsu.reader.ui.pager.MihonPageScroll
import org.koitharu.kotatsu.reader.ui.pager.MihonSwipeMath
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Settles a swipe release of the double-page reader like Mihon's ViewPager: its page choice rules,
 * a velocity based duration and the same ease-out curve. A page here is a spread of two items.
 * Active only while [isActive] is true, otherwise (and for RTL layouts) the original fling listener,
 * i.e. [DoublePageSnapHelper], is used. Attach it after the snap helper.
 *
 * Releases slower than the RecyclerView minimum fling velocity never reach a fling listener and are
 * still snapped by the snap helper.
 */
class MihonSpreadSettler(
	private val rv: RecyclerView,
	private val isActive: () -> Boolean,
) {

	private val density = rv.resources.displayMetrics.density
	private val flingDistance = 25 * density
	private val minVelocity = 400 * density
	private var downX = 0f
	private var lastX = 0f
	private var startSpread = 0

	fun attach() {
		val original = rv.onFlingListener
		rv.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
			override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
				if (e.actionMasked == MotionEvent.ACTION_DOWN) {
					downX = e.x
					startSpread = currentSpreadOrNull()?.roundToInt() ?: 0
				}
				lastX = e.x
				return false
			}
		})
		rv.onFlingListener = object : RecyclerView.OnFlingListener() {
			override fun onFling(velocityX: Int, velocityY: Int): Boolean {
				if (!isActive()) {
					return original?.onFling(velocityX, velocityY) ?: false
				}
				return settle(velocityX) ?: (original?.onFling(velocityX, velocityY) ?: false)
			}
		}
	}

	/** Scroll position in spreads (1.0 = one spread), or null if it can't be determined. */
	private fun currentSpreadOrNull(): Float? {
		val lm = rv.layoutManager as? LinearLayoutManager ?: return null
		val first = lm.findFirstVisibleItemPosition()
		val view = lm.findViewByPosition(first) ?: return null
		if (first == RecyclerView.NO_POSITION || view.width <= 0) return null
		val scrollPx = first * view.width + (rv.paddingLeft + lm.getLeftDecorationWidth(view) - view.left)
		return scrollPx.toFloat() / (view.width * ITEMS_PER_SPREAD)
	}

	/**
	 * @return true if the settle was started, false if there is nothing to scroll,
	 * null if the default behaviour has to be used.
	 */
	private fun settle(velocityX: Int): Boolean? {
		val lm = rv.layoutManager as? LinearLayoutManager ?: return null
		if (lm.orientation != LinearLayoutManager.HORIZONTAL || rv.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
			return null
		}
		val itemCount = rv.adapter?.itemCount ?: return null
		val first = lm.findFirstVisibleItemPosition()
		val view = lm.findViewByPosition(first)
		if (itemCount == 0 || first == RecyclerView.NO_POSITION || view == null || view.width <= 0) {
			return null
		}
		val itemSize = view.width
		val spreadSize = itemSize * ITEMS_PER_SPREAD
		val scrollPx = first * itemSize + (rv.paddingLeft + lm.getLeftDecorationWidth(view) - view.left)
		val firstSpread = scrollPx / spreadSize
		val offsetPx = scrollPx - firstSpread * spreadSize
		// No offset: not a swipe release, e.g. the end of a fake drag
		if (offsetPx <= 0) {
			return false
		}
		val spreadCount = (itemCount + ITEMS_PER_SPREAD - 1) / ITEMS_PER_SPREAD
		val velocity = velocityX.toFloat()
		val target = MihonSwipeMath.targetPage(
			first = firstSpread,
			offset = offsetPx.toFloat() / spreadSize,
			velocity = velocity,
			delta = abs(lastX - downX),
			count = spreadCount,
			current = startSpread,
			flingDistance = flingDistance,
			minVelocity = minVelocity,
		)
		val maxScroll = (itemCount * itemSize - rv.width).coerceAtLeast(0)
		val distance = (target * spreadSize).coerceAtMost(maxScroll) - scrollPx
		if (distance == 0) {
			return false
		}
		val duration = MihonSwipeMath.settleDuration(
			distance = distance,
			size = spreadSize,
			velocity = abs(velocity),
			maxDurationMs = MihonPageScroll.MAX_DURATION_MS.toInt(),
		)
		rv.smoothScrollBy(distance, 0, MihonPageScroll.interpolator, duration)
		return true
	}

	private companion object {

		const val ITEMS_PER_SPREAD = 2
	}
}
