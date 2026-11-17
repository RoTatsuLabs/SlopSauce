package org.koitharu.kotatsu.core.ui.blur

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.RequiresApi

/**
 * Blurs whatever [target] draws inside one rounded rectangle, so a see-through panel in front of it
 * reads as frosted glass. [rect] is in window coordinates; null turns the blur off. Android 13+.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class RegionBlur(
	private val target: View,
	private val cornerPx: Float,
	private val blurPx: Float,
) : ViewTreeObserver.OnPreDrawListener {

	private val shader = RuntimeShader(SHADER)
	private val loc = IntArray(2)
	private var applied: FloatArray? = null
	private var attached = false

	/** left, top, right, bottom in window pixels, or null. */
	var rect: FloatArray? = null
		set(value) {
			field = value
			if (!attached) {
				attached = true
				target.viewTreeObserver.addOnPreDrawListener(this)
			}
			target.invalidate()
		}

	override fun onPreDraw(): Boolean {
		val r = rect
		if (r == null) {
			if (applied != null) {
				applied = null
				target.setRenderEffect(null)
			}
			return true
		}
		target.getLocationInWindow(loc)
		val local = floatArrayOf(r[0] - loc[0], r[1] - loc[1], r[2] - loc[0], r[3] - loc[1])
		if (local.contentEquals(applied)) return true
		applied = local
		shader.setFloatUniform("rect", local[0], local[1], local[2], local[3])
		shader.setFloatUniform("corner", cornerPx)
		shader.setFloatUniform("blurRadius", blurPx)
		target.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "content"))
		return true
	}

	fun release() {
		rect = null
		if (attached) {
			attached = false
			target.viewTreeObserver.removeOnPreDrawListener(this)
		}
		applied = null
		target.setRenderEffect(null)
	}

	private companion object {

		val SHADER = """
			uniform shader content;
			uniform float4 rect;
			uniform float corner;
			uniform float blurRadius;

			const int SAMPLES = 4;

			half4 main(float2 fragCoord) {
				float2 c = float2((rect.x + rect.z) * 0.5, (rect.y + rect.w) * 0.5);
				float2 h = float2((rect.z - rect.x) * 0.5, (rect.w - rect.y) * 0.5);
				float2 q = abs(fragCoord - c) - (h - float2(corner));
				float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - corner;
				if (d > 0.0) {
					return content.eval(fragCoord);
				}
				float dither = fract(sin(dot(fragCoord, float2(12.9898, 78.233))) * 43758.5453);
				float2 jitter = float2(dither - 0.5, fract(dither * 1.618) - 0.5);
				float offsetScale = blurRadius / float(SAMPLES);
				float radiusSq = blurRadius * blurRadius;
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
