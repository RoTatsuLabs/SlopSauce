package org.koitharu.kotatsu.reader.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.RequiresApi

/**
 * Frosted glass for the reader's bars: blurs the page behind the top bar and the floating toolbar
 * while they are shown, so their see-through panels read as glass. The zones are re-measured every
 * frame, so they follow the bars as they slide in and out. Android 13+ only (RuntimeShader).
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class ReaderGlass(
	private val content: View,
	private val top: View,
	private val bottom: View?,
) : ViewTreeObserver.OnPreDrawListener {

	private val shader = RuntimeShader(SHADER)
	private var last = FloatArray(9) { Float.NaN }

	var isEnabled = false
		set(value) {
			if (field == value) return
			field = value
			if (value) {
				content.viewTreeObserver.addOnPreDrawListener(this)
				content.invalidate()
			} else {
				content.viewTreeObserver.removeOnPreDrawListener(this)
				content.setRenderEffect(null)
				last.fill(Float.NaN)
			}
		}

	override fun onPreDraw(): Boolean {
		val a = rectOf(top)
		val b = bottom?.let(::rectOf) ?: EMPTY
		val radiusB = bottom?.let { minOf(it.height, it.width) / 2f } ?: 0f
		val state = floatArrayOf(
			a[0], a[1], a[2], a[3], b[0], b[1], b[2], b[3], radiusB,
		)
		if (state.contentEquals(last)) return true
		last = state
		content.setRenderEffect(
			if (a === EMPTY && b === EMPTY) {
				null
			} else {
				shader.setFloatUniform("rectA", a[0], a[1], a[2], a[3])
				shader.setFloatUniform("rectB", b[0], b[1], b[2], b[3])
				shader.setFloatUniform("radB", radiusB)
				RenderEffect.createRuntimeShaderEffect(shader, "content")
			},
		)
		return true
	}

	/** Bounds of a shown view in the content view's own coordinates, or [EMPTY]. */
	private fun rectOf(view: View): FloatArray {
		if (!view.isShown || view.width == 0 || view.height == 0) return EMPTY
		view.getLocationInWindow(loc)
		content.getLocationInWindow(contentLoc)
		val left = (loc[0] - contentLoc[0]).toFloat()
		val top = (loc[1] - contentLoc[1]).toFloat()
		return floatArrayOf(left, top, left + view.width, top + view.height)
	}

	private val loc = IntArray(2)
	private val contentLoc = IntArray(2)

	private companion object {

		val EMPTY = floatArrayOf(0f, 0f, 0f, 0f)

		val SHADER = """
			uniform shader content;
			uniform float4 rectA;
			uniform float4 rectB;
			uniform float radB;

			const float BLUR_RADIUS = 18.0;
			const int SAMPLES = 4;

			float inside(float2 p, float4 r, float rad) {
				if (r.z <= r.x) {
					return 1.0;
				}
				float2 c = float2((r.x + r.z) * 0.5, (r.y + r.w) * 0.5);
				float2 h = float2((r.z - r.x) * 0.5, (r.w - r.y) * 0.5);
				float2 q = abs(p - c) - (h - float2(rad));
				return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - rad;
			}

			half4 main(float2 fragCoord) {
				float d = min(inside(fragCoord, rectA, 0.0), inside(fragCoord, rectB, radB));
				if (d > 0.0) {
					return content.eval(fragCoord);
				}
				float dither = fract(sin(dot(fragCoord, float2(12.9898, 78.233))) * 43758.5453);
				float2 jitter = float2(dither - 0.5, fract(dither * 1.618) - 0.5);
				float offsetScale = BLUR_RADIUS / float(SAMPLES);
				float radiusSq = BLUR_RADIUS * BLUR_RADIUS;
				half4 accum = half4(0.0);
				float weightSum = 0.0;
				for (int x = -SAMPLES; x <= SAMPLES; x++) {
					for (int y = -SAMPLES; y <= SAMPLES; y++) {
						float2 offset = (float2(float(x), float(y)) + jitter) * offsetScale;
						float distSq = dot(offset, offset);
						if (distSq <= radiusSq) {
							float weight = exp(-3.0 * distSq / radiusSq);
							accum += content.eval(fragCoord + offset) * weight;
							weightSum += weight;
						}
					}
				}
				return weightSum > 0.0 ? accum / weightSum : content.eval(fragCoord);
			}
		""".trimIndent()
	}
}
