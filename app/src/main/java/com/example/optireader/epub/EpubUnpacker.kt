package com.example.optireader.epub

import java.io.File
import java.util.zip.ZipFile

object EpubUnpacker {
    fun ensureUnpacked(epubFile: File, targetDir: File) {
        if (targetDir.exists() && (targetDir.listFiles()?.isNotEmpty() == true)) return
        targetDir.mkdirs()
        val rootCanonical = targetDir.canonicalPath
        ZipFile(epubFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val e = entries.nextElement()
                val out = File(targetDir, e.name)
                val destPath = out.canonicalPath
                if (!destPath.startsWith(rootCanonical)) continue
                if (e.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    zip.getInputStream(e).use { input ->
                        out.outputStream().use { input.copyTo(it) }
                    }
                }
            }
        }
    }
}
