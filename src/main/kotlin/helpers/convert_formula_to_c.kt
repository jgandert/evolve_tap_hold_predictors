package helpers

// Regex to find floating-point numbers like "1.23" or "-0.5".
private val NUM_RE = Regex("""-?\d+\.\d+""")

// Set of keywords to convert to uppercase.
private val MAKE_UPPER = setOf("max", "min", "abs", "sd")

/**
 * Converts a formula from a shorthand syntax to a full, C-compatible format.
 */
fun convertToKotlinFormula(text: String): String {
    var result = text

    // Convert specific keywords (like "min", "max") to uppercase.
    // Using a Regex with word boundaries (\b) ensures we only replace whole words.
    MAKE_UPPER.forEach { keyword ->
        result = result.replace(Regex("\\b$keyword\\b"), keyword.uppercase())
    }

    // Normalize spacing around the multiplication operator.
    result = result.replace(Regex("""\s*\*\s*"""), " * ")

    // Find all floating-point numbers and append 'f' to make them float literals.
    result = NUM_RE.replace(result) { matchResult ->
        "${matchResult.value}f"
    }

    return "$result;"
}

fun main() {
    println("Enter your formula:\n")

    val input = readln()
    val output = convertToKotlinFormula(input)

    println()
    println("Converted Formula:")
    println(output)
    println()
    println("Make sure that (unsigned) ints in operations are converted to float first, if not doing so would change the result!")
}