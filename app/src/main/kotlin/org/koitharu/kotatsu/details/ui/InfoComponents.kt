package org.koitharu.kotatsu.details.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.util.ext.calculateTimeAgo
import org.koitharu.kotatsu.details.data.MangaDetails
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaTag
import java.time.Instant
import java.util.Locale

private const val TILES_PER_ROW = 2

private class InfoTile(
	val label: String,
	val value: String,
	val highlighted: Boolean = false,
	@DrawableRes val icon: Int = 0,
	val onClick: (() -> Unit)? = null,
)

/**
 * Facts about the title at a glance: its type, rating, language and when it last got a chapter.
 * A tile is left out when the source has nothing to say for it, and the whole block when none have.
 */
@Composable
internal fun InfoGrid(
	manga: Manga,
	details: MangaDetails?,
	formats: List<FormatTag>,
	accent: Color,
	onFormatClick: (MangaTag) -> Unit,
) {
	val context = LocalContext.current
	val typeLabel = stringResource(R.string.type)
	val ratingLabel = stringResource(R.string.by_rating)
	val languageLabel = stringResource(R.string.language)
	val updatedLabel = stringResource(R.string.updated)
	val lastChapterDate = details?.allChapters?.maxOfOrNull { it.uploadDate } ?: 0L
	val locale = details?.getLocale()

	val tiles = remember(manga, formats, lastChapterDate, locale, context, onFormatClick) {
		buildList {
			if (formats.isNotEmpty()) {
				add(
					InfoTile(
						label = typeLabel,
						value = formats.joinToString(" · ") { context.getString(it.format.titleResId) },
						highlighted = true,
						onClick = { onFormatClick(formats.first().tag) },
					),
				)
			}
			if (manga.hasRating) {
				add(
					InfoTile(
						label = ratingLabel,
						value = String.format(Locale.ROOT, "%.1f", manga.rating * 5f),
						icon = R.drawable.ic_star_small,
					),
				)
			}
			locale?.let {
				add(
					InfoTile(
						label = languageLabel,
						value = it.getDisplayLanguage(it).replaceFirstChar { ch -> ch.titlecase(it) },
						icon = R.drawable.ic_language,
					),
				)
			}
			if (lastChapterDate > 0L) {
				calculateTimeAgo(Instant.ofEpochMilli(lastChapterDate), showMonths = true)?.let {
					add(InfoTile(label = updatedLabel, value = it.format(context)))
				}
			}
		}
	}
	if (tiles.isEmpty()) return

	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = SCREEN_PADDING, vertical = 4.dp),
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		tiles.chunked(TILES_PER_ROW).forEach { row ->
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(8.dp),
			) {
				row.forEach { tile ->
					InfoTileCard(tile = tile, accent = accent, modifier = Modifier.weight(1f))
				}
				// Keep a lone last tile the width of the others instead of stretching across the row
				repeat(TILES_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
			}
		}
	}
}

@Composable
private fun InfoTileCard(tile: InfoTile, accent: Color, modifier: Modifier) {
	val container = if (tile.highlighted) accent.copy(alpha = 0.20f) else MaterialTheme.colorScheme.surfaceContainerHigh
	val labelColor = if (tile.highlighted) accent else MaterialTheme.colorScheme.onSurfaceVariant
	val valueColor = if (tile.highlighted) accent else MaterialTheme.colorScheme.onSurface
	Surface(
		shape = RoundedCornerShape(20.dp),
		color = container,
		modifier = if (tile.onClick != null) modifier.clickable(onClick = tile.onClick) else modifier,
	) {
		Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
			Text(
				text = tile.label,
				style = MaterialTheme.typography.labelMedium,
				color = labelColor,
				maxLines = 1,
			)
			Spacer(Modifier.height(2.dp))
			Row(verticalAlignment = Alignment.CenterVertically) {
				if (tile.icon != 0) {
					Icon(
						painter = painterResource(tile.icon),
						contentDescription = null,
						tint = accent,
						modifier = Modifier.size(16.dp),
					)
					Spacer(Modifier.size(6.dp))
				}
				Text(
					text = tile.value,
					style = MaterialTheme.typography.titleMedium,
					fontWeight = FontWeight.SemiBold,
					color = valueColor,
					maxLines = 2,
					overflow = TextOverflow.Ellipsis,
				)
			}
		}
	}
}

/** Every other name the title goes by, which the hero only has room to show one of. */
@Composable
internal fun AltTitlesCard(titles: Collection<String>) {
	val names = titles.filter { it.isNotBlank() }
	if (names.size < 2) return
	SectionCard {
		Text(
			text = stringResource(R.string.alternative_titles),
			style = MaterialTheme.typography.titleMedium,
			fontWeight = FontWeight.SemiBold,
			color = MaterialTheme.colorScheme.onSurface,
		)
		Spacer(Modifier.height(10.dp))
		names.forEach { name ->
			Text(
				text = name,
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(vertical = 2.dp),
			)
		}
	}
}
