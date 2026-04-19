package com.xanderscannell.startinggundetector.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimestampFormatter {
    private val format = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun format(wallMillis: Long): String = format.format(Date(wallMillis))
}
