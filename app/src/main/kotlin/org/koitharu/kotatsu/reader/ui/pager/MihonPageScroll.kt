package org.koitharu.kotatsu.reader.ui.pager

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.animation.Interpolator
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.absoluteValue

/**
 * Page turn timing of the ViewPager used by Mihon: quintic ease-out,
 * (pages + 1) * 100 ms, capped at 600 ms.
 */
object MihonPageScroll {

	private const val MAX_DURATION_MS = 600L

	val interpolator = Interpolator { t ->
		val x = t - 1f
		x * x * x * x * x + 1f
	}

	fun durationFor(pages: Int): Long = ((pages.absoluteValue + 1) * 100L).coerceAtMost(MAX_DURATION_MS)
}

/**
 * Animates page turns of a [ViewPager2] with [MihonPageScroll] timing using a fake drag.
 * While an animation runs, [ViewPager2.setCurrentItem] must not be called: use [finish] first.
 */
class MihonPageScroller(private val pager: ViewPager2) {

	private var animator: ValueAnimator? = null
	private var animTarget = -1
	private var inputWasEnabled = true

	/** Page the pager is at or is animating to. */
	val currentItem: Int
		get() = if (animator != null) animTarget else pager.currentItem

	/** @return false if the animation can't be used now, the caller should fall back. */
	fun scrollTo(item: Int): Boolean {
		val itemCount = pager.adapter?.itemCount ?: return false
		if (item !in 0 until itemCount) return false
		val from = currentItem
		finish()
		val pages = item - from
		val pageSize = if (pager.orientation == ViewPager2.ORIENTATION_HORIZONTAL) pager.width else pager.height
		if (pages == 0 || pageSize <= 0) return false
		inputWasEnabled = pager.isUserInputEnabled
		pager.isUserInputEnabled = false
		if (!pager.beginFakeDrag()) {
			pager.isUserInputEnabled = inputWasEnabled
			return false
		}
		var applied = 0
		animTarget = item
		val anim = ValueAnimator.ofInt(0, pageSize * pages).apply {
			duration = MihonPageScroll.durationFor(pages)
			interpolator = MihonPageScroll.interpolator
			addUpdateListener {
				val value = it.animatedValue as Int
				if (pager.isFakeDragging) {
					pager.fakeDragBy(-(value - applied).toFloat())
				}
				applied = value
			}
			addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					if (animator === animation) {
						animator = null
					}
					if (pager.isFakeDragging) {
						pager.endFakeDrag()
					}
					pager.isUserInputEnabled = inputWasEnabled
				}
			})
		}
		animator = anim
		anim.start()
		return true
	}

	/** Jumps a running animation to its target and releases the pager. */
	fun finish() {
		animator?.end()
	}

	fun cancel() {
		animator?.cancel()
	}
}
