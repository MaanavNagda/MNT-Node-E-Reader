package com.example.mntnode.scan

import java.io.File
import java.util.Locale

object FileSystemScanner {
    private val allowed = setOf("pdf", "epub")

    /** Cap list size so the UI stays responsive and Compose does not OOM. */
    const val MAX_SCAN_RESULTS = 4_000

    data class ScanResult(
        val items: List<DiscoveredSource>,
        val truncated: Boolean,
    )

    /**
     * Recursively finds only `.pdf` and `.epub` files (case-insensitive). Other extensions are skipped.
     */
    fun scanDirectory(
        root: File,
        maxDepth: Int = 50,
        maxResults: Int = MAX_SCAN_RESULTS,
        includeHidden: Boolean = false,
    ): ScanResult {
        if (!root.exists() || !root.canRead()) return ScanResult(emptyList(), false)
        val out = ArrayList<DiscoveredSource>(256)
        val seen = LinkedHashSet<String>()
        var truncated = false
        val walk = root.walkTopDown()
            .maxDepth(maxDepth)
            .onEnter { dir ->
                when {
                    dir == root -> true
                    !includeHidden && dir.name.startsWith(".") -> false
                    else -> true
                }
            }
        for (file in walk) {
            if (out.size >= maxResults) {
                truncated = true
                break
            }
            if (!file.isFile) continue
            if (!includeHidden && file.name.startsWith(".")) continue
            val ext = file.extension.lowercase(Locale.US)
            if (ext !in allowed) continue
            val pathKey = try {
                file.canonicalPath
            } catch (_: Exception) {
                file.absolutePath
            }
            if (!seen.add(pathKey)) continue
            out += DiscoveredSource.LocalFile(file)
        }
        val sorted = out.sortedBy { it.displayName.lowercase(Locale.US) }
        return ScanResult(sorted, truncated)
    }
}
