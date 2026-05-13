package com.xanderscannell.startinggundetector.device

object DeviceIdProvider {
    fun shortId(deviceId: String): String = deviceId.takeLast(4).uppercase()
}
