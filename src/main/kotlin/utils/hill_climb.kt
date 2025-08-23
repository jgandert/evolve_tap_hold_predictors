package utils

import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

const val M_MIN = 0.01
const val M_MAX = 100.0
const val M_DIVISOR = 10_000.0

fun hillClimb(
    weights: DoubleArray,
    maxIterationsWithoutImprovement: Int = Int.MAX_VALUE,
    rng: Random = Random,
    printAfterNTriesWithoutImprovement: Int = 100,
    whenGoodSolutionFound: (weights: DoubleArray, fitness: Double, tries: Int) -> Unit,
    calculateFitness: (mutations: DoubleArray, weights: DoubleArray) -> Double
) {
    val numWeights = weights.size
    val best = weights.copyOf()
    val mutations = DoubleArray(numWeights) { 0.0 }
    var bestFitness = calculateFitness(mutations, best)

    var i = 0
    var iWhenBetter = 0

    var divisor = 100.0
    var tries = 0

    var wasLastImprovement = false
    var printedAfterImprovement = false
    var triesSinceLastImprovement = 0

    while (triesSinceLastImprovement < maxIterationsWithoutImprovement) {
        triesSinceLastImprovement = i - iWhenBetter

        // the longer no improvement, the bigger the changes
        divisor = max(M_MIN, min(M_MAX, divisor - 1.0 / M_DIVISOR))

        if (!wasLastImprovement) {
            updateMutations(rng, numWeights, mutations, divisor)
        }

        wasLastImprovement = false

        i += 1

        // instead of printing directly everytime,
        // we only do so after many tries without improvement
        if (!printedAfterImprovement && triesSinceLastImprovement > printAfterNTriesWithoutImprovement) {
            whenGoodSolutionFound(best, bestFitness, tries)
            printedAfterImprovement = true
            tries = 0
        }

        applyMutations(mutations, weights)

        val fitness = calculateFitness(mutations, weights)

        if (fitness < bestFitness) {
            bestFitness = fitness
            best.indices.forEach {
                best[it] = weights[it]
            }
            tries += i - iWhenBetter
            iWhenBetter = i
            divisor = max(M_MIN, min(M_MAX, divisor + triesSinceLastImprovement / M_DIVISOR))

            wasLastImprovement = true
            printedAfterImprovement = false
        } else {
            undoMutations(mutations, weights)
        }
    }

    if (!printedAfterImprovement) {
        whenGoodSolutionFound(best, bestFitness, tries)
    }
}

private fun undoMutations(mutations: DoubleArray, weights: DoubleArray) {
    for (mi in mutations.indices) {
        weights[mi] -= mutations[mi]
    }
}

private fun applyMutations(mutations: DoubleArray, weights: DoubleArray) {
    for (mi in mutations.indices) {
        weights[mi] += mutations[mi]
    }
}

private fun updateMutations(
    rng: Random,
    numWeights: Int,
    mutations: DoubleArray,
    divisor: Double,
) {
    mutations.fill(0.0) // Reset mutations to 0 for each iteration

    val numMutations = rng.nextInt(1, numWeights + 1)
    val mutationIndices = (0..<numWeights).shuffled(rng).take(numMutations)
    for (mi in mutationIndices) {
        mutations[mi] = (rng.nextDouble() - 0.5) / divisor
    }
}

fun printSolutionWithStats(best: DoubleArray, fitness: Double, tries: Int) {
    println("Took tries:    $tries")
    println("Best solution: ${best.contentToString()}")
    println("Fitness        $fitness")
    println()
}

fun main() {
    val initialWeights = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0)
    hillClimb(
        initialWeights.copyOf(),
        maxIterationsWithoutImprovement = 200,
        whenGoodSolutionFound = ::printSolutionWithStats
    ) { _, weights -> (100 - weights.sum()).absoluteValue }
}