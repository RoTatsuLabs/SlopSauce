package org.koitharu.kotatsu.core.ui

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import org.koitharu.kotatsu.core.prefs.AppSettings

/**
 * The font the user imported to replace the app's default one. It is a single file kept in the
 * app's private storage, so views and Compose text both read it from here.
 */
object AppFont {

	private val lock = Any()
	private var loaded = false
	private var base: Typeface? = null
	private var variable = false
	private val styled = HashMap<Int, Typeface>()

	fun file(context: Context) = File(context.applicationContext.filesDir, AppSettings.APP_FONT_FILE)

	fun isActive(context: Context): Boolean = ensure(context) != null

	/** Reads the active font once; [invalidate] makes the next call read it again. */
	private fun ensure(context: Context): Typeface? = synchronized(lock) {
		if (!loaded) {
			val f = file(context)
			styled.clear()
			base = if (f.isFile) runCatching { Typeface.createFromFile(f) }.getOrNull() else null
			variable = base != null && isVariable(f)
			loaded = true
		}
		base
	}

	private fun invalidate() = synchronized(lock) { loaded = false }

	class Entry(val id: String, val name: String)

	private fun libraryDir(context: Context) = File(context.applicationContext.filesDir, "fonts")

	fun libraryFile(context: Context, id: String) = File(libraryDir(context), "$id.font")

	/** Every font the user has added, by name. */
	fun library(context: Context): List<Entry> = libraryDir(context)
		.listFiles { f -> f.extension == "font" }
		.orEmpty()
		.map { f ->
			val id = f.nameWithoutExtension
			val name = File(f.parentFile, "$id.name").takeIf(File::isFile)?.readText()?.trim().orEmpty()
			Entry(id, name.ifBlank { id })
		}
		.sortedBy { it.name.lowercase() }

	/** Copies the picked file into the library. Returns it, or null if it is not a usable font. */
	fun add(context: Context, uri: Uri, fallbackName: String): Entry? {
		val resolver = context.contentResolver
		val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
			if (cursor.moveToFirst()) cursor.getString(0) else null
		}?.takeIf(String::isNotBlank)?.substringBeforeLast('.') ?: fallbackName
		val temporary = File(context.cacheDir, "app_font_import")
		return try {
			resolver.openInputStream(uri)?.use { input -> temporary.outputStream().use(input::copyTo) } ?: return null
			Typeface.createFromFile(temporary)
			val id = UUID.randomUUID().toString().take(8)
			libraryDir(context).mkdirs()
			temporary.copyTo(libraryFile(context, id), overwrite = true)
			File(libraryDir(context), "$id.name").writeText(name)
			Entry(id, name)
		} catch (_: Exception) {
			null
		} finally {
			temporary.delete()
		}
	}

	/** Makes a library font the one the app uses. */
	fun select(context: Context, id: String): Boolean {
		val source = libraryFile(context, id)
		if (!source.isFile) return false
		return try {
			source.copyTo(file(context), overwrite = true)
			invalidate()
			true
		} catch (_: Exception) {
			false
		}
	}

	/** Goes back to the app's own font. The library is left alone. */
	fun clearActive(context: Context) {
		file(context).delete()
		invalidate()
	}

	fun delete(context: Context, id: String) {
		libraryFile(context, id).delete()
		File(libraryDir(context), "$id.name").delete()
	}

	/** The font at the given weight, or null when no custom font is set. */
	fun typeface(context: Context, weight: Int = 400, italic: Boolean = false): Typeface? {
		val root = ensure(context) ?: return null
		val w = weight.coerceIn(100, 900)
		synchronized(lock) {
			return styled.getOrPut(w * 2 + if (italic) 1 else 0) {
				when {
					variable -> Typeface.Builder(file(context))
						.setWeight(w)
						.setItalic(italic)
						.setFontVariationSettings("'wght' $w")
						.build() ?: root
					Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> Typeface.create(root, w, italic)
					else -> Typeface.create(
						root,
						when {
							w >= 600 && italic -> Typeface.BOLD_ITALIC
							w >= 600 -> Typeface.BOLD
							italic -> Typeface.ITALIC
							else -> Typeface.NORMAL
						},
					)
				}
			}
		}
	}

	/** Keeps the weight and slant the view already had and swaps the family. */
	fun apply(view: TextView) {
		val current = view.typeface
		val weight = when {
			current == null -> 400
			Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> current.weight
			current.isBold -> 700
			else -> 400
		}
		val replacement = typeface(view.context, weight, current?.isItalic == true) ?: return
		if (replacement !== current) view.typeface = replacement
	}

	fun applyTree(view: View) {
		if (view is TextView) apply(view)
		if (view is ViewGroup) {
			for (i in 0 until view.childCount) applyTree(view.getChildAt(i))
		}
	}

	/**
	 * Keeps text the layout inflater never sees (toolbar titles, tab and navigation labels, snackbars)
	 * on the font. It re-checks at most once a frame, and only when something was laid out.
	 */
	fun watch(root: View) {
		if (!isActive(root.context)) return
		var scheduled = false
		root.viewTreeObserver.addOnGlobalLayoutListener {
			if (!scheduled) {
				scheduled = true
				root.postOnAnimation {
					scheduled = false
					applyTree(root)
				}
			}
		}
	}

	@OptIn(ExperimentalTextApi::class)
	fun composeFamily(context: Context): FontFamily? {
		val f = file(context)
		if (ensure(context) == null) return null
		return if (synchronized(lock) { variable }) {
			FontFamily(
				listOf(
					FontWeight.Normal,
					FontWeight.Medium,
					FontWeight.SemiBold,
					FontWeight.Bold,
				).map { w -> Font(f, weight = w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight))) },
			)
		} else {
			// One static face: Compose fakes the heavier weights from it.
			FontFamily(Font(f))
		}
	}

	private fun isVariable(f: File): Boolean = try {
		RandomAccessFile(f, "r").use { raf ->
			val header = ByteArray(12)
			raf.readFully(header)
			val tables = ((header[4].toInt() and 0xff) shl 8) or (header[5].toInt() and 0xff)
			val tag = ByteArray(4)
			var found = false
			for (i in 0 until tables.coerceAtMost(64)) {
				raf.seek(12L + i * 16L)
				raf.readFully(tag)
				if (String(tag, Charsets.ISO_8859_1) == "fvar") {
					found = true
					break
				}
			}
			found
		}
	} catch (_: Exception) {
		false
	}
}
