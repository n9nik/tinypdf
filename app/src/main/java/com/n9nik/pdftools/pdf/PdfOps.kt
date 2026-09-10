package com.n9nik.pdftools.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import java.io.IOException
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Offline PDF operations backed by PDFBox-Android (Apache 2.0).
 * Never attempts to crack owner-password encryption: encrypted documents only
 * open with a user-supplied password, otherwise a friendly error is returned.
 */
object PdfOps {

    class PdfException(message: String, cause: Throwable? = null) : IOException(message, cause)

    data class CompressStats(val imagesRecompressed: Int, val pages: Int)

    private fun memUsage() = MemoryUsageSetting.setupTempFileOnly()

    private fun open(context: Context, uri: Uri, password: String?): PDDocument {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw PdfException("Could not open file.")
        // NOTE: the stream is intentionally not closed here; PDDocument takes ownership
        // of scratch-backed streams and closes them with the document.
        return try {
            if (password.isNullOrEmpty()) PDDocument.load(input, memUsage())
            else PDDocument.load(input, password, memUsage())
        } catch (e: Exception) {
            runCatching { input.close() }
            throw asPdfException(e)
        }
    }

    private fun asPdfException(e: Exception): PdfException {
        val name = e.javaClass.simpleName
        return when {
            name.contains("InvalidPassword", ignoreCase = true) ->
                PdfException("This PDF is encrypted. Enter its password to continue.")
            e is PdfException -> e
            else -> PdfException(e.message ?: "Could not read this PDF.", e)
        }
    }

    fun pageCount(context: Context, uri: Uri, password: String?): Int {
        open(context, uri, password).use { return it.numberOfPages }
    }

    /** Merge several PDFs into [out], preserving input order. Streams: never holds whole docs in RAM. */
    fun merge(context: Context, uris: List<Uri>, password: String?, out: OutputStream) {
        require(uris.size >= 2) { "Pick at least two PDFs to merge." }
        val merger = PDFMergerUtility()
        val streams = mutableListOf<java.io.InputStream>()
        try {
            for (uri in uris) {
                val s = context.contentResolver.openInputStream(uri)
                    ?: throw PdfException("Could not open a file.")
                streams.add(s)
                merger.addSource(s)
            }
            merger.destinationStream = out
            try {
                merger.mergeDocuments(memUsage())
            } catch (e: Exception) {
                throw asPdfException(e)
            }
        } finally {
            streams.forEach { runCatching { it.close() } }
        }
    }

    /**
     * Split [uri] into one PDF per range. Each part is written through [partSink],
     * which receives the 0-based part index and a suggested file name.
     */
    fun split(
        context: Context,
        uri: Uri,
        password: String?,
        ranges: List<IntRange>,
        partSink: (partIndex: Int, suggestedName: String) -> OutputStream,
    ) {
        require(ranges.isNotEmpty()) { "Enter at least one page range." }
        open(context, uri, password).use { src ->
            ranges.forEachIndexed { index, range ->
                PDDocument().use { dst ->
                    for (pageIndex in range) {
                        dst.importPage(src.getPage(pageIndex))
                    }
                    partSink(index, "part-${index + 1}.pdf").use { out ->
                        try {
                            dst.save(out)
                        } catch (e: Exception) {
                            throw asPdfException(e)
                        }
                    }
                }
            }
        }
    }

    /**
     * Re-encode embedded images: downscale images larger than [maxDim] and
     * re-encode as JPEG at [quality] (0..1). Skips tiny icons, masks and
     * already-efficient fax/JBIG2 images so quality never gets worse.
     */
    fun compress(
        context: Context,
        uri: Uri,
        password: String?,
        maxDim: Int,
        quality: Float,
        out: OutputStream,
    ): CompressStats {
        var recompressed = 0
        open(context, uri, password).use { doc ->
            for (page in doc.pages) {
                recompressed += recompressResources(doc, page.resources, maxDim, quality)
            }
            try {
                doc.save(out)
            } catch (e: Exception) {
                throw asPdfException(e)
            }
            return CompressStats(recompressed, doc.numberOfPages)
        }
    }

    private fun recompressResources(
        doc: PDDocument,
        resources: PDResources?,
        maxDim: Int,
        quality: Float,
    ): Int {
        if (resources == null) return 0
        var count = 0
        // Snapshot names first: we replace entries while iterating.
        val names = resources.xObjectNames.toList()
        for (name in names) {
            val xobj = runCatching { resources.getXObject(name) }.getOrNull() ?: continue
            when (xobj) {
                is PDFormXObject -> count += recompressResources(doc, xobj.resources, maxDim, quality)
                is PDImageXObject -> if (recompressImage(doc, resources, name, xobj, maxDim, quality)) count++
            }
        }
        return count
    }

    private fun recompressImage(
        doc: PDDocument,
        resources: PDResources,
        name: COSName,
        image: PDImageXObject,
        maxDim: Int,
        quality: Float,
    ): Boolean {
        return try {
            // Never touch images with transparency masks or fax/JBIG2 (already tiny).
            if (image.mask != null) return false
            val suffix = runCatching { image.suffix }.getOrNull().orEmpty().lowercase()
            if (suffix == "jb2" || suffix == "ccitt") return false

            val bmp: Bitmap = image.image ?: return false
            val w = bmp.width
            val h = bmp.height
            if (w < 64 || h < 64) return false // icons, patterns, glyphs
            val scale = if (max(w, h) > maxDim) maxDim.toFloat() / max(w, h) else 1f
            if (scale >= 1f && quality >= 0.99f) return false

            val sw = max(1, (w * scale).toInt())
            val sh = max(1, (h * scale).toInt())
            val scaled = Bitmap.createScaledBitmap(bmp, sw, sh, true)
            val jpeg = JPEGFactory.createFromImage(doc, scaled, quality.coerceIn(0.05f, 1f))
            resources.put(name, jpeg)
            if (scaled !== bmp) scaled.recycle()
            true
        } catch (e: Exception) {
            false // a single stubborn image must never fail the whole job
        }
    }

    /** Draw [text] diagonally across every page at [opacity] (0..1). */
    fun watermark(
        context: Context,
        uri: Uri,
        password: String?,
        text: String,
        opacity: Float,
        out: OutputStream,
    ) {
        require(text.isNotBlank()) { "Enter watermark text." }
        open(context, uri, password).use { doc ->
            val font = PDType1Font.HELVETICA_BOLD
            val fontSize = 54f
            val clean = text.trim().take(64)
            val stringWidth: Float = try {
                font.getStringWidth(clean) / 1000f * fontSize
            } catch (e: Exception) {
                throw PdfException("Watermark text must use basic Latin characters.", e)
            }
            try {
                for (page in doc.pages) {
                    val box = page.mediaBox
                    val cx = box.width / 2f
                    val cy = box.height / 2f
                    val gs = PDExtendedGraphicsState()
                    gs.nonStrokingAlphaConstant = opacity.coerceIn(0.05f, 0.9f)
                    PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                        cs.setGraphicsStateParameters(gs)
                        cs.setNonStrokingColor(0.45f) // medium gray
                        cs.setFont(font, fontSize)
                        cs.beginText()
                        val m = Matrix.getRotateInstance(Math.toRadians(-45.0), cx, cy)
                        m.translate(-stringWidth / 2f, -fontSize * 0.35f)
                        cs.setTextMatrix(m)
                        cs.showText(clean)
                        cs.endText()
                    }
                }
                doc.save(out)
            } catch (e: PdfException) {
                throw e
            } catch (e: Exception) {
                throw asPdfException(e)
            }
        }
    }

    /**
     * Parse "1-3, 5, 7-9" (1-based, inclusive) into 0-based ranges,
     * validated against [pageCount].
     */
    fun parseRanges(input: String, pageCount: Int): List<IntRange> {
        val out = mutableListOf<IntRange>()
        for (raw in input.split(',')) {
            val token = raw.trim()
            if (token.isEmpty()) continue
            val range = if ('-' in token) {
                val parts = token.split('-', limit = 2)
                val a = parts.getOrNull(0)?.trim()?.toIntOrNull()
                val b = parts.getOrNull(1)?.trim()?.toIntOrNull()
                if (a == null || b == null) throw IllegalArgumentException("Could not read \"$token\". Use like 1-3, 5.")
                min(a, b)..max(a, b)
            } else {
                val p = token.toIntOrNull()
                    ?: throw IllegalArgumentException("Could not read \"$token\". Use like 1-3, 5.")
                p..p
            }
            if (range.first < 1 || range.last > pageCount) {
                throw IllegalArgumentException("Pages must be between 1 and $pageCount.")
            }
            out.add(range.first - 1..range.last - 1)
        }
        if (out.isEmpty()) throw IllegalArgumentException("Enter at least one page range, e.g. 1-3, 5.")
        return out
    }
}
