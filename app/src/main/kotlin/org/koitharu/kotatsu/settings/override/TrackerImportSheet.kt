package org.koitharu.kotatsu.settings.override

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import android.content.Context
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.model.titleResId
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblerMangaStatus
import org.koitharu.kotatsu.core.util.ext.mangaSourceExtra
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblingInfo
import androidx.compose.material3.SheetValue

/** What happens to the editor's current value when a field is imported. */
enum class ImportMode(val titleResId: Int) {
	REPLACE(R.string.import_mode_replace),
	APPEND(R.string.import_mode_append),
	FILL(R.string.import_mode_fill),
}

/** What the tracker can hand over, in the order the editor shows those fields. */
enum class ImportField(val titleResId: Int, val modes: List<ImportMode>) {
	COVER(R.string.cover, listOf(ImportMode.REPLACE)),
	TITLE(R.string.name, listOf(ImportMode.REPLACE)),
	DESCRIPTION(R.string.description, listOf(ImportMode.REPLACE, ImportMode.APPEND, ImportMode.FILL)),
	ALT_NAMES(R.string.alt_names, listOf(ImportMode.APPEND)),
	GENRES(R.string.genres, listOf(ImportMode.REPLACE, ImportMode.APPEND, ImportMode.FILL)),
	AUTHORS(R.string.authors, listOf(ImportMode.REPLACE, ImportMode.APPEND, ImportMode.FILL)),
	STATUS(R.string.state, listOf(ImportMode.REPLACE, ImportMode.FILL)),
	CONTENT_RATING(R.string.content_rating, listOf(ImportMode.REPLACE, ImportMode.FILL)),
	RATING(R.string.rating, listOf(ImportMode.REPLACE, ImportMode.FILL)),
}

/**
 * Picks what to pull from a linked tracker: which tracker, then which of its fields. The values
 * only land in the editor's fields, so this sheet never has a destructive outcome — Save does.
 *
 * A field the tracker has nothing for is shown greyed and cannot be picked, which is more honest
 * than hiding it: it explains why the import left that field alone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerImportSheet(
	manga: Manga,
	trackers: List<ScrobblingInfo>,
	imageLoader: ImageLoader,
	onImport: (ScrobblingInfo, Map<ImportField, ImportMode>) -> Unit,
	onDismiss: () -> Unit,
) {
	if (trackers.isEmpty()) {
		return
	}
	var selectedTracker by remember { mutableStateOf(trackers.first().scrobbler) }
	val tracker = trackers.firstOrNull { it.scrobbler == selectedTracker } ?: trackers.first()
	// Re-seeded per tracker: a field one tracker has nothing for may well be filled on the next.
	// The choices start from the defaults every time the sheet opens; nothing is remembered.
	val context = LocalContext.current
	var selection by remember(tracker) {
		mutableStateOf(tracker.availableFields(context).associateWith { it.modes.first() })
	}

	ModalBottomSheet(
		onDismissRequest = onDismiss,
		sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded)),
		shape = RoundedCornerShape(topStart = SHEET_CORNER, topEnd = SHEET_CORNER),
		containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
	) {
		val shown = ImportField.entries.filter { it in tracker.availableFields(context) }
		val missing = ImportField.entries - shown.toSet()
		Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
			Text(
				text = stringResource(R.string.import_from_tracker),
				style = MaterialTheme.typography.headlineSmall,
				fontWeight = FontWeight.SemiBold,
			)
			Spacer(Modifier.height(4.dp))
			Text(
				text = stringResource(R.string.import_from_tracker_hint),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
			if (trackers.size > 1) {
				Spacer(Modifier.height(12.dp))
				Row(
					modifier = Modifier.horizontalScroll(rememberScrollState()),
					horizontalArrangement = Arrangement.spacedBy(8.dp),
				) {
					trackers.forEach { info ->
						FilterChip(
							selected = info.scrobbler == tracker.scrobbler,
							onClick = { selectedTracker = info.scrobbler },
							label = { Text(stringResource(info.scrobbler.titleResId)) },
							leadingIcon = {
								Icon(
									painter = painterResource(info.scrobbler.iconResId),
									contentDescription = null,
									modifier = Modifier.size(18.dp),
								)
							},
						)
					}
				}
			}
			Spacer(Modifier.height(12.dp))
			// Only the list scrolls: the header above and the buttons below stay in reach.
			Column(
				modifier = Modifier
					.weight(1f, fill = false)
					.verticalScroll(rememberScrollState()),
				verticalArrangement = Arrangement.spacedBy(2.dp),
			) {
				shown.forEachIndexed { index, field ->
					val isChecked = field in selection
					FieldRow(
						manga = manga,
						field = field,
						value = tracker.valueOf(field, context),
						mode = selection[field],
						onModeChange = { selection = selection + (field to it) },
						coverUrl = tracker.coverUrl.takeIf { field == ImportField.COVER },
						isChecked = isChecked,
						imageLoader = imageLoader,
						shape = RoundedCornerShape(
							topStart = if (index == 0) 20.dp else 4.dp,
							topEnd = if (index == 0) 20.dp else 4.dp,
							bottomStart = if (index == shown.lastIndex) 20.dp else 4.dp,
							bottomEnd = if (index == shown.lastIndex) 20.dp else 4.dp,
						),
						onToggle = {
							selection = if (isChecked) selection - field else selection + (field to field.modes.first())
						},
					)
				}
				if (missing.isNotEmpty()) {
					Text(
						text = stringResource(
							R.string.import_unavailable,
							missing.joinToString(", ") { stringResource(it.titleResId) },
						),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
					)
				}
			}
			Spacer(Modifier.height(12.dp))
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
			) {
				val allSelected = selection.size == shown.size
				TextButton(
					onClick = {
						selection = if (allSelected) emptyMap() else shown.associateWith { it.modes.first() }
					},
				) {
					Text(stringResource(if (allSelected) R.string.import_select_none else R.string.import_select_all))
				}
				Spacer(Modifier.weight(1f))
				TextButton(onClick = onDismiss) {
					Text(stringResource(android.R.string.cancel))
				}
				Spacer(Modifier.width(8.dp))
				Button(
					onClick = { onImport(tracker, selection) },
					enabled = selection.isNotEmpty(),
				) {
					Text(stringResource(R.string.import_count, selection.size))
				}
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldRow(
	manga: Manga,
	field: ImportField,
	value: String?,
	mode: ImportMode?,
	onModeChange: (ImportMode) -> Unit,
	coverUrl: String?,
	isChecked: Boolean,
	imageLoader: ImageLoader,
	shape: RoundedCornerShape,
	onToggle: () -> Unit,
) {
	Surface(
		shape = shape,
		color = if (isChecked) {
			MaterialTheme.colorScheme.surfaceContainerHigh
		} else {
			MaterialTheme.colorScheme.surfaceContainer
		},
		contentColor = MaterialTheme.colorScheme.onSurface,
	) {
		Column {
			Row(
				modifier = Modifier
					.clickable(onClick = onToggle)
					.fillMaxWidth()
					.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				if (coverUrl != null) {
					val context = LocalContext.current
					AsyncImage(
						model = remember(coverUrl) {
							ImageRequest.Builder(context)
								.data(coverUrl)
								.crossfade(true)
								.mangaSourceExtra(manga.source)
								.build()
						},
						imageLoader = imageLoader,
						contentDescription = null,
						contentScale = ContentScale.Crop,
						modifier = Modifier
							.size(width = 34.dp, height = 48.dp)
							.clip(RoundedCornerShape(8.dp))
							.background(MaterialTheme.colorScheme.surfaceContainerHighest),
					)
					Spacer(Modifier.width(12.dp))
				}
				Column(Modifier.weight(1f)) {
					Text(
						text = stringResource(field.titleResId),
						style = MaterialTheme.typography.titleSmall,
					)
					// The cover's thumbnail is its own preview; a raw URL underneath adds nothing.
					val subtitle = when (field) {
						ImportField.COVER -> null
						ImportField.ALT_NAMES -> stringResource(R.string.alt_names_hint)
						else -> value
					}
					if (subtitle != null) {
						Text(
							text = subtitle,
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							maxLines = 2,
							overflow = TextOverflow.Ellipsis,
						)
					}
				}
				Checkbox(
					checked = isChecked,
					onCheckedChange = { onToggle() },
				)
			}
			if (isChecked && mode != null && field.modes.size > 1) {
				SingleChoiceSegmentedButtonRow(
					modifier = Modifier
						.fillMaxWidth()
						.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
				) {
					field.modes.forEachIndexed { index, option ->
						SegmentedButton(
							selected = option == mode,
							onClick = { onModeChange(option) },
							shape = SegmentedButtonDefaults.itemShape(index, field.modes.size),
							modifier = Modifier.height(36.dp),
							label = {
								Text(
									text = stringResource(option.titleResId),
									style = MaterialTheme.typography.labelMedium,
									maxLines = 1,
								)
							},
						)
					}
				}
			}
		}
	}
}

private fun ScrobblingInfo.valueOf(field: ImportField, context: Context): String? = when (field) {
	ImportField.COVER -> coverUrl
	ImportField.TITLE -> title
	ImportField.DESCRIPTION -> description?.toString()
	ImportField.ALT_NAMES -> altNames.joinToString(", ")
	ImportField.GENRES -> genres.joinToString(", ")
	ImportField.AUTHORS -> authors.joinToString(", ")
	ImportField.STATUS -> asMangaState()?.let { context.getString(it.titleResId) }
	ImportField.CONTENT_RATING -> if (isAdult) context.getString(ContentRating.ADULT.titleResId) else null
	ImportField.RATING -> if (score > 0f) "%.1f / 10".format(score) else null
}

private fun ScrobblingInfo.availableFields(context: Context): Set<ImportField> =
	ImportField.entries.filterTo(HashSet()) { !valueOf(it, context).isNullOrBlank() }

/** The tracker's publication status as the app's own status. */
fun ScrobblingInfo.asMangaState(): MangaState? = when (mangaStatus) {
	ScrobblerMangaStatus.UPCOMING -> MangaState.UPCOMING
	ScrobblerMangaStatus.ONGOING -> MangaState.ONGOING
	ScrobblerMangaStatus.FINISHED -> MangaState.FINISHED
	ScrobblerMangaStatus.PAUSED -> MangaState.PAUSED
	ScrobblerMangaStatus.CANCELLED -> MangaState.ABANDONED
	null -> null
}

private val SHEET_CORNER = 28.dp
