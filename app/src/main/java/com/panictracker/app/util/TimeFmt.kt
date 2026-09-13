package com.panictracker.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 화면에 쓰는 시간 표기를 한 곳에 모았습니다. */
object TimeFmt {

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val korea = Locale.KOREA

    private val hhmm = DateTimeFormatter.ofPattern("HH:mm", korea)
    private val mdHm = DateTimeFormatter.ofPattern("M/d HH:mm", korea)
    private val md = DateTimeFormatter.ofPattern("M/d", korea)
    private val full = DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E) HH:mm", korea)

    fun dateTime(epochMs: Long): LocalDateTime =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDateTime()

    fun date(epochMs: Long): LocalDate = dateTime(epochMs).toLocalDate()

    fun time(epochMs: Long): String = dateTime(epochMs).format(hhmm)

    fun dateTimeShort(epochMs: Long): String = dateTime(epochMs).format(mdHm)

    fun dayShort(epochMs: Long): String = dateTime(epochMs).format(md)

    fun dayShort(date: LocalDate): String = date.format(md)

    fun dateTimeFull(epochMs: Long): String = dateTime(epochMs).format(full)

    fun epochMs(dt: LocalDateTime): Long = dt.atZone(zone).toInstant().toEpochMilli()

    /** 분 단위 하루 시각(0~1439)을 "HH:mm" 으로. */
    fun minuteOfDay(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

    /** 분을 "7시간 20분" 처럼. */
    fun duration(minutes: Int): String {
        if (minutes <= 0) return "0분"
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h > 0 && m > 0 -> "${h}시간 ${m}분"
            h > 0 -> "${h}시간"
            else -> "${m}분"
        }
    }

    /** "3시간 20분 전" / "10분 후" 같은 상대 표기. */
    fun relative(epochMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        val deltaMin = ((epochMs - nowMs) / 60_000L).toInt()
        val abs = kotlin.math.abs(deltaMin)
        val body = when {
            abs < 1 -> "방금"
            abs < 60 -> "${abs}분"
            abs < 60 * 24 -> duration(abs)
            else -> "${abs / (60 * 24)}일"
        }
        return when {
            body == "방금" -> "방금"
            deltaMin < 0 -> "$body 전"
            else -> "$body 후"
        }
    }

    /** 오늘/어제면 시각만, 그 외에는 날짜까지. */
    fun smart(epochMs: Long, todayDate: LocalDate = LocalDate.now()): String {
        val d = date(epochMs)
        return when (d) {
            todayDate -> "오늘 ${time(epochMs)}"
            todayDate.minusDays(1) -> "어제 ${time(epochMs)}"
            else -> dateTimeShort(epochMs)
        }
    }
}
