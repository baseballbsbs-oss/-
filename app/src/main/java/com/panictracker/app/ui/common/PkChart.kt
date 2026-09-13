package com.panictracker.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panictracker.app.pk.PkSeries
import com.panictracker.app.util.TimeFmt
import kotlin.math.ceil
import kotlin.math.roundToInt

/** 곡선 위에 표시할 투약 시점 표식. */
data class DoseMarker(
    val atEpochMs: Long,
    val colorArgb: Int,
    val label: String,
)

/** 스크럽(손가락으로 짚은 시각)에서 읽은 값. */
data class ScrubReading(val epochMs: Long, val entries: List<Pair<PkSeries, Double>>)

/**
 * 약물 농도 곡선.
 *
 * x축 = 시간, y축 = 기준 용량 1회 복용 시 최고농도를 100 %로 둔 상대 농도.
 * 여러 약을 겹쳐 그리며, 과거 투약의 잔류 농도가 이미 더해진 곡선입니다.
 * 그래프를 손가락으로 짚으면 그 시각의 값을 읽어 [onScrub] 으로 전달합니다.
 */
@Composable
fun PkChart(
    series: List<PkSeries>,
    doseMarkers: List<DoseMarker>,
    fromMs: Long,
    toMs: Long,
    nowMs: Long,
    modifier: Modifier = Modifier,
    height: Dp = 240.dp,
    onScrub: (ScrubReading?) -> Unit = {},
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    var scrubX by remember { mutableStateOf<Float?>(null) }

    val axisColor = Color(0xFF9AA5AD)
    val gridColor = Color(0x22808A92)
    val nowColor = Color(0xFFD2564F)

    val maxPercent = remember(series) {
        val m = series.maxOfOrNull { it.maxPercent } ?: 0.0
        (ceil(m / 25.0) * 25.0).coerceAtLeast(100.0)
    }

    Box(modifier = modifier.fillMaxWidth().height(height)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                // 누르는 순간부터 떼는 순간까지 손가락 위치를 따라가며 값을 읽습니다.
                // 탭과 드래그를 하나의 루프로 처리해 두 감지기가 서로 이벤트를 뺏지 않게 합니다.
                .pointerInput(series, fromMs, toMs) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        scrubX = down.position.x
                        onScrub(readingAt(down.position.x, size.width, fromMs, toMs, series))
                        var pressed = true
                        while (pressed) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                pressed = false
                            } else {
                                scrubX = change.position.x
                                onScrub(readingAt(change.position.x, size.width, fromMs, toMs, series))
                                change.consume()
                            }
                        }
                        scrubX = null
                        onScrub(null)
                    }
                },
        ) {
            val leftPad = with(density) { 36.dp.toPx() }
            val bottomPad = with(density) { 22.dp.toPx() }
            val topPad = with(density) { 10.dp.toPx() }
            val plotW = size.width - leftPad
            val plotH = size.height - bottomPad - topPad

            fun xOf(ms: Long): Float =
                leftPad + ((ms - fromMs).toFloat() / (toMs - fromMs).toFloat()) * plotW

            fun yOf(pct: Double): Float =
                topPad + plotH - (pct / maxPercent).toFloat() * plotH

            val labelStyle = TextStyle(fontSize = 9.sp, color = axisColor)

            // ── y축 눈금 ──────────────────────────────────────────────────
            val ySteps = 4
            for (i in 0..ySteps) {
                val pct = maxPercent * i / ySteps
                val y = yOf(pct)
                drawLine(gridColor, Offset(leftPad, y), Offset(size.width, y), strokeWidth = 1f)
                drawAxisLabel(measurer, "${pct.roundToInt()}%", 0f, y - 6f, labelStyle)
            }

            // ── x축 눈금 ──────────────────────────────────────────────────
            val spanHours = (toMs - fromMs) / 3_600_000.0
            val stepHours = when {
                spanHours <= 8 -> 1
                spanHours <= 26 -> 3
                spanHours <= 50 -> 6
                spanHours <= 24 * 8 -> 24
                else -> 48
            }
            val stepMs = stepHours * 3_600_000L
            var tick = fromMs - (fromMs % stepMs) + stepMs
            while (tick < toMs) {
                val x = xOf(tick)
                drawLine(gridColor, Offset(x, topPad), Offset(x, topPad + plotH), strokeWidth = 1f)
                val label = if (stepHours >= 24) TimeFmt.dayShort(tick) else TimeFmt.time(tick)
                drawAxisLabel(measurer, label, x - 14f, topPad + plotH + 4f, labelStyle)
                tick += stepMs
            }

            // ── 투약 시점 표식 ─────────────────────────────────────────────
            for (m in doseMarkers) {
                if (m.atEpochMs < fromMs || m.atEpochMs > toMs) continue
                val x = xOf(m.atEpochMs)
                drawLine(
                    Color(m.colorArgb).copy(alpha = 0.45f),
                    Offset(x, topPad + plotH),
                    Offset(x, topPad + plotH - 12f),
                    strokeWidth = 3f,
                )
                drawCircle(Color(m.colorArgb), radius = 4f, center = Offset(x, topPad + plotH - 14f))
            }

            // ── 농도 곡선 ────────────────────────────────────────────────
            for (s in series) {
                if (s.points.isEmpty()) continue
                val color = Color(s.medication.colorArgb)
                val line = Path()
                val area = Path()
                var started = false
                for (p in s.points) {
                    val x = xOf(p.epochMs)
                    val y = yOf(p.percent)
                    if (!started) {
                        line.moveTo(x, y)
                        area.moveTo(x, topPad + plotH)
                        area.lineTo(x, y)
                        started = true
                    } else {
                        line.lineTo(x, y)
                        area.lineTo(x, y)
                    }
                }
                area.lineTo(xOf(s.points.last().epochMs), topPad + plotH)
                area.close()
                drawPath(area, color.copy(alpha = 0.12f))
                drawPath(line, color, style = Stroke(width = 3f))
            }

            // ── 현재 시각 ────────────────────────────────────────────────
            if (nowMs in fromMs..toMs) {
                val x = xOf(nowMs)
                drawLine(
                    nowColor.copy(alpha = 0.8f),
                    Offset(x, topPad),
                    Offset(x, topPad + plotH),
                    strokeWidth = 2f,
                )
                drawAxisLabel(measurer, "지금", x - 10f, topPad - 2f, labelStyle.copy(color = nowColor))
            }

            // ── 스크럽 선 ────────────────────────────────────────────────
            scrubX?.let { sx ->
                val clamped = sx.coerceIn(leftPad, size.width)
                drawLine(
                    axisColor,
                    Offset(clamped, topPad),
                    Offset(clamped, topPad + plotH),
                    strokeWidth = 1.5f,
                )
                val ms = fromMs + ((clamped - leftPad) / plotW * (toMs - fromMs)).toLong()
                for (s in series) {
                    val pct = valueAt(s, ms) ?: continue
                    drawCircle(
                        Color(s.medication.colorArgb),
                        radius = 5f,
                        center = Offset(clamped, yOf(pct)),
                    )
                }
            }

            // 축 선
            drawLine(axisColor, Offset(leftPad, topPad), Offset(leftPad, topPad + plotH), strokeWidth = 1.5f)
            drawLine(
                axisColor,
                Offset(leftPad, topPad + plotH),
                Offset(size.width, topPad + plotH),
                strokeWidth = 1.5f,
            )
        }
    }
}

private fun DrawScope.drawAxisLabel(
    measurer: TextMeasurer,
    text: String,
    x: Float,
    y: Float,
    style: TextStyle,
) {
    drawText(measurer, text, topLeft = Offset(x, y), style = style)
}

/** 곡선을 선형보간하여 임의 시각의 값을 읽습니다. */
internal fun valueAt(s: PkSeries, ms: Long): Double? {
    val pts = s.points
    if (pts.isEmpty()) return null
    if (ms <= pts.first().epochMs) return pts.first().percent
    if (ms >= pts.last().epochMs) return pts.last().percent
    var lo = 0
    var hi = pts.lastIndex
    while (lo + 1 < hi) {
        val mid = (lo + hi) / 2
        if (pts[mid].epochMs <= ms) lo = mid else hi = mid
    }
    val a = pts[lo]
    val b = pts[hi]
    if (b.epochMs == a.epochMs) return a.percent
    val f = (ms - a.epochMs).toDouble() / (b.epochMs - a.epochMs).toDouble()
    return a.percent + (b.percent - a.percent) * f
}

private fun readingAt(
    x: Float,
    widthPx: Int,
    fromMs: Long,
    toMs: Long,
    series: List<PkSeries>,
): ScrubReading? {
    if (widthPx <= 0 || series.isEmpty()) return null
    val f = (x / widthPx).coerceIn(0f, 1f)
    val ms = fromMs + ((toMs - fromMs) * f).toLong()
    return ScrubReading(ms, series.mapNotNull { s -> valueAt(s, ms)?.let { s to it } })
}
