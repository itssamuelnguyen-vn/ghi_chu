package com.samuelcruz.ghichu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.samuelcruz.ghichu.core.Markdown
import com.samuelcruz.ghichu.core.Markdown.Block
import com.samuelcruz.ghichu.core.Markdown.Style
import com.samuelcruz.ghichu.core.matchRanges

/** Colours used to draw Markdown. */
data class MdColors(val text: Color, val dim: Color, val accent: Color, val codeBg: Color, val highlight: Color)

@Composable
fun mdColors(): MdColors {
    val cs = MaterialTheme.colorScheme
    return MdColors(
        text = cs.onSurface,
        dim = cs.onSurface.copy(alpha = 0.38f),
        accent = cs.primary,
        codeBg = cs.onSurface.copy(alpha = 0.07f),
        highlight = Color(0x66FACC15),
    )
}

private fun headingStyle(block: Block) = when (block) {
    Block.H1 -> SpanStyle(fontSize = 1.5.em, fontWeight = FontWeight.ExtraBold)
    Block.H2 -> SpanStyle(fontSize = 1.28.em, fontWeight = FontWeight.Bold)
    else -> SpanStyle(fontSize = 1.12.em, fontWeight = FontWeight.Bold)
}

private fun inlineStyle(style: Style, c: MdColors) = when (style) {
    Style.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
    Style.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
    Style.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
    Style.CODE -> SpanStyle(fontFamily = FontFamily.Monospace, background = c.codeBg)
    Style.LINK, Style.URL -> SpanStyle(color = c.accent, textDecoration = TextDecoration.Underline)
    Style.NOTE_LINK -> SpanStyle(color = c.accent, fontWeight = FontWeight.SemiBold, background = c.accent.copy(alpha = 0.10f))
}

/**
 * Live styling while typing: the Markdown stays as typed (markers shown faint), but headings are big, bold is
 * bold, lists get coloured markers, and so on. Same characters in and out, so the cursor needs no mapping.
 */
class MarkdownTransformation(private val c: MdColors) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val b = AnnotatedString.Builder(raw)
        var offset = 0
        for ((line, info) in Markdown.lines(raw)) {
            val start = offset
            val end = offset + line.length
            val contentStart = start + info.prefix.coerceAtMost(line.length)
            when (info.block) {
                Block.H1, Block.H2, Block.H3 -> {
                    b.addStyle(SpanStyle(color = c.dim), start, contentStart)
                    b.addStyle(headingStyle(info.block), contentStart, end)
                }
                Block.BULLET, Block.NUMBERED -> b.addStyle(SpanStyle(color = c.accent, fontWeight = FontWeight.Bold), start, contentStart)
                Block.TASK -> {
                    b.addStyle(SpanStyle(color = c.accent, fontWeight = FontWeight.Bold), start, contentStart)
                    if (info.checked) b.addStyle(SpanStyle(color = c.dim, textDecoration = TextDecoration.LineThrough), contentStart, end)
                }
                Block.QUOTE -> {
                    b.addStyle(SpanStyle(color = c.accent), start, contentStart)
                    b.addStyle(SpanStyle(fontStyle = FontStyle.Italic), contentStart, end)
                }
                Block.FENCE -> b.addStyle(SpanStyle(color = c.dim, fontFamily = FontFamily.Monospace), start, end)
                Block.CODE -> b.addStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = c.codeBg), start, end)
                Block.RULE -> b.addStyle(SpanStyle(color = c.dim), start, end)
                Block.PARAGRAPH -> Unit
            }
            if (info.block != Block.CODE && info.block != Block.FENCE) {
                for (s in Markdown.inline(line.substring(info.prefix.coerceAtMost(line.length)))) {
                    val base = contentStart
                    b.addStyle(inlineStyle(s.style, c), base + s.contentStart, base + s.contentEnd)
                    if (s.start < s.contentStart) b.addStyle(SpanStyle(color = c.dim), base + s.start, base + s.contentStart)
                    if (s.contentEnd < s.end) b.addStyle(SpanStyle(color = c.dim), base + s.contentEnd, base + s.end)
                }
            }
            offset = end + 1
        }
        return TransformedText(b.toAnnotatedString(), OffsetMapping.Identity)
    }
}

/**
 * Inline Markdown of one line as display text: markers removed, styles applied, links clickable when [links],
 * search words highlighted.
 */
fun inlineText(
    text: String,
    c: MdColors,
    links: Boolean,
    highlight: List<String> = emptyList(),
    onNoteLink: ((String) -> Unit)? = null,
): AnnotatedString {
    val spans = Markdown.inline(text)
    val hidden = BooleanArray(text.length)
    for (s in spans) {
        for (k in s.start until s.contentStart) hidden[k] = true
        for (k in s.contentEnd until s.end) hidden[k] = true
    }
    // visibleIndex[i] = position of original character i in the shown text.
    val visibleIndex = IntArray(text.length + 1)
    val sb = StringBuilder()
    for (i in text.indices) {
        visibleIndex[i] = sb.length
        if (!hidden[i]) sb.append(text[i])
    }
    visibleIndex[text.length] = sb.length
    val shown = sb.toString()
    val b = AnnotatedString.Builder(shown)
    for (s in spans) {
        val from = visibleIndex[s.contentStart]
        val to = visibleIndex[s.contentEnd]
        if (from >= to) continue
        b.addStyle(inlineStyle(s.style, c), from, to)
        if (s.style == Style.NOTE_LINK) {
            if (links && onNoteLink != null && s.url != null) {
                val title = s.url
                b.addLink(LinkAnnotation.Clickable("note:$title", TextLinkStyles(SpanStyle(color = c.accent))) { onNoteLink(title) }, from, to)
            }
        } else if (links && s.url != null) {
            b.addLink(LinkAnnotation.Url(s.url, TextLinkStyles(SpanStyle(color = c.accent))), from, to)
        }
    }
    for (r in matchRanges(shown, highlight)) b.addStyle(SpanStyle(background = c.highlight), r.first, r.last + 1)
    return b.toAnnotatedString()
}

/**
 * Reads a Markdown note: headings, lists with bullets, tasks with tick boxes (tappable when [onToggleTask] is
 * given), quotes with a bar, code in a box, `[[note links]]` (tappable when [onNoteLink] is given). [maxLines]
 * cuts long notes in cards.
 */
@Composable
fun MarkdownView(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    maxLines: Int = Int.MAX_VALUE,
    links: Boolean = true,
    highlight: List<String> = emptyList(),
    onToggleTask: ((lineIndex: Int) -> Unit)? = null,
    /** A `[[note link]]` was tapped (title of the linked note). */
    onNoteLink: ((String) -> Unit)? = null,
) {
    val c = mdColors()
    val lines = remember(text) { Markdown.lines(text) }
    val raw = remember(lines) { lines.map { it.first } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        var shown = 0
        var i = 0
        while (i < lines.size && shown < maxLines) {
            val (line, info) = lines[i]
            val index = i
            when (info.block) {
                Block.FENCE -> {
                    // Collect the whole code block.
                    val code = StringBuilder()
                    var j = i + 1
                    while (j < lines.size && lines[j].second.block != Block.FENCE) {
                        if (code.isNotEmpty()) code.append('\n')
                        code.append(lines[j].first)
                        j++
                    }
                    Text(
                        code.toString(),
                        style = style.copy(fontFamily = FontFamily.Monospace, fontSize = style.fontSize * 0.9f),
                        maxLines = (maxLines - shown).coerceAtLeast(1),
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.codeBg).padding(10.dp),
                    )
                    shown += code.count { it == '\n' } + 1
                    i = j + 1
                    continue
                }
                Block.RULE -> HorizontalDivider(Modifier.padding(vertical = 8.dp), color = c.dim)
                Block.H1, Block.H2, Block.H3 -> Text(
                    inlineText(line.substring(info.prefix), c, links, highlight, onNoteLink),
                    style = style.merge(TextStyle(fontWeight = FontWeight.Bold, fontSize = style.fontSize * when (info.block) {
                        Block.H1 -> 1.45f
                        Block.H2 -> 1.25f
                        else -> 1.1f
                    })),
                    modifier = Modifier.padding(top = if (index == 0) 0.dp else 6.dp),
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                )
                Block.TASK -> Row(
                    Modifier.padding(start = (info.indent * 8).dp)
                        .then(if (onToggleTask != null) Modifier.clip(RoundedCornerShape(6.dp)).clickable { onToggleTask(index) } else Modifier),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        if (info.checked) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank, null,
                        tint = if (info.checked) c.dim else c.accent,
                        modifier = Modifier.padding(top = 1.dp, end = 8.dp).size((style.fontSize.value * 1.3f).dp),
                    )
                    Text(
                        inlineText(line.substring(info.prefix), c, links, highlight, onNoteLink),
                        style = style.copy(
                            color = if (info.checked) c.dim else style.color.takeOrElse(c.text),
                            textDecoration = if (info.checked) TextDecoration.LineThrough else null,
                        ),
                        maxLines = 4, overflow = TextOverflow.Ellipsis,
                    )
                }
                Block.BULLET, Block.NUMBERED -> Row(Modifier.padding(start = (info.indent * 8).dp)) {
                    Text(
                        if (info.block == Block.BULLET) "•" else "${info.number}.",
                        style = style.copy(color = c.accent, fontWeight = FontWeight.Bold),
                        modifier = Modifier.widthIn(min = 20.dp),
                    )
                    Text(
                        inlineText(line.substring(info.prefix), c, links, highlight, onNoteLink), style = style,
                        maxLines = 4, overflow = TextOverflow.Ellipsis,
                    )
                }
                Block.QUOTE -> Row(Modifier.heightIn(min = 20.dp)) {
                    Box(Modifier.width(3.dp).height((style.fontSize.value * 1.5f).dp).background(c.accent, RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        inlineText(line.substring(info.prefix), c, links, highlight, onNoteLink),
                        style = style.copy(fontStyle = FontStyle.Italic, color = style.color.takeOrElse(c.text).copy(alpha = 0.8f)),
                        maxLines = 4, overflow = TextOverflow.Ellipsis,
                    )
                }
                Block.PARAGRAPH -> {
                    val t = Markdown.table(raw, i)
                    if (t != null) {
                        MdTable(t, style, c, links, highlight, onNoteLink)
                        shown += t.rows.size + 1
                        i += t.lineCount
                        continue
                    }
                    if (line.isBlank()) {
                        Spacer(Modifier.height((style.fontSize.value * 0.6f).dp))
                    } else {
                        Text(
                            inlineText(line, c, links, highlight, onNoteLink), style = style,
                            maxLines = if (maxLines == Int.MAX_VALUE) Int.MAX_VALUE else (maxLines - shown).coerceAtLeast(1),
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Block.CODE -> if (line.isBlank()) {
                    Spacer(Modifier.height((style.fontSize.value * 0.6f).dp))
                } else {
                    Text(
                        inlineText(line, c, links, highlight, onNoteLink), style = style,
                        maxLines = if (maxLines == Int.MAX_VALUE) Int.MAX_VALUE else (maxLines - shown).coerceAtLeast(1),
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            shown++
            i++
        }
    }
}

/** A Markdown table: header in bold on a tinted row, thin borders; wide tables scroll sideways. */
@Composable
private fun MdTable(
    t: Markdown.Table,
    style: TextStyle,
    c: MdColors,
    links: Boolean,
    highlight: List<String>,
    onNoteLink: ((String) -> Unit)?,
) {
    val wide = t.columns > 3
    val border = c.dim.copy(alpha = 0.3f)
    val cellStyle = style.copy(fontSize = style.fontSize * 0.92f)
    Column(
        Modifier.padding(vertical = 4.dp)
            .then(if (wide) Modifier.horizontalScroll(rememberScrollState()) else Modifier.fillMaxWidth())
            .clip(RoundedCornerShape(8.dp)).border(1.dp, border, RoundedCornerShape(8.dp)),
    ) {
        (listOf(t.header) + t.rows).forEachIndexed { r, row ->
            Row(
                Modifier.height(IntrinsicSize.Min).then(if (r == 0) Modifier.background(c.codeBg) else Modifier)
                    .then(if (wide) Modifier else Modifier.fillMaxWidth()),
            ) {
                row.forEachIndexed { k, cell ->
                    Text(
                        inlineText(cell, c, links, highlight, onNoteLink),
                        style = cellStyle.copy(
                            fontWeight = if (r == 0) FontWeight.Bold else cellStyle.fontWeight,
                            textAlign = when (t.align[k]) {
                                Markdown.Align.CENTER -> TextAlign.Center
                                Markdown.Align.END -> TextAlign.End
                                else -> TextAlign.Start
                            },
                        ),
                        modifier = (if (wide) Modifier.width(130.dp) else Modifier.weight(1f))
                            .fillMaxHeight()
                            .border(0.5.dp, border)
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

private fun Color.takeOrElse(other: Color) = if (this == Color.Unspecified) other else this
