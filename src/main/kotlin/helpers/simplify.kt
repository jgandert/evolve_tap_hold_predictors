@file:Suppress("UnusedImport")

package helpers

import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

fun sd(a: Double, b: Double): Double {
    if (b == 0.0) return a
    return a / b
}

fun neg(a: Double): Double {
    return -a
}

/**
 * Just for making it easy, to (bruteforce) verify that a function you simplified is still the same.
 */
fun main() {
    for (i in 0..100_000) {
        val pth_press_to_second_press_dur = nextMsPositiveOrZero()
        val opt_th_down_next_up_dur = nextMsPositiveOrZero()
        val prev_up_th_down_dur = nextMs()
        val prev_dur = nextMsPositiveOrZero()
        val key_release_before_pth_to_pth_press_dur = nextMsPositiveOrZero()
        val opt_next_dur = nextMsPositiveOrZero()
        val pth_second_press_to_third_press_dur = nextMsPositiveOrZero()
        //val key_pressed_before_pth_is_mod = if (Random.nextBoolean()) 0.0 else 1.0
        //val ov1 = nextMaxDurOrNegOne()
        //val ov2 = nextMaxDurOrNegOne(ov1)
        val pth_prev_prev_overlap_dur = nextMaxDurOrNegOne()
        val pth_prev_overlap_dur = nextMaxDurOrNegOne(pth_prev_prev_overlap_dur)
        //val pp1 = nextMaxDurOrNegOne()
        //val pp2 = nextMaxDurOrNegOne(pp1)
        val pth_prev_prev_press_to_prev_press_dur = nextMaxDurOrNegOne()
        val pth_prev_press_to_pth_press_dur =
            nextMaxDurOrNegOne(pth_prev_prev_press_to_prev_press_dur)
        val ov_w_avg = wavg(pth_prev_prev_overlap_dur, pth_prev_overlap_dur)
        val pth_press_to_press_w_avg =
            wavg(pth_prev_prev_press_to_prev_press_dur, pth_prev_press_to_pth_press_dur)

        val original = abs(
            max(
                sd(
                    20145.72453837935,
                    sd(
                        20145.72453837935 - (pth_prev_press_to_pth_press_dur - pth_prev_prev_overlap_dur) * pth_press_to_second_press_dur,
                        pth_press_to_second_press_dur
                    )
                ),
                sd(
                    20141.63979839019 - ((pth_prev_press_to_pth_press_dur - pth_prev_prev_overlap_dur) - pth_prev_prev_overlap_dur) * 10.24699665838974,
                    pth_press_to_second_press_dur
                ) - 32.559018051648636
            )
        )

        // for positive c: max(a, b)/c == max(a/c, b/c)
        // (a/b) / c == a / (b*c)
        // a / (b/c) == (a*c) / b
        // (a/b) / (c/d) == (a*d) / (b*c)

        val new = abs(max(sd(20145.72453837935 * pth_press_to_second_press_dur, 20145.72453837935 - (pth_prev_press_to_pth_press_dur - pth_prev_prev_overlap_dur)*pth_press_to_second_press_dur), sd(20141.63979839019 - ((pth_prev_press_to_pth_press_dur - pth_prev_prev_overlap_dur) - pth_prev_prev_overlap_dur)*10.24699665838974, pth_press_to_second_press_dur) - 32.559018051648636))

        assert(abs(original - new) < 0.00001) { "$original $new differ" }
    }
}

fun wavg(v3: Double, v4: Double): Double {
    // a value < 0 should not count towards the average (and v4 is never < 0)
    if (v3 < 0) {
        return v4
    }

    // each weight is the result of E**index divided by the sum of them all
    return 0.2689414213699951 * v3 + 0.7310585786300049 * v4
}

fun wavg(v1: Double, v2: Double, v3: Double, v4: Double): Double {
    // result of E**index divided by sum of them all
    return 0.03205860328008499 * v1 + 0.08714431874203257 * v2 + 0.23688281808991013 * v3 + 0.6439142598879722 * v4
}

fun nextMaxDurOrNegOne(prev: Double? = null): Double {
    if (prev != null && prev == -1.0) {
        return if (Random.nextBits(2) == 0) -1.0 else Random.nextDouble(until = 8_192.0)
    }
    return if (Random.nextBits(4) == 0) -1.0 else Random.nextDouble(until = 8_192.0)
}

fun nextMs(): Double {
    return if (Random.nextInt(200) == 0) 0.0 else Random.nextDouble(-10_000.0, 10_000.0)
}

fun nextMsPositiveOrZero(): Double {
    return if (Random.nextInt(200) == 0) 0.0 else Random.nextDouble(0.0, 10_000.0)
}

fun expandedFormularToCompact(x: String): String {
    var xPrev = x.replace("\n", "")
    var xNew = xPrev
    while (true) {
        xNew = xNew.replace("  ", " ")
            .replace("( ", "(")
            .replace(") )", "))")
        if (xNew == xPrev) return xNew
        xPrev = xNew
    }
}