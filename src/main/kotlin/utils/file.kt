package utils

import java.util.stream.Collectors
import java.util.stream.Stream
import java.util.zip.GZIPInputStream
import kotlin.io.path.inputStream

fun <T> Stream<T>.toMutableList(): MutableList<T> {
    return collect(Collectors.toCollection { mutableListOf() })
}

fun readTrainingAndValidationData(): Pair<List<Array<Double>>, List<Array<Double>>> {
    println("These are the mappings: $INPUT_TO_TRAIN_COL")
    GZIPInputStream(PATH.inputStream()).bufferedReader(Charsets.UTF_8).use { reader ->
        reader.readLine() // skip CSV heading

        var count = 0
        var modCount = 0
        var nonModCount = 0
        val minValues = Array(TrainCol.entries.size) { Double.POSITIVE_INFINITY }
        val maxValues = Array(TrainCol.entries.size) { Double.NEGATIVE_INFINITY }

        // Making sure that each TrainCol will be set.
        val testOut = Array(TrainCol.entries.size) { Double.MAX_VALUE }
        val testIn = MutableList(InputTrainCol.entries.size) { 0.0 }
        createTrainingDataPoint(testOut, testIn)
        assert(testOut.all { it != Double.MAX_VALUE })

        // Now begin reading
        val validationData = reader.lines()
            .map {
                count += 1
                val parts = it.split("\t").map(String::toDouble)
                val res = Array(TrainCol.entries.size) { 0.0 }

                createTrainingDataPoint(res, parts, minValues, maxValues)

                res
            }
            .filter {
                when (MODE) {
                    Mode.PTH_UP_AFTER_SECOND_DOWN -> it[TrainCol.SECOND_RELEASE_TIME] >= it[TrainCol.PTH_RELEASE_TIME] && it[TrainCol.PTH_RELEASE_TIME] < it[TrainCol.THIRD_PRESS_TIME]
                    Mode.PTH_UP_AFTER_SECOND_UP -> it[TrainCol.SECOND_RELEASE_TIME] <= it[TrainCol.PTH_RELEASE_TIME]
                            && it[TrainCol.PTH_RELEASE_TIME] < it[TrainCol.THIRD_PRESS_TIME]

                    Mode.THIRD_DOWN -> it[TrainCol.THIRD_PRESS_TIME] < it[TrainCol.PTH_RELEASE_TIME]
                    else -> true
                }
            }
            .peek {
                if (it[TrainCol.IS_MOD] == 1.0) modCount++ else nonModCount++
            }.toMutableList()

        println("Loaded ${count.toStringWithThousandSeparator()} events, of which ${validationData.size.toStringWithThousandSeparator()} matched the current mode.\n")
        println("Of those, ${modCount.toStringWithThousandSeparator()} are mods and ${nonModCount.toStringWithThousandSeparator()} are not mods.\n")
        println("These are the durations BEFORE filtering and coercing:\n")
        println("# minimums\n${trainColDurationsToString(minValues)}\n")
        println("# maximums\n${trainColDurationsToString(maxValues)}\n\n")
        println("Note that the large value of pth_second_press_to_third_press_dur is simply a result of the fact that the third down time is included, even if it happens after the PTH release.")
        println()

        if (SAMPLE_TRAINING_DATA_PER_PROGRAM || validationData.size <= NUM_TRAINING_DATA) {
            return validationData to validationData
        }

        if (MAKE_MOD_NON_MOD_RATIOS_SAME_IN_SAMPLE) {
            val nmt = NUM_TRAINING_DATA * (nonModCount.toDouble() / validationData.size)
            val mt = NUM_TRAINING_DATA * (modCount.toDouble() / validationData.size)

            val trainingData = validationData.sample(intArrayOf(nmt.toInt(), mt.toInt())) {
                it[TrainCol.IS_MOD].toInt()
            }
            return trainingData to validationData
        }

        val realValidation = validationData.limitToSampleAndGetRemainder(
            validationData.size.coerceAtMost(NUM_TRAINING_DATA)
        )

        val trainingData = validationData // validationData is now only a sample
        return trainingData to realValidation
    }
}


private fun trainColDurationsToString(minValues: Array<Double>): String {
    return TrainCol.entries.filter { it.isDur && !it.isComposite }.joinToString("\n") {
        " - " + it.name.lowercase() + " = " + minValues[it].toIntStringWithThousandSeparator()
    }
}

val INPUT_TO_TRAIN_COL: Map<InputTrainCol, TrainCol> = buildMap {
    val nameToEnum = TrainCol.entries.associateBy { it.name }
    for (inputCol in InputTrainCol.entries) {
        nameToEnum[inputCol.name]?.let { trainCol ->
            put(inputCol, trainCol)
        }
    }
}


private fun createTrainingDataPoint(
    res: Array<Double>,
    parts: List<Double>,
    minValues: Array<Double>? = null,
    maxValues: Array<Double>? = null
) {
    val thDown = parts[InputTrainCol.PTH_PRESS_TIME]
    val nextUp = parts[InputTrainCol.SECOND_RELEASE_TIME]
    val thirdDown = parts[InputTrainCol.THIRD_PRESS_TIME]
    val nextDown = parts[InputTrainCol.SECOND_PRESS_TIME]

    // if you want ALL the training data, uncomment the following
    /*
    res[TrainCol.FAKE_SECOND_IS_TAP_HOLD] = if (parts[InputTrainCol.SECOND_IS_MOD] > 0.5)
        (if (Random.nextBoolean()) 1.0 else 0.0)
    else
        0.0
     */

    res[TrainCol.KEY_RELEASE_BEFORE_PTH_TO_PTH_PRESS_DUR] =
        thDown - parts[InputTrainCol.KEY_RELEASED_BEFORE_PTH_RELEASE_TIME]
    res[TrainCol.PTH_PRESS_TO_SECOND_PRESS_DUR] = nextDown - thDown

    val c = if (MODE == Mode.THIRD_DOWN) thirdDown else parts[InputTrainCol.PTH_RELEASE_TIME]

    if (c < nextUp) {
        // can't know about the release of next as it hasn't happened yet
        res[TrainCol.OPT_NEXT_DUR] = -1.0
        res[TrainCol.OPT_TH_DOWN_NEXT_UP_DUR] = -1.0
    } else {
        res[TrainCol.OPT_NEXT_DUR] = nextUp - nextDown
        res[TrainCol.OPT_TH_DOWN_NEXT_UP_DUR] = nextUp - thDown
    }

    res[TrainCol.PTH_SECOND_PRESS_TO_THIRD_PRESS_DUR] = thirdDown - nextDown

    INPUT_TO_TRAIN_COL.forEach { (inpCol, outCol) ->
        res[outCol] = parts[inpCol]
    }

    for ((i, r) in res.withIndex()) {
        if (minValues != null && maxValues != null) {
            if (r < minValues[i]) {
                minValues[i] = r
            }
            if (r > maxValues[i]) {
                maxValues[i] = r
            }
        }

        if (TrainCol.entries[i].isDur) {
            res[i] = r.coerceIn(-MS_MAX_DUR_FOR_PREDICTION, MS_MAX_DUR_FOR_PREDICTION)
        }
    }

    // This one is after coercing because we need the coerced values to create the correct avg
    res[TrainCol.PTH_PRESS_TO_PRESS_W_AVG] = eExpWeightedAvg(
        res[TrainCol.PTH_PREV_PREV_PRESS_TO_PREV_PRESS_DUR],
        res[TrainCol.PTH_PREV_PRESS_TO_PTH_PRESS_DUR]
    )
    res[TrainCol.PTH_OVERLAP_W_AVG] =
        eExpWeightedAvg(res[TrainCol.PTH_PREV_PREV_OVERLAP_DUR], res[TrainCol.PTH_PREV_OVERLAP_DUR])
}
