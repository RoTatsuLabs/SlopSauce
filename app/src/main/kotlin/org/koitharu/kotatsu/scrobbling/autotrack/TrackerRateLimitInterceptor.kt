package org.koitharu.kotatsu.scrobbling.autotrack

import okhttp3.Interceptor
import okhttp3.Response
import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblerService

/** Reports every request of one tracker to the shared [TrackerRateLimiter]. */
class TrackerRateLimitInterceptor(
	private val service: ScrobblerService,
	private val limiter: TrackerRateLimiter,
) : Interceptor {

	override fun intercept(chain: Interceptor.Chain): Response {
		limiter.beforeRequest(service)
		val response = chain.proceed(chain.request())
		limiter.onResponse(
			service = service,
			code = response.code,
			limit = response.header("X-RateLimit-Limit")?.trim()?.toIntOrNull(),
			remaining = response.header("X-RateLimit-Remaining")?.trim()?.toIntOrNull(),
			retryAfterSeconds = response.header("Retry-After")?.trim()?.toLongOrNull(),
		)
		return response
	}
}
