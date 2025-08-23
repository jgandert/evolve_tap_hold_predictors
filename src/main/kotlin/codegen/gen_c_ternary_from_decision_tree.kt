package codegen

import smile.base.cart.DecisionNode
import smile.base.cart.Node
import smile.base.cart.OrdinalNode
import utils.DECISION_TREE_DEPTH
import utils.DECISION_TREE_OUTPUT_PROB
import utils.decisionTreeGenerateKotlin

/**
 * Generates a C function with a single, nested ternary return statement.
 *
 * @param root The root node of the decision tree.
 * @param featureNames A list of strings for feature names (used in comments).
 * @return A string containing the complete, formatted C function.
 */
fun generateTernaryC(
    root: Node,
    featureNames: Array<String>,
    partInDocString: String = ""
): String {
    val cCodeBuilder = StringBuilder()

    cCodeBuilder.appendLine("\n/**")
    cCodeBuilder.appendLine(" * Auto-generated decision tree prediction function.")
    cCodeBuilder.appendLine(" * At most $DECISION_TREE_DEPTH comparisons are necessary to get a result.")
    cCodeBuilder.appendLine(" *")
    for (it in partInDocString.lines()) {
        if (it.isBlank()) continue
        cCodeBuilder.appendLine(" * ${it.trim()}")
    }
    cCodeBuilder.appendLine(" *")
    cCodeBuilder.appendLine(" * @return float predicted overlap time in ms.")
    cCodeBuilder.appendLine(" */")
    cCodeBuilder.appendLine(getHeader())
    cCodeBuilder.appendLine("    // clang-format off")

    val expression = generateTernaryExpression(root, featureNames, indentationLevel = 1)

    cCodeBuilder.appendLine("return $expression;")
    cCodeBuilder.appendLine("    // clang-format on")
    cCodeBuilder.appendLine("}")

    return cCodeBuilder.toString()
}

private const val INDENTATION = "  "

fun DecisionNode.getProbOrOutput(): String {
    val c = count()
    if (DECISION_TREE_OUTPUT_PROB) {
        val prob = DecisionNode.posteriori(c, DoubleArray(c.size))

        // The first probability is the one for is_mod = false (0).
        return prob[1].toFloat().toString() + "f"
    }
    return output().toString()
}

val CAN_OPTIMIZE = !decisionTreeGenerateKotlin && !DECISION_TREE_OUTPUT_PROB

/**
 * Recursively builds a nested ternary C expression string for a given node.
 *
 * @param node The current node to process.
 * @param featureNames A list of strings for feature names.
 * @param indentationLevel The current depth in the tree, used for formatting.
 * @return A formatted string representing the C expression for this subtree.
 */
private fun generateTernaryExpression(
    node: Node,
    featureNames: Array<String>,
    indentationLevel: Int
): String {
    return when (node) {
        is DecisionNode -> node.getProbOrOutput()

        is OrdinalNode -> {
            val indent = INDENTATION.repeat(indentationLevel)
            val featureName = getName(featureNames, node)

            // Recursively generate the expressions for the true and false branches.
            val trueExpr =
                generateTernaryExpression(node.trueChild(), featureNames, indentationLevel + 1)
            val falseExpr =
                generateTernaryExpression(node.falseChild(), featureNames, indentationLevel + 1)

            val value = getValue(node, featureName)
            if (CAN_OPTIMIZE && trueExpr == "1" && falseExpr == "0") {
                // Optimization: If true -> 1, if false -> 0. This is the condition itself.
                "$featureName <= $value"
            } else if (CAN_OPTIMIZE && trueExpr == "0" && falseExpr == "1") {
                // Optimization: If true -> 0, if false -> 1. This is the logical NOT of the condition.
                "$featureName > $value"
            } else {
                // Fallback to the general case. Now we generate the mask since it's needed.
                // This raw string format creates a compact, readable, and correctly indented
                // multiline ternary expression. The indentation of the closing parenthesis
                // is adjusted to align with the opening of the current expression block.
                """(
$indent$featureName <= $value
$indent? ${trueExpr.numToTrueAndFalse()}
$indent: ${falseExpr.numToTrueAndFalse()}
${INDENTATION.repeat(indentationLevel - 1)})"""
            }
        }

        else -> error("should never happen")
    }
}