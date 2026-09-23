package io.github.angelosachet.tdahreader.pdf

// Cópia de PdfTextExtractor usando o Apache PDFBox (mesmo motor do PdfBox-Android),
// para testar a extração na JVM. Mantenha as duas versões em sincronia.

import org.apache.pdfbox.io.MemoryUsageSetting
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import io.github.angelosachet.tdahreader.text.Glyph
import io.github.angelosachet.tdahreader.text.LineAssembler
import io.github.angelosachet.tdahreader.text.OutlineEntry
import io.github.angelosachet.tdahreader.text.PageText
import java.io.File
import java.io.Writer
import java.util.Collections
import java.util.IdentityHashMap

class JvmExtractedPdf(
    val pages: List<PageText>,
    val pageCount: Int,
    val outline: List<OutlineEntry>,
    val title: String?,
    val author: String?,
)

/**
 * Extrai do PDF as linhas de texto com posição e tamanho de fonte.
 * Lança [org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException] para PDFs com senha.
 */
object JvmPdfTextExtractor {

    fun extract(file: File, onPage: (page: Int, total: Int) -> Unit): JvmExtractedPdf {
        PDDocument.load(file, "", MemoryUsageSetting.setupMixed(64L * 1024 * 1024)).use { doc ->
            val total = doc.numberOfPages
            val pages = ArrayList<PageText>(total)
            val stripper = LineStripper(onPageStart = { onPage(it, total) }, onPageDone = { pages += it })
            stripper.writeText(doc, NullWriter)
            val info = doc.documentInformation
            return JvmExtractedPdf(
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
            assembler.addWord(text, textPositions.map { tp ->
                Glyph(
                    text = tp.unicode ?: "",
                    x = tp.xDirAdj,
                    baseline = tp.yDirAdj,
                    width = tp.widthDirAdj,
                    fontSize = tp.fontSizeInPt,
                    bold = isBold(tp.font),
                )
            })
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
