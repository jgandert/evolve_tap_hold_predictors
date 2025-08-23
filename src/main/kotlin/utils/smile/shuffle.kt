package utils.smile

import smile.data.DataFrame
import kotlin.random.Random

/**
 * Swaps two rows, i and j, in-place across all columns.
 */
private fun DataFrame.swapRows(i: Int, j: Int) {
    if (i == j) return
    for (colIndex in 0 until this.ncol()) {
        val column = this.column(colIndex)
        val temp = column.get(i)
        column.set(i, column.get(j))
        column.set(j, temp)
    }
}

fun IntArray.swap(i: Int, j: Int) {
    val tmp = this[i]
    this[i] = this[j]
    this[j] = tmp
}

/**
 * Shuffles the rows of a DataFrame in-place using the Fisher-Yates algorithm.
 *
 * This function modifies the DataFrame directly and is efficient for large datasets.
 * It can either generate a new random shuffle or apply a shuffle defined by a
 * provided array of swap indices.
 */
fun DataFrame.shuffle() {
    val colCount = this.ncol()
    if (colCount == 0) {
        return // Nothing to shuffle
    }

    val rowCount = this.column(0).size()
    if (rowCount <= 1) {
        return // No need to shuffle 0 or 1 rows
    }

    // Perform the Fisher-Yates shuffle, iterating from the last row to the second row.
    for (i in rowCount - 1 downTo 1) {
        val j = Random.nextInt(i + 1)
        this.swapRows(i, j)
    }
}
