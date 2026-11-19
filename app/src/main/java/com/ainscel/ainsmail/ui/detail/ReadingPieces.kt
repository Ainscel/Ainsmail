package com.ainscel.ainsmail.ui.detail

private val LINK = Regex("""\[([^\[\]]*)]\(([^)]*)\)""")

internal sealed interface ReadingPiece {
    data class Text(val value: String) : ReadingPiece
    data class Link(val label: String, val url: String) : ReadingPiece
}

/** 文件里仍是轻量 Markdown。阅读时只把链接收成可点的文字，地址不显示出来。 */
internal fun readingPieces(markdown: String): List<ReadingPiece> {
    if (markdown.isEmpty()) return emptyList()
    val pieces = ArrayList<ReadingPiece>()
    var cursor = 0
    for (match in LINK.findAll(markdown)) {
        if (match.range.first > cursor) {
            pieces += ReadingPiece.Text(markdown.substring(cursor, match.range.first))
        }
        val label = match.groupValues[1]
        val url = match.groupValues[2]
        if (label.isNotEmpty() && isReadingLink(url)) {
            pieces += ReadingPiece.Link(label, url)
        } else if (label.isNotEmpty()) {
            pieces += ReadingPiece.Text(label)
        }
        cursor = match.range.last + 1
    }
    if (cursor < markdown.length) {
        pieces += ReadingPiece.Text(markdown.substring(cursor))
    }
    return pieces
}

private fun isReadingLink(url: String): Boolean {
    if (url.isEmpty() || url.any { it.isWhitespace() }) return false
    val scheme = url.substringBefore(':', missingDelimiterValue = "").lowercase()
    return scheme == "http" || scheme == "https" || scheme == "mailto"
}
