package org.koitharu.kotatsu.download.ui.list

import android.os.Bundle
import android.text.format.Formatter
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.view.ActionMode
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePaddingRelative
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil3.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.nav.router
import org.koitharu.kotatsu.core.ui.BaseActivity
import org.koitharu.kotatsu.core.ui.dialog.showConfirmDialog
import org.koitharu.kotatsu.core.ui.util.ReversibleActionObserver
import org.koitharu.kotatsu.core.util.ext.end
import org.koitharu.kotatsu.core.util.ext.observeEvent
import org.koitharu.kotatsu.core.util.ext.start
import org.koitharu.kotatsu.databinding.ActivityDownloadsBinding
import org.koitharu.kotatsu.download.ui.worker.DownloadWorker
import org.koitharu.kotatsu.settings.compose.DropSauceTheme
import javax.inject.Inject

/**
 * The downloads list in Compose, inside the medium collapsing app bar every other screen uses.
 * The activity owns the toolbar menu, the selection bar and the dialogs, like the other screens.
 */
@AndroidEntryPoint
class DownloadsActivity : BaseActivity<ActivityDownloadsBinding>() {

	@Inject
	lateinit var coil: ImageLoader

	@Inject
	lateinit var scheduler: DownloadWorker.Scheduler

	private val viewModel by viewModels<DownloadsViewModel>()

	private val selection = mutableStateOf(emptySet<Long>())
	private val bottomInset = mutableIntStateOf(0)
	private var actionMode: ActionMode? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityDownloadsBinding.inflate(layoutInflater))
		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = false)
		setTitle(R.string.downloads)
		addMenuProvider(DownloadsMenuProvider(this, viewModel))
		val callbacks = DownloadsCallbacks(
			onOpen = { item -> item.manga?.let { router.openDetails(it) } },
			onExpand = viewModel::expandCollapse,
			onPause = { scheduler.pause(it.id) },
			onResume = { scheduler.resume(it.id) },
			onRetry = { viewModel.retry(it.id) },
			onCancel = { viewModel.cancel(it.id) },
			onSkip = { scheduler.skip(it.id) },
			onSkipAll = { scheduler.skipAll(it.id) },
			onRemoveSelected = viewModel::remove,
			onMoveTop = viewModel::moveToTop,
			onMoveEnd = viewModel::moveToEnd,
			onMoveUp = { viewModel.moveUp(it.id) },
			onMoveDown = { viewModel.moveDown(it.id) },
			onDownloadMissing = viewModel::downloadMissing,
			onDeleteStored = ::confirmDeleteStored,
		)
		viewBinding.composeView.setViewCompositionStrategy(
			ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
		)
		viewBinding.composeView.setContent {
			DropSauceTheme {
				val density = LocalDensity.current
				val models by viewModel.models.collectAsState()
				val storage by viewModel.storage.collectAsState()
				val sort by viewModel.sort.collectAsState()
				DownloadsScreen(
					models = models,
					storage = storage,
					imageLoader = coil,
					sort = sort,
					selected = selection.value,
					onSelectedChange = { selection.value = it },
					bottomInset = with(density) { bottomInset.intValue.toDp() },
					callbacks = callbacks,
				)
			}
		}
		viewModel.onActionDone.observeEvent(this, ReversibleActionObserver(viewBinding.composeView))
		lifecycleScope.launch {
			repeatOnLifecycle(Lifecycle.State.STARTED) {
				launch { snapshotFlow { selection.value }.collect(::onSelectionChanged) }
				// The toolbar menu shows the sort and hides what does not apply right now
				launch {
					combine(
						viewModel.sort,
						viewModel.hasActiveWorks,
						viewModel.hasPausedWorks,
						viewModel.hasCancellableWorks,
						viewModel.models,
					) { _, _, _, _, _ -> }.collect { invalidateMenu() }
				}
			}
		}
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
		viewBinding.appbar.updatePaddingRelative(
			start = bars.start(v),
			top = bars.top,
			end = bars.end(v),
		)
		bottomInset.intValue = bars.bottom
		return insets
	}

	private fun selectedItems(): List<DownloadItemModel> {
		val ids = selection.value
		return viewModel.models.value.orEmpty().filter { it.id.mostSignificantBits in ids }
	}

	private fun onSelectionChanged(ids: Set<Long>) {
		if (ids.isEmpty()) {
			val mode = actionMode
			actionMode = null
			mode?.finish()
		} else {
			if (actionMode == null) {
				actionMode = startSupportActionMode(modeCallback)
			}
			actionMode?.invalidate()
		}
	}

	private val modeCallback = object : ActionMode.Callback {

		override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
			mode.menuInflater.inflate(R.menu.mode_downloads, menu)
			return true
		}

		override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
			val items = selectedItems()
			mode.title = getString(R.string.downloads_selected, selection.value.size)
			menu.findItem(R.id.action_pause)?.isVisible = items.isNotEmpty() && items.all { it.canPause }
			menu.findItem(R.id.action_resume)?.isVisible = items.isNotEmpty() && items.all { it.canResume }
			val reorder = items.isNotEmpty() && items.all { it.canReorder }
			menu.findItem(R.id.action_move_top)?.isVisible = reorder
			menu.findItem(R.id.action_move_end)?.isVisible = reorder
			menu.findItem(R.id.action_cancel)?.isVisible = items.isNotEmpty() && items.all { !it.isFinished }
			menu.findItem(R.id.action_remove)?.isVisible = items.isNotEmpty() && items.all { it.isFinished }
			return true
		}

		override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
			val ids = selection.value
			when (item.itemId) {
				R.id.action_pause -> viewModel.pause(ids)
				R.id.action_resume -> viewModel.resume(ids)
				R.id.action_move_top -> viewModel.moveToTop(ids)
				R.id.action_move_end -> viewModel.moveToEnd(ids)
				R.id.action_cancel -> viewModel.cancel(ids)
				R.id.action_remove -> viewModel.remove(ids)
				R.id.action_select_all -> {
					val first = selectedItems().firstOrNull() ?: return true
					selection.value = viewModel.models.value.orEmpty()
						.onSamePageAs(first)
						.mapTo(HashSet()) { it.id.mostSignificantBits }
					return true
				}

				else -> return false
			}
			mode.finish()
			return true
		}

		override fun onDestroyActionMode(mode: ActionMode) {
			actionMode = null
			selection.value = emptySet()
		}
	}

	private fun confirmDeleteStored(entry: StorageEntry) {
		showConfirmDialog(
			context = this,
			icon = R.drawable.ic_delete,
			title = entry.manga.title,
			message = getString(R.string.downloads_storage_delete_confirm, Formatter.formatFileSize(this, entry.bytes)),
			confirmLabel = getString(R.string.delete),
			destructive = true,
		) { viewModel.deleteStored(entry.manga) }
	}
}
