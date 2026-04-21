package com.xanderscannell.startinggundetector.utils

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object TimestampFormatter {
    private val formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
        .withZone(ZoneId.systemDefault())

    fun format(wallMillis: Long): String = formatter.format(Instant.ofEpochMilli(wallMillis))
}
