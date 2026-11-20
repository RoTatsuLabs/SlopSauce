package org.koitharu.kotatsu.scrobbling.autotrack

import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblerManga
import java.text.Normalizer
import java.util.Locale

/**
 * Title comparison for auto-track. Every name of the favourite is compared with every name of a tracker
 * entry, after case, accents, punctuation, bracketed notes and a leading "the" are ignored.
 */
internal object TitleMatch {

	/** Entries scoring at least this are linked on their own. */
	const val ACCEPT = 0.85f

	/** The best entry must lead the runner-up by this much, unless it is the only exact one. */
	private const val LEAD = 0.1f
	private const val CLOSEST_MIN = 0.5f

	private val diacritics = Regex("\\p{InCombiningDiacriticalMarks}+")
	private val brackets = Regex("[(\\[{][^)\\]}]*[)\\]}]")
	private val searchSymbols = Regex("[^\\p{L}\\p{N}\\s]+")
	private val spaces = Regex("\\s+")
	private val separators = Regex("[^\\p{L}\\p{N}]+")

	private val boysLove = setOf("boys love", "boys' love", "boyslove", "bl", "yaoi", "shounen ai", "shonen ai", "danmei")
	private val girlsLove = setOf("girls love", "girls' love", "girlslove", "gl", "yuri", "shoujo ai", "shojo ai", "baihe")

	fun normalize(title: String): String {
		var t = Normalizer.normalize(title, Normalizer.Form.NFKD).replace(diacritics, "")
		t = t.replace(brackets, " ").lowercase(Locale.ROOT)
		t = t.replace(separators, " ").trim()
		return t.removePrefix("the ").trim()
	}

	/** 0..1, where 1 means the names are the same once normalised. */
	fun score(a: String, b: String): Float {
		val x = normalize(a)
		val y = normalize(b)
		if (x.isEmpty() || y.isEmpty()) return 0f
		if (x == y || x.replace(" ", "") == y.replace(" ", "")) return 1f
		val tokensX = x.split(' ').toSet()
		val tokensY = y.split(' ').toSet()
		val jaccard = tokensX.intersect(tokensY).size.toFloat() / tokensX.union(tokensY).size
		// One title being the other plus a subtitle
		val contained = if (x.length >= 5 && y.length >= 5 && (x.contains(y) || y.contains(x))) {
			minOf(x.length, y.length).toFloat() / maxOf(x.length, y.length) * 0.5f + 0.4f
		} else {
			0f
		}
		val edit = 1f - levenshtein(x, y).toFloat() / maxOf(x.length, y.length)
		return maxOf(jaccard * 0.9f, contained, edit * 0.95f).coerceIn(0f, 0.99f)
	}

	fun bestScore(left: Collection<String>, right: Collection<String>): Float {
		var best = 0f
		for (a in left) for (b in right) {
			val s = score(a, b)
			if (s > best) best = s
			if (best >= 1f) return 1f
		}
		return best
	}

	fun namesOf(manga: Manga): List<String> =
		(sequenceOf(manga.title) + manga.altTitles.asSequence()).filter { it.isNotBlank() }.distinct().toList()

	fun namesOf(candidate: ScrobblerManga): List<String> =
		(sequenceOf(candidate.name, candidate.altName) + candidate.names.asSequence())
			.filterNotNull().filter { it.isNotBlank() }.distinct().toList()

	/**
	 * True when the entry cannot be the favourite: a different kind (manga, manhwa, manhua) or Boys' or
	 * Girls' Love against not. Only judged when both sides say something.
	 */
	fun conflicts(manga: Manga, candidate: ScrobblerManga): Boolean {
		val tags = manga.tags.map { it.title.trim().lowercase(Locale.ROOT).replace('’', '\'') }
		val sourceKind = tags.firstNotNullOfOrNull { kindOfLabel(it) }
		val candidateKind = kindOf(candidate.format, candidate.country)
		if (sourceKind != null && candidateKind != null && sourceKind != candidateKind) return true
		if (tags.isNotEmpty()) {
			val labels = candidate.labels.map { it.trim().lowercase(Locale.ROOT).replace('’', '\'') }
			if (tags.any { it in boysLove } != labels.any { it in boysLove }) return true
			if (tags.any { it in girlsLove } != labels.any { it in girlsLove }) return true
		}
		return false
	}

	/**
	 * The entry to link, or null when nothing is clear enough. An entry needs a high score and no
	 * conflict, and must be clearly ahead of the others. A search that returned a single entry is
	 * accepted when nothing conflicts, because the tracker found it by one of the names.
	 */
	fun pick(manga: Manga, candidates: List<ScrobblerManga>): ScrobblerManga? {
		val names = namesOf(manga)
		if (names.isEmpty()) return null
		val usable = candidates.filterNot { conflicts(manga, it) }
		if (usable.isEmpty()) return null
		val scored = usable.map { it to bestScore(names, namesOf(it)) }.sortedByDescending { it.second }
		val (top, topScore) = scored.first()
		if (topScore >= ACCEPT) {
			val runnerUp = scored.getOrNull(1)?.second ?: 0f
			if (topScore - runnerUp >= LEAD) return top
			if (topScore >= 1f) {
				// Several exact hits: only the tracker's own best match decides
				return scored.filter { it.second >= 1f }.map { it.first }.singleOrNull { it.isBestMatch }
			}
			return null
		}
		return if (candidates.size == 1) top else null
	}

	/**
	 * The nearest entry when [pick] found nothing clear: the best score among the entries that do not
	 * conflict, as long as it is a fair match. Used only when the user asks for it by hand.
	 */
	fun closest(manga: Manga, candidates: List<ScrobblerManga>): ScrobblerManga? {
		val names = namesOf(manga)
		if (names.isEmpty()) return null
		val best = candidates.filterNot { conflicts(manga, it) }
			.map { it to bestScore(names, namesOf(it)) }
			.maxByOrNull { it.second } ?: return null
		return best.first.takeIf { best.second >= CLOSEST_MIN }
	}

	/**
	 * The searches to try for a favourite, in order. Trackers match poorly on a title full of quotes,
	 * backticks and other symbols, so after the title as written come a version with those turned
	 * into spaces and, for a long title, just its opening words. The alternative titles follow.
	 */
	fun searchQueries(manga: Manga): List<String> {
		val title = manga.title.trim()
		val queries = LinkedHashSet<String>()
		if (title.isNotEmpty()) queries.add(title)
		val plain = title.replace(searchSymbols, " ").replace(spaces, " ").trim()
		if (plain.length >= 3) queries.add(plain)
		val words = plain.split(' ')
		if (words.size > 6) queries.add(words.take(5).joinToString(" "))
		val seen = queries.mapTo(hashSetOf()) { normalize(it) }
		for (alt in extraQueries(manga)) {
			if (seen.add(normalize(alt))) queries.add(alt)
		}
		return queries.toList()
	}

	/** Alternative titles worth a search of their own, when the main title found nothing clear. */
	fun extraQueries(manga: Manga, limit: Int = 2): List<String> {
		val seen = hashSetOf(normalize(manga.title))
		return manga.altTitles.filter { it.isNotBlank() && it.length >= 3 && seen.add(normalize(it)) }.take(limit)
	}

	private fun kindOf(format: String?, country: String?): String? {
		when (format?.trim()?.uppercase(Locale.ROOT)) {
			"NOVEL", "LIGHT_NOVEL", "WEB_NOVEL" -> return null
		}
		return when (country?.trim()?.uppercase(Locale.ROOT)) {
			"KR" -> "manhwa"
			"CN", "TW", "HK" -> "manhua"
			"JP" -> "manga"
			else -> null
		}
	}

	private fun kindOfLabel(label: String): String? = when (label) {
		"manhwa" -> "manhwa"
		"manhua" -> "manhua"
		"manga" -> "manga"
		else -> null
	}

	private fun levenshtein(a: String, b: String): Int {
		if (a == b) return 0
		var prev = IntArray(b.length + 1) { it }
		var cur = IntArray(b.length + 1)
		for (i in 1..a.length) {
			cur[0] = i
			for (j in 1..b.length) {
				val cost = if (a[i - 1] == b[j - 1]) 0 else 1
				cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
			}
			val tmp = prev
			prev = cur
			cur = tmp
		}
		return prev[b.length]
	}
}
