package codegen

import smile.base.cart.OrdinalNode
import utils.*

private val NAMES_OF_FLOATS = sequenceOf(
    TrainCol.PTH_PRESS_TO_PRESS_W_AVG,
    TrainCol.PTH_OVERLAP_W_AVG,
    //TrainCol.RECENT_DUR_AVG,
).map { it.name.lowercase() }.toSet()


val RESULT_TYPE = if (decisionTreeGenerateKotlin) "int" else "uint8_t"

val GET_VALUE = OrdinalNode::class.java.getDeclaredField("value").apply {
    isAccessible = true
}::getDouble

fun getValue(node: OrdinalNode, featureName: String): String {
    val v = GET_VALUE(node)
    if (decisionTreeGenerateKotlin || featureName in NAMES_OF_FLOATS) {
        return v.toFloat().toString() + "f"
    }
    return v.toInt().toString()
}

fun getName(
    featureNames: Array<String>,
    node: OrdinalNode
): String {
    val featureName = featureNames.getOrElse(node.feature()) { "feature_${node.feature()}" }
    return if (decisionTreeGenerateKotlin) {
        "row.getDouble(TrainCol.$featureName.name)"
    } else {
        return featureName.lowercase()
    }
}

fun getHeader(): String {
    val mode = MODE.name
        .lowercase()
        .replace("_up", "_release")
        .replace("_down", "_press")

    var prefix = "${mode}_"
    var suffix = ""
    when (MODE) {
        Mode.FAST_STREAK_TAP -> {
        }

        Mode.PTH_UP_AFTER_SECOND_DOWN, Mode.PTH_UP_AFTER_SECOND_UP, Mode.THIRD_DOWN -> {
            prefix = "hold_"
            suffix = "_when_$mode"
        }

        Mode.OVERLAP_MS_FOR_HOLD -> {
            prefix = "${mode}_"
        }
    }

    val funcName = "pth_default_get_${prefix}prediction${suffix}"
    val t = if (DECISION_TREE_OUTPUT_PROB) "float" else null
    return if (decisionTreeGenerateKotlin) {
        "${t ?: "int"} $funcName(Row row) {"
    } else {
        "${t ?: "bool"} $funcName(void) {"
    }
}

val strNumToBool = mapOf("0" to "false", "1" to "true")

fun String.numToTrueAndFalse(): String {
    if (decisionTreeGenerateKotlin || DECISION_TREE_OUTPUT_PROB) {
        return this
    }
    return strNumToBool[this] ?: this
}