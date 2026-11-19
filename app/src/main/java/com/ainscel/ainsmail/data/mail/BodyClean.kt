package com.ainscel.ainsmail.data.mail

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** 列表预览只用 Markdown 压成一行后的开头。 */
internal const val MAIL_PREVIEW_CHARS = 80

private const val IMAGE_PLACEHOLDER = "图片"

private val KEEP_TAGS = setOf(
    "p", "br", "h1", "h2", "h3", "h4", "h5", "h6",
    "ul", "ol", "li", "table", "thead", "tbody", "tr", "th", "td",
    "blockquote", "a", "img", "b", "strong", "i", "em", "pre", "code", "span", "div",
)

/** 连可见文字一起丢掉。表单控件属于这一类；表单标签本身展开，留下里面的句子。 */
private val DROP_TAGS = setOf(
    "script", "style", "iframe", "frame", "object", "embed", "applet",
    "noscript", "template", "svg", "math", "link", "meta", "base",
    "input", "button", "textarea", "select", "option",
)

private val LINK_SYNTAX = Regex("""\[([^\[\]]*)]\([^)]*\)""")
private val ZERO_NUMBER = Regex("""^0+(?:\.0+)?$""")
private val ZERO_FONT = Regex("""^0+(?:\.0+)?(?:px|pt|pc|em|rem|ex|ch|vh|vw|%)?$""")
private val RGB_COLOR = Regex("""rgba?\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*(?:,\s*([\d.]+)\s*)?\)""")

private val NAMED_COLORS = mapOf(
    "white" to "#ffffff",
    "black" to "#000000",
    "red" to "#ff0000",
    "green" to "#008000",
    "lime" to "#00ff00",
    "blue" to "#0000ff",
    "yellow" to "#ffff00",
    "gray" to "#808080",
    "grey" to "#808080",
    "silver" to "#c0c0c0",
    "maroon" to "#800000",
    "purple" to "#800080",
    "fuchsia" to "#ff00ff",
    "magenta" to "#ff00ff",
    "aqua" to "#00ffff",
    "cyan" to "#00ffff",
    "navy" to "#000080",
    "teal" to "#008080",
    "olive" to "#808000",
    "orange" to "#ffa500",
)

data class PreparedBody(
    val html: String,
    val markdown: String,
    val preview: String,
)

/**
 * 有 HTML 就洗 HTML，否则把纯文本收成简单 HTML 再走同一套。
 * [loadRemoteImages] 默认关闭。打开时只保留 https 图片地址，按钮在阅读那一节。
 */
fun prepareMailBody(html: String?, plain: String?, loadRemoteImages: Boolean = false): PreparedBody {
    val source = when {
        !html.isNullOrBlank() -> html
        !plain.isNullOrBlank() -> plainTextToHtml(plain)
        else -> ""
    }
    val safe = if (source.isEmpty()) "" else sanitizeMailHtml(source, loadRemoteImages)
    val markdown = if (safe.isEmpty()) "" else mailHtmlToMarkdown(safe)
    return PreparedBody(safe, markdown, previewFromMarkdown(markdown))
}

/** 按空行分段，段内换行变成 br。先转义，避免纯文本被当成标签。 */
fun plainTextToHtml(text: String): String {
    val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
    val builder = StringBuilder()
    for (paragraph in normalized.split(Regex("\n{2,}"))) {
        if (paragraph.isBlank()) continue
        builder.append("<p>")
        paragraph.split('\n').forEachIndexed { index, line ->
            if (index > 0) builder.append("<br>")
            builder.append(escapeHtml(line))
        }
        builder.append("</p>")
    }
    return builder.toString()
}

/**
 * 白名单留下段落、标题、列表、表格、引用、链接、图片位置、粗体和斜体。
 * 先按样式删掉藏起来的节点，再剥掉其余标签和属性。
 */
fun sanitizeMailHtml(html: String, loadRemoteImages: Boolean = false): String {
    val body = Jsoup.parseBodyFragment(html).body()
    removeHidden(body)
    rewriteImages(body, loadRemoteImages)
    stripTags(body, loadRemoteImages)
    body.ownerDocument()?.outputSettings()?.prettyPrint(false)
    return body.html()
}

/** 只留段落、链接的可见文字和地址、列表文字、图片替代文字或「图片」。 */
fun mailHtmlToMarkdown(html: String): String {
    val body = Jsoup.parseBodyFragment(html).body()
    val builder = StringBuilder()
    renderChildren(body, builder)
    return builder.toString().trim()
}

fun previewFromMarkdown(markdown: String): String {
    val visible = LINK_SYNTAX.replace(markdown) { match -> match.groupValues[1] }
        .replace(Regex("(?m)^-\\s+"), "")
    val flat = visible.replace(Regex("\\s+"), " ").trim()
    return flat.take(MAIL_PREVIEW_CHARS)
}

private fun removeHidden(root: Element) {
    val hidden = ArrayList<Element>()
    for (element in root.allElements) {
        if (element === root) continue
        val style = element.attr("style")
        val fontHidden = element.normalName() == "font" && element.attr("size").trim() == "0"
        if (fontHidden || (style.isNotEmpty() && hidesText(style))) hidden.add(element)
    }
    for (element in hidden) element.remove()
}

private fun hidesText(style: String): Boolean {
    val declared = declarations(style)
    if (declared["display"] == "none") return true
    val visibility = declared["visibility"]
    if (visibility == "hidden" || visibility == "collapse") return true
    val opacity = declared["opacity"]
    if (opacity != null && ZERO_NUMBER.matches(opacity.replace(" ", ""))) return true
    val fontSize = declared["font-size"]
    if (fontSize != null && ZERO_FONT.matches(fontSize.replace(" ", ""))) return true
    val color = declared["color"]?.let(::parseColorToken)
    if (color == "transparent") return true
    val background = declared["background-color"]?.let(::parseColorToken)
        ?: declared["background"]?.let(::colorInBackground)
    return color != null && background != null && color == background
}

private fun declarations(style: String): Map<String, String> {
    val declared = LinkedHashMap<String, String>()
    for (part in style.split(';')) {
        val splitAt = part.indexOf(':')
        if (splitAt <= 0) continue
        val key = part.substring(0, splitAt).trim().lowercase()
        val value = part.substring(splitAt + 1).trim().lowercase().removeSuffix("!important").trim()
        if (key.isNotEmpty() && value.isNotEmpty()) declared[key] = value
    }
    return declared
}

private fun parseColorToken(value: String): String? {
    val token = value.trim().split(Regex("\\s+")).firstOrNull() ?: return null
    return parseColor(token)
}

private fun colorInBackground(value: String): String? {
    val withoutUrl = value.replace(Regex("""url\([^)]*\)"""), " ")
    for (token in withoutUrl.split(Regex("\\s+"))) {
        parseColor(token)?.let { return it }
    }
    return null
}

private fun parseColor(token: String): String? {
    val value = token.trim().lowercase()
    if (value.isEmpty()) return null
    if (value == "transparent") return "transparent"
    NAMED_COLORS[value]?.let { return it }
    if (value.startsWith("#")) return expandHex(value)
    if (value.startsWith("rgb")) return parseRgb(value)
    return null
}

private fun expandHex(value: String): String? {
    val hex = value.removePrefix("#")
    if (hex.any { it !in '0'..'9' && it !in 'a'..'f' }) return null
    val rgb = when (hex.length) {
        3 -> hex.map { "$it$it" }.joinToString("")
        4 -> {
            if (hex[3] == '0') return "transparent"
            hex.take(3).map { "$it$it" }.joinToString("")
        }
        6 -> hex
        8 -> {
            if (hex.substring(6).all { it == '0' }) return "transparent"
            hex.take(6)
        }
        else -> return null
    }
    return "#$rgb"
}

private fun parseRgb(value: String): String? {
    val match = RGB_COLOR.find(value) ?: return null
    val alpha = match.groupValues[4]
    if (alpha.isNotEmpty() && alpha.toFloatOrNull() == 0f) return "transparent"
    val red = match.groupValues[1].toIntOrNull() ?: return null
    val green = match.groupValues[2].toIntOrNull() ?: return null
    val blue = match.groupValues[3].toIntOrNull() ?: return null
    if (red !in 0..255 || green !in 0..255 || blue !in 0..255) return null
    return "#%02x%02x%02x".format(red, green, blue)
}

private fun rewriteImages(root: Element, loadRemoteImages: Boolean) {
    for (image in root.select("img").toList()) {
        val alt = image.attr("alt").trim().ifEmpty { IMAGE_PLACEHOLDER }
        val src = image.attr("src").trim()
        if (loadRemoteImages && src.startsWith("https://")) {
            image.clearAttributes()
            image.attr("alt", alt)
            image.attr("src", src)
        } else {
            image.replaceWith(TextNode(alt))
        }
    }
}

private fun stripTags(parent: Element, loadRemoteImages: Boolean) {
    var child = parent.firstChild()
    while (child != null) {
        val next = child.nextSibling()
        val element = child as? Element
        if (element != null) {
            when (element.normalName()) {
                in DROP_TAGS -> element.remove()
                in KEEP_TAGS -> {
                    cleanAttributes(element, loadRemoteImages)
                    stripTags(element, loadRemoteImages)
                }
                else -> {
                    stripTags(element, loadRemoteImages)
                    element.unwrap()
                }
            }
        }
        child = next
    }
}

private fun cleanAttributes(element: Element, loadRemoteImages: Boolean) {
    when (element.normalName()) {
        "a" -> {
            val href = element.attr("href").trim()
            element.clearAttributes()
            if (isSafeHref(href)) element.attr("href", href)
        }
        "img" -> {
            val alt = element.attr("alt").trim()
            val src = element.attr("src").trim()
            element.clearAttributes()
            if (alt.isNotEmpty()) element.attr("alt", alt)
            if (loadRemoteImages && src.startsWith("https://")) element.attr("src", src)
        }
        else -> element.clearAttributes()
    }
}

private fun isSafeHref(href: String): Boolean {
    if (href.isEmpty() || href.any { it.isWhitespace() }) return false
    val scheme = href.substringBefore(':', missingDelimiterValue = "").lowercase()
    return scheme == "http" || scheme == "https" || scheme == "mailto"
}

private fun renderChildren(parent: Element, builder: StringBuilder) {
    for (child in parent.childNodes()) renderNode(child, builder)
}

private fun renderNode(node: Node, builder: StringBuilder) {
    when (node) {
        is TextNode -> appendText(builder, node.text())
        is Element -> when (node.normalName()) {
            "br" -> builder.append('\n')
            "a" -> renderLink(node, builder)
            "img" -> appendText(builder, node.attr("alt").trim().ifEmpty { IMAGE_PLACEHOLDER })
            "li" -> {
                endLine(builder)
                builder.append("- ")
                renderChildren(node, builder)
                builder.append('\n')
            }
            "ul", "ol" -> {
                renderChildren(node, builder)
                endBlock(builder)
            }
            "p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "pre", "tr" -> {
                val start = builder.length
                renderChildren(node, builder)
                if (builder.length > start) endBlock(builder)
            }
            else -> renderChildren(node, builder)
        }
    }
}

private fun renderLink(element: Element, builder: StringBuilder) {
    val href = element.attr("href").trim()
    val text = visibleText(element).ifEmpty { href }
    if (text.isEmpty()) return
    if (isSafeHref(href)) {
        builder.append('[').append(text).append("](").append(href).append(')')
    } else {
        appendText(builder, text)
    }
}

private fun visibleText(element: Element): String {
    val builder = StringBuilder()
    fun walk(node: Node) {
        when (node) {
            is TextNode -> builder.append(node.text())
            is Element -> if (node.normalName() == "img") {
                builder.append(node.attr("alt").trim().ifEmpty { IMAGE_PLACEHOLDER })
            } else {
                node.childNodes().forEach(::walk)
            }
        }
    }
    element.childNodes().forEach(::walk)
    return builder.toString().replace(Regex("\\s+"), " ").trim()
}

private fun appendText(builder: StringBuilder, text: String) {
    val cleaned = text.replace(Regex("[\\t\\x0B\\f\\r ]+"), " ")
    if (cleaned.isBlank()) return
    builder.append(cleaned)
}

private fun endLine(builder: StringBuilder) {
    if (builder.isNotEmpty() && !builder.endsWith('\n')) builder.append('\n')
}

private fun endBlock(builder: StringBuilder) {
    if (builder.isEmpty() || builder.endsWith("\n\n")) return
    if (builder.endsWith('\n')) builder.append('\n') else builder.append("\n\n")
}

private fun escapeHtml(text: String): String = buildString(text.length) {
    for (character in text) {
        when (character) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            else -> append(character)
        }
    }
}
