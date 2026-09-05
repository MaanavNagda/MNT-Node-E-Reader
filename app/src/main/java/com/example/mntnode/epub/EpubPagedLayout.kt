package com.example.mntnode.epub

import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

data class EpubChapterPageCount(val spineIndex: Int, val pageCount: Int)

object EpubColumnLayoutCache {
    private fun dir(ctx: android.content.Context) = File(ctx.cacheDir, "epub_col_layout").apply { mkdirs() }

    private const val LAYOUT_VERSION = 0x0004_0025L

    fun fingerprint(
        epubFile: File,
        widthPx: Int,
        heightPx: Int,
        padTop: Int,
        padLeft: Int,
        padRight: Int,
        padBottom: Int,
    ): Long {
        var fp = epubFile.lastModified() xor (widthPx.toLong() shl 32) xor heightPx.toLong() xor LAYOUT_VERSION
        fp = fp xor (padTop.toLong() shl 16) xor (padLeft.toLong() shl 20) xor (padRight.toLong() shl 24) xor padBottom.toLong()
        return fp
    }

    fun load(context: android.content.Context, bookId: Long, fingerprint: Long): List<EpubChapterPageCount>? {
        val f = File(dir(context), "${bookId}_col_$fingerprint.json")
        if (!f.exists() || f.length() == 0L) return null
        return runCatching {
            val arr = org.json.JSONArray(f.readText())
            val out = ArrayList<EpubChapterPageCount>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONArray(i)
                out.add(EpubChapterPageCount(o.getInt(0), o.getInt(1)))
            }
            out
        }.getOrNull()
    }

    fun save(context: android.content.Context, bookId: Long, fingerprint: Long, chapters: List<EpubChapterPageCount>) {
        runCatching {
            val arr = org.json.JSONArray()
            for (c in chapters) {
                val a = org.json.JSONArray()
                a.put(c.spineIndex)
                a.put(c.pageCount)
                arr.put(a)
            }
            File(dir(context), "${bookId}_col_$fingerprint.json").writeText(arr.toString())
        }
    }
}

/**
 * Multi-column layout on an inner [#mntnode-scroll] with [width: max-content] so columns extend
 * horizontally (one column width = reading pane). Applying columns directly on [body] with viewport
 * width lets two+ columns fit side-by-side and causes overlap when translating — see screenshot.
 *
 * **Units:** [viewportWidthPx], [viewportHeightPx], and padding args must be **WebView CSS pixels**
 * (density-independent, ~1 CSS px = 1 Android dp). Do not pass Compose [toPx] physical pixels —
 * that makes the layout several× too wide and looks zoomed in.
 */
fun epubPaginatedCss(
    viewportWidthPx: Int,
    viewportHeightPx: Int,
    padTopPx: Int,
    padLeftPx: Int,
    padRightPx: Int,
    padBottomPx: Int,
    backgroundCss: String,
    foregroundCss: String,
): String {
    val contentW = (viewportWidthPx - padLeftPx - padRightPx).coerceAtLeast(80)
    val contentH = (viewportHeightPx - padTopPx - padBottomPx).coerceAtLeast(80)
    val bg = backgroundCss
    val fg = foregroundCss
    return """
        html {
            margin: 0 !important;
            padding: 0 !important;
            width: ${viewportWidthPx}px !important;
            max-width: ${viewportWidthPx}px !important;
            height: ${viewportHeightPx}px !important;
            max-height: ${viewportHeightPx}px !important;
            overflow: hidden !important;
            overflow-x: clip !important;
            background: $bg !important;
            box-sizing: border-box !important;
            column-count: 1 !important;
            column-width: auto !important;
            column-gap: normal !important;
            -webkit-column-count: 1 !important;
            -webkit-column-width: auto !important;
            -webkit-column-gap: normal !important;
        }
        body {
            margin: 0 !important;
            padding: 0 !important;
            width: ${viewportWidthPx}px !important;
            max-width: ${viewportWidthPx}px !important;
            height: ${viewportHeightPx}px !important;
            box-sizing: border-box !important;
            background: $bg !important;
            color: $fg !important;
            overflow: hidden !important;
            overflow-x: clip !important;
            overflow-wrap: anywhere !important;
            word-wrap: break-word !important;
            -webkit-text-size-adjust: 100% !important;
            column-count: 1 !important;
            column-width: auto !important;
            column-gap: normal !important;
            column-rule: none !important;
            -webkit-column-count: 1 !important;
            -webkit-column-width: auto !important;
            -webkit-column-gap: normal !important;
        }
        #mntnode-viewport {
            width: ${viewportWidthPx}px !important;
            height: ${viewportHeightPx}px !important;
            overflow-x: hidden !important;
            overflow-y: hidden !important;
            overflow: hidden !important;
            position: relative !important;
            box-sizing: border-box !important;
        }
        #mntnode-frame {
            width: ${viewportWidthPx}px !important;
            height: ${viewportHeightPx}px !important;
            box-sizing: border-box !important;
            padding: ${padTopPx}px ${padRightPx}px ${padBottomPx}px ${padLeftPx}px !important;
        }
        #mntnode-scroll {
            width: max-content !important;
            max-width: none !important;
            height: ${contentH}px !important;
            margin: 0 !important;
            padding: 0 !important;
            box-sizing: border-box !important;
            background: transparent !important;
            color: $fg !important;
            transform-origin: left top !important;
            -webkit-transform-origin: left top !important;
            column-count: auto !important;
            column-width: ${contentW}px !important;
            column-gap: 0px !important;
            column-fill: auto !important;
            -webkit-column-count: auto !important;
            -webkit-column-width: ${contentW}px !important;
            -webkit-column-gap: 0px !important;
            -webkit-column-fill: auto !important;
        }
        #mntnode-scroll * {
            box-sizing: border-box !important;
            column-count: 1 !important;
            column-width: auto !important;
            -webkit-column-count: 1 !important;
            -webkit-column-width: auto !important;
        }
        #mntnode-scroll p, #mntnode-scroll li, #mntnode-scroll blockquote, #mntnode-scroll pre, #mntnode-scroll figure,
        #mntnode-scroll h1, #mntnode-scroll h2, #mntnode-scroll h3, #mntnode-scroll h4, #mntnode-scroll h5, #mntnode-scroll h6 {
            break-inside: avoid !important;
            -webkit-column-break-inside: avoid !important;
            page-break-inside: avoid !important;
            orphans: 2 !important;
            widows: 2 !important;
        }
        #mntnode-scroll img, #mntnode-scroll svg, #mntnode-scroll video, #mntnode-scroll canvas, #mntnode-scroll table {
            break-inside: avoid !important;
            -webkit-column-break-inside: avoid !important;
            page-break-inside: avoid !important;
            max-width: 100% !important;
            max-height: ${contentH}px !important;
            height: auto !important;
        }
    """.trimIndent()
}

/** Paging uses transform on [#mntnode-scroll]; metrics use its scrollWidth and Kotlin [contentWcss]. */
fun epubPageMetricsJs(contentWcss: Int): String = """
    (function() {
        var cw = Math.max(80, $contentWcss);
        var el = document.getElementById('mntnode-scroll');
        if (!el) return JSON.stringify({ pages: 1, scrollW: 0, cw: cw });
        var sw = el.scrollWidth || 0;
        var pages = Math.max(1, Math.ceil(sw / cw - 1e-9));
        return JSON.stringify({ pages: pages, scrollW: sw, cw: cw });
    })();
""".trimIndent()

fun epubScrollToPageJs(pageIndex: Int, contentWcss: Int): String = """
    (function() {
        var cw = Math.max(80, $contentWcss);
        var el = document.getElementById('mntnode-scroll');
        if (!el) return;
        var sw = el.scrollWidth || 0;
        var maxOff = Math.max(0, sw - cw);
        var o = Math.min($pageIndex * cw, maxOff);
        el.style.transform = 'translateX(' + (-o) + 'px)';
        el.style.webkitTransform = 'translateX(' + (-o) + 'px)';
    })();
""".trimIndent()

private fun parseMetrics(raw: String?): Triple<Int, Int, Int> {
    if (raw == null || raw == "null") return Triple(1, 0, 0)
    var s = raw.trim()
    if (s.startsWith("\"") && s.endsWith("\"") && s.length >= 2) {
        s = s.substring(1, s.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
    }
    return runCatching {
        val o = JSONObject(s)
        Triple(
            o.optInt("pages", 1).coerceAtLeast(1),
            o.optInt("scrollW", 0),
            o.optInt("cw", 0),
        )
    }.getOrElse { Triple(1, 0, 0) }
}

fun injectEpubPaginationCss(
    webView: WebView,
    css: String,
    contentWcss: Int,
    viewportWidthCssPx: Int,
    viewportHeightCssPx: Int,
    padTopCss: Int,
    padRightCss: Int,
    padBottomCss: Int,
    padLeftCss: Int,
) {
    val escaped = css.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
    val cw = contentWcss.coerceIn(80, 10000)
    val vw = viewportWidthCssPx.coerceIn(80, 10000)
    val vh = viewportHeightCssPx.coerceIn(80, 20000)
    val pt = padTopCss.coerceIn(0, 500)
    val pr = padRightCss.coerceIn(0, 800)
    val pb = padBottomCss.coerceIn(0, 500)
    val pl = padLeftCss.coerceIn(0, 800)
    val js = """
        (function() {
            var vw = $vw;
            var vh = $vh;
            var cw = $cw;
            var pt = $pt, pr = $pr, pb = $pb, pl = $pl;
            var vp = document.querySelector('meta[name="viewport"]');
            if (!vp) {
                vp = document.createElement('meta');
                vp.setAttribute('name', 'viewport');
                var head = document.head || document.documentElement;
                head.insertBefore(vp, head.firstChild);
            }
            vp.setAttribute('content', 'width=' + vw + ', initial-scale=1.0, minimum-scale=1.0, maximum-scale=1.0, user-scalable=no');
            var body = document.body;
            if (!body) return;
            var old = document.getElementById('mntnode-scroll');
            if (old) {
                while (old.firstChild) body.insertBefore(old.firstChild, old);
                old.remove();
            }
            var oldViewport = document.getElementById('mntnode-viewport');
            if (oldViewport) oldViewport.remove();
            var wrap = document.createElement('div');
            wrap.id = 'mntnode-scroll';
            while (body.firstChild) wrap.appendChild(body.firstChild);
            var viewport = document.createElement('div');
            viewport.id = 'mntnode-viewport';
            viewport.style.width = vw + 'px';
            viewport.style.height = vh + 'px';
            viewport.style.overflow = 'hidden';
            viewport.style.overflowX = 'hidden';
            viewport.style.overflowY = 'hidden';
            var frame = document.createElement('div');
            frame.id = 'mntnode-frame';
            viewport.appendChild(frame);
            frame.appendChild(wrap);
            body.appendChild(viewport);
            var st = document.getElementById('mntnode-pagination');
            if (!st) {
                st = document.createElement('style');
                st.id = 'mntnode-pagination';
                (document.head || document.documentElement).appendChild(st);
            }
            st.textContent = '$escaped';
            body.style.setProperty('padding', '0', 'important');
            body.style.setProperty('box-sizing', 'border-box', 'important');
            body.style.setProperty('margin', '0', 'important');
            wrap.style.transform = 'translateX(0)';
            wrap.style.webkitTransform = 'translateX(0)';
            wrap.style.setProperty('transform-origin', 'left top', 'important');
            wrap.style.setProperty('-webkit-transform-origin', 'left top', 'important');
            wrap.style.setProperty('column-width', cw + 'px', 'important');
            wrap.style.setProperty('-webkit-column-width', cw + 'px', 'important');
            wrap.style.setProperty('column-gap', '0px', 'important');
            wrap.style.setProperty('-webkit-column-gap', '0px', 'important');
        })();
    """.trimIndent()
    webView.evaluateJavascript(js, null)
}

fun scrollEpubToPageIndex(webView: WebView, pageIndex: Int, contentWcss: Int) {
    webView.evaluateJavascript(epubScrollToPageJs(pageIndex, contentWcss), null)
}

fun measurePageCount(webView: WebView, contentWcss: Int, callback: (Int) -> Unit) {
    webView.evaluateJavascript(epubPageMetricsJs(contentWcss)) { raw ->
        val (pages, _, _) = parseMetrics(raw)
        callback(pages)
    }
}

private suspend fun measureOneChapterPages(
    webView: WebView,
    url: String,
    viewportWidthPx: Int,
    viewportHeightPx: Int,
    padTopPx: Int,
    padLeftPx: Int,
    padRightPx: Int,
    padBottomPx: Int,
    backgroundCss: String,
    foregroundCss: String,
    applyTint: (WebView) -> Unit,
): Int = suspendCancellableCoroutine { cont ->
    val done = AtomicBoolean(false)
    val mainHandler = Handler(Looper.getMainLooper())
    fun finish(n: Int) {
        if (done.compareAndSet(false, true)) cont.resume(n.coerceAtLeast(1))
    }
    val timeout = Runnable { finish(1) }
    cont.invokeOnCancellation {
        done.set(true)
        mainHandler.removeCallbacks(timeout)
    }
    mainHandler.postDelayed(timeout, 25_000)

    val css = epubPaginatedCss(
        viewportWidthPx, viewportHeightPx,
        padTopPx, padLeftPx, padRightPx, padBottomPx,
        backgroundCss, foregroundCss,
    )
    val contentWcss = (viewportWidthPx - padLeftPx - padRightPx).coerceAtLeast(80)

    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, loadedUrl: String?) {
            val v = view ?: return finish(1)
            applyTint(v)
            injectEpubPaginationCss(
                v, css, contentWcss, viewportWidthPx, viewportHeightPx,
                padTopPx, padRightPx, padBottomPx, padLeftPx,
            )
            v.postDelayed({
                if (done.get()) return@postDelayed
                measurePageCount(v, contentWcss) { n ->
                    mainHandler.removeCallbacks(timeout)
                    finish(n)
                }
            }, 650)
        }

        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
            if (request == null || !request.isForMainFrame) return
            mainHandler.removeCallbacks(timeout)
            finish(1)
        }
    }
    webView.loadUrl(url)
}

suspend fun measureEpubChapterPageCounts(
    webView: WebView,
    spineUrls: List<String>,
    /** WebView CSS pixels (same units as [epubPaginatedCss]). */
    viewportWidthPx: Int,
    viewportHeightPx: Int,
    padTopPx: Int,
    padLeftPx: Int,
    padRightPx: Int,
    padBottomPx: Int,
    backgroundCss: String,
    foregroundCss: String,
    applyTint: (WebView) -> Unit,
    onChapterProgress: (Int, Int) -> Unit,
): List<EpubChapterPageCount> = withContext(Dispatchers.Main) {
    val out = ArrayList<EpubChapterPageCount>()
    for ((idx, url) in spineUrls.withIndex()) {
        onChapterProgress(idx + 1, spineUrls.size)
        val n = measureOneChapterPages(
            webView, url,
            viewportWidthPx, viewportHeightPx,
            padTopPx, padLeftPx, padRightPx, padBottomPx,
            backgroundCss, foregroundCss, applyTint,
        )
        out.add(EpubChapterPageCount(idx, n))
    }
    out
}
