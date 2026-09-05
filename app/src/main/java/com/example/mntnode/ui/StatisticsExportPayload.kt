package com.example.mntnode.ui

/** Result of building reading statistics for .txt + .pdf export. */
data class StatisticsExportPayload(
    val textForTxtFile: String,
    val pdfDocument: PdfStatisticsDocument,
)

data class PdfStatisticsDocument(
    val segments: List<PdfSegment>,
)

sealed class PdfSegment {
    data class Text(val content: String) : PdfSegment()

    /** Progress 0..1 over wall time (epoch millis). Shown only in PDF. */
    data class ProgressGraph(
        val bookTitle: String,
        val points: List<Pair<Long, Float>>,
    ) : PdfSegment()
}
