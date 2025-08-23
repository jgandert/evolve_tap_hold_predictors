package codegen

import smile.base.cart.DecisionNode
import smile.base.cart.Node
import smile.base.cart.OrdinalNode
import java.util.concurrent.atomic.AtomicInteger


fun generateBranchlessC(
    root: Node,
    featureNames: Array<String>,
): String {
    val cCodeBuilder = StringBuilder()
    // A counter to ensure unique variable names (e.g., condition_0, mask_1, result_2)
    val varCounter = AtomicInteger(0)

    // Append C function signature and opening brace
    cCodeBuilder.appendLine("/**")
    cCodeBuilder.appendLine(" * Auto-generated branchless decision tree prediction function.")
    cCodeBuilder.appendLine(" */")

    cCodeBuilder.appendLine(getHeader())

    // Start the recursive generation
    val finalResultExpression = generateNodeCode(root, cCodeBuilder, featureNames, varCounter)

    // Add the final return statement
    cCodeBuilder.appendLine("    return $finalResultExpression;")
    cCodeBuilder.appendLine("}")

    return cCodeBuilder.toString()
}

/**
 * A recursive helper function to generate C code for a given node.
 *
 * @return The C expression (a literal or a variable name) for the result of this node's subtree.
 */
private fun generateNodeCode(
    node: Node,
    cCodeBuilder: StringBuilder,
    featureNames: Array<String>,
    varCounter: AtomicInteger
): String {
    return when (node) {
        // Base Case: If it's a leaf, the result is its direct output.
        is DecisionNode -> {
            node.output().toString()
        }

        // Recursive Step: If it's an internal node, generate the logic.
        is OrdinalNode -> {
            // 1. Recurse to get the result expressions for true and false branches.
            val trueResultExpr =
                generateNodeCode(node.trueChild(), cCodeBuilder, featureNames, varCounter)
            val falseResultExpr =
                generateNodeCode(node.falseChild(), cCodeBuilder, featureNames, varCounter)

            // 2. Get a unique ID for the C variables for this node.
            val nodeId = varCounter.getAndIncrement()
            val featureName = getName(featureNames, node)

            // 3. Generate and append the C code for the current node's logic.
            cCodeBuilder.appendLine()

            // The condition: (features[index] <= value) evaluates to 0 or 1
            // for some reason there is no accessor for the value
            val value = getValue(node, featureName)

            if (trueResultExpr == "1" && falseResultExpr == "0") {
                // Optimization: If true -> 1, if false -> 0. This is the condition itself.
                cCodeBuilder.appendLine(
                    "    $RESULT_TYPE result_$nodeId = $featureName <= ${value};"
                )
            } else if (trueResultExpr == "0" && falseResultExpr == "1") {
                // Optimization: If true -> 0, if false -> 1. This is the logical NOT of the condition.
                cCodeBuilder.appendLine(
                    "    $RESULT_TYPE result_$nodeId = $featureName > ${value};"
                )
            } else {
                // Fallback to the general case. Now we generate the mask since it's needed.
                cCodeBuilder.appendLine("    $RESULT_TYPE condition_$nodeId = $featureName <= ${value};")
                cCodeBuilder.appendLine("    $RESULT_TYPE mask_$nodeId = -condition_$nodeId;")
                cCodeBuilder.appendLine(
                    "    $RESULT_TYPE result_$nodeId = ($falseResultExpr & ~mask_$nodeId) | ($trueResultExpr & mask_$nodeId);"
                )
            }

            // 4. Return the name of the variable holding the result for this node.
            "result_$nodeId"
        }

        else -> error("should never happen")
    }
}


/*
before the optimization it was just:

            cCodeBuilder.appendLine("    $resultType condition_$nodeId = $featureName <= ${value};")

            // The mask: -1 (0xFFFFFFFF) if true, 0 if false
            cCodeBuilder.appendLine("    $intType mask_$nodeId = -condition_$nodeId;")

            // The bitwise selection
            cCodeBuilder.appendLine(
                "    $intType result_$nodeId = ($falseResultExpr & ~mask_$nodeId) | ($trueResultExpr & mask_$nodeId);"
            )
 */