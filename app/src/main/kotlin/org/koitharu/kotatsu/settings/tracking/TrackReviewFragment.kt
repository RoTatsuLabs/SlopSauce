package org.koitharu.kotatsu.settings.tracking

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.db.entity.toManga
import org.koitharu.kotatsu.core.nav.router
import org.koitharu.kotatsu.core.ui.dialog.showConfirmDialog
import org.koitharu.kotatsu.core.ui.image.coverBlur
import org.koitharu.kotatsu.core.util.ext.mangaSourceExtra
import org.koitharu.kotatsu.core.util.ext.observe
import org.koitharu.kotatsu.core.util.ext.stableMangaCoverKey
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.scrobbling.autotrack.AutoTrackStore
import org.koitharu.kotatsu.scrobbling.autotrack.AutoTrackWorker
import org.koitharu.kotatsu.scrobbling.autotrack.AutoTracker
import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblerService
import org.koitharu.kotatsu.settings.compose.BaseComposeSettingsFragment
import org.koitharu.kotatsu.settings.compose.DropSauceTheme
import org.koitharu.kotatsu.settings.compose.GroupItemPosition
import org.koitharu.kotatsu.settings.compose.SettingsGroup
import org.koitharu.kotatsu.settings.compose.SettingsScaffold
import javax.inject.Inject

/**
 * The favourites that have no clear match on a tracker: one full screen list, grouped per tracker,
 * with the cover and the full title so each one can be picked by hand.
 */
@AndroidEntryPoint
class TrackReviewFragment : BaseComposeSettingsFragment(R.string.auto_track_review) {

	private class Entry(val manga: Manga, val service: ScrobblerService)

	@Inject
	lateinit var autoTracker: AutoTracker

	@Inject
	lateinit var autoTrackStore: AutoTrackStore

	@Inject
	lateinit var autoTrackScheduler: AutoTrackWorker.Scheduler

	@Inject
	lateinit var database: MangaDatabase

	@Inject
	lateinit var coil: ImageLoader

	private val entries = MutableStateFlow<List<Entry>>(emptyList())

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View = ComposeView(requireContext()).apply {
		setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
		setContent {
			DropSauceTheme {
				val items by entries.collectAsState()
				ReviewScreen(
					entries = items,
					imageLoader = coil,
					onLinkAll = ::confirmLinkAll,
					onPick = { router.showScrobblingSelectorSheet(it.manga, it.service) },
				)
			}
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		// Picking an entry by hand (or un-favouriting) changes the list while this screen stays open
		database.invalidationTracker.createFlow("scrobblings", "favourites").observe(viewLifecycleOwner) {
			reload()
		}
		reload()
	}

	private fun reload() {
		viewLifecycleOwner.lifecycleScope.launch {
			val found = withContext(Dispatchers.Default) {
				autoTracker.pendingReview().mapNotNull { (mangaId, service) ->
					val manga = database.getMangaDao().find(mangaId)?.toManga() ?: return@mapNotNull null
					Entry(manga, service)
				}
			}
			entries.value = found
			// The last one was linked: nothing is left to show, so go back to Services
			if (found.isEmpty()) parentFragmentManager.popBackStack()
		}
	}

	private fun confirmLinkAll() {
		val context = context ?: return
		val count = entries.value.map { it.manga.id }.distinct().size
		if (count == 0) return
		showConfirmDialog(
			context = context,
			icon = R.drawable.ic_sync_problem,
			title = context.getString(R.string.auto_track_review),
			message = context.getString(R.string.auto_track_closest_confirm, count),
			confirmLabel = context.getString(R.string.auto_track_start),
		) { autoTrackScheduler.start(closest = true) }
	}

	@Composable
	private fun ReviewScreen(
		entries: List<Entry>,
		imageLoader: ImageLoader,
		onLinkAll: () -> Unit,
		onPick: (Entry) -> Unit,
	) {
		SettingsScaffold {
			if (entries.isEmpty()) return@SettingsScaffold
			item {
				FilledTonalButton(
					onClick = onLinkAll,
					modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
				) {
					Text(stringResource(R.string.auto_track_link_all))
				}
			}
			entries.groupBy { it.service }.forEach { (service, group) ->
				item {
					SettingsGroup(title = "${stringResource(service.titleResId)} · ${group.size}") {
						group.forEach { entry ->
							item { pos -> ReviewRow(entry, pos, imageLoader) { onPick(entry) } }
						}
					}
				}
			}
		}
	}

	@Composable
	private fun ReviewRow(entry: Entry, pos: GroupItemPosition, imageLoader: ImageLoader, onClick: () -> Unit) {
		val context = LocalContext.current
		val manga = entry.manga
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.clip(pos.shape)
				.background(MaterialTheme.colorScheme.surfaceContainer)
				.clickable(onClick = onClick)
				.padding(horizontal = 12.dp, vertical = 10.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(12.dp),
		) {
			Box(Modifier.size(width = 48.dp, height = 68.dp).clip(RoundedCornerShape(10.dp))) {
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
					modifier = Modifier.fillMaxSize().coverBlur(manga),
				)
			}
			Column(Modifier.weight(1f)) {
				Text(
					text = manga.title,
					style = MaterialTheme.typography.titleMedium,
					maxLines = 2,
					overflow = TextOverflow.Ellipsis,
				)
				Text(
					text = manga.source.name,
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}
			Icon(
				painter = painterResource(R.drawable.ic_chevron_right),
				contentDescription = null,
				tint = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.size(20.dp),
			)
		}
	}
}
