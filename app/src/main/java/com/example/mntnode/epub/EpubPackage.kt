package com.example.mntnode.epub

data class EpubPackage(
    val title: String,
    /** Dublin Core creators (authors). */
    val creators: List<String> = emptyList(),
    /** Dublin Core subjects (often genre/tags). */
    val subjects: List<String> = emptyList(),
    val opfDir: String,
    val manifestHrefsById: Map<String, String>,
    val spineIds: List<String>,
    val coverHref: String?,
) {
    fun spineHrefs(): List<String> =
        spineIds.mapNotNull { manifestHrefsById[it] }

    fun spinePathsInZip(): List<String> =
        spineHrefs().map { EpubOpfParser.joinZipPath(opfDir, it) }
}
