package org.koitharu.kotatsu.download.ui.list

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.WindowInsetsCompat
import coil3.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.core.nav.router
import org.koitharu.kotatsu.core.ui.BaseActivity
import org.koitharu.kotatsu.core.ui.util.ReversibleActionObserver
import org.koitharu.kotatsu.core.util.ext.observeEvent
import org.koitharu.kotatsu.databinding.ActivityDownloadsBinding
import org.koitharu.kotatsu.download.ui.worker.DownloadWorker
import org.koitharu.kotatsu.settings.compose.DropSauceTheme
import javax.inject.Inject

@AndroidEntryPoint
class DownloadsActivity : BaseActivity<ActivityDownloadsBinding>() {

	@Inject
	lateinit var coil: ImageLoader

	@Inject
	lateinit var scheduler: DownloadWorker.Scheduler

	private val viewModel by viewModels<DownloadsViewModel>()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityDownloadsBinding.inflate(layoutInflater))
		val callbacks = DownloadsCallbacks(
			onBack = ::finish,
			onOpen = { item -> item.manga?.let { router.openDetails(it) } },
			onExpand = viewModel::expandCollapse,
			onPause = { scheduler.pause(it.id) },
			onResume = { scheduler.resume(it.id) },
			onRetry = { viewModel.retry(it.id) },
			onCancel = { viewModel.cancel(it.id) },
			onSkip = { scheduler.skip(it.id) },
			onSkipAll = { scheduler.skipAll(it.id) },
			onPauseSelected = viewModel::pause,
			onResumeSelected = viewModel::resume,
			onCancelSelected = viewModel::cancel,
			onRemoveSelected = viewModel::remove,
			onMoveTop = viewModel::moveToTop,
			onMoveEnd = viewModel::moveToEnd,
			onPauseAll = viewModel::pauseAll,
			onResumeAll = viewModel::resumeAll,
			onCancelAll = viewModel::cancelAll,
			onRemoveCompleted = viewModel::removeCompleted,
			onSettings = { router.openDownloadsSetting() },
		)
		viewBinding.composeView.setViewCompositionStrategy(
			ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
		)
		viewBinding.composeView.setContent {
			DropSauceTheme {
				val models by viewModel.models.collectAsState()
				val hasActive by viewModel.hasActiveWorks.collectAsState()
				val hasPaused by viewModel.hasPausedWorks.collectAsState()
				val hasCancellable by viewModel.hasCancellableWorks.collectAsState()
				DownloadsScreen(
					models = models,
					imageLoader = coil,
					hasActive = hasActive,
					hasPaused = hasPaused,
					hasCancellable = hasCancellable,
					callbacks = callbacks,
				)
			}
		}
		viewModel.onActionDone.observeEvent(this, ReversibleActionObserver(viewBinding.composeView))
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat = insets
}
