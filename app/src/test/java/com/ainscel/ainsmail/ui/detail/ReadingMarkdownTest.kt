package com.ainscel.ainsmail.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingMarkdownTest {
    @Test
    fun linkShowsLabelAndKeepsUrlForTheClick() {
        val pieces = readingPieces(
            "通过[第一项设置](https://c.gle/abc)，你可以控制。通过[第二项设置](https://c.gle/def)，你可以决定。",
        )
        assertEquals(
            listOf(
                ReadingPiece.Text("通过"),
                ReadingPiece.Link("第一项设置", "https://c.gle/abc"),
                ReadingPiece.Text("，你可以控制。通过"),
                ReadingPiece.Link("第二项设置", "https://c.gle/def"),
                ReadingPiece.Text("，你可以决定。"),
            ),
            pieces,
        )
    }

    @Test
    fun plainParagraphsStayAsText() {
        val pieces = readingPieces("Ainscel，你好！\n\n我们正在更新。")
        assertEquals(listOf(ReadingPiece.Text("Ainscel，你好！\n\n我们正在更新。")), pieces)
    }

    @Test
    fun unsafeLinkDropsTheAddress() {
        val pieces = readingPieces("点[这里](javascript:alert)结束")
        assertEquals(
            listOf(
                ReadingPiece.Text("点"),
                ReadingPiece.Text("这里"),
                ReadingPiece.Text("结束"),
            ),
            pieces,
        )
    }
}
