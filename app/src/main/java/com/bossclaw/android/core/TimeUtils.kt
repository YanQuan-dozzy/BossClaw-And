package com.bossclaw.android.core

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TimeUtils {
    fun todayStart(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun yesterdayStart(): Long = todayStart() - 24 * 60 * 60 * 1000L

    fun formatTime(timestamp: Long): String =
        SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(timestamp))

    fun formatDate(timestamp: Long): String =
        SimpleDateFormat("MM-dd", Locale.CHINA).format(Date(timestamp))

    fun formatDateTime(timestamp: Long): String =
        SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(timestamp))

    fun dayLabel(timestamp: Long): String {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = timestamp }
        val sameYear = now.get(Calendar.YEAR) == target.get(Calendar.YEAR)
        val sameDay = sameYear && now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
        return when {
            sameDay -> "今天"
            else -> SimpleDateFormat(if (sameYear) "M月d日" else "yyyy年M月d日", Locale.CHINA).format(Date(timestamp))
        }
    }

    fun relative(timestamp: Long): String {
        val delta = System.currentTimeMillis() - timestamp
        return when {
            delta < 60_000L -> "刚刚"
            delta < 3_600_000L -> "${delta / 60_000L} 分钟前"
            delta < 86_400_000L -> "${delta / 3_600_000L} 小时前"
            else -> formatDate(timestamp)
        }
    }
}
