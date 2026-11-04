package org.koitharu.kotatsu.settings.override

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.text.parseAsHtml
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePaddingRelative
import coil3.ImageLoader
import kotlinx.coroutines.flow.filterNotNull
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.ui.BaseActivity
import org.koitharu.kotatsu.core.util.ext.end
import org.koitharu.kotatsu.core.util.ext.getDisplayMessage
import org.koitharu.kotatsu.core.util.ext.isHttpUrl
import org.koitharu.kotatsu.core.util.ext.observe
import org.koitharu.kotatsu.core.util.ext.observeEvent
import org.koitharu.kotatsu.core.util.ext.start
import org.koitharu.kotatsu.core.util.ext.tryLaunch
import org.koitharu.kotatsu.databinding.ActivityOverrideEditBinding
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.picker.ui.PageImagePickContract
import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblingInfo
import org.koitharu.kotatsu.settings.compose.DropSauceTheme
import javax.inject.Inject

/**
 * Hosts [OverrideEditScreen]. The activity owns only the window: the toolbar with its Save action,
 * the system pickers a cover can come from, and the typed text, which lives here so Save can read
 * it without the screen having to push every keystroke into the view model.
 */
@AndroidEntryPoint
class OverrideConfigActivity : BaseActivity<ActivityOverrideEditBinding>(), ActivityResultCallback<Uri?> {

	@Inject
	lateinit var coil: ImageLoader

	private val viewModel: OverrideConfigViewModel by viewModels()

	private val bottomInset = mutableIntStateOf(0)
	private val isImportSheetShown = mutableStateOf(false)
	private val titleText = mutableStateOf<String?>(null)
	private val descriptionText = mutableStateOf<String?>(null)
	private val errorText = mutableStateOf<String?>(null)
	private val genresText = mutableStateOf("")
	private val authorsText = mutableStateOf("")
	private val ratingText = mutableStateOf("")
	private val stateValue = mutableStateOf<MangaState?>(null)
	private val contentRatingValue = mutableStateOf<ContentRating?>(null)

	private val pickCoverFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument(), this)
	private val pickPageLauncher = registerForActivityResult(PageImagePickContract(), this)

	// The photo picker grants temporary read access (no persistable permission), which lasts long
	// enough for the cover to be copied into app storage when the override is saved.
	private val pickCoverGalleryLauncher = registerForActivityResult(
		ActivityResultContracts.PickVisualMedia(),
	) { uri ->
		if (uri != null) {
			viewModel.updateCover(uri.toString())
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityOverrideEditBinding.inflate(layoutInflater))
		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = true)
		setTitle(R.string.edit)
		viewBinding.composeView.setViewCompositionStrategy(
			ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
		)
		viewBinding.composeView.setContent {
			DropSauceTheme {
				val density = LocalDensity.current
				val data by viewModel.data.collectAsState()
				val isLoading by viewModel.isLoading.collectAsState()
				data?.let { (manga, override) ->
					OverrideEditScreen(
						manga = manga,
						coverUrl = override.coverUrl,
						title = titleText.value.orEmpty(),
						description = descriptionText.value.orEmpty(),
						originalDescription = remember(manga) {
							manga.description?.parseAsHtml()?.toString()?.trim()
						},
						genres = genresText.value,
						authors = authorsText.value,
						ratingText = ratingText.value,
						state = stateValue.value,
						contentRating = contentRatingValue.value,
						isLoading = isLoading,
						error = errorText.value,
						bottomInset = with(density) { bottomInset.intValue.toDp() },
						imageLoader = coil,
						onTitleChange = { titleText.value = it },
						onDescriptionChange = { descriptionText.value = it },
						onGenresChange = { genresText.value = it },
						onAuthorsChange = { authorsText.value = it },
						onRatingChange = { ratingText.value = it },
						onStateChange = { stateValue.value = it },
						onContentRatingChange = { contentRatingValue.value = it },
						onCoverPick = ::onCoverPick,
						onCoverReset = { viewModel.updateCover(null) },
					)
					val trackers by viewModel.trackers.collectAsState()
					if (isImportSheetShown.value) {
						TrackerImportSheet(
							manga = manga,
							trackers = trackers,
							imageLoader = coil,
							onImport = { tracker, modes ->
								applyImport(tracker, modes)
								isImportSheetShown.value = false
							},
							onDismiss = { isImportSheetShown.value = false },
						)
					}
				}
			}
		}
		// Seed the fields from the stored override once; a later re-emission must never overwrite
		// what is being typed.
		viewModel.data.filterNotNull().observe(this) { (_, override) ->
			if (titleText.value == null) {
				titleText.value = override.title.orEmpty()
				descriptionText.value = override.description.orEmpty()
				genresText.value = override.tags?.joinToString(", ").orEmpty()
				authorsText.value = override.authors?.joinToString(", ").orEmpty()
				ratingText.value = override.rating?.let { "%.1f".format(it * 10f) }.orEmpty()
				stateValue.value = override.state
				contentRatingValue.value = override.contentRating
			}
		}
		viewModel.onSaved.observeEvent(this) {
			setResult(RESULT_OK)
			finish()
		}
		viewModel.onError.observeEvent(this) { errorText.value = it.getDisplayMessage(resources) }
		viewModel.trackers.observe(this) { invalidateOptionsMenu() }
	}

	override fun onCreateOptionsMenu(menu: Menu?): Boolean {
		menuInflater.inflate(R.menu.opt_override_edit, menu)
		menu?.findItem(R.id.action_done)?.actionView?.setOnClickListener { save() }
		return super.onCreateOptionsMenu(menu)
	}

	override fun onPrepareOptionsMenu(menu: Menu): Boolean {
		// Greyed out while there is nothing to import from — but still tappable, so a tap can say
		// why instead of doing nothing. A disabled item would keep its full-strength icon anyway.
		val hasTrackers = viewModel.trackers.value.isNotEmpty()
		menu.findItem(R.id.action_import)?.icon?.alpha = if (hasTrackers) 255 else 97
		return super.onPrepareOptionsMenu(menu)
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
		R.id.action_done -> {
			save()
			true
		}

		R.id.action_import -> {
			if (viewModel.trackers.value.isEmpty()) {
				Snackbar.make(
					viewBinding.composeView,
					R.string.import_from_tracker_empty,
					Snackbar.LENGTH_SHORT,
				).show()
			} else {
				isImportSheetShown.value = true
			}
			true
		}

		else -> super.onOptionsItemSelected(item)
	}

	/** Fills the editor with the tracker's values; saving them stays the user's call. */
	private fun applyImport(tracker: ScrobblingInfo, modes: Map<ImportField, ImportMode>) {
		val manga = viewModel.data.value?.first
		fun mode(field: ImportField) = modes[field]
		mode(ImportField.COVER)?.let { viewModel.updateCover(tracker.coverUrl) }
		mode(ImportField.TITLE)?.let { titleText.value = tracker.title }

		// Description and alternative names share one text, so they are applied together.
		var description = descriptionText.value.orEmpty()
		val sourceDescription = manga?.description?.parseAsHtml()?.toString()?.trim().orEmpty()
		val baseDescription = description.ifBlank { sourceDescription }
		val imported = tracker.description?.toString().orEmpty()
		mode(ImportField.DESCRIPTION)?.let {
			description = when (it) {
				ImportMode.REPLACE -> imported
				ImportMode.APPEND -> if (baseDescription.isBlank()) imported else baseDescription + "\n\n" + imported
				ImportMode.FILL -> if (baseDescription.isBlank()) imported else description
			}
		}
		if (mode(ImportField.ALT_NAMES) != null && tracker.altNames.isNotEmpty()) {
			val base = description.ifBlank { sourceDescription }
			val heading = getString(R.string.alt_names_heading)
			val cut = base.indexOf(heading)
			val text = (if (cut >= 0) base.substring(0, cut) else base).trimEnd()
			description = (if (text.isEmpty()) "" else text + "\n\n") +
				heading + "\n" + tracker.altNames.joinToString("\n")
		}
		descriptionText.value = description

		mode(ImportField.GENRES)?.let {
			genresText.value = mergeList(
				current = genresText.value,
				source = manga?.tags?.map { tag -> tag.title }.orEmpty(),
				imported = tracker.genres,
				mode = it,
			)
		}
		mode(ImportField.AUTHORS)?.let {
			authorsText.value = mergeList(
				current = authorsText.value,
				source = manga?.authors?.toList().orEmpty(),
				imported = tracker.authors,
				mode = it,
			)
		}
		mode(ImportField.STATUS)?.let {
			val value = tracker.asMangaState()
			if (value != null && (it == ImportMode.REPLACE || (stateValue.value == null && manga?.state == null))) {
				stateValue.value = value
			}
		}
		mode(ImportField.CONTENT_RATING)?.let {
			if (tracker.isAdult && (it == ImportMode.REPLACE || (contentRatingValue.value == null && manga?.contentRating == null))) {
				contentRatingValue.value = ContentRating.ADULT
			}
		}
		mode(ImportField.RATING)?.let {
			val empty = ratingText.value.isBlank() && (manga?.rating ?: -1f) < 0f
			if (tracker.score > 0f && (it == ImportMode.REPLACE || empty)) {
				ratingText.value = "%.1f".format(tracker.score)
			}
		}
	}

	private fun parseList(text: String): List<String> =
		text.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }

	private fun mergeList(current: String, source: List<String>, imported: List<String>, mode: ImportMode): String {
		val own = parseList(current).ifEmpty { source }
		val result = when (mode) {
			ImportMode.REPLACE -> imported
			ImportMode.APPEND -> own + imported
			ImportMode.FILL -> own.ifEmpty { imported }
		}
		return result.distinctBy { it.lowercase() }.joinToString(", ")
	}

	private fun save() {
		errorText.value = null
		viewModel.save(
			title = titleText.value?.trim(),
			description = descriptionText.value?.trim(),
			tags = parseList(genresText.value).takeIf { it.isNotEmpty() }?.toSet(),
			authors = parseList(authorsText.value).takeIf { it.isNotEmpty() }?.toSet(),
			state = stateValue.value,
			contentRating = contentRatingValue.value,
			rating = ratingText.value.trim().replace(',', '.').toFloatOrNull()?.coerceIn(0f, 10f)?.div(10f),
		)
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

	override fun onActivityResult(result: Uri?) {
		if (result != null) {
			if (result.host?.startsWith(packageName) != true) {
				contentResolver.takePersistableUriPermission(result, Intent.FLAG_GRANT_READ_URI_PERMISSION)
			}
			viewModel.updateCover(result.toString())
		}
	}

	private fun onCoverPick(source: CoverSource) {
		when (source) {
			CoverSource.GALLERY -> if (
				!pickCoverGalleryLauncher.tryLaunch(
					PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
				)
			) {
				showNotSupported()
			}

			CoverSource.FILE -> if (!pickCoverFileLauncher.tryLaunch(arrayOf("image/*"))) {
				showNotSupported()
			}

			CoverSource.PAGE -> pickPageLauncher.launch(viewModel.data.value?.first)

			CoverSource.URL -> showCoverUrlDialog()
		}
	}

	private fun showNotSupported() {
		Snackbar.make(viewBinding.composeView, R.string.operation_not_supported, Snackbar.LENGTH_SHORT).show()
	}

	private fun showCoverUrlDialog() {
		val editText = com.google.android.material.textfield.TextInputEditText(this).apply {
			setHint(R.string.url)
			inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
			setText(viewModel.data.value?.second?.coverUrl?.takeIf { it.isHttpUrl() })
		}
		val padding = resources.getDimensionPixelOffset(R.dimen.margin_normal)
		val container = android.widget.FrameLayout(this).apply {
			setPadding(padding, padding / 2, padding, 0)
			addView(editText)
		}
		com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
			.setTitle(R.string.pick_cover_url)
			.setView(container)
			.setNegativeButton(android.R.string.cancel, null)
			.setPositiveButton(android.R.string.ok) { _, _ ->
				val url = editText.text?.toString()?.trim().orEmpty()
				if (url.isHttpUrl()) {
					viewModel.updateCover(url)
				} else {
					Snackbar.make(viewBinding.composeView, R.string.invalid_url, Snackbar.LENGTH_SHORT).show()
				}
			}
			.show()
	}
}
