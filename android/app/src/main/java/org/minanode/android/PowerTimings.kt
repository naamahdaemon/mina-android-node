package org.minanode.android

/** Seconds, persisted independently; defaults retain the original protection windows. */
enum class PowerTiming(val key: String, val defaultSeconds: Int, val range: IntRange) {
    AWAKE("minimum-awake", 120, 10..7200),
    STABLE("stable-sync", 30, 5..600),
    GRACE("production-grace", 300, 0..7200),
    GAP("minimum-production-gap", 900, 0..7200),
    UNFINISHED("unfinished-production", 900, 60..7200),
    SCHEDULE("schedule-freshness", 10, 5..300),
    TIP("maximum-tip-age", 1800, 60..86400),
}

data class PowerTimings(val values: Map<PowerTiming, Int> = emptyMap()) {
    init { values.forEach { (key, value) -> require(value in key.range) } }
    operator fun get(key: PowerTiming): Int = values[key] ?: key.defaultSeconds
    fun ms(key: PowerTiming): Long = get(key) * 1000L
    fun scheduleFresh(now: Long, updated: Long): Boolean = now >= updated && now - updated < ms(PowerTiming.SCHEDULE)
    fun tipFresh(ageSeconds: Long?): Boolean = ageSeconds != null && ageSeconds >= 0 && ageSeconds <= get(PowerTiming.TIP)
}
