package com.ainscel.ainsmail.ui.detail

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink

/** 阅读里的链接用蓝色。主题主色是紫，不拿来当链接。 */
private val ReadingLinkBlue = Color(0xFF1A73E8)

@Composable
internal fun ReadingMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val annotated = remember(markdown) {
        buildAnnotatedString {
            for (piece in readingPieces(markdown)) {
                when (piece) {
                    is ReadingPiece.Text -> append(piece.value)
                    is ReadingPiece.Link -> withLink(
                        LinkAnnotation.Url(
                            url = piece.url,
                            styles = TextLinkStyles(style = SpanStyle(color = ReadingLinkBlue)),
                        ),
                    ) {
                        append(piece.label)
                    }
                }
            }
        }
    }
    Text(text = annotated, modifier = modifier, style = style)
}
