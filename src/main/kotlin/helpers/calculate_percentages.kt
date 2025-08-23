package helpers

import utils.Mode
import utils.pct
import java.text.NumberFormat
import java.util.*

val BLOCK_REGEX = Regex(
    """Mod:\s*([\d,_]+)\s*/\s*([\d,_]+)[\s\S]*?""" +
            """Non-mod:\s*([\d,_]+)\s*/\s*([\d,_]+)[\s\S]*?""" +
            """\s*Mode\.([A-Z0-9_]+)""",
    RegexOption.MULTILINE
)

data class Record(
    val mode: Mode?,
    val modCorrect: Long,
    val modTotal: Long,
    val nonModCorrect: Long,
    val nonModTotal: Long
) {
    val totalCorrect: Long
        get() = modCorrect + nonModCorrect

    val total: Long
        get() = modTotal + nonModTotal
}

fun parseNum(s: String): Long {
    return NumberFormat.getNumberInstance(Locale.US).parse(
        s.replace(
            "_", ""
        )
    ).toLong()
}

fun printRecord(rec: Record) {
    val modeLabel = rec.mode?.name ?: "<AGGREGATE>"
    println("Mode: $modeLabel")
    println("  Mod:     ${pct(rec.modCorrect, rec.modTotal)}")
    println("  Non-mod: ${pct(rec.nonModCorrect, rec.nonModTotal)}")
    println("  Total:   ${pct(rec.totalCorrect, rec.total)}")
    println()
}

fun main() {
    val input = """
/**
 * Auto-generated decision tree prediction function.
 * At most 7 comparisons are necessary to get a result.
 *
 * Mod:     50,599 / 68,121 (74.28 %)
 * Non-mod: 306,692 / 310,294 (98.84 %)
 * Total:   357,291 / 378,415 (94.42 %)
 *
 * @return float predicted overlap time in ms.
 */
float pth_default_get_hold_prediction_when_third_press(void) {
Mode.THIRD_DOWN


/**
 * Auto-generated decision tree prediction function.
 * At most 7 comparisons are necessary to get a result.
 *
 *   Mod:       741,259 /  1,057,871 (70.07 %)
 *   Non-mod: 9,162,154 /  9,190,163 (99.70 %)
 *   Total:   9,903,413 / 10,248,034 (96.64 %)
 *
 * @return float prediction value in [0, 1]. > 0.5f is considered hold.
 */
float pth_default_get_hold_prediction_when_pth_release_after_second_press(void) {
Mode.PTH_UP_AFTER_SECOND_DOWN


/**
 * Auto-generated decision tree prediction function.
 * At most 7 comparisons are necessary to get a result.
 *
 *   Mod:     420,158 / 435,604 (96.45 %)
 *   Non-mod:  60,870 /  85,031 (71.59 %)
 *   Total:   481,028 / 520,635 (92.39 %)
 *
 * @return float prediction value in [0, 1]. > 0.5f is considered hold.
 */
float pth_default_get_hold_prediction_when_pth_release_after_second_release(void) {
Mode.PTH_UP_AFTER_SECOND_UP

    """.trimIndent()

    // Extract a list of all matches in order
    val allRecords = BLOCK_REGEX.findAll(input).map { m ->
        val (modC, modT, nonC, nonT, modeStr) = m.destructured
        Record(
            mode = Mode.valueOf(modeStr),
            modCorrect = parseNum(modC),
            modTotal = parseNum(modT),
            nonModCorrect = parseNum(nonC),
            nonModTotal = parseNum(nonT)
        )
    }.toList()

    for (record in allRecords) {
        printRecord(record)
    }
    println()
    println()

    // Retain only the last occurrence for each mode
    val lastByMode = allRecords
        .groupBy { it.mode }
        .mapValues { it.value.last() }
        .values

    val aggregated = lastByMode
        .fold(Record(null, 0, 0, 0, 0)) { acc, r ->
            Record(
                mode = null,
                modCorrect = acc.modCorrect + r.modCorrect,
                modTotal = acc.modTotal + r.modTotal,
                nonModCorrect = acc.nonModCorrect + r.nonModCorrect,
                nonModTotal = acc.nonModTotal + r.nonModTotal
            )
        }

    printRecord(aggregated)
}
