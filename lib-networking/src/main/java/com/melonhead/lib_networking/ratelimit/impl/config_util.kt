package com.melonhead.lib_networking.ratelimit.impl

import com.melonhead.lib_networking.ratelimit.core.RateInfo
import kotlin.time.DurationUnit

internal class MultiRateBuilder {
    private val rates = ArrayList<RateInfo>()

    private fun add(rate: RateInfo) {
        rates.add(rate)
    }

    fun add(
        permits: Int,
        period: Int,
        unit: DurationUnit,
    ) = add(RateInfo(permits, period, unit))

    internal fun toRates() = rates.toList()
}
