package org.koitharu.kotatsu.local.data

import org.jetbrains.annotations.Blocking
import org.koitharu.kotatsu.core.model.isNovelSource
import org.koitharu.kotatsu.core.util.AlphanumComparator
import org.koitharu.kotatsu.local.data.output.LocalMangaOutput.Companion.ENTRY_NAME_INDEX
import org.koitharu.kotatsu.parsers.model.Manga
import java.io.File
import java.nio.file.Files

/** A folder holding a file with this name is skipped by the local library scan. */
const val FILENAME_SKIP = ".notamanga"

private fun isZipExtension(ext: String?): Boolean {
	return ext.equals("cbz", ignoreCase = true) || ext.equals("zip", ignoreCase = true)
}

private fun isPdfExtension(ext: String?): Boolean {
	return ext.equals("pdf", ignoreCase = true)
}

private fun isRarExtension(ext: String?): Boolean {
	return ext.equals("cbr", ignoreCase = true) || ext.equals("rar", ignoreCase = true)
}

private fun isEpubExtension(ext: String?): Boolean {
	return ext.equals("epub", ignoreCase = true)
}

fun hasZipExtension(string: String): Boolean {
	val ext = string.substringAfterLast('.', "")
	return isZipExtension(ext)
}

fun hasPdfExtension(string: String): Boolean {
	val ext = string.substringAfterLast('.', "")
	return isPdfExtension(ext)
}

/** CBR is only an import format: the importer repacks it into a CBZ. */
fun hasRarExtension(string: String): Boolean {
	val ext = string.substringAfterLast('.', "")
	return isRarExtension(ext)
}

fun hasEpubExtension(string: String): Boolean {
	val ext = string.substringAfterLast('.', "")
	return isEpubExtension(ext)
}

fun isSupportedArchive(string: String): Boolean {
	val ext = string.substringAfterLast('.', "")
	return isZipExtension(ext) || isPdfExtension(ext) || isRarExtension(ext) || isEpubExtension(ext)
}

/** Far deeper than any real library; only guards against runaway trees and symlink loops. */
private const val MAX_SCAN_DEPTH = 32

/** A folder the library scan may descend into: not hidden, not a symlink, not marked [FILENAME_SKIP]. */
private fun File.isScannable(): Boolean = isDirectory && !isHidden &&
	!Files.isSymbolicLink(toPath()) && !File(this, FILENAME_SKIP).exists()

/**
 * True when a folder actually holds manga: an index, an archive, or an image at any depth below it.
 * Any other folder that happens to sit in a storage dir — fonts, novel plugins, translations,
 * backups — would otherwise be listed as a manga with no cover and no chapters.
 */
@Blocking
fun File.hasMangaContent(depth: Int = MAX_SCAN_DEPTH): Boolean {
	val children = listFiles() ?: return false
	if (children.any { it.isFile && (it.name == ENTRY_NAME_INDEX || it.isMangaContentFile) }) {
		return true
	}
	return depth > 0 && children.any { it.isScannable() && it.hasMangaContent(depth - 1) }
}

/** Every EPUB below this folder at any depth, in natural path order (`Volume 2` before `Volume 10`). */
@Blocking
fun File.findEpubFiles(): List<File> {
	val result = ArrayList<File>()
	fun walk(dir: File, depth: Int) {
		for (child in dir.listFiles().orEmpty()) {
			when {
				child.isEpubFile && !child.isHidden -> result.add(child)
				depth > 0 && child.isScannable() -> walk(child, depth - 1)
			}
		}
	}
	walk(this, MAX_SCAN_DEPTH)
	return result.sortedWith(compareBy(AlphanumComparator()) { it.relativeTo(this).path })
}

// ponytail: an extension set rather than MimeTypes — the scan above visits every file in every
// storage dir, and the platform mime lookup is slower per file. Add a format here if the reader
// learns to decode a new one.
private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "bmp", "heic", "heif", "jxl")

fun hasImageExtension(string: String): Boolean {
	return string.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS
}

private val File.isMangaContentFile: Boolean
	get() = (isSupportedArchive(name) && !hasRarExtension(name)) || hasImageExtension(name)

val File.isZipArchive: Boolean
	get() = isFile && isZipExtension(extension)

val File.isEpubFile: Boolean
	get() = isFile && isEpubExtension(extension)

/**
 * True for anything the text reader handles: a local EPUB book (the manga is a single .epub file or
 * its chapters point inside one) or a novel source, whose "pages" are prose fetched over the network
 * rather than images.
 */
val Manga.isEpub: Boolean
	get() = source.isNovelSource ||
		hasEpubExtension(url.substringBefore('#')) ||
		chapters?.firstOrNull()?.let { hasEpubExtension(it.url.substringBefore('#')) } == true
