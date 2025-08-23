import codegen.generateTernaryC
import smile.base.mlp.Layer
import smile.base.mlp.OutputFunction
import smile.classification.LogisticRegression
import smile.classification.logit
import smile.classification.mlp
import smile.data.DataFrame
import smile.data.Row
import smile.data.formula.Formula
import smile.data.vector.DoubleVector
import smile.data.vector.IntVector
import smile.regression.LinearModel
import smile.regression.OLS
import smile.util.function.TimeFunction
import utils.*
import utils.smile.shuffle
import kotlin.math.min
import smile.classification.cart as ccart
import smile.regression.cart as rcart


val VALID_COLS = TrainCol.entries
    .filter { MODE in it.mayUseInModes }
    .map { it.name }
    .toTypedArray()

val ALL_COLS = TrainCol.entries.map { it.name }.toTypedArray()

fun main() {
    val data = getData()

    if (MODE == Mode.OVERLAP_MS_FOR_HOLD) {
        //testOverlapRegressionTree(data)
    }

    val trainData = DataFrame.of(data, *ALL_COLS)!!

    val isModValues = setIsModColToIntAndGetView(trainData)

    val formula = Formula.of(TrainCol.IS_MOD.name, *VALID_COLS)!!
    //testMLP(data)
    //testLogReg(data, isModValues)
    //testOLS(formula, trainData, isModValues)

    if (MODE != Mode.OVERLAP_MS_FOR_HOLD) {
        //testRegressionTree(trainData, isModValues, formula)

        // repeat to find the best one
        repeat(30) {
            testDecisionTree(trainData, isModValues, formula)

            // isModValues does not need to be shuffled, because it's the
            // underlying array of the IS_MOD column and thus will already have
            // been shuffled.
            trainData.shuffle()
        }
    }

    println("\n-----------------------------------\n")

    if (MODE == Mode.THIRD_DOWN) {
        println("# Test previously generated code (not the above)")
        val result = trainData.stream().mapToInt { row: Row ->
            (pth_default_get_hold_prediction_when_third_press(row) >= 0.5).toInt()
        }.toArray()
        printPerformance(isModValues, result)
    }
}

private fun setIsModColToIntAndGetView(trainData: DataFrame): IntArray {
    val isModValues = trainData.column(TrainCol.IS_MOD.name)
        .doubleStream()
        .mapToInt { it.toInt() }
        .toArray()!!

    trainData.set(
        TrainCol.IS_MOD.name,
        IntVector(
            TrainCol.IS_MOD.name,
            isModValues
        )
    )

    return isModValues
}

private fun getData(): Array<DoubleArray> {
    val trainingAndValidationData = readTrainingAndValidationData()
    val all = trainingAndValidationData.first.plus(trainingAndValidationData.second)
    val newAll = all.toMutableList()

    if (MODE == Mode.THIRD_DOWN) {
        // How often does this happen:
        // 0. three keys down (PTH, second, third)
        // 1. is not mod
        // 2. second is not released (before third press)
        var count = 0
        for (row in all) {
            if (row[TrainCol.IS_MOD.ordinal] < 0.5 && row[TrainCol.SECOND_RELEASE_TIME.ordinal] > row[TrainCol.THIRD_PRESS_TIME.ordinal]) {
                count += 1
            }
        }
        println("Rows where TH is not mod and second is released after the third press: ${count.toStringWithThousandSeparator()}")
        /*
        repeat(5) {
            for (row in all) {
                if (row[TrainCol.IS_MOD.ordinal] > 0.5) {
                    newAll.add(row)
                }
            }
        }
        */
    }


    return newAll.map {
        it.toDoubleArray()
    }.toTypedArray()
}

private fun testOverlapRegressionTree(
    data: Array<DoubleArray>,
) {
    // Does not perform as well as our evolved formular, so disabled
    //    Mod:     1,190,226 / 1,500,612 (79.32 %)
    //    Non-mod: 9,196,473 / 9,610,730 (95.69 %)
    //    Total:   10,386,699 / 11,111,342 (93.48 %)
    println("# Regression Tree")
    val ovName = "overlap"

    val dataWithOverlap = data.map {
        val p = if (it[TrainCol.IS_MOD.ordinal] > 0.5) -50 else 50
        val overlapEnd =
            min(it[TrainCol.SECOND_RELEASE_TIME.ordinal], it[TrainCol.PTH_RELEASE_TIME.ordinal])
        it.plus(p + (overlapEnd - it[TrainCol.SECOND_PRESS_TIME.ordinal]))
    }.toTypedArray()

    val trainData = DataFrame.of(dataWithOverlap, *ALL_COLS, ovName)!!
    val formula = Formula.of(ovName, *VALID_COLS)!!
    val tree = rcart(formula, trainData, maxDepth = DECISION_TREE_DEPTH)
    println(tree)
    println()

    val isModCol = trainData.column(TrainCol.IS_MOD.name) as DoubleVector
    val nextUpCol = trainData.column(TrainCol.SECOND_RELEASE_TIME.name) as DoubleVector
    val nextDownCol = trainData.column(TrainCol.SECOND_PRESS_TIME.name) as DoubleVector
    val thUpCol = trainData.column(TrainCol.PTH_RELEASE_TIME.name) as DoubleVector
    println(
        getPerformance(
            tree.predict(trainData).asIterable(),
            { isModCol.get(it) > 0.5 },
            { i, isMod, prediction ->
                val nextDownTime = nextDownCol.get(i)
                val estimateReleaseHappensAfterIfMod = nextDownTime + prediction

                val overlapEnd = min(nextUpCol.get(i), thUpCol.get(i))

                val estimatesThatIsMod = estimateReleaseHappensAfterIfMod < overlapEnd
                estimatesThatIsMod == isMod
            })
    )
    println()
}


private fun testRegressionTree(
    trainData: DataFrame,
    isModValues: IntArray,
    formula: Formula
) {
    println("# Regression Tree")
    val tree = rcart(formula, trainData, maxDepth = DECISION_TREE_DEPTH)
    println(tree)
    println()
    printPerformance(
        isModValues,
        tree.predict(trainData).map { if (it > 0.5) 1 else 0 }.toIntArray()
    )
}


private fun testDecisionTree(
    trainData: DataFrame,
    isModValues: IntArray,
    formula: Formula
) {
    println("# Decision Tree")
    val tree = ccart(formula, trainData, maxDepth = DECISION_TREE_DEPTH)
    val perf = getPerformance(isModValues, tree.predict(trainData))

    println(tree)
    println()
    decisionTreeGenerateKotlin = false
    println(generateTernaryC(tree.root(), VALID_COLS, perf))
    println()
    decisionTreeGenerateKotlin = true
    println(generateTernaryC(tree.root(), VALID_COLS, perf))
    println()
}

private fun testLogReg(data: Array<DoubleArray>, isModValues: IntArray) {
    println("Logistic regression model")

    val logRegModel = logit(data, isModValues, maxIter = 10_000_000)
    if (logRegModel is LogisticRegression.Binomial) {
        println("Coefficients: ${logRegModel.coefficients()}")
    }
    printPerformance(
        isModValues,
        logRegModel.predict(data).map { if (it > 0.5) 1 else 0 }.toIntArray()
    )
}

private fun testOLS(
    formula: Formula,
    trainData: DataFrame,
    isModValues: IntArray
) {
    println("# OLS model")
    val linearModel = OLS.fit(formula, trainData)!!
    printLinearModel(linearModel)
    printPerformance(
        isModValues,
        linearModel.predict(trainData).map { if (it > 0.5) 1 else 0 }.toIntArray()
    )
}

private fun testMLP(data: Array<DoubleArray>) {
    println("# MLP")
    // must be missing something about MLP, as this model always chooses one or the other
    // have to learn more about neural nets (or there's some bug here)
    val limit = 5
    var modToNonModRatio = 0
    val mlpIsModValues = mutableListOf<Int>()
    val mlpTrainData = data.map {
        val isMod = it[TrainCol.IS_MOD.ordinal] > 0
        if ((modToNonModRatio > limit && !isMod) || (modToNonModRatio < -limit && isMod)) {
            return@map null
        }
        modToNonModRatio += if (isMod) -1 else 1

        mlpIsModValues.add(isMod.toInt())

        it.mapIndexed { index, d ->
            val col = TrainCol.entries[index]
            if (MODE !in col.mayUseInModes)
                null
            else if (col.isDur)
                d / MS_MAX_DUR_FOR_PREDICTION
            else
                d
        }
            .filterNotNull()
            .toDoubleArray()
    }.filterNotNull().toTypedArray()

    val mlpIsMod = mlpIsModValues.toIntArray()

    val mlpModel = mlp(
        mlpTrainData,
        mlpIsMod,
        arrayOf(
            Layer.input(VALID_COLS.size),
            Layer.sigmoid(VALID_COLS.size / 2),
            Layer.mle(1, OutputFunction.SIGMOID)
        ),
        learningRate = TimeFunction.constant(0.1),
        momentum = TimeFunction.constant(0.1)
    )

    println(mlpModel.toString())
    printPerformance(
        mlpIsMod,
        mlpModel.predict(mlpTrainData)
    )
}

fun Boolean.toInt() = if (this) 1 else 0


fun printLinearModel(model: LinearModel) {
    println("Intercept: ${model.intercept()}")
    println("Coefficients:")
    model.coefficients().forEachIndexed { i, coef ->
        println("  ${VALID_COLS[i]} = $coef")
    }
    println(model)
}

fun printPerformance(
    isModValues: IntArray,
    predictions: IntArray,
) {
    println(getPerformance(isModValues, predictions))
}

fun getPerformance(
    isModValues: IntArray,
    predictions: IntArray,
): String {
    return getPerformance(
        predictions.asIterable(),
        { isModValues[it] == 1 },
        { i, isMod, prediction -> isMod.toInt() == prediction })
}

inline fun <P> getPerformance(
    predictions: Iterable<P>,
    isMod: (Int) -> Boolean,
    isCorrect: (i: Int, isMod: Boolean, prediction: P) -> Boolean,
): String {
    var numModCorrect = 0
    var numNonModCorrect = 0
    var numMod = 0
    var numNonMod = 0
    for ((i, prediction) in predictions.withIndex()) {
        val isMod = isMod(i)
        val correct = isCorrect(i, isMod, prediction)

        if (isMod) {
            numMod += 1
            if (correct) numModCorrect += 1
        } else {
            // non-mod
            numNonMod += 1
            if (correct) numNonModCorrect += 1
        }
    }
    val numTotal = numMod + numNonMod
    val numTotalCorrect = numModCorrect + numNonModCorrect

    return "  Mod:     ${pct(numModCorrect, numMod)}\n" +
            "  Non-mod: ${pct(numNonModCorrect, numNonMod)}\n" +
            "  Total:   ${pct(numTotalCorrect, numTotal)}\n"
}

// Surprisingly accurate to combine oversampled mod with oversampled non-mod one
// Mod:     50,269 / 68,121 (73.79 %)
// Non-mod: 306,836 / 310,294 (98.89 %)
// Total:   357,105 / 378,415 (94.37 %)
fun pth_default_get_hold_prediction_when_third_press(row: Row): Float {
    return (pth_default_get_hold_prediction_when_third_press_hnm(row) + pth_default_get_hold_prediction_when_third_press_hm(
        row
    )) / 2f
}

/**
 * Auto-generated decision tree prediction function.
 * At most 7 comparisons are necessary to get a result.
 */
fun pth_default_get_hold_prediction_when_third_press_hm(row: Row): Float {
    // clang-format off
    return (if (row.getDouble(TrainCol.PTH_PREV_PRESS_TO_PTH_PRESS_DUR.name) <= 234.5f)
        (if (row.getDouble(TrainCol.OPT_TH_DOWN_NEXT_UP_DUR.name) <= 142.5f)
            (if (row.getDouble(TrainCol.PTH_PRESS_TO_SECOND_PRESS_DUR.name) <= 148.5f)
                (if (row.getDouble(TrainCol.PTH_SECOND_PRESS_TO_THIRD_PRESS_DUR.name) <= 101.5f)
                    (if (row.getDouble(TrainCol.DOWN_COUNT.name) <= 0.5f)
                        0.09302839f
                    else
                        (if (row.getDouble(TrainCol.PTH_PREV_PRESS_TO_PTH_PRESS_DUR.name) <= 19.5f)
                            0.88617885f
                        else
                            0.03517096f
                                )
                            )
                else
                    (if (row.getDouble(TrainCol.DOWN_COUNT.name) <= 0.5f)
                        (if (row.getDouble(TrainCol.OPT_TH_DOWN_NEXT_UP_DUR.name) <= 109.5f)
                            0.26531547f
                        else
                            0.6768942f
                                )
                    else
                        0.11336007f
                            )
                        )
            else
                (if (row.getDouble(TrainCol.PTH_PRESS_TO_SECOND_PRESS_DUR.name) <= 200.5f)
                    (if (row.getDouble(TrainCol.DOWN_COUNT.name) <= 0.5f)
                        (if (row.getDouble(TrainCol.PTH_SECOND_PRESS_TO_THIRD_PRESS_DUR.name) <= 75.5f)
                            0.3782581f
                        else
                            0.6281859f
                                )
                    else
                        0.2514955f
                            )
                else
                    (if (row.getDouble(TrainCol.PTH_PRESS_TO_SECOND_PRESS_DUR.name) <= 215.5f)
                        (if (row.getDouble(TrainCol.PTH_SECOND_PRESS_TO_THIRD_PRESS_DUR.name) <= 79.5f)
                            0.5f
                        else
                            0.74757284f
                                )
                    else
                        0.8905962f
                            )
                        )
                    )
        else
            (if (row.getDouble(TrainCol.PTH_PRESS_TO_SECOND_PRESS_DUR.name) <= 87.5f)
                (if (row.getDouble(TrainCol.PTH_SECOND_PRESS_TO_THIRD_PRESS_DUR.name) <= 146.5f)
                    (if (row.getDouble(TrainCol.PTH_PRESS_TO_SECOND_PRESS_DUR.name) <= 55.5f)
                        0.2678762f
                    else
                        (if (row.getDouble(TrainCol.PTH_PREV_PRESS_TO_PTH_PRESS_DUR.name) <= 118.5f)
                            0.41272345f
                        else
                            0.60213417f
                                )
                            )
                else
                    (if (row.getDouble(TrainCol.PTH_PRESS_TO_SECOND_PRESS_DUR.name) <= 44.5f)
                        (if (row.getDouble(TrainCol.PTH_SECOND_PRESS_TO_THIRD_PRESS_DUR.name) <= 200.5f)
                            0.3018018f
                        else
                            0.66292137f
                                )
                    else
                        0.8701477f
                            )
                        )
            else
                (if (row.getDouble(TrainCol.PTH_SECOND_PRESS_TO_THIRD_PRESS_DUR.name) <= 120.5f)
                    (if (row.getDouble(TrainCol.OPT_TH_DOWN_NEXT_UP_DUR.name) <= 214.5f)
                        (if (row.getDouble(TrainCol.PTH_SECOND_PRESS_TO_THIRD_PRESS_DUR.name) <= 74.5f)
                            0.39292365f
                        else
                            0.7136549f
                                )
                    else
                        (if (row.getDouble(TrainCol.PTH_PRESS_TO_SECOND_PRESS_DUR.name) <= 104.5f)
                            0.20833333f
                        else
                            0.8867416f
                                )
                            )
                else
                    0.95000446f
                        )
                    )
                )
    else
        (if (row.getDouble(TrainCol.PTH_PREV_PRESS_TO_PTH_PRESS_DUR.name) <= 1174.5f)
            (if (row.getDouble(TrainCol.OPT_TH_DOWN_NEXT_UP_DUR.name) <= 120.5f)
                (if (row.getDouble(TrainCol.PTH_PRESS_TO_SECOND_PRESS_DUR.name) <= 139.5f)
                    0.26299894f
                else
                    0.7519518f
                        )
            else
                (if (row.getDouble(TrainCol.PTH_PRESS_TO_SECOND_PRESS_DUR.name) <= 59.5f)
                    (if (row.getDouble(TrainCol.PTH_SECOND_PRESS_TO_THIRD_PRESS_DUR.name) <= 131.5f)
                        (if (row.getDouble(TrainCol.KEY_RELEASE_BEFORE_PTH_TO_PTH_PRESS_DUR.name) <= 100.0f)
                            0.083333336f
                        else
                            0.5056818f
                                )
                    else
                        (if (row.getDouble(TrainCol.PTH_PRESS_TO_SECOND_PRESS_DUR.name) <= 26.5f)
                            0.39473686f
                        else
                            0.859375f
                                )
                            )
                else
                    (if (row.getDouble(TrainCol.PTH_SECOND_PRESS_TO_THIRD_PRESS_DUR.name) <= 87.5f)
                        (if (row.getDouble(TrainCol.OPT_TH_DOWN_NEXT_UP_DUR.name) <= 156.5f)
                            0.42574257f
                        else
                            0.81292516f
                                )
                    else
                        (if (row.getDouble(TrainCol.DOWN_COUNT.name) <= 0.5f)
                            0.9776487f
                        else
                            0.1764706f
                                )
                            )
                        )
                    )
        else
            (if (row.getDouble(TrainCol.PTH_PRESS_TO_SECOND_PRESS_DUR.name) <= 19.5f)
                0.09375f
            else
                (if (row.getDouble(TrainCol.KEY_RELEASE_BEFORE_PTH_TO_PTH_PRESS_DUR.name) <= 1218.0f)
                    (if (row.getDouble(TrainCol.PTH_PRESS_TO_SECOND_PRESS_DUR.name) <= 90.0f)
                        (if (row.getDouble(TrainCol.OPT_NEXT_DUR.name) <= 50.0f)
                            0.32857144f
                        else
                            0.875f
                                )
                    else
                        0.93414634f
                            )
                else
                    (if (row.getDouble(TrainCol.DOWN_COUNT.name) <= 0.5f)
                        0.9962415f
                    else
                        0.1f
                            )
                        )
                    )
                )
            )
    // clang-format on
}

/**
 * Auto-generated decision tree prediction function.
 * At most 7 comparisons are necessary to get a result.
 */
fun pth_default_get_hold_prediction_when_third_press_hnm(row: Row): Float {
    // clang-format off
    return (if (row.getDouble(TrainCol.PTH_PREV_PRESS_TO_PTH_PRESS_DUR.name) <= 1174.5f)
        (if (row.getDouble(TrainCol.OPT_TH_DOWN_NEXT_UP_DUR.name) <= 165.5f)
            (if (row.getDouble(TrainCol.PTH_PRESS_TO_SECOND_PRESS_DUR.name) <= 200.5f)
                (if (row.getDouble(TrainCol.OPT_TH_DOWN_NEXT_UP_DUR.name) <= 121.5f)
                    0.025816808f
                else
                    (if (row.getDouble(TrainCol.PTH_SECOND_PRESS_TO_THIRD_PRESS_DUR.name) <= 140.5f)
                        0.14864238f
                    else
                        (if (row.getDouble(TrainCol.PTH_PRESS_TO_SECOND_PRESS_DUR.name) <= 45.5f)
                            0.23217247f
                        else
                            0.8319594f
                                )
                            )
                        )
            else
                (if (row.getDouble(TrainCol.PTH_PRESS_TO_SECOND_PRESS_DUR.name) <= 232.5f)
                    (if (row.getDouble(TrainCol.PTH_SECOND_PRESS_TO_THIRD_PRESS_DUR.name) <= 79.5f)
                        0.3099099f
                    else
                        0.5588235f
                            )
                else
                    (if (row.getDouble(TrainCol.PTH_PRESS_TO_SECOND_PRESS_DUR.name) <= 264.5f)
                        0.67992425f
                    else
                        (if (row.getDouble(TrainCol.PTH_SECOND_PRESS_TO_THIRD_PRESS_DUR.name) <= 13.0f)
                            0.083333336f
                        else
                            0.9394347f
                                )
                            )
                        )
                    )
        else
            (if (row.getDouble(TrainCol.PTH_SECOND_PRESS_TO_THIRD_PRESS_DUR.name) <= 154.5f)
                (if (row.getDouble(TrainCol.PTH_PRESS_TO_SECOND_PRESS_DUR.name) <= 104.5f)
                    (if (row.getDouble(TrainCol.DOWN_COUNT.name) <= 0.5f)
                        (if (row.getDouble(TrainCol.KEY_RELEASE_BEFORE_PTH_TO_PTH_PRESS_DUR.name) <= 103.5f)
                            0.35218215f
                        else
                            0.6159738f
                                )
                    else
                        0.15572315f
                            )
                else
                    (if (row.getDouble(TrainCol.DOWN_COUNT.name) <= 0.5f)
                        0.77053475f
                    else
                        (if (row.getDouble(TrainCol.PTH_PRESS_TO_PRESS_W_AVG.name) <= 105.41671f)
                            0.5794392f
                        else
                            0.25873363f
                                )
                            )
                        )
            else
                (if (row.getDouble(TrainCol.OPT_NEXT_DUR.name) <= 134.5f)
                    0.94416785f
                else
                    (if (row.getDouble(TrainCol.PTH_PRESS_TO_SECOND_PRESS_DUR.name) <= 121.5f)
                        (if (row.getDouble(TrainCol.PTH_SECOND_PRESS_TO_THIRD_PRESS_DUR.name) <= 243.5f)
                            0.39121038f
                        else
                            0.829932f
                                )
                    else
                        0.84813875f
                            )
                        )
                    )
                )
    else
        (if (row.getDouble(TrainCol.KEY_RELEASE_BEFORE_PTH_TO_PTH_PRESS_DUR.name) <= 1295.5f)
            (if (row.getDouble(TrainCol.PTH_PRESS_TO_SECOND_PRESS_DUR.name) <= 107.0f)
                (if (row.getDouble(TrainCol.OPT_NEXT_DUR.name) <= 28.0f)
                    (if (row.getDouble(TrainCol.PTH_PRESS_TO_SECOND_PRESS_DUR.name) <= 90.0f)
                        0.15079366f
                    else
                        (if (row.getDouble(TrainCol.PTH_PREV_PREV_PRESS_TO_PREV_PRESS_DUR.name) <= 105.5f)
                            0.8888889f
                        else
                            0.29268292f
                                )
                            )
                else
                    0.8235294f
                        )
            else
                (if (row.getDouble(TrainCol.PTH_PRESS_TO_SECOND_PRESS_DUR.name) <= 163.0f)
                    (if (row.getDouble(TrainCol.OPT_TH_DOWN_NEXT_UP_DUR.name) <= 206.0f)
                        (if (row.getDouble(TrainCol.PTH_PREV_PRESS_TO_PTH_PRESS_DUR.name) <= 1270.5f)
                            0.4473684f
                        else
                            0.7457627f
                                )
                    else
                        0.9736842f
                            )
                else
                    0.9719101f
                        )
                    )
        else
            (if (row.getDouble(TrainCol.PTH_PRESS_TO_SECOND_PRESS_DUR.name) <= 19.5f)
                0.033898305f
            else
                (if (row.getDouble(TrainCol.DOWN_COUNT.name) <= 0.5f)
                    0.9862644f
                else
                    0.055555556f
                        )
                    )
                )
            )
    // clang-format on
}