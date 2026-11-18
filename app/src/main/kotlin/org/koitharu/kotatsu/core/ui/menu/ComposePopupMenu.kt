package org.koitharu.kotatsu.core.ui.menu

import android.view.View
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.koitharu.kotatsu.core.util.ext.adjustPopupMenuIcons
import org.koitharu.kotatsu.core.util.ext.setOptionalIconsVisibleCompat

/** One row of a three-dot menu opened from Compose. */
class PopupEntry(
	val title: String,
	@DrawableRes val icon: Int,
	val onClick: () -> Unit,
)

/**
 * Wraps a button so that [open] shows [entries] as the app's own menu (the frosted one with icons)
 * instead of Compose's plain DropdownMenu. [entries] is read when the menu opens.
 */
@Composable
fun PopupMenuBox(
	entries: () -> List<PopupEntry>,
	modifier: Modifier = Modifier,
	button: @Composable (open: () -> Unit) -> Unit,
) {
	// MenuPopup needs a real View to bloom out of, so an empty one is parked under the button
	val anchor = remember { mutableStateOf<View?>(null) }
	Box(modifier, contentAlignment = Alignment.Center) {
		AndroidView(
			factory = { View(it) },
			update = { anchor.value = it },
			modifier = Modifier.matchParentSize(),
		)
		button {
			anchor.value?.let { showEntries(it, entries()) }
		}
	}
}

private fun showEntries(anchor: View, entries: List<PopupEntry>) {
	if (entries.isEmpty()) return
	MenuPopup(anchor).apply {
		setForceShowIcon(true)
		entries.forEachIndexed { index, entry ->
			menu.add(0, index, index, entry.title).setIcon(entry.icon)
		}
		menu.setOptionalIconsVisibleCompat(true)
		menu.adjustPopupMenuIcons(anchor.resources)
		setOnMenuItemClickListener { item ->
			entries.getOrNull(item.itemId)?.onClick?.invoke()
			true
		}
		show()
	}
}
