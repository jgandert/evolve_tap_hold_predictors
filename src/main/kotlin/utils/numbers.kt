package utils

import java.util.*
import kotlin.math.E
import kotlin.math.pow

fun createWeightArray(size: Int): DoubleArray {
    if (size == 1) return doubleArrayOf(1.0)
    var sum = 0.0
    val weights = DoubleArray(size) { index ->
        val weight = E.pow(index)
        sum += weight
        weight
    }

    for (i in weights.indices) {
        weights[i] /= sum
    }
    return weights
}

fun eExpWeightedAvg(v3: Double, v4: Double): Double {
    // a value < 0 should not count towards the average (and v4 is never < 0)
    if (v3 < 0) {
        return v4
    }

    // each weight is the result of E**index divided by the sum of them all
    return 0.2689414213699951 * v3 + 0.7310585786300049 * v4
}

fun Double.toRoundedString(decimals: Int = FMT_DECIMAL_PLACES): String {
    return "%.${decimals}f".format(Locale.US, this)
}

fun Double.toIntStringWithThousandSeparator(): String {
    return "%,.0f".format(Locale.US, this).replace(",", "_")
}

fun Int.toStringWithThousandSeparator(): String {
    return "%,d".format(Locale.US, this).replace(",", "_")
}

private const val FMT_PCT = "%,12d / %,12d (%6.2f %%)"

fun pct(a: Int, total: Int): String {
    return pct(a, total, 100 * a.toDouble() / total)
}

fun pct(a: Long, total: Long): String {
    return pct(a, total, 100 * a.toDouble() / total)
}

fun pct(a: Any, total: Any, percentage: Any): String {
    return String.format(Locale.US, FMT_PCT, a, total, percentage).replace(",", "_")
}
