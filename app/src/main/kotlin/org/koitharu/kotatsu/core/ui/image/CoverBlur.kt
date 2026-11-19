package org.koitharu.kotatsu.core.ui.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import org.koitharu.kotatsu.core.model.isNsfw
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.settings.compose.rememberBooleanPref
import org.koitharu.kotatsu.settings.compose.rememberStringPref
import org.koitharu.kotatsu.suggestions.domain.TagsBlacklist

/**
 * Blurs the cover of a comic or novel that is rated 18+ (or comes from an 18+ source) or has one of
 * the genres the user listed. Holding the cover shows it for as long as the finger stays down.
 */
object CoverBlur {

	const val RADIUS_DP = 16

	/** How long a cover must be held before it shows through. A normal tap is much shorter. */
	const val PEEK_DELAY_MS = 200L

	private const val GENRE_THRESHOLD = 0.8f

	@Volatile
	private var cachedGenres: String? = null

	@Volatile
	private var cachedBlacklist: TagsBlacklist? = null

	fun shouldBlur(context: Context, manga: Manga?): Boolean {
		if (manga == null) return false
		val prefs = PreferenceManager.getDefaultSharedPreferences(context)
		if (!prefs.getBoolean(AppSettings.KEY_BLUR_SENSITIVE_COVERS, false)) return false
		if (manga.isNsfw()) return true
		val raw = prefs.getString(AppSettings.KEY_BLUR_COVER_GENRES, null).orEmpty()
		if (raw.isBlank()) return false
		var blacklist = cachedBlacklist
		if (blacklist == null || cachedGenres != raw) {
			blacklist = TagsBlacklist(
				raw.split(',').mapNotNullTo(HashSet()) { it.trim().takeIf(String::isNotEmpty) },
				GENRE_THRESHOLD,
			)
			cachedBlacklist = blacklist
			cachedGenres = raw
		}
		return manga in blacklist
	}
}

/** Compose side of [CoverBlur]: blurs this cover when it should be, and lets a held finger peek. */
@Composable
fun Modifier.coverBlur(manga: Manga?): Modifier {
	val context = LocalContext.current
	val enabled by rememberBooleanPref(AppSettings.KEY_BLUR_SENSITIVE_COVERS, false)
	val genres by rememberStringPref(AppSettings.KEY_BLUR_COVER_GENRES, "")
	var peeking by remember { mutableStateOf(false) }
	val blurred = enabled && remember(manga, genres) { CoverBlur.shouldBlur(context, manga) }
	if (!blurred) return this
	val peek = Modifier.pointerInput(manga) {
		awaitEachGesture {
			awaitFirstDown(requireUnconsumed = false)
			val released = withTimeoutOrNull(CoverBlur.PEEK_DELAY_MS) { waitForUpOrCancellation() }
			if (released == null) {
				peeking = true
				waitForUpOrCancellation()
				peeking = false
			}
		}
	}
	val look = when {
		peeking -> Modifier
		Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
			Modifier.blur(CoverBlur.RADIUS_DP.dp, BlurredEdgeTreatment.Rectangle)
		else -> Modifier.drawWithContent {
			drawContent()
			drawRect(Color(0xF2202020))
		}
	}
	return this.then(peek).then(look)
}

/**
 * Draws a drawable blurred: a tiny copy of it is blurred in software and scaled up with filtering.
 * Unlike a render effect, this keeps the clip the caller sets, so rounded corners stay round.
 * The blurred copy is made once per image; drawing it again only scales the small bitmap.
 */
class CoverBlurPainter {

	private var small: Bitmap? = null
	private var pixels = IntArray(0)
	private var scratch = IntArray(0)
	private var source: Drawable? = null
	private var sourceWidth = 0
	private var sourceHeight = 0
	private var ready = false
	private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
	private val oldBounds = Rect()
	private val target = RectF()

	fun draw(canvas: Canvas, drawable: Drawable, width: Int, height: Int) {
		val animating = drawable is Animatable && drawable.isRunning
		if (!ready || source !== drawable || sourceWidth != width || sourceHeight != height) {
			render(drawable, width, height)
		} else if (!animating && wasAnimating) {
			render(drawable, width, height)
		}
		wasAnimating = animating
		val bitmap = small ?: return
		target.set(0f, 0f, width.toFloat(), height.toFloat())
		canvas.drawBitmap(bitmap, null, target, paint)
	}

	private var wasAnimating = false

	private fun render(drawable: Drawable, width: Int, height: Int) {
		val tw = SMALL_WIDTH
		val th = maxOf(1, Math.round(SMALL_WIDTH.toFloat() * height / width))
		var bitmap = small
		if (bitmap == null || bitmap.width != tw || bitmap.height != th) {
			bitmap = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
			small = bitmap
			pixels = IntArray(tw * th)
			scratch = IntArray(tw * th)
		}
		bitmap.eraseColor(0)
		val c = Canvas(bitmap)
		oldBounds.set(drawable.bounds)
		val dw = drawable.intrinsicWidth
		val dh = drawable.intrinsicHeight
		if (dw > 0 && dh > 0) {
			val scale = maxOf(tw / dw.toFloat(), th / dh.toFloat())
			c.translate((tw - dw * scale) / 2f, (th - dh * scale) / 2f)
			c.scale(scale, scale)
			drawable.setBounds(0, 0, dw, dh)
		} else {
			drawable.setBounds(0, 0, tw, th)
		}
		drawable.draw(c)
		drawable.bounds = oldBounds
		bitmap.getPixels(pixels, 0, tw, 0, 0, tw, th)
		repeat(ROUNDS) {
			pass(pixels, scratch, tw, th, horizontal = true)
			pass(scratch, pixels, tw, th, horizontal = false)
		}
		bitmap.setPixels(pixels, 0, tw, 0, 0, tw, th)
		source = drawable
		sourceWidth = width
		sourceHeight = height
		ready = true
	}

	private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, horizontal: Boolean) {
		val length = if (horizontal) w else h
		val lines = if (horizontal) h else w
		val count = RADIUS * 2 + 1
		for (line in 0 until lines) {
			for (i in 0 until length) {
				var a = 0
				var r = 0
				var g = 0
				var b = 0
				for (k in -RADIUS..RADIUS) {
					val j = (i + k).coerceIn(0, length - 1)
					val p = src[if (horizontal) line * w + j else j * w + line]
					a += p ushr 24
					r += (p shr 16) and 0xFF
					g += (p shr 8) and 0xFF
					b += p and 0xFF
				}
				dst[if (horizontal) line * w + i else i * w + line] =
					((a / count) shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
			}
		}
	}

	private companion object {
		const val SMALL_WIDTH = 32
		const val RADIUS = 2
		const val ROUNDS = 2
	}
}
