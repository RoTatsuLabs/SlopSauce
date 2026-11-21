package org.koitharu.kotatsu.settings.tracking

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.ui.dialog.DialogAction
import org.koitharu.kotatsu.core.ui.dialog.showActionChoiceDialog
import org.koitharu.kotatsu.core.ui.dialog.showConfirmDialog
import org.koitharu.kotatsu.core.util.ext.observe
import org.koitharu.kotatsu.scrobbling.autotrack.AutoTrackStore
import org.koitharu.kotatsu.scrobbling.autotrack.AutoTrackWorker
import org.koitharu.kotatsu.scrobbling.autotrack.AutoTracker
import org.koitharu.kotatsu.settings.SettingsActivity
import org.koitharu.kotatsu.settings.compose.ActionSettingsItem
import org.koitharu.kotatsu.settings.compose.BaseComposeSettingsFragment
import org.koitharu.kotatsu.settings.compose.DropSauceTheme
import org.koitharu.kotatsu.settings.compose.SettingsGroup
import org.koitharu.kotatsu.settings.compose.SettingsItem
import org.koitharu.kotatsu.settings.compose.SettingsScaffold
import org.koitharu.kotatsu.settings.compose.SettingsSwitch
import org.koitharu.kotatsu.settings.compose.SwitchSettingsItem
import org.koitharu.kotatsu.settings.compose.rememberBooleanPref
import org.koitharu.kotatsu.settings.compose.rememberIntPref
import javax.inject.Inject

@AndroidEntryPoint
class TrackingSettingsFragment : BaseComposeSettingsFragment(R.string.tracking_options) {

	@Inject
	lateinit var autoTracker: AutoTracker

	@Inject
	lateinit var autoTrackScheduler: AutoTrackWorker.Scheduler

	@Inject
	lateinit var autoTrackStore: AutoTrackStore

	@Inject
	lateinit var database: MangaDatabase

	private val autoTrackStatus = MutableStateFlow<String?>(null)
	private val reviewCount = MutableStateFlow(0)

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View = ComposeView(requireContext()).apply {
		setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
		setContent {
			DropSauceTheme {
				val status by autoTrackStatus.collectAsState()
				val reviewTotal by reviewCount.collectAsState()
				TrackingScreen(
					autoTrackStatus = status,
					reviewCount = reviewTotal,
					onTrackUntracked = ::confirmTrackUntracked,
					onOpenReview = {
						(activity as? SettingsActivity)?.openFragment(
							TrackReviewFragment::class.java,
							null,
							isFromRoot = false,
						)
					},
				)
			}
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		// Picking an entry by hand (or un-favouriting) changes the review list while this screen stays open
		database.invalidationTracker.createFlow("scrobblings", "favourites").observe(viewLifecycleOwner) {
			reviewCount.value = autoTracker.pendingReview().size
		}
		autoTrackScheduler.observe().observe(viewLifecycleOwner) { progress ->
			reviewCount.value = autoTracker.pendingReview().size
			autoTrackStatus.value = when {
				progress == null -> autoTrackStore.lastSummary?.let {
					getString(R.string.auto_track_summary, it.tracked, it.needsReview, it.failed)
				} ?: getString(R.string.auto_track_idle)

				progress.isWaiting -> getString(R.string.auto_track_waiting)
				else -> getString(R.string.auto_track_progress, progress.done, progress.total)
			}
		}
	}

	override fun onResume() {
		super.onResume()
		viewLifecycleOwner.lifecycleScope.launch {
			reviewCount.value = withContext(Dispatchers.Default) { autoTracker.pendingReview().size }
		}
	}

	private fun showInfo(@StringRes title: Int, message: String) {
		val context = context ?: return
		showConfirmDialog(
			context = context,
			icon = R.drawable.ic_sync,
			title = context.getString(title),
			message = message,
			onConfirm = {},
		)
	}

	private fun confirmTrackUntracked() {
		val context = context ?: return
		viewLifecycleOwner.lifecycleScope.launch {
			val active = autoTracker.enabledScrobblers
			if (active.isEmpty()) {
				showInfo(R.string.auto_track_untracked, getString(R.string.auto_track_no_trackers))
				return@launch
			}
			val counts = withContext(Dispatchers.Default) {
				AutoTracker.Scope.entries.associateWith { scope ->
					autoTracker.findPending(scope).map { it.mangaId }.distinct().size
				}
			}
			val labels = mapOf(
				AutoTracker.Scope.UNTRACKED to R.string.auto_track_scope_untracked,
				AutoTracker.Scope.WITH_REVIEW to R.string.auto_track_scope_review,
				AutoTracker.Scope.ALL to R.string.auto_track_scope_all,
			)
			val actions = AutoTracker.Scope.entries
				.filter { (counts[it] ?: 0) > 0 }
				// A scope that covers the same titles as the one before it adds nothing
				.distinctBy { counts[it] }
				.map { scope ->
					DialogAction(label = context.getString(labels.getValue(scope), counts.getValue(scope))) {
						autoTrackScheduler.start(scope = scope)
					}
				}
			if (actions.isEmpty()) {
				showInfo(R.string.auto_track_untracked, getString(R.string.auto_track_nothing))
				return@launch
			}
			showActionChoiceDialog(
				context = context,
				icon = R.drawable.ic_sync,
				title = context.getString(R.string.auto_track_untracked),
				actions = actions,
			)
		}
	}
}

@Composable
private fun TrackingScreen(
	autoTrackStatus: String?,
	reviewCount: Int,
	onTrackUntracked: () -> Unit,
	onOpenReview: () -> Unit,
) {
	var autoTrack by rememberBooleanPref(AppSettings.KEY_AUTO_TRACK_FAVOURITES, false)
	var syncProgress by rememberBooleanPref(AppSettings.KEY_SCROBBLING_PROGRESS_SYNC, true)
	var smartStatus by rememberBooleanPref(AppSettings.KEY_SMART_READING_STATUS, true)
	var autoPause by rememberBooleanPref(AppSettings.KEY_AUTO_PAUSE_STALE, false)
	var autoPauseDays by rememberIntPref(AppSettings.KEY_AUTO_PAUSE_DAYS, AppSettings.AUTO_PAUSE_DEFAULT_DAYS)
	val dialogContext = LocalContext.current

	SettingsScaffold {
		item {
			SettingsGroup {
				item { pos ->
					SwitchSettingsItem(
						title = stringResource(R.string.auto_track_favourites),
						subtitle = stringResource(R.string.auto_track_favourites_summary),
						checked = autoTrack,
						onCheckedChange = { autoTrack = it },
						icon = R.drawable.ic_heart_outline,
						shape = pos.shape,
						main = true,
					)
				}
			}
		}
		item { Spacer(Modifier.height(8.dp).fillMaxWidth()) }
		item {
			SettingsGroup(title = stringResource(R.string.tracking_section_progress)) {
				item { pos ->
					SwitchSettingsItem(
						title = stringResource(R.string.sync_progress_from_tracking),
						subtitle = stringResource(R.string.sync_progress_from_tracking_summary),
						checked = syncProgress,
						onCheckedChange = { syncProgress = it },
						icon = R.drawable.ic_sync_alt,
						shape = pos.shape,
					)
				}
				item { pos ->
					SwitchSettingsItem(
						title = stringResource(R.string.smart_reading_status),
						subtitle = stringResource(R.string.smart_reading_status_summary),
						checked = smartStatus,
						onCheckedChange = { smartStatus = it },
						icon = R.drawable.ic_eye_check,
						shape = pos.shape,
					)
				}
				item { pos ->
					// Split row: the row picks the number of days, the switch turns pausing on or off
					SettingsItem(
						title = stringResource(R.string.auto_pause_stale),
						subtitle = if (autoPause) {
							stringResource(R.string.auto_pause_stale_after_summary, autoPauseDays)
						} else {
							stringResource(R.string.auto_pause_stale_summary)
						},
						icon = R.drawable.ic_eye_off,
						shape = pos.shape,
						onClick = {
							showActionChoiceDialog(
								context = dialogContext,
								icon = R.drawable.ic_history,
								title = dialogContext.getString(R.string.auto_pause_after),
								actions = listOf(30, 60, 90, 180).map { days ->
									DialogAction(label = dialogContext.getString(R.string.auto_pause_days, days)) {
										autoPauseDays = days
										autoPause = true
									}
								},
							)
						},
						trailing = {
							Row(verticalAlignment = Alignment.CenterVertically) {
								VerticalDivider(modifier = Modifier.height(32.dp))
								Spacer(Modifier.width(14.dp))
								SettingsSwitch(
									checked = autoPause,
									onCheckedChange = { autoPause = it },
								)
							}
						},
					)
				}
			}
		}
		item {
			SettingsGroup(title = stringResource(R.string.tracking_section_actions)) {
				item { pos ->
					ActionSettingsItem(
						title = stringResource(R.string.auto_track_untracked),
						subtitle = autoTrackStatus,
						icon = R.drawable.ic_sync_alt,
						shape = pos.shape,
						onClick = onTrackUntracked,
					)
				}
				if (reviewCount > 0) {
					item { pos ->
						ActionSettingsItem(
							title = stringResource(R.string.auto_track_review),
							subtitle = pluralStringResource(R.plurals.auto_track_review_count, reviewCount, reviewCount),
							icon = R.drawable.ic_search,
							shape = pos.shape,
							onClick = onOpenReview,
						)
					}
				}
			}
		}
		item { Spacer(Modifier.height(24.dp).fillMaxWidth()) }
	}
}
