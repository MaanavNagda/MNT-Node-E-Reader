package com.example.optireader.epub

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

object EpubOpfParser {
    fun readPackage(zip: ZipFile): EpubPackage? {
        val container = zip.getEntry("META-INF/container.xml") ?: return null
        val opfPath = zip.getInputStream(container).use { parseContainerRootfile(it) } ?: return null
        val opfEntry = zip.getEntry(opfPath) ?: return null
        val opfDir = opfPath.substringBeforeLast('/', "")
        return zip.getInputStream(opfEntry).use { parseOpf(it, opfDir) }
    }

    private fun parseContainerRootfile(input: InputStream): String? {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(input, null)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG &&
                parser.name == "rootfile" &&
                parser.getAttributeValue(null, "media-type") == "application/oebps-package+xml"
            ) {
                return parser.getAttributeValue(null, "full-path")
            }
            event = parser.next()
        }
        return null
    }

    private fun parseOpf(input: InputStream, opfDir: String): EpubPackage {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(input, null)

        var title = "Book"
        val manifest = linkedMapOf<String, String>()
        val spine = ArrayList<String>()
        var coverId: String? = null
        var coverHrefDirect: String? = null

        var event = parser.eventType
        var inMetadata = false
        var inManifest = false
        var inSpine = false
        var pendingDcTitle = false
        var pendingDcCreator = false
        var pendingDcSubject = false
        val creators = mutableListOf<String>()
        val subjects = mutableListOf<String>()

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name
                    val local = name.substringAfter(':')
                    when {
                        name == "metadata" -> inMetadata = true
                        name == "manifest" -> inManifest = true
                        name == "spine" -> inSpine = true
                        inMetadata && name == "dc:title" -> pendingDcTitle = true
                        inMetadata && (name == "dc:creator" || local == "creator") -> pendingDcCreator = true
                        inMetadata && (name == "dc:subject" || local == "subject") -> pendingDcSubject = true
                        inMetadata && name == "meta" -> {
                            val metaName = parser.getAttributeValue(null, "name")
                            val prop = parser.getAttributeValue(null, "property")
                            val content = parser.getAttributeValue(null, "content")
                            if (metaName == "cover" && content != null) coverId = content
                            if (prop == "dcterms:modified") { /* ignore */ }
                            if (prop == "cover-image" || prop?.contains("cover-image") == true) {
                                val id = parser.getAttributeValue(null, "id")
                                if (id != null) coverId = id
                            }
                        }
                        inManifest && name == "item" -> {
                            val id = parser.getAttributeValue(null, "id")
                            val href = parser.getAttributeValue(null, "href")
                            if (id != null && href != null) {
                                val props = parser.getAttributeValue(null, "properties") ?: ""
                                manifest[id] = href
                                if (props.contains("cover-image")) {
                                    coverHrefDirect = joinZipPath(opfDir, href)
                                }
                            }
                        }
                        inSpine && name == "itemref" -> {
                            val idref = parser.getAttributeValue(null, "idref")
                            if (idref != null) spine += idref
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (pendingDcTitle) {
                        val t = parser.text?.trim().orEmpty()
                        if (t.isNotEmpty()) title = t
                    }
                    if (pendingDcCreator) {
                        val t = parser.text?.trim().orEmpty()
                        if (t.isNotEmpty()) creators += t
                    }
                    if (pendingDcSubject) {
                        val t = parser.text?.trim().orEmpty()
                        if (t.isNotEmpty()) subjects += t
                    }
                }
                XmlPullParser.END_TAG -> {
                    val name = parser.name
                    val local = name.substringAfter(':')
                    when {
                        name == "metadata" -> inMetadata = false
                        name == "manifest" -> inManifest = false
                        name == "spine" -> inSpine = false
                        name == "dc:title" -> pendingDcTitle = false
                        name == "dc:creator" || local == "creator" -> pendingDcCreator = false
                        name == "dc:subject" || local == "subject" -> pendingDcSubject = false
                    }
                }
            }
            event = parser.next()
        }

        val coverFromId = coverId?.let { id ->
            manifest[id]?.let { joinZipPath(opfDir, it) }
        }
        val coverHref = coverHrefDirect ?: coverFromId

        return EpubPackage(
            title = title,
            creators = creators.distinct(),
            subjects = subjects.distinct(),
            opfDir = opfDir,
            manifestHrefsById = manifest,
            spineIds = spine,
            coverHref = coverHref,
        )
    }

    fun joinZipPath(opfDir: String, href: String): String {
        if (opfDir.isEmpty()) return href.replace('\\', '/')
        val base = opfDir.trimEnd('/') + "/"
        return (base + href).replace("//", "/").replace('\\', '/')
    }

    fun extractCoverToFile(zip: ZipFile, coverEntryPath: String, dest: java.io.File): Boolean {
        val entry: ZipEntry = zip.getEntry(coverEntryPath) ?: return false
        dest.outputStream().use { out ->
            zip.getInputStream(entry).use { it.copyTo(out) }
        }
        return true
    }
}
