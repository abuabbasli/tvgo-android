package com.mc2soft.ontv.ontvapp.ui

import org.joda.time.DateTime
import org.joda.time.Period
import org.joda.time.format.PeriodFormatterBuilder
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

class TextFormatter(val locale: Locale) {
    companion object {
        private var _inst: TextFormatter? = null

        val inst: TextFormatter
           get() {
               if (_inst == null) {
                   _inst = TextFormatter(Locale.getDefault())
               }
               return _inst!!
           }

        fun updateIfLocalizationChanged() {
            val curLocale = Locale.getDefault()
            if (curLocale != _inst?.locale || _inst == null) {
                _inst = TextFormatter(curLocale)
            }
        }
    }

    val seekTimeFormatter = PeriodFormatterBuilder()
        .printZeroAlways()
        .minimumPrintedDigits(2)
        .appendHours()
        .appendSeparator(":")
        .appendMinutes()
        .appendSeparator(":")
        .appendSeconds()
        .toFormatter()

    fun printTimeAndDuration(timeMs: Long?, durationMs: Long?): String {
        if (durationMs == null || timeMs == null)
            return ""
        if (durationMs < 0 || timeMs < 0)
            return ""
        try {
            return seekTimeFormatter.print(Period(timeMs)) + " / " + seekTimeFormatter.print(Period(durationMs))
        } catch (ex: Exception) {
            Timber.e(ex)
            return ""
        }
    }

    val liveOffsetInSeekTimeFormat = PeriodFormatterBuilder()
        .printZeroAlways()
        .minimumPrintedDigits(2)
        .appendHours()
        .appendSeparator(":")
        .appendMinutes()
        .appendSeparator(":")
        .appendSeconds()
        .toFormatter()

    val liveOffsetTimeFormat = PeriodFormatterBuilder()
        .printZeroAlways()
        .minimumPrintedDigits(2)
        .appendHours()
        .appendSeparator(":")
        .appendMinutes()
        .toFormatter()
    fun printLiveOffset(offset: Long): String {
        try {
            return "- " + liveOffsetTimeFormat.print(Period(offset))
        } catch (ex: Exception) {
            return ""
        }
    }

    fun printLiveOffsetInSeek(offset: Long): String {
        try {
            return "- " + liveOffsetInSeekTimeFormat.print(Period(offset))
        } catch (ex: Exception) {
            return ""
        }
    }

    val programTimeFormat = SimpleDateFormat("HH:mm", locale)

    fun printProgramTime(timeMS: Long): String {
        return programTimeFormat.format(DateTime(timeMS).toDate())
    }

    val globalClockFormat = SimpleDateFormat("EEEE dd MMMM HH:mm", locale)

    fun printClockTime(): String {
        return globalClockFormat.format(DateTime(System.currentTimeMillis()).toDate())
    }

    val programDayTimeFormat = SimpleDateFormat("EEEE dd MMMM", locale)

    fun printProgramDayTime(timeMS: Long): String {
        return programDayTimeFormat.format(DateTime(timeMS).toDate())
    }
}