package org.koitharu.kotatsu.settings.appearance

import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.ui.AppFont
import org.koitharu.kotatsu.core.ui.util.ActivityRecreationHandle
import org.koitharu.kotatsu.settings.compose.ActionSettingsItem
import org.koitharu.kotatsu.settings.compose.BaseComposeSettingsFragment
import org.koitharu.kotatsu.settings.compose.DropSauceTheme
import org.koitharu.kotatsu.settings.compose.GoogleSansRounded
import org.koitharu.kotatsu.settings.compose.SettingsGroup
import org.koitharu.kotatsu.settings.compose.SettingsItem
import org.koitharu.kotatsu.settings.compose.SettingsScaffold
import org.koitharu.kotatsu.settings.compose.rememberStringPref

private val FONT_MIME_TYPES = arrayOf(
	"font/ttf",
	"font/otf",
	"application/x-font-ttf",
	"application/x-font-opentype",
	"application/octet-stream",
)

/** Pick the app's font from the ones you added, or add another. */
@AndroidEntryPoint
class FontSettingsFragment : BaseComposeSettingsFragment(R.string.app_font) {

	@Inject
	lateinit var settings: AppSettings

	@Inject
	lateinit var activityRecreationHandle: ActivityRecreationHandle

	private var fonts by mutableStateOf<List<AppFont.Entry>>(emptyList())

	private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
		if (uri != null) addFont(uri)
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		fonts = AppFont.library(requireContext())
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View = ComposeView(requireContext()).apply {
		setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
		setContent {
			DropSauceTheme {
				FontScreen(
					fonts = fonts,
					onSelect = ::select,
					onDelete = ::delete,
					onAdd = { picker.launch(FONT_MIME_TYPES) },
				)
			}
		}
	}

	private fun addFont(uri: Uri) {
		val app = requireContext().applicationContext
		val fallback = getString(R.string.app_font_custom)
		viewLifecycleOwner.lifecycleScope.launch {
			val entry = withContext(Dispatchers.IO) { AppFont.add(app, uri, fallback) }
			if (entry == null) {
				Toast.makeText(app, R.string.epub_font_invalid, Toast.LENGTH_SHORT).show()
				return@launch
			}
			fonts = withContext(Dispatchers.IO) { AppFont.library(app) }
			select(entry)
		}
	}

	private fun select(entry: AppFont.Entry?) {
		if ((entry?.id ?: "") == settings.appFontId) return
		val app = requireContext().applicationContext
		viewLifecycleOwner.lifecycleScope.launch {
			val applied = withContext(Dispatchers.IO) {
				if (entry == null) {
					AppFont.clearActive(app)
					true
				} else {
					AppFont.select(app, entry.id)
				}
			}
			if (!applied) {
				Toast.makeText(app, R.string.epub_font_invalid, Toast.LENGTH_SHORT).show()
				return@launch
			}
			settings.appFontId = entry?.id.orEmpty()
			settings.appFontName = entry?.name.orEmpty()
			activityRecreationHandle.recreateAll()
		}
	}

	private fun delete(entry: AppFont.Entry) {
		val app = requireContext().applicationContext
		viewLifecycleOwner.lifecycleScope.launch {
			withContext(Dispatchers.IO) { AppFont.delete(app, entry.id) }
			fonts = withContext(Dispatchers.IO) { AppFont.library(app) }
			if (settings.appFontId == entry.id) select(null)
		}
	}
}

@Composable
private fun FontScreen(
	fonts: List<AppFont.Entry>,
	onSelect: (AppFont.Entry?) -> Unit,
	onDelete: (AppFont.Entry) -> Unit,
	onAdd: () -> Unit,
) {
	val ctx = LocalContext.current
	val activeId by rememberStringPref(AppSettings.KEY_APP_FONT_ID, "")
	SettingsScaffold {
		item {
			SettingsGroup {
				item { pos ->
					FontRow(
						name = stringResource(R.string.app_font_default),
						family = GoogleSansRounded,
						selected = activeId.isBlank(),
						shape = pos.shape,
						onClick = { onSelect(null) },
						onDelete = null,
					)
				}
				fonts.forEach { font ->
					item { pos ->
						val family = remember(font.id) {
							runCatching {
								FontFamily(Typeface.createFromFile(AppFont.libraryFile(ctx, font.id)))
							}.getOrNull()
						}
						FontRow(
							name = font.name,
							family = family,
							selected = activeId == font.id,
							shape = pos.shape,
							onClick = { onSelect(font) },
							onDelete = { onDelete(font) },
						)
					}
				}
			}
		}
		item { Spacer(Modifier.height(8.dp).fillMaxWidth()) }
		item {
			SettingsGroup {
				item { pos ->
					ActionSettingsItem(
						title = stringResource(R.string.app_font_add),
						subtitle = stringResource(R.string.app_font_add_summary),
						icon = R.drawable.ic_add,
						shape = pos.shape,
						onClick = onAdd,
					)
				}
			}
		}
	}
}

@Composable
private fun FontRow(
	name: String,
	family: FontFamily?,
	selected: Boolean,
	shape: Shape,
	onClick: () -> Unit,
	onDelete: (() -> Unit)?,
) {
	val title = MaterialTheme.typography.titleMedium
	val sample = MaterialTheme.typography.bodyMedium
	SettingsItem(
		title = name,
		titleStyle = if (family != null) title.copy(fontFamily = family) else title,
		shape = shape,
		onClick = onClick,
		supporting = {
			Text(
				text = stringResource(R.string.app_font_sample),
				style = if (family != null) sample.copy(fontFamily = family) else sample,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		},
		trailing = {
			Row(verticalAlignment = Alignment.CenterVertically) {
				if (onDelete != null) {
					IconButton(onClick = onDelete) {
						Icon(
							painter = painterResource(R.drawable.ic_delete),
							contentDescription = stringResource(R.string.app_font_delete),
						)
					}
				}
				RadioButton(selected = selected, onClick = null)
			}
		},
	)
}
