package org.koitharu.kotatsu.download.ui.list

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import org.koitharu.kotatsu.core.ui.menu.PopupEntry
import org.koitharu.kotatsu.core.ui.menu.PopupMenuBox
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.font.FontWeight
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.koitharu.kotatsu.settings.compose.groupItemShape
import org.koitharu.kotatsu.settings.compose.LegendItem
import org.koitharu.kotatsu.settings.compose.SegmentedBar
import org.koitharu.kotatsu.settings.compose.BarSegment
import org.koitharu.kotatsu.settings.compose.SegmentedRow
import org.koitharu.kotatsu.core.util.FileSize
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.util.ext.mangaSourceExtra
import org.koitharu.kotatsu.core.util.ext.stableMangaCoverKey

/** What a row or the storage card can ask for. The toolbar menu and the selection bar live in the activity. */
class DownloadsCallbacks(
	val onOpen: (DownloadItemModel) -> Unit,
	val onExpand: (DownloadItemModel) -> Unit,
	val onPause: (DownloadItemModel) -> Unit,
	val onResume: (DownloadItemModel) -> Unit,
	val onRetry: (DownloadItemModel) -> Unit,
	val onCancel: (DownloadItemModel) -> Unit,
	val onSkip: (DownloadItemModel) -> Unit,
	val onSkipAll: (DownloadItemModel) -> Unit,
	val onRemoveSelected: (Set<Long>) -> Unit,
	val onMoveTop: (Set<Long>) -> Unit,
	val onMoveEnd: (Set<Long>) -> Unit,
	val onMoveUp: (DownloadItemModel) -> Unit,
	val onMoveDown: (DownloadItemModel) -> Unit,
	val onDownloadMissing: (DownloadItemModel) -> Unit,
	val onDeleteStored: (StorageEntry) -> Unit,
)

private val ROW_COVER = 52.dp

/** Where a download sits in the list. Also the filter chips and the section headers. */
internal enum class Bucket(@StringRes val title: Int) {
	DOWNLOADING(R.string.downloads_section_downloading),
	WAITING(R.string.downloads_section_waiting),
	PAUSED(R.string.downloads_section_paused),
	FAILED(R.string.downloads_section_failed),
	FINISHED(R.string.downloads_section_finished),
}

enum class DownloadSort(@StringRes val title: Int) {
	QUEUE(R.string.downloads_sort_queue),
	TITLE(R.string.downloads_sort_title),
	NEWEST(R.string.downloads_sort_newest),
}

internal fun DownloadItemModel.bucket(): Bucket = when {
	workState == WorkInfo.State.FAILED -> Bucket.FAILED
	isFinished -> Bucket.FINISHED
	workState == WorkInfo.State.RUNNING && isPaused -> Bucket.PAUSED
	workState == WorkInfo.State.RUNNING && !isQueued -> Bucket.DOWNLOADING
	else -> Bucket.WAITING
}

/** The three tabs, swiped or tapped like in 1DM and ADM. */
internal enum class Page(@StringRes val title: Int, @StringRes val empty: Int) {
	ACTIVE(R.string.downloads_tab_active, R.string.downloads_empty_active),
	FAILED(R.string.downloads_tab_failed, R.string.downloads_empty_failed),
	DONE(R.string.downloads_tab_done, R.string.downloads_empty_done),
}

internal fun Bucket.page(): Page = when (this) {
	Bucket.FAILED -> Page.FAILED
	Bucket.FINISHED -> Page.DONE
	else -> Page.ACTIVE
}

/** Every download of the tab that [item] is on, for "select all". */
internal fun List<DownloadItemModel>.onSamePageAs(item: DownloadItemModel): List<DownloadItemModel> {
	val page = item.bucket().page()
	return filter { it.bucket().page() == page }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadsScreen(
	models: List<DownloadItemModel>?,
	imageLoader: ImageLoader,
	storage: StorageSummary?,
	sort: DownloadSort,
	selected: Set<Long>,
	onSelectedChange: (Set<Long>) -> Unit,
	bottomInset: Dp,
	callbacks: DownloadsCallbacks,
) {
	val all = models.orEmpty()
	LaunchedEffect(models) {
		val present = all.mapTo(HashSet()) { it.id.mostSignificantBits }
		if (!present.containsAll(selected)) {
			onSelectedChange(selected.filterTo(HashSet()) { it in present })
		}
	}
	val byPage = all.groupBy { it.bucket().page() }
	val pager = rememberPagerState(pageCount = { Page.entries.size })
	val scope = rememberCoroutineScope()
	// Open on the first tab that has something in it
	var opened by remember { mutableStateOf(false) }
	LaunchedEffect(models != null) {
		if (models != null && !opened) {
			opened = true
			val first = Page.entries.firstOrNull { byPage[it].orEmpty().isNotEmpty() }
			if (first != null && first != Page.ACTIVE) pager.scrollToPage(first.ordinal)
		}
	}
	LaunchedEffect(pager.currentPage) {
		if (selected.isNotEmpty()) onSelectedChange(emptySet())
	}
	val labels = Page.entries.map { page ->
		val count = byPage[page].orEmpty().size
		if (count > 0) "${stringResource(page.title)} $count" else stringResource(page.title)
	}
	// Nothing here sits in a Surface, so without this the text falls back to black
	CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
	Column(Modifier.fillMaxSize()) {
		Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)) {
			SegmentedRow(
				labels = labels,
				selectedIndex = pager.currentPage,
				onSelected = { scope.launch { pager.animateScrollToPage(it) } },
				enabled = true,
			)
		}
		if (models == null) {
			Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
		} else {
			HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { index ->
				val page = Page.entries[index]
				PageList(
					page = page,
					items = byPage[page].orEmpty(),
					sort = sort,
					storage = storage,
					selected = selected,
					imageLoader = imageLoader,
					callbacks = callbacks,
					bottomInset = bottomInset,
					onToggle = { id -> onSelectedChange(if (id in selected) selected - id else selected + id) },
				)
			}
		}
	}
	}
}

@Composable
private fun PageList(
	page: Page,
	items: List<DownloadItemModel>,
	sort: DownloadSort,
	storage: StorageSummary?,
	selected: Set<Long>,
	imageLoader: ImageLoader,
	callbacks: DownloadsCallbacks,
	bottomInset: Dp,
	onToggle: (Long) -> Unit,
) {
	val hasStorage = page == Page.DONE && storage != null && storage.mangaCount > 0
	if (items.isEmpty() && !hasStorage) {
		Box(Modifier.fillMaxSize().padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
			Column(horizontalAlignment = Alignment.CenterHorizontally) {
				Icon(
					painterResource(R.drawable.ic_download),
					contentDescription = null,
					tint = MaterialTheme.colorScheme.outline,
					modifier = Modifier.size(56.dp),
				)
				Spacer(Modifier.height(12.dp))
				Text(
					text = stringResource(page.empty),
					style = MaterialTheme.typography.bodyLarge,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					textAlign = TextAlign.Center,
				)
			}
		}
		return
	}
	val sections = when (page) {
		Page.ACTIVE -> listOf(Bucket.DOWNLOADING, Bucket.WAITING, Bucket.PAUSED)
		Page.FAILED -> listOf(Bucket.FAILED)
		Page.DONE -> listOf(Bucket.FINISHED)
	}.mapNotNull { bucket ->
		val part = items.filter { it.bucket() == bucket }
		if (part.isEmpty()) {
			null
		} else {
			bucket to when (sort) {
				DownloadSort.QUEUE -> part
				DownloadSort.TITLE -> part.sortedBy { it.manga?.title?.lowercase().orEmpty() }
				DownloadSort.NEWEST -> part.sortedByDescending { it.timestamp }
			}
		}
	}
	LazyColumn(
		modifier = Modifier.fillMaxSize(),
		contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + bottomInset),
		verticalArrangement = Arrangement.spacedBy(2.dp),
	) {
		if (page == Page.ACTIVE) {
			item(key = "speed") { SpeedStrip(items) }
		}
		if (hasStorage && storage != null) {
			item(key = "storage") { StorageMeter(storage, callbacks.onDeleteStored) }
		}
		sections.forEach { (bucket, part) ->
			if (page == Page.ACTIVE) {
				item(key = "header-$bucket") { SectionHeader(stringResource(bucket.title), part.size) }
			}
			itemsIndexed(part, key = { _, it -> it.id }) { index, item ->
				val id = item.id.mostSignificantBits
				DownloadRow(
					item = item,
					imageLoader = imageLoader,
					isSelected = id in selected,
					shape = groupItemShape(index, part.size),
					callbacks = callbacks,
					onClick = { if (selected.isNotEmpty()) onToggle(id) else callbacks.onExpand(item) },
					onOpenManga = { if (selected.isNotEmpty()) onToggle(id) else callbacks.onOpen(item) },
					onLongClick = { onToggle(id) },
					canDrag = sort == DownloadSort.QUEUE && selected.isEmpty(),
					modifier = Modifier.animateItem(),
				)
			}
		}
	}
}

/** Same look as the title of a settings group. */
@Composable
private fun SectionHeader(title: String, count: Int) {
	Text(
		text = "$title · $count",
		style = MaterialTheme.typography.titleSmall,
		fontWeight = FontWeight.SemiBold,
		color = MaterialTheme.colorScheme.primary,
		modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 12.dp, bottom = 6.dp),
	)
}

/** Live speed on top of the active tab. */
@Composable
private fun SpeedStrip(active: List<DownloadItemModel>) {
	val context = LocalContext.current
	val running = active.filter { it.bucket() == Bucket.DOWNLOADING }
	val speed = running.sumOf { it.speed }
	val waiting = active.count { it.bucket() == Bucket.WAITING }
	val paused = active.count { it.bucket() == Bucket.PAUSED }
	Surface(
		shape = groupItemShape(0, 1),
		color = MaterialTheme.colorScheme.surfaceContainer,
		modifier = Modifier.fillMaxWidth(),
	) {
		Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
			Box(
				Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
				contentAlignment = Alignment.Center,
			) {
				Icon(
					painterResource(R.drawable.ic_download),
					contentDescription = null,
					tint = MaterialTheme.colorScheme.onPrimaryContainer,
				)
			}
			Spacer(Modifier.width(14.dp))
			Column {
				Text(
					text = context.getString(R.string.download_speed_format, Formatter.formatFileSize(context, speed)),
					style = MaterialTheme.typography.titleMedium,
				)
				Text(
					text = buildList {
						add(stringResource(R.string.download_summary_downloading, running.size))
						if (waiting > 0) add(stringResource(R.string.download_summary_waiting, waiting))
						if (paused > 0) add(stringResource(R.string.download_summary_paused, paused))
					}.joinToString(" · "),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
		}
	}
}

/** The saved-manga card of the storage settings, split by manga. Tap it to list the biggest ones. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StorageMeter(storage: StorageSummary, onDelete: (StorageEntry) -> Unit) {
	val context = LocalContext.current
	val cs = MaterialTheme.colorScheme
	var open by remember { mutableStateOf(false) }
	val colors = listOf(cs.primary, cs.tertiary, cs.secondary, cs.error, cs.outline)
	val total = storage.totalBytes.coerceAtLeast(1L).toFloat()
	val listed = storage.largest.sumOf { it.bytes }
	Surface(
		shape = groupItemShape(0, 1),
		color = cs.surfaceContainer,
		modifier = Modifier.fillMaxWidth(),
	) {
		Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
			Row(
				Modifier.fillMaxWidth().combinedClickable(onClick = { open = !open }),
				verticalAlignment = Alignment.CenterVertically,
			) {
				Column(Modifier.weight(1f)) {
					Text(stringResource(R.string.saved_manga), style = MaterialTheme.typography.titleMedium)
					Text(
						text = "${FileSize.BYTES.format(context, storage.totalBytes)} · " +
							stringResource(R.string.downloads_storage_summary, storage.mangaCount),
						style = MaterialTheme.typography.bodyMedium,
						color = cs.onSurfaceVariant,
					)
				}
				Icon(
					painterResource(if (open) R.drawable.ic_expand_more else R.drawable.ic_expand),
					contentDescription = null,
					tint = cs.onSurfaceVariant,
				)
			}
			SegmentedBar(
				segments = storage.largest.mapIndexed { i, entry ->
					BarSegment(entry.bytes / total, colors[i % colors.size])
				} + BarSegment((storage.totalBytes - listed).coerceAtLeast(0L) / total, cs.outlineVariant),
			)
			AnimatedVisibility(open) {
				Column {
					storage.largest.forEachIndexed { i, entry ->
						Row(verticalAlignment = Alignment.CenterVertically) {
							Box(Modifier.weight(1f)) {
								LegendItem(
									label = entry.manga.title,
									value = FileSize.BYTES.format(context, entry.bytes),
									color = colors[i % colors.size],
								)
							}
							IconButton(onClick = { onDelete(entry) }) {
								Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.delete))
							}
						}
					}
				}
			}
		}
	}
}

private class RingAction(@DrawableRes val icon: Int, @StringRes val label: Int, val onClick: () -> Unit)

/** The round pause, resume or retry button with the download's progress drawn around it. */
@Composable
private fun ActionRing(item: DownloadItemModel, hasError: Boolean, callbacks: DownloadsCallbacks) {
	val action: RingAction? = when {
		item.canPause -> RingAction(R.drawable.ic_action_pause, R.string.pause) { callbacks.onPause(item) }
		item.canResume -> if (hasError) {
			RingAction(R.drawable.ic_retry, R.string.retry) { callbacks.onResume(item) }
		} else {
			RingAction(R.drawable.ic_action_resume, R.string.resume) { callbacks.onResume(item) }
		}
		item.canRetry -> RingAction(R.drawable.ic_retry, R.string.retry) { callbacks.onRetry(item) }
		else -> null
	}
	if (action == null) return
	val running = item.workState == WorkInfo.State.RUNNING && !item.isQueued
	Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
		if (running && !item.isIndeterminate) {
			CircularProgressIndicator(
				progress = { item.percent.coerceIn(0f, 1f) },
				modifier = Modifier.fillMaxSize().padding(4.dp),
				strokeWidth = 3.dp,
				color = if (item.isPaused) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
				trackColor = MaterialTheme.colorScheme.surfaceVariant,
			)
		} else if (running && !item.isPaused) {
			CircularProgressIndicator(
				modifier = Modifier.fillMaxSize().padding(4.dp),
				strokeWidth = 3.dp,
			)
		}
		IconButton(onClick = action.onClick, modifier = Modifier.size(38.dp)) {
			Icon(
				painterResource(action.icon),
				contentDescription = stringResource(action.label),
				tint = if (item.canRetry) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
				modifier = Modifier.size(22.dp),
			)
		}
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadRow(
	item: DownloadItemModel,
	imageLoader: ImageLoader,
	isSelected: Boolean,
	shape: Shape,
	callbacks: DownloadsCallbacks,
	onClick: () -> Unit,
	onOpenManga: () -> Unit,
	onLongClick: () -> Unit,
	canDrag: Boolean,
	modifier: Modifier = Modifier,
) {
	val context = LocalContext.current
	// Drag to reorder: moving past half of the row's height swaps it with the neighbour
	var dragY by remember { mutableStateOf(0f) }
	var dragging by remember { mutableStateOf(false) }
	var rowHeight by remember { mutableStateOf(0) }
	val container by animateColorAsState(
		when {
			dragging -> MaterialTheme.colorScheme.surfaceContainerHighest
			isSelected -> MaterialTheme.colorScheme.secondaryContainer
			else -> MaterialTheme.colorScheme.surfaceContainer
		},
		label = "row_bg",
	)
	val chapters by item.chapters.collectAsState()
	val hasChapters = !chapters.isNullOrEmpty()
	val hasError = item.error != null
	val openManga = Modifier.combinedClickable(onClick = onOpenManga, onLongClick = onLongClick)
	val statusColor = when (item.bucket()) {
		Bucket.FAILED -> MaterialTheme.colorScheme.error
		Bucket.DOWNLOADING -> MaterialTheme.colorScheme.primary
		else -> MaterialTheme.colorScheme.onSurfaceVariant
	}
	Column(
		modifier = modifier
			.fillMaxWidth()
			.onSizeChanged { rowHeight = it.height }
			.zIndex(if (dragging) 1f else 0f)
			.graphicsLayer { translationY = dragY }
			.clip(shape)
			.background(container)
			.combinedClickable(onClick = onClick, onLongClick = onLongClick),
	) {
		Row(
			Modifier.padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Box(Modifier.size(ROW_COVER).clip(RoundedCornerShape(12.dp)).then(openManga)) {
				item.manga?.let { manga ->
					AsyncImage(
						model = remember(manga) {
							ImageRequest.Builder(context)
								.data(manga.coverUrl)
								.crossfade(true)
								.mangaSourceExtra(manga.source)
								.stableMangaCoverKey(manga, manga.coverUrl)
								.build()
						},
						imageLoader = imageLoader,
						contentDescription = null,
						contentScale = ContentScale.Crop,
						modifier = Modifier.fillMaxSize(),
					)
				}
				if (isSelected) {
					Box(
						Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)),
						contentAlignment = Alignment.Center,
					) {
						Icon(
							painterResource(R.drawable.ic_check),
							contentDescription = null,
							tint = MaterialTheme.colorScheme.onPrimary,
						)
					}
				}
			}
			Spacer(Modifier.width(12.dp))
			Column(Modifier.weight(1f)) {
				Text(
					text = item.manga?.title ?: stringResource(R.string.unknown),
					style = MaterialTheme.typography.bodyLarge,
					fontWeight = FontWeight.Medium,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
					modifier = openManga,
				)
				Spacer(Modifier.height(2.dp))
				Text(
					text = item.getInfoLine(context),
					style = MaterialTheme.typography.bodySmall,
					color = statusColor,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
				if (item.workState == WorkInfo.State.RUNNING && !item.isQueued) {
					Spacer(Modifier.height(8.dp))
					SegmentedProgress(item, barHeight = 4.dp)
					if (item.pageStates.isNotEmpty() && !item.isPaused && !item.isIndeterminate) {
						Spacer(Modifier.height(4.dp))
						PageSegments(item.pageStates)
					}
				}
				if (hasError) {
					Spacer(Modifier.height(2.dp))
					Text(
						text = item.error.orEmpty(),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.error,
						maxLines = 2,
						overflow = TextOverflow.Ellipsis,
					)
				}
				if (item.isPaused && hasError) {
					Row {
						TextButton(onClick = { callbacks.onSkip(item) }) { Text(stringResource(R.string.skip)) }
						TextButton(onClick = { callbacks.onSkipAll(item) }) { Text(stringResource(R.string.skip_all)) }
					}
				}
			}
			if (canDrag && item.canReorder) {
				Icon(
					painterResource(R.drawable.ic_reorder_handle),
					contentDescription = stringResource(R.string.downloads_drag_to_reorder),
					tint = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier
						.size(40.dp)
						.padding(8.dp)
						.pointerInput(item.id) {
							detectDragGestures(
								onDragStart = { dragging = true },
								onDragEnd = { dragging = false; dragY = 0f },
								onDragCancel = { dragging = false; dragY = 0f },
							) { change, amount ->
								change.consume()
								dragY += amount.y
								val half = rowHeight / 2f
								if (dragY > half) {
									callbacks.onMoveDown(item)
									dragY -= rowHeight
								} else if (dragY < -half) {
									callbacks.onMoveUp(item)
									dragY += rowHeight
								}
							}
						},
				)
			}
			ActionRing(item, hasError, callbacks)
			PopupMenuBox(entries = {
				buildList {
					if (item.manga != null) {
						add(PopupEntry(context.getString(R.string.downloads_open_manga), R.drawable.ic_open_external) { callbacks.onOpen(item) })
					}
					if (item.isFinished && item.manga != null) {
						add(PopupEntry(context.getString(R.string.download_missing), R.drawable.ic_download) { callbacks.onDownloadMissing(item) })
					}
					if (item.canReorder) {
						val ids = setOf(item.id.mostSignificantBits)
						add(PopupEntry(context.getString(R.string.download_move_top), R.drawable.ic_arrow_up) { callbacks.onMoveTop(ids) })
						add(PopupEntry(context.getString(R.string.download_move_up), R.drawable.ic_arrow_up) { callbacks.onMoveUp(item) })
						add(PopupEntry(context.getString(R.string.download_move_down), R.drawable.ic_arrow_down) { callbacks.onMoveDown(item) })
						add(PopupEntry(context.getString(R.string.download_move_end), R.drawable.ic_arrow_down) { callbacks.onMoveEnd(ids) })
					}
					if (hasChapters) {
						add(
							PopupEntry(
								context.getString(if (item.isExpanded) R.string.downloads_hide_chapters else R.string.downloads_show_chapters),
								R.drawable.ic_list,
							) { callbacks.onExpand(item) },
						)
					}
					add(PopupEntry(context.getString(R.string.downloads_select), R.drawable.ic_select_group) { onLongClick() })
					if (!item.isFinished) {
						add(PopupEntry(context.getString(android.R.string.cancel), R.drawable.ic_close) { callbacks.onCancel(item) })
					} else {
						add(PopupEntry(context.getString(R.string.remove), R.drawable.ic_delete) {
							callbacks.onRemoveSelected(setOf(item.id.mostSignificantBits))
						})
					}
				}
			}, modifier = Modifier.size(40.dp)) { open ->
				IconButton(onClick = open, modifier = Modifier.size(40.dp)) {
					Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.more))
				}
			}
		}
		AnimatedVisibility(item.isExpanded && hasChapters) {
			Column(
				Modifier
					.fillMaxWidth()
					.heightIn(max = 240.dp)
					.verticalScroll(rememberScrollState())
					.padding(start = 76.dp, end = 16.dp, bottom = 8.dp),
			) {
				chapters.orEmpty().forEach { chapter ->
					Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
						if (chapter.isDownloaded) {
							Icon(
								painterResource(R.drawable.ic_check),
								contentDescription = null,
								tint = MaterialTheme.colorScheme.primary,
								modifier = Modifier.size(18.dp),
							)
						} else {
							Spacer(Modifier.size(18.dp))
						}
						Spacer(Modifier.width(8.dp))
						Text(
							text = listOfNotNull(chapter.number, chapter.name.ifEmpty { null }).joinToString("  "),
							style = MaterialTheme.typography.bodyMedium,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
						)
					}
				}
			}
		}
	}
}

/** One segment per chapter, filled as each chapter finishes, like 1DM's connection bar. */
@Composable
private fun SegmentedProgress(item: DownloadItemModel, barHeight: Dp = 8.dp) {
	val track = MaterialTheme.colorScheme.surfaceVariant
	val fill = if (item.isPaused) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
	val total = item.totalChapters
	if (item.isIndeterminate || total <= 0) {
		LinearProgressIndicator(
			modifier = Modifier
				.fillMaxWidth()
				.height(barHeight)
				.clip(RoundedCornerShape(barHeight / 2)),
		)
		return
	}
	val done = item.chaptersDownloaded.coerceIn(0, total)
	val current = item.chapterProgress.coerceIn(0f, 1f)
	Canvas(
		Modifier
			.fillMaxWidth()
			.height(barHeight)
			.clip(RoundedCornerShape(barHeight / 2)),
	) {
		val h = size.height
		if (total > MAX_SEGMENTS) {
			drawRect(track, size = size)
			drawRect(fill, size = Size(size.width * ((done + current) / total).coerceAtMost(1f), h))
		} else {
			val gap = 2.dp.toPx()
			val w = (size.width - gap * (total - 1)) / total
			for (i in 0 until total) {
				val x = i * (w + gap)
				drawRect(track, Offset(x, 0f), Size(w, h))
				val amount = when {
					i < done -> 1f
					i == done -> current
					else -> 0f
				}
				if (amount > 0f) {
					drawRect(fill, Offset(x, 0f), Size(w * amount, h))
				}
			}
		}
	}
}

/**
 * One segment per page of the chapter being downloaded. Finished pages are filled and the ones
 * being fetched right now are striped and moving, so the parallel connections are visible.
 */
@Composable
private fun PageSegments(states: String) {
	val track = MaterialTheme.colorScheme.surfaceVariant
	val fill = MaterialTheme.colorScheme.primary
	val phase by rememberInfiniteTransition(label = "pages").animateFloat(
		initialValue = 0f,
		targetValue = 1f,
		animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
		label = "stripes",
	)
	Canvas(
		Modifier
			.fillMaxWidth()
			.height(6.dp)
			.clip(RoundedCornerShape(3.dp)),
	) {
		val h = size.height
		val count = states.length
		val gap = if (count > 60) 1.dp.toPx() else 2.dp.toPx()
		val w = (size.width - gap * (count - 1)) / count
		val step = 6.dp.toPx()
		for (i in 0 until count) {
			val x = i * (w + gap)
			drawRect(track, Offset(x, 0f), Size(w, h))
			when (states[i]) {
				'2' -> drawRect(fill, Offset(x, 0f), Size(w, h))
				'1' -> clipRect(x, 0f, x + w, h) {
					drawRect(fill.copy(alpha = 0.35f), Offset(x, 0f), Size(w, h))
					var sx = x - step + phase * step
					while (sx < x + w + h) {
						drawLine(fill, Offset(sx, h), Offset(sx + h, 0f), strokeWidth = step / 3f)
						sx += step
					}
				}
			}
		}
	}
}

private const val MAX_SEGMENTS = 80
