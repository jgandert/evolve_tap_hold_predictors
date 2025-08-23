package utils

import java.util.*
import kotlin.random.Random


fun <E> MutableList<E>.limitToSample(count: Int, rng: Random = Random) {
    shuffleLastNElements(count, rng)
    subList(0, size - count).clear()
}


private fun <E> MutableList<E>.shuffleLastNElements(count: Int, rng: Random) {
    if (count > size) error("count is larger than the size: $count vs ${this.size}")

    for (i in size - 1 downTo size - count) {
        Collections.swap(this, i, rng.nextInt(i + 1))
    }
}


fun <E> MutableList<E>.limitToSampleAndGetRemainder(
    count: Int,
    rng: Random = Random
): MutableList<E> {
    shuffleLastNElements(count, rng)

    val result = subList(0, size - count).toMutableList()
    subList(0, size - count).clear()
    return result
}


inline fun <E> MutableList<E>.sample(
    partitions: IntArray,
    getPartitionId: (E) -> Int
): MutableList<E> {
    return sample(Random, partitions, getPartitionId)
}


inline fun <E> MutableList<E>.sample(
    rng: Random,
    partitions: IntArray,
    getPartitionId: (E) -> Int
): MutableList<E> {
    val size = size
    var count = partitions.sum()

    if (count > size) error("sum of partitions is larger than the size: $count vs ${this.size}")

    val result = mutableListOf<E>()
    val indices = IntArray(size) { it }
    indices.shuffle(rng)

    for (i in indices) {
        val partitionId = getPartitionId(this[i])
        if (partitions[partitionId] > 0) {
            result.add(this[i])
            count--
            if (count == 0) {
                break
            }
            partitions[partitionId]--
        }
    }
    return result
}