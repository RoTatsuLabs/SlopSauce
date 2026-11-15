package org.koitharu.kotatsu.core.util

private val IMAGE = Regex("""!\[([^\]]*)]\(([^)\s]+)[^)]*\)""")
private val LINK = Regex("""\[([^\]]+)]\(\s*<?([^)\s>]+)>?[^)]*\)""")
private val BOLD_STAR = Regex("""\*\*(.+?)\*\*""", RegexOption.DOT_MATCHES_ALL)
private val BOLD_UNDER = Regex("""(?<![\w])__(.+?)__(?![\w])""", RegexOption.DOT_MATCHES_ALL)
private val ITALIC_STAR = Regex("""(?<![*\w])\*(?!\s)([^*\n]+?)(?<!\s)\*(?![*\w])""")
private val ITALIC_UNDER = Regex("""(?<![\w])_(?!\s)([^_\n]+?)(?<!\s)_(?![\w])""")
private val STRIKE = Regex("""~~(.+?)~~""", RegexOption.DOT_MATCHES_ALL)
private val SPOILER = Regex("""~!(.+?)!~""", RegexOption.DOT_MATCHES_ALL)
private val CODE = Regex("""`([^`\n]+)`""")
private val HEADING = Regex("""(?m)^[ \t]*#{1,6}[ \t]+""")
private val LIST_ITEM = Regex("""(?m)^[ \t]*[-*+][ \t]+""")
private val QUOTE = Regex("""(?m)^[ \t]*>[ \t]?""")
private val RULE = Regex("""(?m)^[ \t]*([-*_])([ \t]*\1){2,}[ \t]*$""")
private val LINE_BREAK_TAG = Regex("""(?i)<br\s*/?>|</p\s*>|</div\s*>|</li\s*>""")
private val ANY_TAG = Regex("""</?[a-zA-Z][^>]*>""")
private val BLANK_LINES = Regex("""\n{3,}""")
private val TRAILING_SPACES = Regex("""(?m)[ \t]+$""")

/**
 * Turns the Markdown (and the bit of HTML) that trackers send in descriptions into plain text,
 * because an edited description is shown as plain text and would otherwise keep every `**` and `[](...)`.
 * Links keep their address in brackets: `[Site](https://a.b)` becomes `Site (https://a.b)`.
 */
fun String.markdownToPlainText(): String {
	var text = replace("\r\n", "\n").replace('\r', '\n')
	text = LINE_BREAK_TAG.replace(text, "\n")
	text = ANY_TAG.replace(text, "")
	text = IMAGE.replace(text) { it.groupValues[1] }
	text = LINK.replace(text) {
		val label = it.groupValues[1].trim()
		val url = it.groupValues[2]
		if (label == url || label.isEmpty()) url else "$label ($url)"
	}
	text = RULE.replace(text, "")
	text = HEADING.replace(text, "")
	text = QUOTE.replace(text, "")
	text = LIST_ITEM.replace(text, "• ")
	text = BOLD_STAR.replace(text) { it.groupValues[1] }
	text = BOLD_UNDER.replace(text) { it.groupValues[1] }
	text = ITALIC_STAR.replace(text) { it.groupValues[1] }
	text = ITALIC_UNDER.replace(text) { it.groupValues[1] }
	text = STRIKE.replace(text) { it.groupValues[1] }
	text = SPOILER.replace(text) { it.groupValues[1] }
	text = CODE.replace(text) { it.groupValues[1] }
	text = text.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
		.replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
	text = TRAILING_SPACES.replace(text, "")
	return BLANK_LINES.replace(text, "\n\n").trim()
}
