package org.koitharu.kotatsu.core.ui.image

import android.content.Context
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
