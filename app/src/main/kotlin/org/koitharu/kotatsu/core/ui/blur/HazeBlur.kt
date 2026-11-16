package org.koitharu.kotatsu.core.ui.blur

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.preference.PreferenceManager
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.settings.compose.rememberBooleanPref

/** A blur state shared by a blurred source and the bars drawn over it, or null when "Blur behind bars" is off. */
@Composable
fun rememberBlurState(): HazeState? {
	val enabled by rememberBooleanPref(AppSettings.KEY_BAR_BLUR, true)
	val state = rememberHazeState()
	return if (enabled) state else null
}

/** Marks this content as what gets blurred behind a [blurBar]. */
fun Modifier.blurSource(state: HazeState?): Modifier =
	if (state == null) this else hazeSource(state)

/** Blurs whatever [blurSource] content is behind this, under a translucent [tint]. */
fun Modifier.blurBar(state: HazeState?, tint: Color): Modifier =
	if (state == null) {
		this
	} else {
		hazeEffect(state, HazeStyle(backgroundColor = tint, tint = HazeTint(tint.copy(alpha = 0.55f)), blurRadius = 24.dp))
	}

private const val WINDOW_BLUR_DP = 32

/** True when the "Blur behind bars" switch is on. */
fun Context.isBackdropBlurOn(): Boolean =
	PreferenceManager.getDefaultSharedPreferences(this).getBoolean(AppSettings.KEY_BAR_BLUR, true)

/** True when the "Frosted glass" look is on. It needs "Blur behind bars", since glass is a see-through surface over the blur. */
fun Context.isGlassLookOn(): Boolean =
	isBackdropBlurOn() && PreferenceManager.getDefaultSharedPreferences(this).getBoolean(AppSettings.KEY_GLASS_BLUR, true)

/** Android 12+: blurs the screen behind this dialog or sheet window, while "Blur behind bars" is on. */
fun Window.applyGlassBlur() {
	if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !context.isBackdropBlurOn()) return
	addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
	attributes = attributes.apply {
		blurBehindRadius = (WINDOW_BLUR_DP * context.resources.displayMetrics.density).toInt()
	}
}

/** Same as [applyGlassBlur], for the window of a Compose `Dialog` or `ModalBottomSheet`. */
@Composable
fun GlassWindowEffect() {
	val view = LocalView.current
	SideEffect {
		(view.parent as? DialogWindowProvider)?.window?.applyGlassBlur()
	}
}

/** True when popups sit on a blurred screen (Android 12+ with "Blur behind bars" and "Frosted glass" on), so they can turn translucent. */
@Composable
fun rememberGlassWindow(): Boolean {
	val context = LocalContext.current
	return remember { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && context.isGlassLookOn() }
}

/** A popup or sheet colour that lets the blurred screen show through when [rememberGlassWindow] is true. */
@Composable
fun glassColor(color: Color, alpha: Float = 0.74f): Color =
	if (rememberGlassWindow()) color.copy(alpha = alpha) else color

/** The thin light edge that makes a translucent surface read as glass, or null when glass is off. */
@Composable
fun glassBorder(): BorderStroke? =
	if (rememberGlassWindow()) BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)) else null

/** True when bars and buttons get the glass look: "Frosted glass" on, with "Blur behind bars" on. */
@Composable
fun rememberGlassLook(): Boolean {
	val context = LocalContext.current
	return remember { context.isGlassLookOn() }
}
