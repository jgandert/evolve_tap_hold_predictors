import io.jenetics.*
import io.jenetics.engine.Engine
import io.jenetics.engine.EvolutionResult
import io.jenetics.ext.SingleNodeCrossover
import io.jenetics.ext.TreeRewriteAlterer
import io.jenetics.ext.rewriting.TreeRewriteRule
import io.jenetics.ext.util.Tree
import io.jenetics.ext.util.TreeNode
import io.jenetics.prog.MathRewriteAlterer
import io.jenetics.prog.ProgramChromosome
import io.jenetics.prog.ProgramGene
import io.jenetics.prog.op.*
import io.jenetics.prog.regression.Complexity
import io.jenetics.prog.regression.Error
import io.jenetics.prog.regression.Regression
import io.jenetics.prog.regression.Sampling
import io.jenetics.util.ISeq
import io.jenetics.util.RandomRegistry
import utils.*
import utils.smile.GaussianValTreeRewriter
import java.time.Instant
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.min

private const val COMMENT_LINE_PREFIX = "        // "
private const val FUNCTION_LINE_PREFIX = "        "


private val evalProgFitness: (Array<Double>, Double) -> Double =
    if (MODE == Mode.OVERLAP_MS_FOR_HOLD)
        ::estimateOverlap
    else
        ::guessIfIsHold

// MathOp.DIV -> can lead to divide by zero
private val safeDiv: Op<Double> = Op.of("sd") { a, b ->
    if (b == 0.0) a else a / b
}

private val lts: Op<Double> = Op.of("lts") { a, b ->
    //if (a < b) 1.0 else 0.0
    if (a < b) (b - a) else 0.0
}

private val BRANCH_OPS = setOf(safeDiv, lts, MathOp.MAX, MathOp.MIN, MathOp.ABS)

val divToSd = TreeRewriteRule.parse($$"div($x,$y) -> sd($x,$y)") {
    if (it == "sd") safeDiv else MathOp.toMathOp(it)
}!!

val modToLessThan = TreeRewriteRule.parse($$"mod($x,$y) -> lts($x,$y)") {
    if (it == "lts") lts else MathOp.toMathOp(it)
}!!

private val ops: ISeq<Op<Double>> =
    ISeq.of(
        MathOp.ADD,
        MathOp.SUB,
        MathOp.MUL,
        MathOp.MAX,
        MathOp.MIN,
        MathOp.ABS,
        lts,
        safeDiv
    )

private val vars = ISeq.of(
    TrainCol.entries
        .filter { it.mayUseInModes.contains(MODE) }
        .map { Var.of<Double>(it.name.lowercase(), it.ordinal) }
)

private val terminals: ISeq<Op<Double>> = ISeq.concat(
    vars,
    Const.of(0.0),
    Const.of(1.0),
    Const.of(2.0),
    Const.of(3.0),
    Const.of(5.0),
    Const.of(8.0),
    Const.of(0.6180339887498948), // inverse of Phi
    Const.of(0.00001),
    EphemeralConst.of { RandomRegistry.random().nextGaussian(0.0, 1000.0) },
    EphemeralConst.of { RandomRegistry.random().nextGaussian(0.0, 500.0) },
    EphemeralConst.of { RandomRegistry.random().nextGaussian(0.0, 250.0) },
    EphemeralConst.of { RandomRegistry.random().nextGaussian(0.0, 100.0) },
    EphemeralConst.of { RandomRegistry.random().nextGaussian(0.0, 50.0) },
)

private fun sampling(): Sampling<Double> {
    return Sampling { programm ->
        var modsCount = 0
        var modsCorrect = 0

        val calculated = sumPerTrainingEventOf {
            val r = evalProgFitness(it, programm.callWith(it))

            if (it[TrainCol.IS_MOD] > 0) {
                modsCount += 1
                if (r == 0.0) {
                    modsCorrect += 1
                }
            }
            r
        }

        Sampling.Result(arrayOf(calculated + penalty(modsCorrect, modsCount)), results)
    }
}

fun penalty(modsCorrect: Int, modsCount: Int): Double {
    val modsCorrectRatio = modsCorrect.toDouble() / modsCount
    val deltaFromPenalty = APPLY_PENALTY_IF_MOD_CORRECT_RATIO_LOWER - modsCorrectRatio
    return if (deltaFromPenalty > 0) {
        deltaFromPenalty * PENALTY_IF_MOD_CORRECT_RATIO_LOWER
    } else 0.0
}

private inline fun sumPerTrainingEventOf(a: (Array<Double>) -> Double): Double {
    if (SAMPLE_TRAINING_DATA_PER_PROGRAM) {
        val rng = RandomRegistry.random()
        // one part from the first half
        val s1 = rng.nextInt(trainingData.size / 2 - NUM_TRAINING_DATA / 2)
        val s2 = trainingData.size / 2 + rng.nextInt(trainingData.size / 2 - NUM_TRAINING_DATA / 2)

        return (s1..<(s1 + NUM_TRAINING_DATA / 2)).sumOf {
            a(trainingData[it])
        } + (s2..<(s2 + NUM_TRAINING_DATA / 2)).sumOf {
            a(trainingData[it])
        }
    } else {
        return trainingData.sumOf {
            a(it)
        }
    }
}

fun Tree<out Op<Double>, *>.callWith(ev: Array<Double>): Double {
    return reduce(ev) { acc, p -> acc.apply(p) }!!
}

// This penalizes programs that become too large.
// A large value leads to only a tiny penalty for the size.
// We make the value so large, since we generally want the better program to win.
// Only if all other things are equal, we want the smaller one to beat the larger one.
private val complexity = Complexity.ofNodeCount<Double>(if (HIGH_MUTATION) 1024 else 333)!!

private val regression = Regression.of(
    Regression.codecOf(ops, terminals, 5) { true }, //{ it.gene().size() <= 64 },
    Error.of(
        { calculated: Array<Double>, _: Array<Double> ->
            //LossFunction.mse(calculated, expected)
            //calculated.average()
            calculated[0]
        },
        complexity
    ),
    sampling()
)

private val pairOfTrainingAndValidationData = readTrainingAndValidationData()
val trainingData = pairOfTrainingAndValidationData.first
val validationData = pairOfTrainingAndValidationData.second

private val results = arrayOf(0.0)

private val initialPopulation = listOfNotNull(
    // With Fast Streak Tap, we only tap right away when tap (non-mod) was
    // predicted. Otherwise, we let other prediction functions decide when
    // there's more information. That's why the non-mod value here can be low.
    //
    // Looking at the data, 99 % of taps of the training set, which involves
    // many kinds of typists (77,614 to be precise), at all kinds of speeds and
    // performance, are predicted correctly. The numbers of holds might not
    // seem good with 75 %, but that's the price for getting great tap results.
    // Taps are vastly more common, and there's always a balance to be struck.
    // If the functions predict taps better, they tend to predict holds worse.
    // If they predict holds better, they tend to predict taps worse.
    // Of course, in many cases, the prediction functions must not even be
    // called and a decision can be made simply by which side the keys are on.

    // Not included in PTH, but also reasonable
    // Mod:              1,491,833 / 1,500,612 (99.41 %)
    // Non-mod:          636,149 / 9,610,730 (6.62 %)
    // Total:            2,127,982 / 11,111,342 (19.15 %)
    // Node Count:       12 (complexity = 0.00005770)
    // Branch Count:     2
    parseToProgGenotypeIfMode(
        Mode.FAST_STREAK_TAP,
        "abs(sd(pth_prev_prev_overlap_dur - pth_prev_press_to_pth_press_dur, (pth_prev_prev_overlap_dur - pth_prev_press_to_pth_press_dur) + -3.0799989144449125*ov_w_avg))"
    ),

    // DEFAULT FAST STREAK TAP
    // Mod:              1,490,594 / 1,500,612 (99.33 %)
    // Non-mod:          719,514 / 9,610,730 (7.49 %)
    // Total:            2,210,108 / 11,111,342 (19.89 %)
    // Node Count:       8 (complexity = 0.00002337)
    // Branch Count:     2
    parseToProgGenotypeIfMode(
        Mode.FAST_STREAK_TAP,
        "abs(sd(pth_prev_prev_overlap_dur - pth_prev_press_to_pth_press_dur, 4.280551301886473 - pth_prev_press_to_pth_press_dur))"
    ),

    // CONSERVATIVE FAST STREAK TAP
    // Mod:              1,496,221 / 1,500,612 (99.71 %)
    // Non-mod:          332,284 / 9,610,730 (3.46 %)
    // Total:            1,828,505 / 11,111,342 (16.46 %)
    // Node Count:       12 (complexity = 0.00005770)
    // Branch Count:     2
    parseToProgGenotypeIfMode(
        Mode.FAST_STREAK_TAP,
        "abs(sd(pth_prev_prev_overlap_dur - pth_prev_press_to_pth_press_dur, (pth_prev_prev_overlap_dur - pth_prev_press_to_pth_press_dur) + 5.3131340976019885*ov_w_avg))"
    ),

    // Mod:              729,874 / 1,057,872 (68.99 %)
    // Non-mod:          9,164,575 / 9,190,163 (99.72 %)
    // Total:            9,894,449 / 10,248,035 (96.55 %)
    // Node Count:       12 (complexity = 0.00005770)
    // Branch Count:     3
    parseToProgGenotypeIfMode(
        Mode.PTH_UP_AFTER_SECOND_DOWN,
        "abs(sd(-0.47622462221406836*pth_prev_press_to_pth_press_dur, min(8.146676296892599*pth_press_to_second_press_dur - 2132.7709261723817, -57.96437122223286)))"
    ),

    // Mod:              420,045 / 435,604 (96.43 %)
    // Non-mod:          59,052 / 85,031 (69.45 %)
    // Total:            479,097 / 520,635 (92.02 %)
    // Node Count:       36 (complexity = 0.00058430)
    // Branch Count:     7
    parseToProgGenotypeIfMode(
        Mode.PTH_UP_AFTER_SECOND_UP,
        "(opt_th_down_next_up_dur + 357.0520154856858)*sd(ov_w_avg, -99.09117768179419*(opt_th_down_next_up_dur + pth_prev_press_to_pth_press_dur)) + max(sd(min(pth_press_to_second_press_dur - 17.18620887762382, abs(-19.149818047010875 - opt_next_dur)), (ov_w_avg + 177.7888162786241) - opt_next_dur), sd(opt_th_down_next_up_dur + -4.970065167891373, max(175.35486939947307, 375.11931363347594 - key_release_before_pth_to_pth_press_dur)))"
    ),

    // Mod:              50,846 / 68,121 (74.64 %)
    // Non-mod:          304,569 / 310,294 (98.15 %)
    // Total:            355,415 / 378,415 (93.92 %)
    // Node Count:       20 (complexity = 0.00017215)
    // Branch Count:     2
    parseToProgGenotypeIfMode(
        Mode.THIRD_DOWN,
        "abs((1.0E-5*(1.2360679774997896 + max(9.305893796445337, opt_th_down_next_up_dur)) - 5.4503226951E-4)*(((7.843392784960043 + pth_second_press_to_third_press_dur) + pth_prev_press_to_pth_press_dur) + 3.361515827334373*pth_press_to_second_press_dur))"
    ),

    // These are here, because some of them have better fitness due to the complexity
    // Mod:              991,282 / 1,496,094 (66.26 %)
    // Non-mod:          9,527,646 / 9,582,479 (99.43 %)
    // Total:            10,518,928 / 11,078,573 (94.95 %)
    // Node Count:       28 (complexity = 0.00329249)
    // Branch Count:     4
    //
    // Outputs of the function
    // min:            0.007358696625757943
    // max:            2196587.9018891375
    // median:         220.23687934547232
    // unique.size:    21245
    parseToProgGenotypeIfMode(Mode.OVERLAP_MS_FOR_HOLD, "abs(max(pth_press_to_second_press_dur*sd(20145.72453837935, 20145.72453837935 - (pth_prev_press_to_pth_press_dur - pth_prev_prev_overlap_dur)*pth_press_to_second_press_dur), sd(20141.63979839019 - ((pth_prev_press_to_pth_press_dur - 2.0*pth_prev_prev_overlap_dur) - pth_prev_prev_overlap_dur)*10.24699665838974, pth_press_to_second_press_dur) - 32.559018051648636))"),

    // Mod:              994,151 / 1,496,055 (66.45 %)
    // Non-mod:          9,521,092 / 9,582,518 (99.36 %)
    // Total:            10,515,243 / 11,078,573 (94.92 %)
    // Node Count:       20 (complexity = 0.00162908)
    // Branch Count:     5
    parseToProgGenotypeIfMode(Mode.OVERLAP_MS_FOR_HOLD, "abs(max(sd(20142.976227875624, sd(20142.976227875624 - pth_prev_press_to_pth_press_dur*pth_press_to_second_press_dur, pth_press_to_second_press_dur)), sd(20142.976227875624 - pth_prev_press_to_pth_press_dur*9.966576964356308, pth_press_to_second_press_dur) - 31.698342165242384))"),

    // Mod:              994,038 / 1,496,055 (66.44 %)
    // Non-mod:          9,518,530 / 9,582,518 (99.33 %)
    // Total:            10,512,568 / 11,078,573 (94.89 %)
    // Node Count:       12 (complexity = 0.00054574)
    // Branch Count:     3
    parseToProgGenotypeIfMode(Mode.OVERLAP_MS_FOR_HOLD, "abs(max(sd(20140.24325387131 - pth_prev_press_to_pth_press_dur*9.938171439401396, pth_press_to_second_press_dur) - 27.714852241643733, 18.186242444845295))"),

    )

fun parseToProgGenotypeIfMode(mode: Mode, expr: String): Genotype<ProgramGene<Double>>? {
    if (mode != MODE) return null

    try {
        return parseToProgGenotype(expr)
    } catch (e: NullPointerException) {
        println("Error in parsing program genotype $expr:\n\n${e.message}\n\nThis is most likely due to using a variable (TrainCol) that is not enabled for the current mode")
        return null
    }
}


fun parseToProgGenotype(expr: String): Genotype<ProgramGene<Double>> {
    // MathExpr doesn't know about sd function
    val patchedExpr = expr
        .replace("sd(", "div(")
        .replace("lts(", "mod(")
    val tree: Tree<Op<Double>, *> = MathExpr.parse(patchedExpr).tree()

    val asNode: TreeNode<Op<Double>> = TreeNode.ofTree(tree)
    Var.reindex(asNode, vars.associateWith { it.index() })

    // now we can turn it back to sd
    divToSd.rewrite(asNode)
    modToLessThan.rewrite(asNode)

    // this was .of(tree
    return Genotype.of(ProgramChromosome.of(asNode, ops, terminals))
}


fun howToRunProgram() {
    val treeNode = parseToProgGenotype("sd(3, -1.5)").gene().toTreeNode()
    val input = trainingData[0]
    val result = treeNode.callWith(input)
    println("$treeNode called with ${input.contentToString()} = $result")
}


private fun guessIfIsHold(trainingEvent: Array<Double>, progResult: Double): Double {
    // is_mod is 1.0 for true, 0.0 for false
    val isMod = trainingEvent[TrainCol.IS_MOD] > 0

    val absProgResult = progResult.absoluteValue.coerceIn(0.0, 1.0)
    val predictsMod = absProgResult > 0.5

    if (isMod == predictsMod) {
        return 0.0
    }

    val delta = if (isMod) 0.5 - absProgResult else absProgResult - 0.5

    return 0.1 + delta.coerceAtMost(MAX_MS_CONSIDERED) / MAX_MS_CONSIDERED
}


private fun estimateOverlap(trainingEvent: Array<Double>, progResult: Double): Double {
    // The overlap between the tap hold and next key have to be larger than
    // the estimate for it to be considered held. Otherwise, it is estimated
    // to be a tap. The overlap is the time from the next press to either
    // the next or tap hold release, whichever comes first.
    var res = progResult.absoluteValue
    if (FIX_MAX_OVERLAP_MS > 0) {
        res = res.coerceAtMost(FIX_MAX_OVERLAP_MS)
    }
    if (FIX_MIN_OVERLAP_MS > 0) {
        res = res.coerceAtLeast(FIX_MIN_OVERLAP_MS)
    }

    val nextDownTime = trainingEvent[TrainCol.SECOND_PRESS_TIME]
    val estimateReleaseHappensAfterIfMod = nextDownTime + res
    val overlapEnd = min(
        trainingEvent[TrainCol.SECOND_RELEASE_TIME],
        trainingEvent[TrainCol.PTH_RELEASE_TIME]
    )

    val estimatesThatIsMod = estimateReleaseHappensAfterIfMod < overlapEnd

    // is_mod is 1.0 for true, 0.0 for false
    val isMod = trainingEvent[TrainCol.IS_MOD] != 0.0

    if (estimatesThatIsMod == isMod) {
        return 0.0
    }

    // for calculating the delta of incorrect solutions,
    // we want to use the real delta to give the fitness landscape a gradient
    val realEstimateReleaseHappensAfterIfMod = nextDownTime + progResult.absoluteValue
    val delta = abs(overlapEnd - realEstimateReleaseHappensAfterIfMod)

    return 1 + delta.coerceAtMost(MAX_MS_CONSIDERED) / MAX_MS_CONSIDERED
}


fun main() {
    println("Kept ${trainingData.size.toStringWithThousandSeparator()} events for training.")
    println("Kept ${validationData.size.toStringWithThousandSeparator()} events for validation.")
    println()
    println("A program can use the following variables: $vars")
    println()

    processInitialPopulation()

    val engineBuilder = Engine
        .builder(regression)
        .minimizing()
        .populationSize(POPULATION_SIZE)

    if (!HIGH_MUTATION) {
        engineBuilder.survivorsSelector(
            EliteSelector(
                // Number of the best individuals preserved for the next generation: elites
                2,  // Selector used for selecting rest of population.
                TournamentSelector(3)
            )
        )
    }

    val numMutator = TreeRewriteAlterer<Op<Double>, ProgramGene<Double>, Double>(
        GaussianValTreeRewriter(0.8, -50_000.0, 50_000.0, 5.0),
        0.3
    )

    if (ONLY_MUTATE_NUMBERS) {
        engineBuilder.alterers(numMutator)
    } else if (HIGH_MUTATION) {
        engineBuilder.alterers(
            Mutator(0.5),
        )
    } else {
        engineBuilder.alterers(
            SingleNodeCrossover(0.2),
            Mutator(0.2),
            MathRewriteAlterer(0.1),
            numMutator
        )
    }

    val engine = engineBuilder.build()

    val evolutionStream = if (START_FRESH || initialPopulation.isEmpty())
        engine.stream()
    else
        engine.stream(initialPopulation)

    evolutionStream
        .peek(::update)
        .collect(EvolutionResult.toBestEvolutionResult())

    queuePrinter.shutdown()
}

fun processInitialPopulation() {
    if (initialPopulation.isEmpty()) return

    val initial = mutableListOf<Pair<Double, TreeNode<Op<Double>>>>()
    for ((i, genes) in initialPopulation.withIndex()) {
        if (!genes.isValid) {
            println("ERROR: $i was invalid\n")
            continue
        }

        val tree = genes.toRewrittenTreeNode()

        val fitness = trainingData.getFitness(tree)
        if (!fitness.isFinite()) {
            println("ERROR: Fitness is $fitness: $tree")
            continue
        }

        initial.add(fitness to tree)
    }

    initial.sortByDescending { it.first } // smaller = better
    if (initial.size > 10) {
        initial.subList(0, initial.size - 10).clear()
    }

    for (pair in initial) {
        printSolution(pair.second, pair.first)
    }

    val best = initial.last()

    collectStatsAboutOutput(best.second)

    startFindingBestInitialTrainColValue(
        best.second.copy(),
        FINDING_BEST_INITIAL_TRAIN_COL,
        FINDING_BEST_INITIAL_TRAIN_COL_FROM,
        FINDING_BEST_INITIAL_TRAIN_COL_TO
    )

    if (START_NUMBER_HILL_CLIMBING) {
        startHillClimbing(best.second)
    }

    println("=".repeat(80))
    println()
}

fun collectStatsAboutOutput(second: TreeNode<Op<Double>>) {
    val outputs = mutableListOf<Double>()
    for (event in trainingData) {
        outputs.add(second.callWith(event))
    }
    outputs.sort()
    val median = if (outputs.size % 2 == 0) {
        (outputs[outputs.size / 2 - 1] + outputs[outputs.size / 2]) / 2.0
    } else {
        outputs[outputs.size / 2]
    }
    println("# Outputs of the best function")
    println(
        "min:            ${outputs[0]}\n" +
                "max:            ${outputs.last()}\n" +
                "median:         $median\n" +
                "unique.size:    ${outputs.toSet().size}"
    )
}


private fun Genotype<ProgramGene<Double>>.toRewrittenTreeNode(): TreeNode<Op<Double>> {
    val tree = this.gene().toTreeNode()
    MathExpr.rewrite(tree)
    return tree
}


fun startFindingBestInitialTrainColValue(
    best: TreeNode<Op<Double>>,
    trainCol: TrainCol,
    from: Int,
    to: Int
) {
    if (!START_FINDING_BEST_INITIAL_TRAIN_COL_VALUE) return
    val name = trainCol.name.lowercase()

    println("Looking for the best initial value of $name")

    val node = best.firstOrNull {
        it.value() is Var<Double> && it.value().name() == name
    } ?: return

    var bestI = 0
    if (USE_BINARY_SEARCH_TO_FIND_BEST_INITIAL_TRAIN_COL_VALUE) {
        bestI = (from..to).binarySearchForMinimum {
            node.value(Const.of(it.toDouble()))
            trainingData.getFitness(best)
        }
    } else {
        var bestFitness = Double.POSITIVE_INFINITY
        for (i in from..to step 100) {
            node.value(Const.of(i.toDouble()))
            val fitness = trainingData.getFitness(best)
            if (fitness < bestFitness) {
                bestFitness = fitness
                bestI = i
            }
        }
    }

    println("$name = $bestI had the best fitness")
}


fun IntRange.binarySearchForMinimum(continueOnEqual: Boolean = true, get: (Int) -> Double): Int {
    // given that the fitness space is not sorted, this will not actually find the minimum
    if (isEmpty()) throw IllegalArgumentException("the range is empty")

    var low = first
    var high = last
    var lowVal = get(low)
    var mid = 0

    while (low <= high) {
        mid = (low + high).ushr(1)
        val midVal = get(mid)

        if (lowVal > midVal) {
            low = mid + 1
            lowVal = get(low)
            high = mid - 1
        } else if (continueOnEqual || lowVal < midVal) {
            high = mid - 1
        } else {
            break
        }
    }
    return mid
}


fun startHillClimbing(best: TreeNode<Op<Double>>) {
    println()
    println("Start hill climbing.")
    println()
    val nodes = best.filter { it.value() is Val<Double> }
    val initialWeights = nodes.map { (it.value() as Val<Double>).value() }.toDoubleArray()

    hillClimb(
        initialWeights,
        whenGoodSolutionFound = { _, fitness, tries ->
            printHillClimbedSolution(
                best,
                fitness,
                tries
            )
        }
    ) { mutations, weights ->
        for ((i, n) in nodes.withIndex()) {
            if (mutations[i] != 0.0) {
                n.value(Const.of(weights[i]))
            }
        }
        trainingData.getFitness(best)
    }
}


fun printHillClimbedSolution(solution: TreeNode<Op<Double>>, fitness: Double, tries: Int) {
    printComment("Tries:            $tries")
    printSolution(solution, fitness)
}


private fun List<Array<Double>>.getFitness(tree: TreeNode<Op<Double>>): Double {
    return forEachEvalAndGetFitness(tree)
}


data class TCounts(
    var m: Int = 0,
    var nm: Int = 0,
    var mc: Int = 0,
    var nmc: Int = 0,
)


private fun List<Array<Double>>.getFitnessAndCounts(tree: TreeNode<Op<Double>>): Pair<Double, TCounts> {
    val c = TCounts()
    val fitness = forEachEvalAndGetFitness(tree) { isMod, r ->
        if (isMod) c.m += 1 else c.nm += 1
        if (r == 0.0) {
            if (isMod) c.mc += 1 else c.nmc += 1
        }
    }
    return fitness to c
}


private inline fun List<Array<Double>>.forEachEvalAndGetFitness(
    tree: TreeNode<Op<Double>>,
    call: (isMod: Boolean, res: Double) -> Unit = { _, _ -> }
): Double {
    var modsCount = 0
    var modsCorrect = 0

    val loss = sumOf {
        val progResult = tree.callWith(it)
        val isMod = it[TrainCol.IS_MOD] > 0
        val r = evalProgFitness(it, progResult)
        if (isMod) {
            modsCount += 1
            if (r == 0.0) {
                modsCorrect += 1
            }
        }
        call(isMod, r)
        r
    } + penalty(modsCorrect, modsCount)

    return loss + loss * complexity.apply(tree)
}


private fun printResult(result: EvolutionResult<ProgramGene<Double>, Double>) {
    val phenotype = result.bestPhenotype()
    val numGeneration = result.totalGenerations()

    queuePrinter.add(numGeneration to phenotype)
}


private fun printSolution(
    tree: TreeNode<Op<Double>>,
    fitness: Double,
    numGeneration: Long = 100_000,
) {
    val trainingSize =
        if (SAMPLE_TRAINING_DATA_PER_PROGRAM) NUM_TRAINING_DATA else trainingData.size

    printComment("Training Size:    ${trainingSize.toStringWithThousandSeparator()}")
    printComment("Time:             ${Instant.now()}")
    printComment("Fitness:          ${fitness.toRoundedString()} (lower = better)")
    printComment("")
    if (numGeneration > AVOID_FULL_FITNESS_IN_FIRST_N_GENERATIONS) {
        val (_, c) = validationData.getFitnessAndCounts(tree)
        printComment("Mod:              ${pct(c.mc, c.m)}")
        printComment("Non-mod:          ${pct(c.nmc, c.nm)}")
        printComment("Total:            ${pct(c.mc + c.nmc, c.m + c.nm)}")
    }
    printComment(
        "Node Count:       ${tree.size()} " +
                "(complexity = ${complexity.apply(tree).toRoundedString(8)})"
    )

    val branchCount = tree.count {
        it.value() in BRANCH_OPS
    }
    printComment("Branch Count:     $branchCount")

    printFunction(tree)
    println()
}

fun printFunction(tree: TreeNode<Op<Double>>) {
    val exprAsStr = "${MathExpr(tree)}"

    if (PRINT_FOR_EASY_PASTE_TO_CODE) {
        println(FUNCTION_LINE_PREFIX + "parseToProgGenotypeIfMode(Mode.$MODE, \"$exprAsStr\"),")
    } else {
        printComment("Mode:             $MODE")
        println(exprAsStr)
    }
}

fun printComment(comment: String) {
    if (PRINT_FOR_EASY_PASTE_TO_CODE) {
        println(COMMENT_LINE_PREFIX + comment)
    } else {
        println(comment)
    }
}


val queuePrinter = QueuePrinter()
var bestFitness: Double? = null

fun update(result: EvolutionResult<ProgramGene<Double>, Double>) {
    val nf = result.bestPhenotype().fitness()
    val best = bestFitness

    if (best == null || best > nf) {
        bestFitness = nf
        printResult(result)
    }
}

typealias EvPr = Pair<Long, Phenotype<ProgramGene<Double>, Double>>

class QueuePrinter {
    private val queue: BlockingQueue<EvPr> = LinkedBlockingQueue()
    private val isRunning = AtomicBoolean(true)
    private val consumerThread = Thread {
        while (isRunning.get()) {
            try {
                val item = queue.take() // Blocks until an item is available
                process(item)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
    }

    init {
        consumerThread.start()
    }

    fun add(item: EvPr) {
        if (isRunning.get()) {
            queue.offer(item)
        }
    }

    private fun process(item: EvPr) {
        val (numGeneration, phenotype) = item

        val program = phenotype.genotype().gene()
        val fitness = phenotype.fitness()

        val tree: TreeNode<Op<Double>> = program.toTreeNode()
        MathExpr.rewrite(tree) // Simplify result program.

        printComment("Generations:      $numGeneration")
        printSolution(tree, fitness, numGeneration)
    }

    fun shutdown() {
        isRunning.set(false)
        consumerThread.interrupt()
    }
}
