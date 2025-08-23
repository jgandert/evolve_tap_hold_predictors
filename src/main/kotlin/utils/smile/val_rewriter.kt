package utils.smile

import io.jenetics.ext.rewriting.TreeRewriter
import io.jenetics.ext.util.TreeNode
import io.jenetics.internal.math.Probabilities
import io.jenetics.prog.op.Const
import io.jenetics.prog.op.Op
import io.jenetics.prog.op.Val
import io.jenetics.util.RandomRegistry
import java.util.random.RandomGenerator
import kotlin.math.nextDown
import kotlin.math.pow

class GaussianValTreeRewriter(
    probability: Double,
    private val min: Double = Double.MIN_VALUE,
    max: Double = Double.MAX_VALUE,
    private val stdDev: Double = (max - min) * 0.25
) : TreeRewriter<Op<Double>> {
    private val dMax = max.nextDown()
    private val p = Probabilities.toInt(probability.pow(1.0 / 3.0))

    override fun rewrite(node: TreeNode<Op<Double>>, limit: Int): Int {
        if (limit == 0) return 0;

        val random = RandomRegistry.random()

        var rewritten = 0

        for (n in node) {
            val value = n.value()
            if (value is Val<out Double> && random.nextInt() < p) {
                this.rewriteNode(n, value, random)
                rewritten += 1
                if (rewritten >= limit) {
                    break
                }
            }
        }

        return rewritten
    }

    private fun rewriteNode(
        node: TreeNode<Op<Double>>,
        value: Val<out Double>,
        random: RandomGenerator
    ) {
        val gaussian = random.nextGaussian(value.value(), stdDev)
        node.value(Const.of(gaussian.coerceIn(min, dMax)))
    }
}