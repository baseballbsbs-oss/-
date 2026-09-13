package com.panictracker.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 세로 막대 하나에 들어갈 값. 여러 개면 위로 쌓입니다. */
data class BarSegment(val value: Float, val color: Color)

data class BarEntry(val label: String, val segments: List<BarSegment>)

/**
 * 날짜별 막대그래프 — 수면시간·증상 횟수처럼 "하루에 얼마나" 를 보여줍니다.
 * 쌓인 막대 사이에는 배경색 간격을 2 px 두어 경계가 색에만 의존하지 않게 합니다.
 */
@Composable
fun DayBarChart(
    entries: List<BarEntry>,
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
    maxValueOverride: Float? = null,
    /** 참고선 (예: 권장 수면시간 7시간). null 이면 그리지 않습니다. */
    referenceValue: Float? = null,
    referenceLabel: String? = null,
    valueLabel: (Float) -> String = { it.toInt().toString() },
) {
    if (entries.isEmpty()) {
        EmptyChartHint("아직 표시할 기록이 없습니다.")
        return
    }
    val surface = MaterialTheme.colorScheme.surface
    val axis = MaterialTheme.colorScheme.onSurfaceVariant
    val total = entries.map { e -> e.segments.sumOf { it.value.toDouble() }.toFloat() }
    val maxV = (maxValueOverride ?: total.maxOrNull() ?: 1f).coerceAtLeast(1f)

    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val n = entries.size
            val slot = size.width / n
            val barW = (slot * 0.62f).coerceAtMost(28f)
            val gap = 2f
            val corner = CornerRadius(4f, 4f)

            referenceValue?.let { rv ->
                val y = size.height - (rv / maxV) * size.height
                drawLine(axis.copy(alpha = 0.45f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }

            entries.forEachIndexed { i, e ->
                val cx = slot * i + slot / 2f
                var yBottom = size.height
                for (seg in e.segments) {
                    if (seg.value <= 0f) continue
                    val h = (seg.value / maxV) * size.height
                    val top = yBottom - h
                    drawRoundRect(
                        color = seg.color,
                        topLeft = Offset(cx - barW / 2f, top),
                        size = Size(barW, h.coerceAtLeast(2f)),
                        cornerRadius = corner,
                    )
                    // 세그먼트 경계를 배경색으로 끊어 줍니다.
                    if (yBottom < size.height) {
                        drawRect(
                            color = surface,
                            topLeft = Offset(cx - barW / 2f, yBottom - gap),
                            size = Size(barW, gap),
                        )
                    }
                    yBottom = top
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            entries.forEach { e ->
                Text(
                    text = e.label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
            }
        }
        if (referenceLabel != null) {
            Text(
                text = referenceLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * 가로 막대 — 증상 종류별 횟수처럼 항목 수가 적고 이름이 긴 경우에 씁니다.
 * 한 계열뿐이므로 범례 없이 각 막대에 이름과 값을 직접 붙입니다.
 */
@Composable
fun HorizontalBars(
    rows: List<Triple<String, Int, Color>>,
    modifier: Modifier = Modifier,
) {
    if (rows.isEmpty()) {
        EmptyChartHint("아직 기록된 증상이 없습니다.")
        return
    }
    val maxV = rows.maxOf { it.second }.coerceAtLeast(1)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { (label, value, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(92.dp),
                    maxLines = 1,
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(value.toFloat() / maxV)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(color),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "${value}회",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(40.dp),
                )
            }
        }
    }
}

/**
 * 24시간 히트맵 — 하루 중 언제 증상이 잦은지 보여줍니다.
 * 크기(횟수)를 나타내므로 한 가지 색의 밝기 단계만 씁니다(무지개 금지).
 */
@Composable
fun HourHeatmap(
    counts: List<Int>,
    modifier: Modifier = Modifier,
) {
    val maxV = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    val base = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            counts.take(24).forEachIndexed { hour, c ->
                val ratio = c.toFloat() / maxV
                Box(
                    Modifier
                        .weight(1f)
                        .height(28.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (c == 0) empty else base.copy(alpha = 0.25f + 0.75f * ratio)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (c > 0) {
                        Text(
                            "$c",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            color = if (ratio > 0.55f) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth()) {
            (0..23).forEach { h ->
                Text(
                    text = if (h % 3 == 0) "$h" else "",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 색 칩 + 이름 — 색만으로 계열을 구분하지 않도록 그래프에는 항상 붙입니다. */
@Composable
fun LegendChip(color: Color, label: String, trailing: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
        if (trailing != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                trailing,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun EmptyChartHint(text: String, height: Dp = 100.dp) {
    Box(
        Modifier.fillMaxWidth().height(height),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
