package org.koitharu.kotatsu.download.ui.list

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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

/** Everything the downloads screen can ask for. Ids are the ones the view model uses. */
class DownloadsCallbacks(
	val onBack: () -> Unit,
	val onOpen: (DownloadItemModel) -> Unit,
	val onExpand: (DownloadItemModel) -> Unit,
	val onPause: (DownloadItemModel) -> Unit,
	val onResume: (DownloadItemModel) -> Unit,
	val onRetry: (DownloadItemModel) -> Unit,
	val onCancel: (DownloadItemModel) -> Unit,
	val onSkip: (DownloadItemModel) -> Unit,
	val onSkipAll: (DownloadItemModel) -> Unit,
	val onPauseSelected: (Set<Long>) -> Unit,
	val onResumeSelected: (Set<Long>) -> Unit,
	val onCancelSelected: (Set<Long>) -> Unit,
	val onRemoveSelected: (Set<Long>) -> Unit,
	val onMoveTop: (Set<Long>) -> Unit,
	val onMoveEnd: (Set<Long>) -> Unit,
	val onPauseAll: () -> Unit,
	val onResumeAll: () -> Unit,
	val onCancelAll: () -> Unit,
	val onRemoveCompleted: () -> Unit,
	val onSettings: () -> Unit,
)

private val ROW_COVER_WIDTH = 60.dp
private val ROW_COVER_HEIGHT = 86.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
	models: List<DownloadItemModel>?,
	imageLoader: ImageLoader,
	hasActive: Boolean,
	hasPaused: Boolean,
	hasCancellable: Boolean,
	callbacks: DownloadsCallbacks,
) {
	var selected by remember { mutableStateOf(emptySet<Long>()) }
	var menuOpen by remember { mutableStateOf(false) }
	var confirmCancelAll by remember { mutableStateOf(false) }
	var confirmRemoveCompleted by remember { mutableStateOf(false) }

	val all = models.orEmpty()
	LaunchedEffect(models) {
		val present = all.mapTo(HashSet()) { it.id.mostSignificantBits }
		if (!present.containsAll(selected)) {
			selected = selected.filterTo(HashSet()) { it in present }
		}
	}
	BackHandler(enabled = selected.isNotEmpty()) { selected = emptySet() }

	val active = all.filter { !it.isFinished }
	val visible = all
	val selectedItems = all.filter { it.id.mostSignificantBits in selected }
	val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

	Scaffold(
		modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
		topBar = {
			if (selected.isEmpty()) {
				LargeFlexibleTopAppBar(
					scrollBehavior = scrollBehavior,
					title = { Text(stringResource(R.string.downloads)) },
					navigationIcon = {
						IconButton(onClick = callbacks.onBack) {
							Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = null)
						}
					},
					actions = {
						if (hasActive) {
							IconButton(onClick = callbacks.onPauseAll) {
								Icon(painterResource(R.drawable.ic_action_pause), stringResource(R.string.pause))
							}
						}
						if (hasPaused) {
							IconButton(onClick = callbacks.onResumeAll) {
								Icon(painterResource(R.drawable.ic_action_resume), stringResource(R.string.resume))
							}
						}
						Box {
							IconButton(onClick = { menuOpen = true }) {
								Icon(painterResource(R.drawable.ic_more_vert), contentDescription = null)
							}
							DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
								if (hasCancellable) {
									DropdownMenuItem(
										text = { Text(stringResource(R.string.cancel_all)) },
										onClick = {
											menuOpen = false
											confirmCancelAll = true
										},
									)
								}
								DropdownMenuItem(
									text = { Text(stringResource(R.string.remove_completed)) },
									onClick = {
										menuOpen = false
										confirmRemoveCompleted = true
									},
								)
								DropdownMenuItem(
									text = { Text(stringResource(R.string.settings)) },
									onClick = {
										menuOpen = false
										callbacks.onSettings()
									},
								)
							}
						}
					},
				)
			} else {
				SelectionBar(
					items = selectedItems,
					ids = selected,
					onClose = { selected = emptySet() },
					onSelectAll = { selected = visible.mapTo(HashSet()) { it.id.mostSignificantBits } },
					callbacks = callbacks,
				)
			}
		},
	) { padding ->
		Column(Modifier.padding(padding).fillMaxSize()) {
			when {
				models == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
					CircularProgressIndicator()
				}

				visible.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
					Text(
						text = stringResource(R.string.text_downloads_list_holder),
						style = MaterialTheme.typography.bodyLarge,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						textAlign = TextAlign.Center,
					)
				}

				else -> LazyColumn(
					contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
					verticalArrangement = Arrangement.spacedBy(3.dp),
				) {
					if (active.isNotEmpty()) {
						item(key = "summary") {
							SummaryBar(active)
							Spacer(Modifier.height(8.dp))
						}
					}
					itemsIndexed(visible, key = { _, it -> it.id }) { index, item ->
						val id = item.id.mostSignificantBits
						DownloadRow(
							item = item,
							imageLoader = imageLoader,
							isSelected = id in selected,
							shape = groupShape(index, visible.size),
							callbacks = callbacks,
							onClick = {
								if (selected.isNotEmpty()) {
									selected = if (id in selected) selected - id else selected + id
								} else {
									callbacks.onExpand(item)
								}
							},
							onOpenManga = {
								if (selected.isNotEmpty()) {
									selected = if (id in selected) selected - id else selected + id
								} else {
									callbacks.onOpen(item)
								}
							},
							onLongClick = { selected = if (id in selected) selected - id else selected + id },
							modifier = Modifier.animateItem(),
						)
					}
				}
			}
		}
	}

	if (confirmCancelAll) {
		ConfirmDialog(
			title = stringResource(R.string.cancel_all),
			message = stringResource(R.string.cancel_all_downloads_confirm),
			confirm = stringResource(R.string.confirm),
			onConfirm = callbacks.onCancelAll,
			onDismiss = { confirmCancelAll = false },
		)
	}
	if (confirmRemoveCompleted) {
		ConfirmDialog(
			title = stringResource(R.string.remove_completed),
			message = stringResource(R.string.remove_completed_downloads_confirm),
			confirm = stringResource(R.string.clear),
			onConfirm = callbacks.onRemoveCompleted,
			onDismiss = { confirmRemoveCompleted = false },
		)
	}
}

@Composable
private fun ConfirmDialog(
	title: String,
	message: String,
	confirm: String,
	onConfirm: () -> Unit,
	onDismiss: () -> Unit,
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(title) },
		text = { Text(message) },
		confirmButton = {
			TextButton(onClick = {
				onDismiss()
				onConfirm()
			}) { Text(confirm) }
		},
		dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
	)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBar(
	items: List<DownloadItemModel>,
	ids: Set<Long>,
	onClose: () -> Unit,
	onSelectAll: () -> Unit,
	callbacks: DownloadsCallbacks,
) {
	val canPause = items.isNotEmpty() && items.all { it.canPause }
	val canResume = items.isNotEmpty() && items.all { it.canResume }
	val canCancel = items.isNotEmpty() && items.all { !it.isFinished }
	val canRemove = items.isNotEmpty() && items.all { it.isFinished }
	val canReorder = items.isNotEmpty() && items.all { it.canReorder }
	TopAppBar(
		title = { Text(ids.size.toString()) },
		navigationIcon = {
			IconButton(onClick = onClose) {
				Icon(painterResource(R.drawable.ic_close), contentDescription = null)
			}
		},
		actions = {
			if (canReorder) {
				IconButton(onClick = { callbacks.onMoveTop(ids); onClose() }) {
					Icon(painterResource(R.drawable.ic_arrow_up), stringResource(R.string.download_move_top))
				}
				IconButton(onClick = { callbacks.onMoveEnd(ids); onClose() }) {
					Icon(painterResource(R.drawable.ic_expand_more), stringResource(R.string.download_move_end))
				}
			}
			if (canPause) {
				IconButton(onClick = { callbacks.onPauseSelected(ids); onClose() }) {
					Icon(painterResource(R.drawable.ic_action_pause), stringResource(R.string.pause))
				}
			}
			if (canResume) {
				IconButton(onClick = { callbacks.onResumeSelected(ids); onClose() }) {
					Icon(painterResource(R.drawable.ic_action_resume), stringResource(R.string.resume))
				}
			}
			if (canCancel) {
				IconButton(onClick = { callbacks.onCancelSelected(ids); onClose() }) {
					Icon(painterResource(R.drawable.ic_cancel_multiple), stringResource(android.R.string.cancel))
				}
			}
			if (canRemove) {
				IconButton(onClick = { callbacks.onRemoveSelected(ids); onClose() }) {
					Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.remove))
				}
			}
			IconButton(onClick = onSelectAll) {
				Icon(painterResource(R.drawable.ic_select_group), stringResource(android.R.string.selectAll))
			}
		},
	)
}

/** Total speed and counts for everything that is not finished. */
@Composable
private fun SummaryBar(active: List<DownloadItemModel>) {
	val context = LocalContext.current
	val running = active.filter { it.workState == WorkInfo.State.RUNNING && !it.isQueued && !it.isPaused }
	val speed = running.sumOf { it.speed }
	val waiting = active.count { it.isQueued || it.workState != WorkInfo.State.RUNNING }
	Surface(
		shape = MaterialTheme.shapes.extraLarge,
		color = MaterialTheme.colorScheme.primaryContainer,
		contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
		modifier = Modifier.fillMaxWidth(),
	) {
		Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
			Text(
				text = context.getString(R.string.download_speed_format, Formatter.formatFileSize(context, speed)),
				style = MaterialTheme.typography.displaySmall,
			)
			Spacer(Modifier.height(4.dp))
			Text(
				text = stringResource(R.string.download_summary_line, running.size, waiting),
				style = MaterialTheme.typography.bodyLarge,
			)
		}
	}
}

/** Cards in a list share one rounded block: big corners at the ends, small ones between. */
private fun groupShape(index: Int, count: Int): RoundedCornerShape {
	val big = 28.dp
	val small = 6.dp
	return RoundedCornerShape(
		topStart = if (index == 0) big else small,
		topEnd = if (index == 0) big else small,
		bottomStart = if (index == count - 1) big else small,
		bottomEnd = if (index == count - 1) big else small,
	)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadRow(
	item: DownloadItemModel,
	imageLoader: ImageLoader,
	isSelected: Boolean,
	shape: RoundedCornerShape,
	callbacks: DownloadsCallbacks,
	onClick: () -> Unit,
	onOpenManga: () -> Unit,
	onLongClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val context = LocalContext.current
	val container by animateColorAsState(
		if (isSelected) {
			MaterialTheme.colorScheme.secondaryContainer
		} else {
			MaterialTheme.colorScheme.surfaceContainer
		},
		label = "row_bg",
	)
	val chapters by item.chapters.collectAsState()
	val hasChapters = !chapters.isNullOrEmpty()
	val hasError = item.error != null
	val hasActions = !item.isFinished
	val openManga = Modifier.combinedClickable(onClick = onOpenManga, onLongClick = onLongClick)
	Surface(
		modifier = modifier
			.fillMaxWidth()
			.clip(shape)
			.combinedClickable(onClick = onClick, onLongClick = onLongClick),
		shape = shape,
		color = container,
	) {
		Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = if (hasActions) 8.dp else 16.dp)) {
			Row(Modifier.padding(end = 8.dp)) {
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
						modifier = Modifier
							.size(ROW_COVER_WIDTH, ROW_COVER_HEIGHT)
							.clip(RoundedCornerShape(16.dp))
							.then(openManga),
					)
				} ?: Spacer(Modifier.size(ROW_COVER_WIDTH, ROW_COVER_HEIGHT))
				Spacer(Modifier.width(16.dp))
				Column(Modifier.weight(1f)) {
					Text(
						text = item.manga?.title ?: stringResource(R.string.unknown),
						style = MaterialTheme.typography.titleMedium,
						maxLines = 2,
						overflow = TextOverflow.Ellipsis,
						modifier = openManga,
					)
					Spacer(Modifier.height(4.dp))
					Text(
						text = item.getInfoLine(context),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						maxLines = 2,
						overflow = TextOverflow.Ellipsis,
					)
					if (hasError) {
						Text(
							text = item.error.orEmpty(),
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.error,
							maxLines = 2,
							overflow = TextOverflow.Ellipsis,
						)
					}
				}
			}
			if (item.workState == WorkInfo.State.RUNNING && !item.isQueued) {
				Spacer(Modifier.height(14.dp))
				Box(Modifier.padding(end = 8.dp)) { SegmentedProgress(item) }
			}
			if (hasActions) {
				Row(verticalAlignment = Alignment.CenterVertically) {
					Spacer(Modifier.weight(1f))
					if (item.isPaused && hasError) {
						TextButton(onClick = { callbacks.onSkip(item) }) { Text(stringResource(R.string.skip)) }
						TextButton(onClick = { callbacks.onSkipAll(item) }) { Text(stringResource(R.string.skip_all)) }
					}
					if (!item.isFinished) {
						IconButton(onClick = { callbacks.onCancel(item) }) {
							Icon(painterResource(R.drawable.ic_close), stringResource(android.R.string.cancel))
						}
					}
					when {
						item.canPause -> FilledTonalIconButton(onClick = { callbacks.onPause(item) }) {
							Icon(painterResource(R.drawable.ic_action_pause), stringResource(R.string.pause))
						}

						item.canResume -> FilledTonalIconButton(onClick = { callbacks.onResume(item) }) {
							Icon(
								painterResource(if (hasError) R.drawable.ic_retry else R.drawable.ic_action_resume),
								stringResource(if (hasError) R.string.retry else R.string.resume),
							)
						}

						item.canRetry -> FilledTonalIconButton(onClick = { callbacks.onRetry(item) }) {
							Icon(painterResource(R.drawable.ic_retry), stringResource(R.string.retry))
						}
					}
					Spacer(Modifier.width(4.dp))
				}
			}
			AnimatedVisibility(item.isExpanded && hasChapters) {
				Column(
					Modifier
						.fillMaxWidth()
						.heightIn(max = 240.dp)
						.verticalScroll(rememberScrollState())
						.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
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
}

/** One segment per chapter, filled as each chapter finishes, like 1DM's connection bar. */
@Composable
private fun SegmentedProgress(item: DownloadItemModel) {
	val track = MaterialTheme.colorScheme.surfaceVariant
	val fill = if (item.isPaused) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
	val total = item.totalChapters
	if (item.isIndeterminate || total <= 0) {
		LinearProgressIndicator(
			modifier = Modifier
				.fillMaxWidth()
				.height(8.dp)
				.clip(RoundedCornerShape(4.dp)),
		)
		return
	}
	val done = item.chaptersDownloaded.coerceIn(0, total)
	val current = item.chapterProgress.coerceIn(0f, 1f)
	Canvas(
		Modifier
			.fillMaxWidth()
			.height(8.dp)
			.clip(RoundedCornerShape(4.dp)),
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

private const val MAX_SEGMENTS = 80
