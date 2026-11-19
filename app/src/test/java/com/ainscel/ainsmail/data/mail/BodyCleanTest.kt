package com.ainscel.ainsmail.data.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyCleanTest {
    @Test
    fun dropsScriptFramesPluginsAndForms() {
        val html = sanitizeMailHtml(
            """
            <p>可见正文</p>
            <script>alert(1)</script>
            <iframe src="https://evil.example"></iframe>
            <object data="https://evil.example/plugin"></object>
            <form><p>验证码 123</p><input value="secret-token"><button>提交</button></form>
            """.trimIndent(),
        )
        assertTrue(html.contains("可见正文"))
        assertTrue(html.contains("验证码 123"))
        assertFalse(html.contains("alert"))
        assertFalse(html.contains("script"))
        assertFalse(html.contains("iframe"))
        assertFalse(html.contains("evil"))
        assertFalse(html.contains("object"))
        assertFalse(html.contains("secret-token"))
        assertFalse(html.contains("<input"))
        assertFalse(html.contains("<button"))
        assertFalse(html.contains("<form"))
    }

    @Test
    fun dropsEventAttributesAndJavascriptLinks() {
        val html = sanitizeMailHtml(
            """<p onclick="alert(1)" style="color:red">你好</p><a href="javascript:alert(1)">点我</a>""",
        )
        assertTrue(html.contains("你好"))
        assertTrue(html.contains("点我"))
        assertFalse(html.contains("onclick"))
        assertFalse(html.contains("javascript"))
        assertFalse(html.contains("alert"))
        val markdown = mailHtmlToMarkdown(html)
        assertTrue(markdown.contains("点我"))
        assertFalse(markdown.contains("javascript"))
    }

    @Test
    fun dropsHiddenKeywordNodes() {
        val html = sanitizeMailHtml(
            """
            <p>留下</p>
            <div style="display:none">关键词</div>
            <span style="visibility:hidden">再藏</span>
            <span style="font-size:0px">堆词</span>
            <font size="0">零号</font>
            <div style="color:#ffffff;background-color:white">同色</div>
            <p style="color:transparent">透明</p>
            """.trimIndent(),
        )
        assertTrue(html.contains("留下"))
        assertFalse(html.contains("关键词"))
        assertFalse(html.contains("再藏"))
        assertFalse(html.contains("堆词"))
        assertFalse(html.contains("零号"))
        assertFalse(html.contains("同色"))
        assertFalse(html.contains("透明"))
        assertFalse(mailHtmlToMarkdown(html).contains("堆词"))
    }

    @Test
    fun remoteImagesStayPlaceholderUntilAllowed() {
        val html = """
            <p>见<img src="https://cdn.example/a.png" alt="封面">
            <img src="http://cdn.example/b.png" alt="普通">
            <img src="cid:abc" alt="内嵌">
            <img src="data:image/png;base64,AAAA"></p>
        """.trimIndent()
        val blocked = sanitizeMailHtml(html)
        assertFalse(blocked.contains("https://"))
        assertFalse(blocked.contains("http://"))
        assertFalse(blocked.contains("cid:"))
        assertFalse(blocked.contains("base64"))
        assertTrue(blocked.contains("封面"))
        assertTrue(blocked.contains("图片"))
        val markdown = mailHtmlToMarkdown(blocked)
        assertTrue(markdown.contains("封面"))
        assertTrue(markdown.contains("图片"))
        assertFalse(markdown.contains("base64"))

        val allowed = sanitizeMailHtml(html, loadRemoteImages = true)
        assertTrue(allowed.contains("https://cdn.example/a.png"))
        assertFalse(allowed.contains("http://cdn.example/b.png"))
        assertFalse(allowed.contains("cid:"))
        assertFalse(allowed.contains("base64"))
        assertTrue(allowed.contains("普通"))
        assertTrue(allowed.contains("内嵌"))
    }

    @Test
    fun markdownKeepsParagraphsLinksListsAndImageText() {
        val safe = sanitizeMailHtml(
            """
            <p>请看<a href="https://example.com/a">这里</a>。</p>
            <ul><li>甲</li><li>乙</li></ul>
            <p><img src="https://cdn.example/a.png" alt="封面"></p>
            <p><b>粗体</b>就这样</p>
            """.trimIndent(),
        )
        val markdown = mailHtmlToMarkdown(safe)
        assertTrue(markdown.contains("请看[这里](https://example.com/a)。"))
        assertTrue(markdown.contains("- 甲"))
        assertTrue(markdown.contains("- 乙"))
        assertTrue(markdown.contains("封面"))
        assertTrue(markdown.contains("粗体就这样"))
        assertFalse(markdown.contains("cdn.example"))
        assertFalse(markdown.contains("**"))
    }

    @Test
    fun plainTextUsesTheSameFiles() {
        val prepared = prepareMailBody(
            html = null,
            plain = "第一段\n第二行\n\n第二段 <tag>",
        )
        assertTrue(prepared.html.contains("<p>"))
        assertTrue(prepared.html.contains("第一段"))
        assertTrue(prepared.html.contains("&lt;tag&gt;"))
        assertFalse(prepared.html.contains("<tag>"))
        assertTrue(prepared.markdown.contains("第一段"))
        assertTrue(prepared.markdown.contains("第二行"))
        assertTrue(prepared.markdown.contains("第二段"))
        assertEquals(prepared.preview, previewFromMarkdown(prepared.markdown))
    }

    @Test
    fun htmlWinsWhenBothPartsExist() {
        val prepared = prepareMailBody(
            html = "<p>网页</p>",
            plain = "纯文本不该出现",
        )
        assertTrue(prepared.markdown.contains("网页"))
        assertFalse(prepared.markdown.contains("纯文本不该出现"))
    }

    @Test
    fun previewIsThePlainOpening() {
        val markdown = "[这里](https://example.com/" + "a".repeat(40) + ")\n\n" + "字".repeat(200)
        val preview = previewFromMarkdown(markdown)
        assertTrue(preview.startsWith("这里"))
        assertEquals(MAIL_PREVIEW_CHARS, preview.length)
        assertFalse(preview.contains("https://"))
        assertFalse(preview.contains("\n"))
    }
}
