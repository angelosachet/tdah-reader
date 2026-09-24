package io.github.angelosachet.tdahreader.pdf

import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.font.PDCIDFontType0
import com.tom_roush.pdfbox.pdmodel.font.PDCIDFontType2
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDSimpleFont
import com.tom_roush.pdfbox.pdmodel.font.PDTrueTypeFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.encoding.GlyphList
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import io.github.angelosachet.tdahreader.text.Glyph
import io.github.angelosachet.tdahreader.text.GlyphRepair
import io.github.angelosachet.tdahreader.text.LineAssembler
import io.github.angelosachet.tdahreader.text.OutlineEntry
import io.github.angelosachet.tdahreader.text.PageText
import java.io.File
import java.io.Writer
import java.util.Collections
import java.util.IdentityHashMap

class ExtractedPdf(
    val pages: List<PageText>,
    val pageCount: Int,
    val outline: List<OutlineEntry>,
    val title: String?,
    val author: String?,
)

/**
 * Extrai do PDF as linhas de texto com posição e tamanho de fonte.
 * Lança [com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException] para PDFs com senha.
 */
object PdfTextExtractor {

    fun extract(file: File, onPage: (page: Int, total: Int) -> Unit): ExtractedPdf {
        PDDocument.load(file, "", MemoryUsageSetting.setupMixed(64L * 1024 * 1024)).use { doc ->
            val total = doc.numberOfPages
            val pages = ArrayList<PageText>(total)
            val stripper = LineStripper(onPageStart = { onPage(it, total) }, onPageDone = { pages += it })
            stripper.writeText(doc, NullWriter)
            val info = doc.documentInformation
            return ExtractedPdf(
                pages = pages,
                pageCount = total,
                outline = runCatching { readOutline(doc) }.getOrDefault(emptyList()),
                title = info?.title,
                author = info?.author,
            )
        }
    }

    private fun readOutline(doc: PDDocument): List<OutlineEntry> {
        val root = doc.documentCatalog.documentOutline ?: return emptyList()
        val result = mutableListOf<OutlineEntry>()
        val seen = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        fun walk(node: PDOutlineNode, level: Int) {
            var child = node.firstChild
            while (child != null && result.size < 3000 && seen.add(child.cosObject)) {
                val title = child.title?.replace(Regex("\\s+"), " ")?.trim()
                val page = runCatching { child.findDestinationPage(doc)?.let { doc.pages.indexOf(it) } }
                    .getOrNull() ?: -1
                if (!title.isNullOrEmpty() && page >= 0) result += OutlineEntry(title, page, level)
                if (level < 8) walk(child, level + 1)
                child = child.nextSibling
            }
        }
        walk(root, 0)
        return result
    }

    private object NullWriter : Writer() {
        override fun write(cbuf: CharArray, off: Int, len: Int) = Unit
        override fun flush() = Unit
        override fun close() = Unit
    }

    private class LineStripper(
        private val onPageStart: (Int) -> Unit,
        private val onPageDone: (PageText) -> Unit,
    ) : PDFTextStripper() {
        private val assembler = LineAssembler()
        private val boldFonts = IdentityHashMap<PDFont, Boolean>()
        private val glyphNameCache = HashMap<Pair<PDFont, Int>, String>()
        private var pageIndex = 0
        private var width = 0f
        private var height = 0f

        init {
            sortByPosition = false
            setShouldSeparateByBeads(true)
        }

        override fun startPage(page: PDPage) {
            pageIndex = currentPageNo - 1
            onPageStart(pageIndex)
            val box = page.cropBox
            if (page.rotation % 180 != 0) {
                width = box.height; height = box.width
            } else {
                width = box.width; height = box.height
            }
        }

        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            var repaired = false
            val glyphs = textPositions.map { tp ->
                val raw = tp.unicode ?: ""
                val unicode = if (raw.isEmpty() || raw.any(GlyphRepair::isSuspicious)) {
                    recoverFromGlyphName(tp)?.also { repaired = true } ?: raw
                } else {
                    raw
                }
                Glyph(
                    text = unicode,
                    x = tp.xDirAdj,
                    baseline = tp.yDirAdj,
                    width = tp.widthDirAdj,
                    fontSize = tp.fontSizeInPt,
                    bold = isBold(tp.font),
                )
            }
            val wordText = if (repaired) LineAssembler.normalizeLigatures(glyphs.joinToString("") { it.text }) else text
            assembler.addWord(wordText, glyphs)
        }

        /**
         * Ligaduras sem Unicode ("T_h", "f_f_i") chegam como símbolos: tenta descobrir
         * o texto pelo nome do glifo dentro da própria fonte.
         */
        private fun recoverFromGlyphName(tp: TextPosition): String? {
            val code = tp.characterCodes?.singleOrNull() ?: return null
            val font = tp.font ?: return null
            val key = font to code
            glyphNameCache[key]?.let { return it.ifEmpty { null } }
            val name = runCatching { glyphName(font, code) }.getOrNull()
            val decoded = GlyphRepair.decodeGlyphName(name) { GlyphList.getAdobeGlyphList().toUnicode(it) }
            glyphNameCache[key] = decoded ?: ""
            return decoded
        }

        private fun glyphName(font: PDFont, code: Int): String? {
            if (font is PDSimpleFont) {
                val name = font.encoding?.getName(code)
                if (name != null && name != ".notdef") return name
                if (font is PDTrueTypeFont) return font.trueTypeFont?.postScript?.getName(font.codeToGID(code))
                return null
            }
            if (font is PDType0Font) {
                return when (val cid = font.descendantFont) {
                    is PDCIDFontType2 -> cid.trueTypeFont?.postScript?.getName(cid.codeToGID(code))
                    is PDCIDFontType0 -> cid.cffFont?.charset?.let { charset ->
                        if (charset.isCIDFont) null else charset.getNameForGID(cid.codeToGID(code))
                    }
                    else -> null
                }
            }
            return null
        }

        override fun writeWordSeparator() = Unit
        override fun writeLineSeparator() = assembler.endLine()

        override fun endArticle() {
            assembler.endLine()
            super.endArticle()
        }

        override fun endPage(page: PDPage) {
            onPageDone(PageText(pageIndex, width, height, assembler.takeLines()))
        }

        private fun isBold(font: PDFont?): Boolean {
            if (font == null) return false
            return boldFonts.getOrPut(font) {
                val d = font.fontDescriptor
                val name = (font.name ?: "").lowercase()
                (d != null && (d.isForceBold || d.fontWeight >= 600f)) ||
                    BOLD_NAMES.any { it in name }
            }
        }

        companion object {
            private val BOLD_NAMES = listOf("bold", "black", "heavy", "semibold", "demi")
        }
    }
}
