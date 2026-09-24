package io.github.angelosachet.tdahreader.pdf

import io.github.angelosachet.tdahreader.text.Chunker
import io.github.angelosachet.tdahreader.text.LayoutAnalyzer
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Ferramenta manual: processa todos os PDFs de uma pasta e grava o resultado em texto
 * para inspeção. Rode com: TDAH_PDF_DIR=/caminho ./gradlew testDebugUnitTest --tests '*RealPdfHarness*'
 */
class RealPdfHarnessTest {
    @Test
    fun processRealPdfs() {
        val dir = System.getenv("TDAH_PDF_DIR")?.let(::File)
        assumeTrue(dir != null && dir.isDirectory)
        dir!!.listFiles { f -> f.extension.equals("pdf", true) }!!.sorted().forEach { pdf ->
            val t0 = System.nanoTime()
            val extracted = JvmPdfTextExtractor.extract(pdf) { _, _ -> }
            val t1 = System.nanoTime()
            System.getenv("TDAH_DUMP_PAGES")?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.forEach { n ->
                extracted.pages.firstOrNull { it.index == n - 1 }?.let { page ->
                    println("--- ${pdf.name} página $n (${page.width}x${page.height})")
                    page.lines.forEach { l ->
                        println("  y=%.1f x=%.1f..%.1f size=%.1f bold=%s | %s".format(l.baseline, l.x0, l.x1, l.fontSize, l.bold, l.text.take(70)))
                    }
                }
            }
            val paragraphs = LayoutAnalyzer {
                listOf("en.txt", "pt.txt").flatMap { File("src/main/assets/words/$it").readLines() }.toSet()
            }.analyze(extracted.pages)
            val chunks = Chunker(20).chunk(paragraphs)
            val t2 = System.nanoTime()
            File(dir, pdf.nameWithoutExtension + ".paragraphs.txt").printWriter().use { out ->
                paragraphs.forEach { p -> out.println("[p${p.page + 1}${if (p.heading) " H" else ""}] ${p.text}") }
            }
            File(dir, pdf.nameWithoutExtension + ".chunks.txt").printWriter().use { out ->
                chunks.forEach { c -> out.println("[p${c.page + 1}${if (c.heading) " H" else ""}${if (c.paragraphStart) " ¶" else ""}] ${c.text}") }
            }
            File(dir, pdf.nameWithoutExtension + ".outline.txt").printWriter().use { out ->
                extracted.outline.forEach { out.println("${"  ".repeat(it.level)}${it.title} -> p${it.page + 1}") }
            }
            println("${pdf.name}: ${extracted.pageCount} págs, ${paragraphs.size} parágrafos, ${chunks.size} trechos, " +
                "extração ${(t1 - t0) / 1_000_000} ms, análise ${(t2 - t1) / 1_000_000} ms, título=${extracted.title}")
        }
    }
}
