package com.example.optireader.covers

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.example.optireader.epub.EpubOpfParser
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

object CoverExtractor {
    fun writePdfCover(pdfFile: File, destJpeg: File, maxSide: Int = 512): Boolean {
        if (!pdfFile.exists()) return false
        return try {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount <= 0) return false
                    renderer.openPage(0).use { page ->
                        val ratio = maxSide.toFloat() / maxOf(page.width, page.height).toFloat()
                        val w = (page.width * ratio).toInt().coerceAtLeast(1)
                        val h = (page.height * ratio).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        FileOutputStream(destJpeg).use { out ->
                            bmp.compress(Bitmap.CompressFormat.JPEG, 88, out)
                        }
                        bmp.recycle()
                    }
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun writeEpubCover(epubFile: File, destFile: File): Boolean {
        if (!epubFile.exists()) return false
        return try {
            ZipFile(epubFile).use { zip ->
                val pkg = EpubOpfParser.readPackage(zip) ?: return false
                val coverPath = pkg.coverHref ?: return false
                EpubOpfParser.extractCoverToFile(zip, coverPath, destFile)
            }
        } catch (_: Exception) {
            false
        }
    }
}
