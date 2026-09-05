package com.example.mntnode.ui

import android.graphics.fonts.FontStyle
import android.graphics.fonts.SystemFonts
import android.os.Build
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.StringReader
import java.util.Locale
import java.util.TreeSet
import kotlin.math.abs

/**
 * Enumerates CSS [font-family] names available on this device: named families from system
 * `fonts.xml` (and related files) plus names derived from [SystemFonts.getAvailableFonts] for
 * fonts not listed there (e.g. user-installed).
 */
object SystemFontFamilies {

    private val fontFileSuffix = Regex(
        "(?i)(-(Regular|Bold|Italic|BoldItalic|Medium|Light|Thin|Black|Heavy|" +
            "SemiBold|SemiBoldItalic|DemiBold|Oblique|Variable|VF))$|" +
            "(_(Regular|Bold|Italic|BoldItalic))$",
    )

    private val systemFontXmlPaths = listOf(
        "/system/etc/fonts.xml",
        "/system_ext/etc/fonts.xml",
        "/vendor/etc/fonts.xml",
        "/product/etc/fonts_customization.xml",
    )

    /** Readium-shipped faces (may not appear in system XML). */
    private val readiumBundledNames = listOf(
        "OpenDyslexic",
        "AccessibleDfA",
        "IA Writer Duospace",
    )

    private val genericFamilies = listOf(
        "serif",
        "sans-serif",
        "monospace",
        "cursive",
        "fantasy",
    )

    /**
     * Returns sorted unique CSS font-family names (no quotes; caller passes them to Readium as-is).
     */
    fun loadCssFontFamilyNames(): List<String> {
        val out = TreeSet<String>(String.CASE_INSENSITIVE_ORDER)
        out.addAll(genericFamilies)
        out.addAll(readiumBundledNames)

        for (path in systemFontXmlPaths) {
            val f = File(path)
            if (!f.isFile || !f.canRead()) continue
            runCatching { f.readText() }
                .onSuccess { xml -> out.addAll(parseFamilyNamesFromFontsXml(xml)) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            out.addAll(namesFromAvailableFontFiles())
        }

        return out.toList()
    }

    private fun parseFamilyNamesFromFontsXml(xml: String): Set<String> =
        runCatching {
            val names = mutableSetOf<String>()
            val parser: XmlPullParser = Xml.newPullParser().apply { setInput(StringReader(xml)) }
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "family") {
                    val n = parser.getAttributeValue(null, "name")
                    if (!n.isNullOrBlank()) names.add(n.trim())
                }
                event = parser.next()
            }
            names
        }.getOrElse { emptySet() }

    private fun namesFromAvailableFontFiles(): Set<String> {
        val fonts = SystemFonts.getAvailableFonts()
        val byKey = mutableMapOf<String, Pair<String, Int>>()

        for (font in fonts) {
            val file = font.file ?: continue
            val style = font.style
            val candidate = baseFamilyNameFromFileName(file.name) ?: continue
            if (candidate.isBlank()) continue
            val key = candidate.lowercase(Locale.US)
            val dist = abs(style.weight - FontStyle.FONT_WEIGHT_NORMAL) +
                if (style.slant == FontStyle.FONT_SLANT_UPRIGHT) 0 else 50
            val prev = byKey[key]
            if (prev == null || dist < prev.second) {
                byKey[key] = candidate to dist
            }
        }
        return byKey.values.map { it.first }.toSet()
    }

    private fun baseFamilyNameFromFileName(fileName: String): String? {
        val noExt = fileName.substringBeforeLast('.')
        if (noExt.isBlank()) return null
        var base = noExt
        var prev: String
        do {
            prev = base
            base = base.replace(fontFileSuffix, "")
        } while (base != prev)
        if (base.isBlank()) return null
        return base.trim()
    }
}
