package org.minanode.android

import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

object ResourceChartScale {
    fun maximum(values: List<Double?>): Double {
        val raw = (values.filterNotNull().filter { it.isFinite() }.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
        val magnitude = 10.0.pow(floor(log10(raw)))
        return listOf(1.0, 2.0, 5.0, 10.0).first { it * magnitude >= raw } * magnitude
    }

    fun timeFraction(time: Long, start: Long, end: Long): Float =
        if (end <= start) 0.5f else ((time - start).toDouble() / (end - start)).coerceIn(0.0, 1.0).toFloat()
}
