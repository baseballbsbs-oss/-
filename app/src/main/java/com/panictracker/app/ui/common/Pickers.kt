package com.panictracker.app.ui.common

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.panictracker.app.util.TimeFmt
import java.time.LocalDateTime

/**
 * 날짜·시각을 고치는 칩. 누르면 날짜 선택 → 시각 선택이 이어집니다.
 * 갤럭시 기본 다이얼로그를 그대로 써서 One UI 와 이질감이 없습니다.
 */
@Composable
fun DateTimeChip(
    label: String,
    epochMs: Long,
    onChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    AssistChip(
        modifier = modifier,
        enabled = enabled,
        onClick = {
            val dt = TimeFmt.dateTime(epochMs)
            DatePickerDialog(
                context,
                { _, y, m, d ->
                    TimePickerDialog(
                        context,
                        { _, h, min ->
                            onChange(TimeFmt.epochMs(LocalDateTime.of(y, m + 1, d, h, min)))
                        },
                        dt.hour,
                        dt.minute,
                        true,
                    ).show()
                },
                dt.year,
                dt.monthValue - 1,
                dt.dayOfMonth,
            ).show()
        },
        label = { Text("$label · ${TimeFmt.dateTimeShort(epochMs)}") },
    )
}
